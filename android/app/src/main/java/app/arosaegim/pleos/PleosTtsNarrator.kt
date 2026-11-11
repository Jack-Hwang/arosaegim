package app.arosaegim.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.arosaegim.ports.Narrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Narrator 의 Pleos TextToSpeech 3.3.1 (Mode.ON_DEVICE) 구현.
 * ON_DEVICE 는 네트워크와 앱 등록(Client ID/Secret)이 필요 없다(01문서).
 *
 * 핵심 흐름: 생성 → addEventListener → initialize → setLocale(KOREA) →
 * speak 시 오디오 포커스(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) 요청 → speak(text, isFinal=true) →
 * onDone/onStop 수신 → 포커스 반납.
 *
 * U6 미검증: initialize 직후 speak 가능 여부는 확인되지 않았다([UNKNOWN]) →
 * onReady 수신 전까지 isAvailable=false 로 유지하고, speak 도 onReady 전이면 실패를 반환한다.
 */
class PleosTtsNarrator(context: Context) : Narrator {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val readyFlag = AtomicBoolean(false)

    private val _ready = MutableStateFlow(false)

    /** onReady 수신 여부(U6 관찰 + AppContainer 선택 대기용). */
    val readyState: StateFlow<Boolean> = _ready.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    override val isAvailable: Boolean
        get() = readyFlag.get()

    /** speak 완료 대기 중인 코루틴. 리스너 콜백이 임의 스레드에서 올 수 있어 Atomic 으로 보호한다. */
    private val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null)
    private var focusRequest: AudioFocusRequest? = null

    private val listener = object : EventListener {
        override fun onReady() {
            readyFlag.set(true)
            _ready.value = true
        }

        override fun onStart() { /* 진행 표시 용도 없음 */ }

        override fun onDone() {
            finishSpeak(true)
        }

        override fun onError(type: ErrorType) {
            finishSpeak(false)
        }

        override fun onStop() {
            finishSpeak(false)
        }

        override fun onUpdatedRms(rms: Double) { /* RMS 표시 생략 */ }
    }

    init {
        // [F30 실측] initialize 전에 addEventListener 를 부르면 UninitializedPropertyAccessException 이
        // 발생한다(에뮬레이터 v2.1.0 첫 기동에서 확인). 따라서 initialize 를 먼저 호출하고 리스너를 등록한다.
        // onReady 는 등록 이후에 올 것을 가정하며, 누락 시 U6 에서 isAvailable=false 로 관측된다.
        runCatching { tts.initialize() }
            .onFailure { t -> _initError.value = "initialize 실패: ${t.javaClass.simpleName}: ${t.message ?: ""}" }
        runCatching { tts.addEventListener(listener) }
            .onFailure { t -> _initError.value = "addEventListener 실패: ${t.javaClass.simpleName}" }
        runCatching { tts.setLocale(Locale.KOREA) } // ko-KR 지원(01문서 확인)
            .onFailure { t -> _initError.value = "setLocale 실패: ${t.javaClass.simpleName}" }
    }

    /**
     * 안내 문장 1건 재생. onDone 을 최대 5초 기다린다.
     * [REPORTED] 재생이 끝났는데도 onDone 이 안 오는 사례 대응 타임아웃(01문서 §5).
     * [UNKNOWN] 선행 묵음 150~200ms 는 PCM/WAV 패딩으로만 가능 — TTS 텍스트로는 삽입 불가 →
     * T5(주행 음성)/T6(정차 음성) 과제로 남긴다.
     */
    override suspend fun speak(text: String): Result<Unit> {
        if (!readyFlag.get()) {
            return Result.failure(
                IllegalStateException("onReady 미수신 상태(U6 미검증: 초기화 직후 speak 가능 여부)"),
            )
        }
        if (!requestFocus()) {
            return Result.failure(IllegalStateException("오디오 포커스 획득 실패"))
        }

        val done = try {
            withTimeoutOrNull(SPEAK_TIMEOUT_MS) { awaitDone(text) }
        } catch (t: Throwable) {
            releaseFocus()
            return Result.failure(t)
        }

        releaseFocus()
        return when {
            done == true -> Result.success(Unit)
            done == false -> Result.failure(IllegalStateException("재생 중단(onStop/onError)"))
            else -> {
                // 타임아웃: onDone 누락 사례 대응. 남은 재생을 끊고 실패로 보고한다.
                runCatching { tts.stop() }
                Result.failure(IllegalStateException("onDone ${SPEAK_TIMEOUT_MS}ms 미수신(타임아웃)"))
            }
        }
    }

    override fun stop() {
        runCatching { tts.stop() }
        releaseFocus()
        // 대기 중인 speak 는 중단으로 마무리한다.
        pending.getAndSet(null)?.let { cont ->
            try {
                cont.resume(false)
            } catch (t: IllegalStateException) { /* 이미 취소된 continuation */ }
        }
    }

    /** 종료 시 일괄 해제(AppContainer.closeAll 전용). 리스너 해제 → release 순서를 지킨다. */
    fun release() {
        stop()
        runCatching { tts.removeEventListener(listener) }
        runCatching { tts.release() }
    }

    /** speak(text, true) 호출 후 onDone/onStop/onError 를 기다린다. */
    private suspend fun awaitDone(text: String): Boolean =
        suspendCancellableCoroutine { cont ->
            pending.set(cont)
            cont.invokeOnCancellation {
                if (pending.compareAndSet(cont, null)) {
                    // 타임아웃/외부 취소 시 재생을 끊는다(포커스는 speak 의 finally 에서 반납).
                    runCatching { tts.stop() }
                }
            }
            val queued = runCatching { tts.speak(text, true) }.isSuccess
            if (!queued) {
                if (pending.compareAndSet(cont, null)) {
                    cont.resume(false)
                }
            }
        }

    /** 리스너 콜백에서 speak 대기를 마무리한다(1회만 재개). */
    private fun finishSpeak(ok: Boolean) {
        val cont = pending.getAndSet(null) ?: return
        try {
            cont.resume(ok)
        } catch (t: IllegalStateException) { /* 이미 취소/완료된 continuation */ }
    }

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
            .also { focusRequest = it }
        return runCatching {
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
    }

    private fun releaseFocus() {
        val req = focusRequest ?: return
        runCatching { audioManager.abandonAudioFocusRequest(req) }
    }

    private companion object {
        /** [REPORTED] onDone 누락 사례 대응 — 단문 안내 기준 5초. */
        const val SPEAK_TIMEOUT_MS = 5_000L
    }
}

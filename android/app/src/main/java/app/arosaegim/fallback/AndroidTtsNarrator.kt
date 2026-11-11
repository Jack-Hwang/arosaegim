package app.arosaegim.fallback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.arosaegim.ports.Narrator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Narrator 의 안드로이드 기본 TTS(android.speech.tts) 폴백 구현.
 * Pleos TTS 서비스가 응답하지 않는 환경(AppContainer 가 onReady 미수신으로 판단)에서 쓰인다.
 * 'Pleos → Android TTS 자동 폴백'은 SDK 기능이 아니므로 직접 분기한다(01문서 §5).
 *
 * 핵심 흐름: 엔진 생성(콜백 초기화) → ko-KR 설정 → speak 시 오디오 포커스(MAY_DUCK) →
 * UtteranceProgressListener.onDone 대기(5초 타임아웃) → 포커스 반납.
 */
class AndroidTtsNarrator(context: Context) : Narrator {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val engineRef = AtomicReference<TextToSpeech?>(null)
    private val engineReady = AtomicBoolean(false)
    private val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null)
    private var focusRequest: AudioFocusRequest? = null

    override val isAvailable: Boolean
        get() = engineReady.get()

    /** 엔진 초기화 결과 콜백(TTS 서비스 스레드에서 호출될 수 있어 Atomic 으로 보호). */
    private fun onEngineReady(status: Int) {
        engineReady.set(status == TextToSpeech.SUCCESS)
        if (status == TextToSpeech.SUCCESS) {
            // ko-KR 음성 데이터가 없는 엔진이면 setLanguage 실패 — 이후 speak 는 엔진 기본 언어로 시도.
            runCatching { engineRef.get()?.language = Locale.KOREA }
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            // 대기 시작 표시는 불필요.
        }

        override fun onDone(utteranceId: String?) {
            finishSpeak(true)
        }

        @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
        override fun onError(utteranceId: String?) {
            finishSpeak(false)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            finishSpeak(false)
        }
    }

    init {
        val created = TextToSpeech(context.applicationContext) { status -> onEngineReady(status) }
        created.setOnUtteranceProgressListener(progress)
        engineRef.set(created)
    }

    /**
     * 안내 문장 1건 재생. onDone 을 최대 5초 기다린다(Pleos 구현과 동일한 [REPORTED] 대응).
     * 재생 중단(stop) 시에도 포커스를 반납한다.
     */
    override suspend fun speak(text: String): Result<Unit> {
        val tts = engineRef.get()
        if (tts == null || !engineReady.get()) {
            return Result.failure(IllegalStateException("Android TTS 초기화 미완료/실패"))
        }
        if (!requestFocus()) {
            return Result.failure(IllegalStateException("오디오 포커스 획득 실패"))
        }

        val done = try {
            withTimeoutOrNull(SPEAK_TIMEOUT_MS) { awaitDone(tts, text) }
        } catch (t: Throwable) {
            releaseFocus()
            return Result.failure(t)
        }

        releaseFocus()
        return when {
            done == true -> Result.success(Unit)
            done == false -> Result.failure(IllegalStateException("재생 중단(onError/stop)"))
            else -> {
                runCatching { tts.stop() }
                Result.failure(IllegalStateException("onDone ${SPEAK_TIMEOUT_MS}ms 미수신(타임아웃)"))
            }
        }
    }

    override fun stop() {
        runCatching { engineRef.get()?.stop() }
        releaseFocus()
        pending.getAndSet(null)?.let { cont ->
            try {
                cont.resume(false)
            } catch (t: IllegalStateException) { /* 이미 취소된 continuation */ }
        }
    }

    private suspend fun awaitDone(tts: TextToSpeech, text: String): Boolean =
        suspendCancellableCoroutine { cont ->
            pending.set(cont)
            cont.invokeOnCancellation {
                if (pending.compareAndSet(cont, null)) {
                    runCatching { tts.stop() }
                }
            }
            val queued = runCatching {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            }.getOrDefault(TextToSpeech.ERROR)
            if (queued == TextToSpeech.ERROR) {
                if (pending.compareAndSet(cont, null)) {
                    cont.resume(false)
                }
            }
        }

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
        const val SPEAK_TIMEOUT_MS = 5_000L
        const val UTTERANCE_ID = "arosaegim-narration"
    }
}

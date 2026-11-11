package app.arosaegim.pleos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import app.arosaegim.domain.VoiceLetter
import app.arosaegim.ports.VoiceLetterPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * VoiceLetterPlayer 의 Pleos(assets) 구현 — T9-2 예시 음성 편지 재생(MediaPlayer).
 *
 * 계약 주석: play 는 정차(PARKED) 상태에서만 호출된다는 계약을 가진다(00문서 11-1 T9-2:
 * '해당 장소에 도착해 정차했을 때만 재생 버튼으로 연다'). 다만 포트가 PARKED 를 강제할 수는
 * 없으므로, 실제 게이트는 호출부(UI)가 DrivingState 로 맡는다. 이 클래스는 강제하지 않는다.
 *
 * 재생 경로(A1, 2026-11-11): **사전 제작 오디오가 기본 경로다.** Pleos TTS 합성은 쓰지 않고,
 * 에셋에 준비된 오디오 파일을 MediaPlayer 로 재생한다. 파일 탐색 순서:
 *  1. 시드 편지의 audioAsset("assets/seed/letter_demo.ogg" 등)에 실제 파일이 있으면 그것.
 *  2. 없으면 사전 제작 데모 오디오 [PREBUILT_AUDIO_CANDIDATES] 를 순서대로 확인해
 *     처음 존재하는 파일(assets/voice/letter_demo.wav)을 쓴다.
 *     — seed.json 은 수정 금지라 시드 audio 경로와 실제 파일 위치가 어긋날 수 있어,
 *       재생 경로만 사전 제작 파일로 돌린다(시드 데이터 자체는 그대로다).
 * 어느 파일도 없으면 [isAvailable]=false 로 '조용히 사용 불가' 처리하고 play 는 실패로 답한다
 * (가짜 재생 금지 — 03문서: 대체 구현 사용을 숨기지 않는다). UI 는 이 경우 텍스트 편지를 보여준다.
 *
 * 오디오 포커스는 Narrator(AndroidTtsNarrator) 패턴과 같은 GAIN_TRANSIENT_MAY_DUCK 을 쓴다.
 * 고인의 목소리 재현 원칙: 이 재생은 사전 제작된 일반 음성(데모용)이며 음성 합성으로 어떤
 * 사람의 목소리도 만들지 않는다(00문서 voice_letter_demo 규정 준수).
 */
class PleosVoiceLetterPlayer(
    context: Context,
    /** 시드의 예시 편지 목록(AppContainer 가 seed.voiceLetters 로 주입). */
    private val letters: List<VoiceLetter>,
) : VoiceLetterPlayer {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** 재생 중인 플레이어. stop()·완료 콜백이 release 한다. */
    private val playerRef = AtomicReference<MediaPlayer?>(null)

    /** 재생 완료 대기 continuation(AndroidTtsNarrator 와 같은 보호 방식). */
    private val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null)
    private var focusRequest: AudioFocusRequest? = null

    /**
     * 오디오 포커스 변화 수신(01문서 규칙 — 포커스를 잃으면 즉시 멈춘다).
     * LOSS(전체 상실)·LOSS_TRANSIENT(일시 상실) 수신 시 [stop] 으로 즉시 멈춘다 — 진행 중 play 는
     * '중단'으로 풀리고 MediaPlayer 는 release 된다. LOSS_TRANSIENT_CAN_DUCK 은 MAY_DUCK 으로
     * 요청했으므로 낮추며 계속 재생한다(멈추지 않는다).
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> stop()
            // LOSS_TRANSIENT_CAN_DUCK: 계속 재생(볼륨 낮춤은 시스템이 처리한다).
        }
    }

    /**
     * 사전 제작 오디오가 하나라도 준비돼 있어야 '사용 가능'이다(없으면 UI 는 텍스트 편지로 대체).
     * 재생 경로와 같은 [resolveAsset] 규칙을 쓴다 — 시드 audioAsset 파일이 없어도 사전 제작
     * 데모 오디오가 있으면 사용 가능이다.
     */
    override val isAvailable: Boolean by lazy {
        letters.any { letter -> resolveAsset(letter) != null }
    }

    /** 예시 편지 1건 재생. 완료(onCompletion)를 기다렸다가 성공으로 답한다. */
    override suspend fun play(letterId: String): Result<Unit> {
        val letter = letters.firstOrNull { it.id == letterId }
            ?: return Result.failure(IllegalStateException(NOT_READY_MESSAGE))
        val assetPath = resolveAsset(letter)
            ?: return Result.failure(IllegalStateException(NOT_READY_MESSAGE))

        if (!requestFocus()) {
            return Result.failure(IllegalStateException("오디오 포커스 획득 실패"))
        }

        // true = 정상 완료, false = 오류·stop 으로 중단, null = 타임아웃·시작 실패.
        val done = runCatching {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(PLAY_TIMEOUT_MS) { awaitDone(assetPath) }
            }
        }.getOrNull()

        if (done != true) {
            // 타임아웃 경로는 콜백이 오지 않아 플레이어가 남는다 — 여기서 정리한다.
            runCatching { playerRef.getAndSet(null)?.let { it.stop(); it.release() } }
        }
        releaseFocus()
        return when (done) {
            true -> Result.success(Unit)
            false -> Result.failure(IllegalStateException("재생 중단(stop 또는 재생 오류)"))
            null -> Result.failure(IllegalStateException(NOT_READY_MESSAGE))
        }
    }

    /** 재생을 즉시 중단한다(주행 재개 시 UI 가 부른다 — 00문서 T9-2 완주 기준). */
    override fun stop() {
        runCatching { playerRef.get()?.stop() }
        finishPlay(false) // 대기 중이던 play 를 '중단'으로 푼다.
    }

    /** 내부 자원 해제(AppContainer.closeAll 용 — 포트에 없는 보조 함수). */
    fun release() {
        stop()
        runCatching { playerRef.getAndSet(null)?.release() }
    }

    /**
     * 편지 1건의 재생 파일을 고른다(A1 사전 제작 오디오 우선).
     * ① 시드 audioAsset 에 실제 파일이 있으면 그것 ② 없으면 사전 제작 데모 오디오 후보 중
     * 처음 존재하는 것 ③ 둘 다 없으면 null(사용 불가 — 재생 생략).
     */
    private fun resolveAsset(letter: VoiceLetter): String? {
        letter.audioAsset
            ?.takeIf { it.isNotBlank() && assetExists(it.removePrefix("assets/")) }
            ?.let { return it.removePrefix("assets/") }
        return PREBUILT_AUDIO_CANDIDATES.firstOrNull { assetExists(it) }
    }

    /** assets 에 상대 경로 파일이 실제로 존재하는지 확인한다(열어 보고 닫는다). */
    private fun assetExists(relativePath: String): Boolean =
        runCatching { appContext.assets.openFd(relativePath).close() }.isSuccess

    /**
     * MediaPlayer 생성 → assets 재생 → onCompletion 대기.
     * 시스템 콜백은 다른 스레드에서 올 수 있어 continuation 은 Atomic 으로 보호한다.
     */
    private suspend fun awaitDone(assetPath: String): Boolean? =
        suspendCancellableCoroutine { cont ->
            pending.set(cont)
            cont.invokeOnCancellation { finishPlay(false) }
            try {
                val player = MediaPlayer()
                playerRef.set(player)
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA) // 음성 편지 재생은 미디어 계열이다.
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                assetFd(assetPath).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                player.setOnCompletionListener { finishPlay(true) }
                player.setOnErrorListener { _, _, _ ->
                    finishPlay(false)
                    true // onError 리스너가 true 를 돌려주면 시스템의 완료 콜백은 오지 않는다.
                }
                player.prepare() // 작은 자산 파일이므로 동기 prepare 로 충분하다(IO 디스패처).
                player.start()
            } catch (t: Throwable) {
                runCatching { playerRef.getAndSet(null)?.release() }
                if (pending.compareAndSet(cont, null) && cont.isActive) {
                    cont.resume(null) // 시작 실패 → '준비 중'과 같은 결과로 정규화한다.
                }
            }
        }

    /** 완료·중단 콜백 공통 처리. 대기 continuation 이 있으면 정리하고 푼다. */
    private fun finishPlay(ok: Boolean) {
        val cont = pending.getAndSet(null) ?: return
        runCatching { playerRef.getAndSet(null)?.release() }
        try {
            cont.resume(ok)
        } catch (t: IllegalStateException) { /* 이미 취소·완료된 continuation */ }
    }

    /** AssetManager 상대 경로("voice/letter_demo.wav")를 열기 위한 AssetFileDescriptor. */
    private fun assetFd(relativePath: String) = appContext.assets.openFd(relativePath)

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener) // 상실 시 즉시 stop(F54 규칙)
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
        /**
         * 사전 제작 데모 오디오 탐색 후보(AssetManager 상대 경로, 앞에서부터 첫 존재 파일).
         * 1순위는 A1 지시대로 만든 assets/voice/letter_demo.wav. 뒤쪽은 발주자가 시드 경로
         * 그대로(assets/seed/letter_demo.ogg·wav) 파일을 넣는 경우를 위한 호환 후보다.
         */
        val PREBUILT_AUDIO_CANDIDATES = listOf(
            "voice/letter_demo.wav",
            "seed/letter_demo.ogg",
            "seed/letter_demo.wav",
        )

        /** 편지 1건 최대 재생 대기. 음성 파일 길이를 모르므로 넉넉히 잡는다(초과 시 중단 처리). */
        const val PLAY_TIMEOUT_MS = 120_000L

        /** assets 에 사전 제작 오디오가 없을 때의 안내 문구 — 재생을 생략하고 실패로 답한다. */
        const val NOT_READY_MESSAGE = "사전 제작 오디오 없음 — 재생 생략"
    }
}

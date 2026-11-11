package app.arosaegim.fallback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import app.arosaegim.ports.Narrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Narrator 의 사전 제작 오디오(E4, 2026-11-11) 구현 — TTS 합성 없이 assets 오디오를 MediaPlayer 로
 * 재생한다.
 *
 * 배경(F54): 도착 문장·'다시 듣기'가 Pleos TTS/Android TTS 합성만 썼는데, 이 에뮬레이터에서는
 * 둘 다 실패해 소리가 나지 않았다. 자주 쓰는 문장(도착 안내 등)을 assets 오디오 파일로 미리
 * 준비해 두면 이 구현이 TTS 없이 그 파일을 재생한다(합성 음성 또는 발주자 직접 녹음만 사용 —
 * 고인 목소리 AI 재생성 금지, 00문서 11-2. 파일 규격은 assets/audio/README.md 참조).
 *
 * 매핑 방식: 도착 문장("{장소명}에 도착했어요. 스탬프를 찍었어요.")은 장소명이 가변이라 문장 전체를
 * 미리 매핑할 수 없다. 그래서 assets/audio/ 폴더의 **파일명 ↔ 키 매핑 테이블**을 쓴다 —
 * 파일명이 곧 키다(예: audio/arrival_gj-museum.ogg 의 키 = "arrival_gj-museum").
 * AppContainer 는 시드의 장소로 문장 → 키 정확 매핑을 만들어 주입하고, 주입이 없으면
 * [defaultSentences] 가 assets/audio/ 를 훑어 파일 기준 기본 매핑을 만든다.
 * speak(text) 는 text(또는 변형 문장을 정규화한 문장)가 매핑된 파일이 있으면 재생하고,
 * 없으면 Result.failure("사전 제작 오디오 없음") 로 답한다(가짜 재생 금지).
 *
 * 오디오 포커스(F54 규칙 — 포커스를 잃으면 즉시 멈춘다): 요청은
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK(PleosTtsNarrator 와 같은 패턴), LOSS·LOSS_TRANSIENT 수신 시
 * 즉시 stop 한다. CAN_DUCK 은 MAY_DUCK 요청에 대응하는 낮추기 신호이므로 계속 재생한다.
 */
class AssetAudioNarrator(
    context: Context,
    // assets/audio/ 파일 기준 기본 매핑(선택). AppContainer 는 시드 문장 기준 매핑을 주입한다.
    private val sentences: Map<String, String> = defaultSentences(context),
) : Narrator {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** 재생 중인 플레이어. stop()·완료 콜백이 release 한다(PleosVoiceLetterPlayer 와 같은 보호). */
    private val playerRef = AtomicReference<MediaPlayer?>(null)

    /** 재생 완료 대기 continuation(AndroidTtsNarrator 와 같은 Atomic 보호 — 콜백 스레드 불문). */
    private val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null)
    private var focusRequest: AudioFocusRequest? = null

    /**
     * 매핑된 오디오 파일이 하나라도 실제로 존재할 때만 사용 가능이다(파일 없는 매핑은 failure 규약).
     */
    override val isAvailable: Boolean by lazy {
        sentences.values.any { assetExists(it) }
    }

    /** 오디오 포커스 변화 수신 — 상실(전체·일시)이면 즉시 멈춘다(F54 규칙, 01문서). */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> stop() // 즉시 멈춤 — 진행 중 play 는 '중단'으로 풀린다.
            // LOSS_TRANSIENT_CAN_DUCK: MAY_DUCK 으로 요청했으므로 낮추며 계속 재생한다.
        }
    }

    /**
     * 문장 1건 재생. 매핑된 파일이 있으면 MediaPlayer 로 재생하고 완료를 기다린다.
     * 매핑이 없거나 파일이 없으면 "사전 제작 오디오 없음" 실패를 돌려준다.
     */
    override suspend fun speak(text: String): Result<Unit> {
        val assetPath = resolveAsset(text)
            ?: return Result.failure(IllegalStateException(MISSING_MESSAGE))

        if (!requestFocus()) {
            return Result.failure(IllegalStateException("오디오 포커스 획득 실패"))
        }

        // true = 정상 완료, false = 중단(포커스 상실·stop·재생 오류), null = 타임아웃·시작 실패.
        val done = runCatching {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(PLAY_TIMEOUT_MS) { awaitDone(assetPath) }
            }
        }.getOrNull()

        if (done != true) {
            // 타임아웃·시작 실패 경로는 콜백이 오지 않아 플레이어가 남는다 — 여기서 정리한다.
            runCatching { playerRef.getAndSet(null)?.let { it.stop(); it.release() } }
        }
        releaseFocus()
        return when (done) {
            true -> Result.success(Unit)
            false -> Result.failure(IllegalStateException("사전 제작 오디오 재생 중단(포커스 상실·stop·오류)"))
            else -> Result.failure(IllegalStateException("사전 제작 오디오 완료 미수신(타임아웃·시작 실패)"))
        }
    }

    /** 재생을 즉시 중단한다. 포커스 상실 리스너와 closeAll 정리가 함께 쓴다. */
    override fun stop() {
        runCatching { playerRef.get()?.stop() }
        finishPlay(false) // 대기 중이던 speak 를 '중단'으로 푼다(플레이어 release 포함).
        releaseFocus()
    }

    /** 내부 자원 해제(AppContainer.closeAll 용 보조 함수 — 포트에 없다). */
    fun release() = stop()

    /**
     * text → assets 상대 경로 해석.
     *  ① sentences[text] 정확 일치  ② 없으면 T6-2 인원 변형 문장("오늘은 N명이 함께 왔어요.")을
     * 벗긴 기본 문장으로 재조회  ③ 매핑이 있어도 실제 파일이 없으면 null(실패 규약).
     */
    private fun resolveAsset(text: String): String? {
        val mapped = sentences[text]
            ?: sentences[text.replace(PARTY_SEGMENT, "").trim()]
            ?: return null
        return mapped.takeIf { assetExists(it) }
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
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                appContext.assets.openFd(assetPath).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                player.setOnCompletionListener { finishPlay(true) }
                player.setOnErrorListener { _, _, _ ->
                    finishPlay(false)
                    true // onError 에서 true 를 돌려주면 시스템 완료 콜백은 오지 않는다.
                }
                player.prepare() // 작은 자산 파일이므로 동기 prepare 로 충분하다(IO 디스패처).
                player.start()
            } catch (t: Throwable) {
                runCatching { playerRef.getAndSet(null)?.release() }
                if (pending.compareAndSet(cont, null) && cont.isActive) {
                    cont.resume(null) // 시작 실패 → 타임아웃과 같은 결과로 정규화한다.
                }
            }
        }

    /** 완료·중단 콜백 공통 처리. 대기 continuation 이 있으면 플레이어를 정리하고 푼다. */
    private fun finishPlay(ok: Boolean) {
        val cont = pending.getAndSet(null) ?: return
        runCatching { playerRef.getAndSet(null)?.release() }
        try {
            cont.resume(ok)
        } catch (t: IllegalStateException) { /* 이미 취소·완료된 continuation */ }
    }

    /** 재생 전 오디오 포커스 요청. 상실 감시는 [focusListener] 가 맡는다(F54 규칙). */
    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
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

    companion object {
        /** 진단 화면 표시명(DiagnosticsViewModel 의 '구현:' 줄이 쓴다). */
        const val DISPLAY_NAME = "사전 제작 오디오(예시 문장)"

        /**
         * 기본 매핑: assets/audio/ 폴더를 훑어 오디오 파일명(확장자 제외)을 키로 삼는다 —
         * audio/arrival_gj-museum.ogg 의 키는 "arrival_gj-museum". 폴더가 없거나 비어 있으면
         * 빈 매핑(= 항상 failure, isAvailable=false). 오디오 확장자가 아닌 파일(예: README.md)은
         * 매핑에서 제외한다. assets 탐색에 Context 가 필요해 기본값 식이 context 를 받는다.
         */
        fun defaultSentences(context: Context): Map<String, String> = runCatching {
            context.applicationContext.assets.list(AUDIO_DIR).orEmpty()
                .filter { file -> file.substringAfterLast('.').lowercase() in AUDIO_EXTENSIONS }
                .associate { file -> file.substringBeforeLast('.') to "$AUDIO_DIR/$file" }
        }.getOrDefault(emptyMap())

        /** TripSession T6-2 인원 변형 문장을 기본 도착 문장으로 되돌리기 위한 정규식 조각. */
        private val PARTY_SEGMENT = Regex("오늘은 \\d+명이 함께 왔어요\\. ?")

        private const val AUDIO_DIR = "audio"

        /** 기본 매핑에서 오디오 파일로 인정할 확장자(README.md 등 문서 파일 제외용). */
        private val AUDIO_EXTENSIONS = setOf("ogg", "wav", "mp3", "m4a", "aac", "flac")

        /** 매핑·파일이 없을 때의 안내 문구 — 재생을 생략하고 실패로 답한다(가짜 재생 금지). */
        private const val MISSING_MESSAGE = "사전 제작 오디오 없음"

        /** 도착 안내 1문장 기준 재생 대기. 파일 길이를 모르므로 넉넉히 잡는다(초과 시 중단 처리). */
        private const val PLAY_TIMEOUT_MS = 30_000L
    }
}

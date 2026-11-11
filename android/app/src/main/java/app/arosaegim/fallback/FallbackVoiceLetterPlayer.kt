package app.arosaegim.fallback

import app.arosaegim.ports.VoiceLetterPlayer

/**
 * VoiceLetterPlayer 의 대체 구현(T9-2). 예시 음성 파일이 없는 환경(AppContainer 가
 * Pleos 구현의 isAvailable=false 를 확인)에서 쓰인다.
 *
 * 항상 failure("예시 음성 준비 중") 로 답한다 — 가짜 재생을 만들지 않는다. UI 는 이 결과를
 * 받아 텍스트 편지(sealedLabel·text)를 보여주는 용도로 이 포트를 둔다.
 * 대체 구현이 쓰인다는 사실은 AppContainer.adapterNotes 로 표시한다(00문서 11-2).
 */
class FallbackVoiceLetterPlayer : VoiceLetterPlayer {

    override val isAvailable: Boolean = false

    override suspend fun play(letterId: String): Result<Unit> =
        Result.failure(IllegalStateException("사전 제작 오디오 없음 — 재생 생략"))

    override fun stop() = Unit

    override fun toString(): String = "FallbackVoiceLetterPlayer(텍스트 편지 표시용)"
}

package app.arosaegim.domain

/**
 * 음성 편지 한 건(T9-2). 이번 범위는 시드의 예시 편지 1건 재생이 전부다 — 실제 녹음·전송 기능은
 * 만들지 않는다(00문서 11-2: 예시 음성은 합성 음성 또는 발주자 직접 녹음만. 고인의 목소리를
 * AI 로 만들거나 흉내 내지 않는다).
 *
 * @property id          편지 식별자(예: "letter-demo-1"). VoiceLetterPlayer.play(letterId) 의 키.
 * @property placeId     편지가 묶인 장소 id(예: "gj-museum"). 해당 장소 도착·정차 시에만 연다.
 * @property sealedLabel 화면에 먼저 보이는 봉인 문구(예: "지난 방문에 남긴 한마디").
 * @property text        텍스트 편지 본문. audio 가 없을 때 UI 가 보여주는 대체 콘텐츠다.
 * @property audioAsset  재생용 오디오 자산 경로(예: "assets/seed/letter_demo.ogg").
 *                       파일이 없으면 null 취급하고 재생은 실패 처리한다('예시 음성 준비 중').
 * @property simulated   시뮬레이션 표식. 데모 전용 타입이므로 기본값 true — 시드에 값이
 *                       빠져도 '예시'로 안전하게 취급된다.
 */
data class VoiceLetter(
    val id: String,
    val placeId: String,
    val sealedLabel: String,
    val text: String,
    val audioAsset: String?,
    val simulated: Boolean = true,
)

package app.arosaegim.data

import app.arosaegim.domain.Course
import app.arosaegim.domain.Place
import app.arosaegim.domain.StampBookDef
import app.arosaegim.domain.VoiceLetter

/**
 * 시드(seed.json) 파싱 결과를 담는 저장소(data 계층) 공개 타입 모음.
 *
 * 도메인 모델([Course], [Place], [StampBookDef])은 app.arosaegim.domain 의 모델을 그대로 재사용하고,
 * 이 파일의 타입들은 시드 파일 특유의 정보(초안 여부 집계, 데모 혜택·제휴처 등)를 추가로 담는다.
 */

/**
 * 시드 파일 전체를 해석한 결과.
 *
 * @property course                코스 정보. 시드 course.place_ids 순서대로 places 를 채워 넣은 [Course].
 * @property places                시드에 선언된 장소 목록(선언 순서 유지. place_ids 에 없는 장소도 포함).
 *                                 Place.coordVerified / narrationReviewed / QuizItem.reviewed 는
 *                                 도메인 필드가 1차 출처(진실의 원천)이다.
 * @property thenNowDemo           '그때 vs 지금' 데모 기록. 시드에 없으면 null.
 * @property offersDemo            데모용 혜택 목록(서버 없이 화면 예시로만 사용, 시드의 simulated=true 전용).
 * @property draftQuizKeys         아직 검수되지 않은('초안') 퀴즈의 키 집합 — 도메인 필드(reviewed)의
 *                                 UI 편의 뷰(집합). 도메인 필드가 1차 출처, 이 집합은 UI 표시 편의용.
 *                                 형식: "placeId#퀴즈배열인덱스(0부터)" 예) "gj-museum#0".
 * @property unverifiedCoordPlaceIds 좌표 검증이 안 된 장소 id 집합 — 도메인 필드(coordVerified)의
 *                                 UI 편의 뷰(집합). UI 는 해당 장소 좌표에 '대략값' 표시를 붙일 수 있다.
 * @property stampBooks            스탬프북 정의 목록. 시드에 없으면 빈 목록(다른 호출부 호환 기본값).
 * @property partnersDemo          데모용 제휴처 목록(전부 가상, simulated=true). 시드에 없으면 빈 목록.
 * @property voiceLetters          예시 음성 편지 목록(T9-2). 시드 voice_letter_demo 단일 객체를
 *                                 목록 1건으로 정규화한다. 시드에 없으면 빈 목록(호환 기본값).
 * @property seatRoles             '오늘의 자리 이름표' 문구(T9-3 모바일 웹 연동용 데이터).
 *                                 시드 seat_roles(운전석/조수석 길잡이/뒷좌석 탐험가). 없으면 빈 목록.
 */
data class SeedContent(
    val course: Course,
    val places: List<Place>,
    val thenNowDemo: ThenNowDemo?,
    val offersDemo: List<OfferDemo>,
    val draftQuizKeys: Set<String>,
    val unverifiedCoordPlaceIds: Set<String>,
    val stampBooks: List<StampBookDef> = emptyList(),
    val partnersDemo: List<PartnerDemo> = emptyList(),
    val voiceLetters: List<VoiceLetter> = emptyList(),
    val seatRoles: List<String> = emptyList(),
)

/**
 * 시드 then_now_demo 항목(데모용 '그때' 기록).
 *
 * 도메인의 ThenNow 로의 변환은 ThenNowResolver 가 담당하므로 여기서는 시드 원문을 보관한다.
 * 실제 가족 사진은 사용자가 직접 올리고, 시드 photo 는 자리표시 이미지이다.
 *
 * @property photoAsset 시드 photo 필드 값(예: "assets/seed/then_placeholder.png").
 *                      AssetManager 로 열 때는 "seed/then_placeholder.png" 를 사용해야 한다.
 * @property label      연도 표시용 라벨(예: "1990년대 중반"). 시드의 절대 연도 대신 쓰는 표시값이며
 *                      없으면 null(화면은 '현재 연도 − yearsAgo'로 계산 — 00문서 10.4).
 * @property partySize  '그때 함께 간 인원'(가족 입력값, 2026-11-10 발주자 확정 5명).
 *                      T6-2 '오늘은 N명 … 그때는 M명' 문장의 M 이다. 시드에 없으면 null.
 */
data class ThenNowDemo(
    val placeId: String,
    val yearsAgo: Int,
    val year: Int? = null,   // 절대 연도(예: 1994) — 발주자 확정값. label 이 없을 때 표시용.
    val note: String,
    val vehicleLabel: String,
    val photoAsset: String,
    val label: String? = null,
    val partySize: Int? = null,
)

/**
 * 시드 offers_demo 항목(데모용 혜택). 시드는 전부 simulated=true 이므로 실제 결제·발급에 쓰지 않는다.
 */
data class OfferDemo(
    val id: String,
    val title: String,
    val condition: String,
)

/**
 * 시드 partners_demo 항목(데모용 제휴처). 실제 상호·결제·포인트 지급이 아니라 화면 예시 전용이다.
 *
 * @property pointRate  적립률(예: 0.02 = 2%). 데모 계산에만 쓴다(실 적립 연동 금지 — 00문서 9절).
 * @property simulated  시뮬레이션 표식. 데모 전용 타입이므로 기본값 true 로 두어,
 *                      시드에 값이 빠져도 '가상'으로 안전하게 취급된다.
 */
data class PartnerDemo(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val nearPlaceId: String = "",
    val pointRate: Double = 0.0,
    val simulated: Boolean = true,
)

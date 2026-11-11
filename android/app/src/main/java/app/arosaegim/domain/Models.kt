package app.arosaegim.domain

/**
 * 퀴즈 문항.
 *
 * @property reviewed 검수 여부. false 면 아직 검수 전 '초안'이다(00문서 4절·10절).
 *                    시드 집계용 draftQuizKeys 는 data 계층(SeedModels)이 관리하며,
 *                    도메인의 이 필드가 1차 출처(진실의 원천)이다.
 */
data class QuizItem(
    val q: String, val choices: List<String>, val answer: Int, val explain: String,
    val reviewed: Boolean = false,
)

/**
 * 장소. coordVerified=false 면 좌표가 대략값(지도 검증 전), narrationReviewed=false 면
 * 해설이 검수 전 '초안'이다 — UI 는 이 두 값을 보고 '초안' 표시를 붙인다(00문서 10절).
 *
 * heritage(T6-1): 이 장소의 유물 시간 정보(예: 성덕대왕신종). 시드에 없으면 null 이고
 * UI 는 heritage 가 있을 때만 '그때와 지금' 아래 타임라인을 한 줄 보여준다. 기존 필드는 그대로다.
 */
data class Place(
    val id: String, val name: String, val address: String,
    val lat: Double, val lon: Double, val arriveRadiusM: Int,
    val narration: String, val quiz: List<QuizItem>,
    val coordVerified: Boolean = false, val narrationReviewed: Boolean = false,
    val heritage: HeritageInfo? = null,
)

/**
 * 유물의 시간(then-now heritage 스펙, 00문서 then-now heritage 기준).
 *
 * @property name     유물 이름(예: "성덕대왕신종").
 * @property year     유물 연도(예: 771).
 * @property label    표시용 라벨(예: "1,250여 년"). 없으면 null.
 * @property reviewed 검수 여부. false 면 '초안' 표시 대상이다.
 * @property note     검수 메모(예: "주조 연도 771년(통일신라). 공식 자료로 검수 필요"). 없으면 null.
 */
data class HeritageInfo(
    val name: String, val year: Int, val label: String?, val reviewed: Boolean, val note: String?,
)

/** 코스: 장소들의 묶음. places 순서가 곧 방문 순서이다. */
data class Course(val id: String, val title: String, val places: List<Place>)

/** 주행(PARKED)/정차(DRIVING) 상태 구분값. */
enum class DrivingState { PARKED, DRIVING }

/** 차량 정보. VIN 은 규정상 저장·사용하지 않는다(00문서 9절 '하지 말아야 할 것'). */
data class VehicleInfo(val model: String?, val modelYear: Int?)

/**
 * '그때 vs 지금' 비교 정보.
 *
 * @property label 연도 표시용 라벨(예: "1990년대 중반"). 화면 표시는 label 을 우선하고,
 *                 label 이 없으면 '현재 연도 − yearsAgo'로 계산한다(00문서 10.4).
 */
data class ThenNow(
    val pastYear: Int?, val yearsAgo: Int?, val pastNote: String?, val pastPhoto: String?,
    val pastVehicle: String?, val nowVehicle: String?,
    val label: String? = null,
)

/**
 * 차량 신호 한 묶음(00문서 4절). 좌석 점유로 읽은 인원 수와 주행거리계 값을 함께 담는
 * 단순 스냅샷 타입이다. 안 읽힌 신호는 null 이다 — 가짜 값을 채우지 않는다(00문서 11-1 T5-1).
 *
 * 개인 식별 금지 규칙: partySize 는 '점유 좌석 수' 합계만 담는다. 좌석별·사람별 정보는
 * 만들지도 저장하지도 않는다(00문서 11-1 T6-2).
 */
data class TripSignals(val partySize: Int?, val odometerKm: Double?)

package app.arosaegim.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/*
 * 가족 기록 신규 API(heir·then-now·folded·metrics) 응답 파서(B1~B4, 2026-11-11).
 *
 * 공통 원칙(요구 문서 계약): 서버 응답에 필드가 없거나 파싱에 실패하면 '기능 숨김'이다.
 * 추측으로 문구·버튼을 만들지 않는다. 모든 파서는 순수 Kotlin이라 JVM 단위 테스트가 가능하다.
 */

/** then-now/folded/heir 응답 파싱 전용 Json(모르는 키 무시 — 서버 필드 추가에 흔들리지 않게). */
private val familyApiJson = Json { ignoreUnknownKeys = true }

// ─────────────────────────── B2·B4: then-now 장소 상태 ───────────────────────────

/**
 * then-now 조회에서 앱이 쓰는 필드만 담는 상태(B2 첫 기록·B4 접기).
 * 파싱 성공(visit_ordinal 이 응답에 있음)일 때만 존재한다 — 없으면 [parseThenNowPlaceState] 가
 * null 을 돌려 기능(첫 기록 문구·접기 버튼) 전체를 숨긴다(기존 동작 유지).
 *
 * @property visitOrdinal  이 장소의 이번 방문 순번(1부터 — 1이면 옛 기록·지난 방문이 전혀 없음).
 * @property pastVisitCount 지난 방문 수(0부터 — 서버가 주지 않으면 null).
 * @property foldAll       가족 설정 '모든 장소에서 인원 비교 문장 숨김'(fold_person_comparison_all).
 * @property folded        이 장소만 인원 비교 문장을 접어 둔 상태.
 */
data class ThenNowPlaceState(
    val visitOrdinal: Int,
    val pastVisitCount: Int?,
    val foldAll: Boolean?,
    val folded: Boolean?,
)

/** then-now 응답 원문 파싱. visit_ordinal 이 없거나 파싱 실패면 null(기능 숨김 — 추측 금지). */
fun parseThenNowPlaceState(body: String): ThenNowPlaceState? = runCatching {
    val dto = familyApiJson.decodeFromString<ThenNowPlaceDto>(body)
    val ordinal = dto.visitOrdinal ?: return@runCatching null
    ThenNowPlaceState(
        visitOrdinal = ordinal,
        pastVisitCount = dto.pastVisitCount,
        foldAll = dto.foldPersonComparisonAll,
        folded = dto.folded,
    )
}.getOrNull()

@Serializable
private data class ThenNowPlaceDto(
    @SerialName("visit_ordinal") val visitOrdinal: Int? = null,
    @SerialName("past_visit_count") val pastVisitCount: Int? = null,
    @SerialName("fold_person_comparison_all") val foldPersonComparisonAll: Boolean? = null,
    val folded: Boolean? = null,
)

/**
 * 인원 비교 문장을 숨겨야 하는지(B4). 이 장소 접힘(folded) 또는 가족 설정(foldAll) 중 하나라도
 * true 면 숨긴다. 상태 자체가 없으면(조회 실패) 기존 동작 — 문장을 숨기지 않는다.
 */
fun isPersonComparisonFolded(state: ThenNowPlaceState?): Boolean =
    state != null && (state.folded == true || state.foldAll == true)

/**
 * '접어 두기' 버튼을 보여야 하는지(B4). then-now 조회에 성공했고(상태 존재) 가족 설정으로
 * 이미 전부 숨겨진 상태가 아닐 때만 보인다. 조회 실패 시 버튼 없이 문장은 기존대로(계약).
 */
fun shouldShowFoldButton(state: ThenNowPlaceState?): Boolean =
    state != null && state.foldAll != true && state.folded != true

// ─────────────────────────── B3: 함께 달린 거리 누적 ───────────────────────────

/**
 * metrics 응답에서 family_total_trip_km(km, 숫자)을 읽는다(B3).
 * {"family_total_trip_km": 1240} / {"metrics":{"family_total_trip_km":1240.5}} 감싸기 둘 다
 * 흡수하고, 필드가 없거나 숫자로 못 읽으면 null — 누적 줄을 숨긴다(가짜 값 금지).
 */
fun parseFamilyTotalTripKm(body: String): Double? = runCatching {
    val root = familyApiJson.parseToJsonElement(body).jsonObject
    val obj = (root["metrics"] as? JsonObject) ?: root
    (obj["family_total_trip_km"] as? JsonPrimitive)?.let { p ->
        p.doubleOrNull ?: p.content.toDoubleOrNull()
    }
}.getOrNull()

/**
 * km 값을 표시용 문자열로 정리한다(B3 — "0이 아니면 소수점 정리(예: 1,240 km)").
 * 정수면 천 단위 콤마("1,240"), 소수가 남으면 한 자리("1,240.5"). 음수·NaN·∞ 는 null(숨김).
 */
fun formatFamilyTotalTripKm(km: Double): String? {
    if (!km.isFinite() || km <= 0.0) return null
    val rounded = Math.round(km * 10.0) / 10.0
    val whole = rounded.toLong()
    // 소수 한 자리는 반올림으로 정리한다 — 부동소수 오차(0.2999…→0)로 잘리지 않게.
    val fraction = Math.round((rounded - whole) * 10.0).toInt()
    val grouped = String.format(java.util.Locale.US, "%,d", whole)
    return if (fraction == 0) grouped else "$grouped.$fraction"
}

// ─────────────────────────── B1: 이어받을 사람(heir) ───────────────────────────

/**
 * GET /v1/families/me/heir 결과. '미지정(null)'과 '모름(필드 없음·파싱 실패)'을 구별한다 —
 * B1 재문 금지 조건이 '이미 정해져 있음'만 걸러내므로, 모를 때는 질문하지 않는다(추측 금지).
 */
sealed interface HeirStatus {
    /** 조회 성공. [memberId] 가 null 이면 미지정(질문 가능), 아니면 이미 정해짐(질문 금지). */
    data class Known(val memberId: String?) : HeirStatus

    /** 필드 없음·파싱 실패 — 질문하지 않고 기능을 숨긴다. */
    data object Unknown : HeirStatus
}

/**
 * heir 응답({"member_id": "<id>" | null}) 파싱. member_id 필드가 실제로 응답에 없으면 Unknown
 * (기능 숨김). decodeFromString 은 기본값을 흡수해 필드 유무를 흐리므로 JSON 키로 직접 본다.
 * JSON null 은 '미지정'과 같다(Known(null)).
 */
fun parseHeirStatus(body: String): HeirStatus = runCatching {
    val obj = familyApiJson.parseToJsonElement(body).jsonObject
    if (!obj.containsKey("member_id")) {
        HeirStatus.Unknown
    } else {
        HeirStatus.Known((obj["member_id"] as? JsonPrimitive)?.content?.takeIf { it != "null" })
    }
}.getOrDefault(HeirStatus.Unknown)

/**
 * 가족 구성원 1건 — B1 질문 카드의 등록 가족 버튼 목록용.
 * members 응답({"id":1,"nickname":"…",…})에서 id·nickname 만 받는다. id 는 서버가 숫자로
 * 주므로 숫자·문자열을 모두 흡수해 문자열로 정규화한다.
 */
data class HeirCandidate(
    val memberId: String,
    val nickname: String,
)

/**
 * members 응답을 heir 후보 목록으로 바꾼다. 배열([ {...} ]) 또는 {"members":[ … ]} 감싸기 둘 다
 * 흡수한다. id·nickname 이 없는 구성원은 건너뛴다. 파싱 실패는 빈 목록(→ 질문 카드 숨김).
 */
fun parseHeirCandidates(body: String): List<HeirCandidate> = runCatching {
    val root = familyApiJson.parseToJsonElement(body)
    val array = when (root) {
        is kotlinx.serialization.json.JsonArray -> root
        is JsonObject -> (root["members"] as? kotlinx.serialization.json.JsonArray)
            ?: return emptyList()
        else -> return emptyList()
    }
    array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = when (val raw = obj["id"] as? JsonPrimitive) {
            null -> null
            else -> raw.intOrNull?.toString() ?: raw.content.takeIf { it.isNotBlank() }
        } ?: return@mapNotNull null
        val nickname = (obj["nickname"] as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        HeirCandidate(memberId = id, nickname = nickname)
    }
}.getOrDefault(emptyList())

/** POST /v1/families/me/heir 요청 몸체({"member_id": "<id>"}). */
fun heirRequestBody(memberId: String): String =
    familyApiJson.encodeToString(HeirRequestDto(memberId))

@Serializable
private data class HeirRequestDto(@SerialName("member_id") val memberId: String)

/** POST /v1/families/me/folded-places 요청 몸체({"place_id": "<id>"}). */
fun foldedPlaceRequestBody(placeId: String): String =
    familyApiJson.encodeToString(FoldedPlaceRequestDto(placeId))

@Serializable
private data class FoldedPlaceRequestDto(@SerialName("place_id") val placeId: String)

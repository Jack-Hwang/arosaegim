package app.arosaegim.ui.parked

import app.arosaegim.data.ApiClient
import java.util.Calendar
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/*
 * 정차 화면의 서버 응답 파싱 헬퍼 모음 — ParkedScreen.kt 에서 분리했다(Q20 순수 이동).
 * T9-3(members.seat_role 이름표) 파싱과 E2(then-now 서버 기록) 파싱을 함께 둔다.
 * 로컬 DTO — 화면에 잠깐 쓰고 저장하지 않는다. 호출부는 ParkedScreen(LaunchedEffect)이다.
 */

// ── T9-3 서버 members 파싱(kotlinx.serialization — 로컬 DTO, 화면에 잠깐 쓰고 저장하지 않는다) ──

/**
 * members JSON 배열 항목 중 이름표에 필요한 필드만 받는 로컬 DTO.
 * 서버 항목 형태: {"id":1,"nickname":"...","generation":"...","role":"...","seat_role":"driver|
 * navigator|explorer"|null,"is_minor":false,...} — 이름표에 없는 필드는 모두 무시한다.
 */
@Serializable
private data class MemberNameDto(
    val nickname: String? = null,
    @SerialName("seat_role") val seatRole: String? = null,
)

/** members 응답 파싱 전용 Json(모르는 필드 무시 — 서버 필드 추가에 흔들리지 않게). */
private val membersJson = Json { ignoreUnknownKeys = true }

/** 화면 표시 순서 고정: 운전석 → 조수석 길잡이 → 뒷좌석 탐험가. */
private val SEAT_ROLE_ORDER = listOf("driver", "navigator", "explorer")

/**
 * getMembers 원본 JSON 문자열을 seat_role → nickname Map 으로 바꾼다.
 *  - 배열([ {...}, ... ]) 또는 {"members":[ ... ]} 감싸기 둘 다 흡수한다(서버 응답 형태 방어).
 *  - seat_role 이 없거나 닉네임이 빈 구성원은 건너뛴다. 하나도 없으면 빈 Map(→ 이름표 숨김).
 *  - 파싱 실패는 조용히 빈 Map — 이름표가 숨겨질 뿐 화면이 망가지지 않는다.
 */
internal fun parseSeatTags(body: String): Map<String, String> {
    val byRole = decodeMembers(body)
        .filter { !it.seatRole.isNullOrBlank() && !it.nickname.isNullOrBlank() }
        .associate { it.seatRole!! to it.nickname!! }
    return SEAT_ROLE_ORDER.mapNotNull { role -> byRole[role]?.let { role to it } }.toMap()
}

/** 배열 응답 1차 시도, {"members":[...]} 감싼 객체 2차 시도. 실패 시 빈 목록. */
private fun decodeMembers(body: String): List<MemberNameDto> {
    runCatching { return membersJson.decodeFromString<List<MemberNameDto>>(body) }
    return runCatching {
        val array = membersJson.parseToJsonElement(body).jsonObject["members"]?.jsonArray
            ?: return emptyList()
        array.map { membersJson.decodeFromJsonElement<MemberNameDto>(it) }
    }.getOrDefault(emptyList())
}

/**
 * 가족 인증 실패(401/403/429) 판정. ApiClient 의 실패 전달 관례("가족 인증 실패(HTTP 401)"·
 * "getMembers HTTP 401" 형태의 예외 메시지)라는 전제로 상태 코드만 본다.
 * 401·403 에 429(PIN 오류 잠금)도 함께 거절 대상으로 본다 — ApiClient.getJson 은 셋을
 * 모두 AuthException 으로 답한다(2026-11-14 소스 재확인: 잠금 10분간은 올바른 PIN 으로도
 * 거부되므로 거절 표시 대상이다). 방어: 메시지에 코드가 없으면 '아니오'로 흘러 일반 실패
 * (기능 숨김 + lastError 1줄) 처리만 된다.
 * (Q20 파일 분리: 호출부 ParkedScreen.kt 와 파일이 갈라져 private → internal 로만 넓혔다.)
 */
internal fun isAuthFailure(t: Throwable?): Boolean {
    val message = t?.message ?: return false
    // 숫자 일부 우연 일치(예: 1401)를 막기 위해 코드 앞뒤가 숫자가 아닐 때만 인정한다.
    return Regex("(^|[^0-9])(401|403|429)([^0-9]|$)").containsMatchIn(message)
}

// ── E2 then-now 서버 응답 파싱(2026-11-11, kotlinx.serialization 로컬 DTO — 화면에 잠깐 쓰고 저장하지 않는다) ──

/**
 * ⚠ 병렬 의존 지점(E2, 2026-11-11) — 이 함수가 파일에서 유일하게 신규 ApiClient API 를 부른다.
 *
 * 계약: GET /v1/places/{id}/then-now 의 원본 JSON 문자열(Result<String>). 병렬 담당이 추가한
 * 실제 이름은 [ApiClient.getPlaceThenNow](placeId, familyCode, pin)다 — 계약 초안의 이름
 * `getThenNow` 는 기존 B2 의 `getThenNow(familyCode, pin, placeId)`(/v1/families/me/then-now/)
 * 와 같은 이름·비슷한 인자라 오버로드 모호성으로 ui 호출이 깨지므로, 담당이 getPlaceThenNow 로
 * 바꿔 착지했다. 호출은 이 헬퍼 한 곳에만 둔다(이름이 다시 바뀌면 여기 한 줄만 고치면 된다).
 *  - 임시 폴백: 신규 메서드가 없는 환경에서는 `api.getThenNow(familyCode, pin, placeId)`(기존
 *    B2 별칭 경로)로 바꿔도 화면은 같게 동작한다 — 서버가 두 경로(/v1/places/{id}/then-now 와
 *    /v1/families/me/then-now/{place_id})에서 같은 응답 본문을 돌려주기 때문이다
 *    (backend routers/places.py `_then_now_response` 공용).
 * (Q20 파일 분리: 호출부 ParkedScreen.kt 와 파일이 갈라져 private → internal 로만 넓혔다.)
 */
internal suspend fun fetchThenNowBody(
    api: ApiClient,
    placeId: String,
    familyCode: String?,
    pin: String?,
): Result<String> = api.getPlaceThenNow(placeId, familyCode, pin)

/** then-now 응답 파싱 전용 Json(모르는 필드 무시 — 서버 필드 추가에 흔들리지 않게). */
private val thenNowJson = Json { ignoreUnknownKeys = true }

/** 사진 속 이름표 표시 상한(명) — 서버 MAX_MARKERS·웹 안내 '최대 10명'과 통일(02문서 13절). */
private const val MARKER_NAME_LIMIT = 10

/**
 * then-now 응답의 'past' 블록 중 화면에 필요한 필드만 받는 로컬 DTO.
 * 서버 형태: {"kind":"memory"|"visit", "year":1994, "years_ago":32, "note":"…",
 * "photo_url":"/uploads/…", "vehicle_label":"…", "party_size":4, "markers":[…]}.
 * markers(B5, 2026-11-12): 가족이 웹 '그때 기록하기'에서 사진 위를 직접 눌러 붙인 이름표다 —
 * 탑승자 자동 식별 금지('누가 탔는지' 표시 금지, 04문서 화면 4)와 다른 것으로, 가족이 직접
 * 적은 이름만 표시 대상이다. 차량 화면에서는 이름만 글자로 보여 주고 점(x·y 좌표)은 그리지
 * 않는다(차량에서 입력·수정 없음 — 07문서 B5).
 */
@Serializable
private data class ThenNowPastDto(
    val kind: String? = null,
    val year: Int? = null,
    @SerialName("years_ago") val yearsAgo: Int? = null,
    val note: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("vehicle_label") val vehicleLabel: String? = null,
    @SerialName("party_size") val partySize: Int? = null,
    val markers: List<ThenNowMarkerDto> = emptyList(), // 옛 응답에 필드가 없으면 빈 목록(기본값).
)

/**
 * past.markers 한 항목의 로컬 DTO — 서버 형태: {"x":0.3, "y":0.4, "name":"할아버지"}.
 * x·y는 사진 크기에 대한 비율(0~1)이지만 차량 화면에는 점을 그리지 않으므로 받아만 둔다(표시 미사용).
 * name 을 null 허용으로 둔 것은 방어다 — 이름 없는 항목이 섞여도 응답 전체 파싱이 깨지지 않게.
 */
@Serializable
private data class ThenNowMarkerDto(
    val x: Float? = null,
    val y: Float? = null,
    val name: String? = null,
)

/** then-now 응답의 'now' 블록 — 이번 방문 기록. 방문 기록이 전무하면 서버가 now 도 null 로 보낸다. */
@Serializable
private data class ThenNowNowDto(
    @SerialName("visited_at") val visitedAt: String? = null,
    @SerialName("vehicle_label") val vehicleLabel: String? = null,
    @SerialName("party_size") val partySize: Int? = null,
    @SerialName("trip_km") val tripKm: Double? = null,
)

/** then-now 응답의 'heritage' 블록 — 시드의 domain.HeritageInfo 와 이름이 겹치지 않게 별도 명명. */
@Serializable
private data class ThenNowHeritageDto(
    val name: String? = null,
    val year: Int? = null,
    val label: String? = null,
)

/** then-now 응답 전체. 필드가 빠지거나 형태가 달라도 null 기본값으로 흡수한다(방어 — 단일 객체 응답). */
@Serializable
private data class ThenNowResponseDto(
    @SerialName("place_id") val placeId: String? = null,
    val past: ThenNowPastDto? = null,
    val now: ThenNowNowDto? = null,
    val heritage: ThenNowHeritageDto? = null,
    @SerialName("visit_ordinal") val visitOrdinal: Int? = null,
    @SerialName("past_kind") val pastKind: String? = null, // 방어: kind 가 최상위 past_kind 로 오는 형태도 흡수.
)

/**
 * getThenNow 원본 JSON 을 화면 표시값(ServerThenNowInfo — ThenNowExtras.kt)으로 바꾼다.
 *  - 파싱 실패는 null — 호출부가 예시(시드) 폴백으로 조용히 돌아간다.
 *  - past 는 ThenNowResolver 와 같은 방어를 적용한다: 연도가 없거나 0 이하, 현재 연도보다 큰
 *    '미래 과거'는 과거 기록으로 인정하지 않고 past=null(→ 첫 방문 문구)로 둔다.
 *  - now.party_size·now.trip_km 는 있을 때만 화면에 나온다(04문서 화면 4 — 없으면 숨김).
 *  - B5(2026-11-12): past.markers 는 이름만 순서 그대로 뽑아 markerNames 로 넘긴다. 빈 이름은
 *    건너뛰고, 상한 10명(02문서 — 서버 MAX_MARKERS·웹 안내와 통일)까지만 받는다. 하나도 없으면
 *    빈 목록 — 사진 아래 '함께한 사람' 줄은 스스로 숨는다(ThenMarkerNamesLine 계약).
 */
internal fun parseThenNowServerData(body: String): ServerThenNowInfo? {
    val dto = runCatching { thenNowJson.decodeFromString<ThenNowResponseDto>(body) }.getOrNull()
        ?: return null
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val past = dto.past?.let { p ->
        val year = p.year
        if (year == null || year <= 0 || year > thisYear) {
            null // 비정상·미래 연도 — 과거 기록으로 인정하지 않는다(ThenNowResolver 규칙과 동일).
        } else {
            ThenNowServerPast(
                year = year,
                yearsAgo = p.yearsAgo ?: (thisYear - year), // years_ago 서버값 우선, 없으면 직접 계산.
                note = p.note?.takeIf { it.isNotBlank() },
                photoUrl = p.photoUrl?.takeIf { it.isNotBlank() },
                partySize = p.partySize,
                vehicleLabel = p.vehicleLabel?.takeIf { it.isNotBlank() },
                kind = p.kind ?: dto.pastKind, // kind 는 past 안, past_kind 는 최상위 — 둘 다 흡수(방어).
                markerNames = p.markers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }.take(MARKER_NAME_LIMIT),
            )
        }
    }
    return ServerThenNowInfo(
        past = past,
        nowPartySize = dto.now?.partySize,
        nowTripKm = dto.now?.tripKm,
        visitOrdinal = dto.visitOrdinal,
    )
}

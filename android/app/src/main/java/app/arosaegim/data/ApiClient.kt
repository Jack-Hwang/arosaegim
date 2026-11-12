package app.arosaegim.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.arosaegim.domain.foldedPlaceRequestBody
import app.arosaegim.domain.heirRequestBody
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 방문 기록 1건의 서버 전송 몸체(T8, E1 재정합 — 2026-11-11 서버 실측 계약).
 *
 * 서버 POST /v1/visits 는 스네이크 케이스 필드만 받고(extra=forbid — 모르는 필드는 400),
 * 그래서 프로퍼티 이름은 캐멀케이스로 두고 @SerialName 으로 전송 키만 맞춘다.
 *
 * @property placeId      방문한 장소 id → 전송 키 place_id(시드의 place id — 좌표는 절대 보내지 않는다).
 * @property courseId     어느 코스에서 찍었는지 → course_id.
 * @property visitedAtIso 방문 시각(ISO-8601 문자열, UTC) → visited_at.
 * @property vehicleLabel 차종 표시용 라벨(선택) → vehicle_label. VIN 은 규정상 전송 금지(00문서 9절).
 * @property partySize    함께 온 인원(선택) → party_size. 아직 연동 전 — null 로 두면 몸체에서 생략된다.
 * @property tripKm       이 방문까지의 거리(선택) → trip_km. 여정 완주 전 값은 null 로 둔다(가짜 값 금지).
 * @property courseRunId  같은 코스 주행 1회의 식별자(선택) → course_run_id. 서버가 '같은 주행의
 *                        거리를 한 번만 합산'하는 기준키다(TripSession 이 start() 때 1회 발급).
 *
 * 참고: 기존에 실어 보내던 source("fused"|"navi"|"manual")는 서버 계약에 없는 필드라 400의
 * 원인이 된다 — 몸체에서 완전히 뺐다(도착 출처는 LocalVisit 에만 남긴다).
 */
@Serializable
data class VisitPayload(
    @SerialName("place_id") val placeId: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("visited_at") val visitedAtIso: String,
    @SerialName("vehicle_label") val vehicleLabel: String? = null,
    @SerialName("party_size") val partySize: Int? = null,
    @SerialName("trip_km") val tripKm: Double? = null,
    @SerialName("course_run_id") val courseRunId: String? = null,
)

/**
 * POST /v1/visits 성공 응답(E1 재정합 — 서버 실측 계약).
 *
 * 서버는 {"visit_id": 3, "stamp_no": 2, ...} 로 정수 visit_id 와 스네이크 키로 답하고,
 * then_now·party_size 등의 추가 필드를 함께 보낼 수 있다. then_now 는 E2 UI 담당이
 * 소비하므로 여기서는 파싱하지 않고 무시한다(ApiClient 의 wireJson 이 ignoreUnknownKeys).
 */
@Serializable
data class VisitResponse(
    @SerialName("visit_id") val visitId: Int,
    @SerialName("stamp_no") val stampNo: Int,
)

/**
 * 가족 인증 실패(401·403) 전용 예외(T9-3·T12) — 00문서 10.6.
 *
 * PIN 누락·오류는 '다시 입력'이 답이므로 일반 네트워크 오류(IOException)와 구별해 별도
 * 타입으로 답한다. 호출부는 Result.exceptionOrNull() 이 AuthException 인지 보고 PIN 재입력
 * UI 를 띄우면 된다. 404(모르는 가족 코드·장소)·429(PIN 오류 잠금) 등은 AuthException 이
 * 아니라 일반 실패로 흡수된다 — 재입력이 답인 경우만 이 타입이다.
 * 메시지에 가족 코드·PIN 은 절대 담지 않는다(00문서 9절 로깅 규칙과 같은 취지).
 */
class AuthException(message: String) : Exception(message)

/**
 * owner 권한 부족(403) 전용 예외(B1 heir 지정, 2026-11-11).
 *
 * '이어받을 사람 지정'은 owner 만 가능하다. 차량 세션에는 PIN 만 있고 owner 권한이 없으므로
 * 403이 예상되는 정상 경로다 — 호출부는 이 예외를 보고 버튼을 숨기지 말고
 * '스마트폰의 가족 관리에서 정해 주세요' 안내를 표시한다(계약). AuthException 과 구별되는
 * 별도 타입인 이유: 403이 '권한 없음' 안내(정상 경로)인지 '재입력 필요'인지 화면 행동이 다르다.
 */
class OwnerRequiredException(message: String) : Exception(message)

/**
 * PUT /v1/families/me/prefs 요청 몸체(B4 — 인원 비교 문장 접어 두기, 2026-11-11).
 *
 * 서버 계약(PUT 은 '전체 교체'): 보내지 않은 필드는 서버 초기값(빈 목록·false)으로 저장되므로
 * 항상 두 필드를 모두 실어 보낸다. 그래서 기본값을 두지 않는다 — wireJson(encodeDefaults=false)
 * 로도 빈 목록·false 가 몸체에서 빠지지 않게 한다.
 */
@Serializable
private data class PartyLinePrefsBody(
    @SerialName("party_line_hidden_places") val hiddenPlaceIds: List<String>,
    @SerialName("party_line_all_hidden") val allHidden: Boolean,
)

/**
 * 아로새김 백엔드 클라이언트(T8) — java.net.HttpURLConnection 기반.
 *
 * 설계 의도(02문서 4절 offline-first):
 *  - OkHttp 등 외부 HTTP 라이브러리를 넣지 않는다(의존 최소화 — 표준 라이브러리로 충분한 호출량).
 *  - 모든 실패는 Result.failure 로 돌려준다. 네트워크 오류가 앱을 크래시시키지 않는다.
 *    서버가 없는 환경이 기본이어야 한다(호출 실패는 VisitQueue 가 흡수해 로컬에 보존).
 *  - Dispatchers.IO 에서만 네트워크를 만진다(메인 스레드 차단 금지).
 *  - 좌표·가족코드·응답 몸체를 로그로 남기지 않는다. 이 파일에는 Log 호출이 없다(규칙).
 *
 * open class 인 이유: 단위 테스트(VisitQueueTest)가 postVisit 만 가로채는 하위 클래스로
 * 대체하기 위함이다(기능 동일, 네트워크만 가짜). VisitStore 의 open 규약과 같은 취지다.
 *
 * @property baseUrl 서버 주소. 10.0.2.2 는 에뮬레이터에서 호스트 머신의 루프백이다.
 *                   cleartext 허용은 debug 매니페스트(usesCleartextTraffic)에서 이미 처리.
 */
open class ApiClient(private val baseUrl: String = "http://10.0.2.2:8000") {

    /** 전송용 Json — 계약 테스트(VisitPayloadContractTest)도 같은 설정으로 검증한다. */
    private val json = wireJson

    /** GET /health — 서버 생존 확인. 2xx 가 아니면 실패로 본다(3초 타임아웃). */
    suspend fun health(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = newConnection("GET", "/health")
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = HEALTH_TIMEOUT_MS
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("health HTTP $code")
                Unit
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * POST /v1/visits — 방문 기록 1건 전송(E1 재정합).
     *
     * 서버는 X-Family-Code 와 X-Family-Pin 을 모두 요구한다(실측: 하나만 보내면 401 pin_required).
     *
     * @param familyCode 가족 코드. 있으면 X-Family-Code 헤더로 실어 보낸다.
     *                   헤더 값은 요청에만 쓰고 저장·로그하지 않는다.
     * @param pin        세션 PIN(00문서 10.6 — 메모리 값만 전달, 저장·로그 금지). 있으면
     *                   X-Family-Pin 헤더로 실어 보낸다. 둘 중 하나라도 없으면 서버 401.
     * @return 2xx 면 VisitResponse(visit_id·stamp_no), 그 외·예외는 전부 Result.failure.
     *         401·403 은 [AuthException](PIN 재입력 신호), 그 외 4xx·5xx 는 IOException.
     */
    open suspend fun postVisit(
        v: VisitPayload,
        familyCode: String? = null,
        pin: String? = null,
    ): Result<VisitResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = newConnection("POST", "/v1/visits")
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = POST_TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                familyCode?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("X-Family-Code", it)
                }
                pin?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("X-Family-Pin", it)
                }
                // 요청 몸체는 직렬화해 한 번에 쓴다. 실패 시 예외가 runCatching 으로 흡수된다.
                conn.outputStream.use { out ->
                    out.write(json.encodeToString(v).toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                val code = conn.responseCode
                if (code == 401 || code == 403) {
                    throw AuthException("가족 인증 실패(HTTP $code)")
                }
                if (code !in 200..299) {
                    // 에러 몸체는 읽지 않고 버린다(값을 남기지 않기 위함 — 코드만 실패 사유).
                    runCatching { conn.errorStream?.close() }
                    throw IOException("postVisit HTTP $code")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                json.decodeFromString<VisitResponse>(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * GET /v1/families/me/members — 가족 구성원 목록(T9-3 구성원 이름표의 원천).
     *
     * 원본 JSON 문자열을 그대로 돌려준다. 파싱은 호출부 몫이다(이 파일은 전송 계층만 담당).
     * 서버 실측 응답 형태(127.0.0.1:8000):
     *   [ {"id":1, "nickname":"…", "generation":"grandparent", "role":"owner",
     *      "seat_role":"driver", "is_minor":false, "guardian_consent":false,
     *      "consent_at":null, "created_at":"2026-…"} ]
     * 웹에서 구성원의 seat_role(driver/navigator/explorer)을 정하면 여기에 반영되고,
     * 정차 화면은 nickname+seat_role 로 이름표를 만든다.
     *
     * @param familyCode X-Family-Code 헤더. null·빈값이면 헤더 없이 보낸다(서버 401).
     * @param pin        X-Family-Pin 헤더(열람 권한 — 00문서 10.6). 세션 메모리 값만 전달하고
     *                   저장·로그하지 않는다. 오류·누락이면 서버 401 → Result.failure(AuthException).
     */
    open suspend fun getMembers(familyCode: String?, pin: String?): Result<String> =
        getJson("/v1/families/me/members", familyCode, pin)

    /**
     * GET /v1/families/me/metrics — 가족 지표 6종(T12, PIN 필요).
     *
     * 원본 JSON 문자열 반환. 서버 실측 응답 형태(127.0.0.1:8000):
     *   { "stamp_books":   {"completed":0,"total":1,"rate_pct":0,"source":"…"},
     *     "then_now_views":{"count":0,"approx":true,"source":"…"},
     *     "revisit_gap_days":{"avg_days":null,"pairs":0,"source":"…"},
     *     "receipts_accepted":{"count":0,"source":"…"},
     *     "heir":          {"designated":false,"source":"…"} }
     * 각 지표에 source(출처) 문자열이 함께 온다 — 화면·문서 표기는 이 값을 그대로 쓴다
     * (서버 값이 없으면 호출부가 로컬 카운터로 대체하고 출처 표기를 바꾼다).
     * B3(2026-11-11): 응답에 family_total_trip_km 가 함께 오면 완주 화면·오늘의 기록에서
     * '우리 가족이 함께 달린 거리 N km'로 쓴다(파싱은 domain.parseFamilyTotalTripKm).
     *
     * @param familyCode X-Family-Code 헤더(getMembers 와 동일 규칙).
     * @param pin        X-Family-Pin 헤더 — 세션 메모리 값만 전달, 저장·로그 금지(00문서 10.6).
     */
    open suspend fun getMetrics(familyCode: String?, pin: String?): Result<String> =
        getJson("/v1/families/me/metrics", familyCode, pin)

    // ── 가족 기록 신규 API(B1~B4, 2026-11-11) — 응답은 원본 JSON 문자열, 파싱은 호출부(domain 파서) ──

    /**
     * GET /v1/families/me/heir — 이어받을 사람 조회(B1).
     * 계약 응답: {"member_id": "<id>" | null}. 필드가 없으면 호출부는 기능을 숨긴다(추측 금지).
     * 401·403 은 AuthException(기존 getJson 관례).
     */
    open suspend fun getHeir(familyCode: String?, pin: String?): Result<String> =
        getJson("/v1/families/me/heir", familyCode, pin)

    /**
     * POST /v1/families/me/heir — 이어받을 사람 지정(B1). 몸체 {"member_id": "<id>"}
     * (domain.heirRequestBody). 이 호출은 owner 인증이 필요하며 차량 세션에는 owner 권한이
     * 없으므로 403이 예상된다 — 403은 [OwnerRequiredException] 으로 구별해 답하고, 호출부는
     * 버튼을 숨기지 말고 '스마트폰의 가족 관리에서 정해 주세요' 안내를 표시한다(계약).
     * 200 {"member_id": "<id>"} 성공 / 404 멤버 없음은 일반 실패(IOException).
     */
    open suspend fun postHeir(
        familyCode: String?,
        pin: String?,
        memberId: String,
    ): Result<String> =
        postJson("/v1/families/me/heir", familyCode, pin, heirRequestBody(memberId), ownerOnly = true)

    /**
     * GET /v1/families/me/then-now/{place_id} — 장소별 then-now 부가 상태(B2·B4).
     * 계약 응답 필드: {"visit_ordinal": 1부터, "past_visit_count": 0부터,
     * "fold_person_comparison_all": bool, "folded": bool}.
     * 필드가 없거나 조회 실패하면 호출부가 해당 기능을 숨기고 기존 동작을 유지한다.
     * (기존 then-now 응답에 같은 필드가 합쳐져 오면 그 응답에도 같은 파서가 그대로 적용된다.)
     */
    open suspend fun getThenNow(
        familyCode: String?,
        pin: String?,
        placeId: String,
    ): Result<String> =
        getJson("/v1/families/me/then-now/${encodedSegment(placeId)}", familyCode, pin)

    /**
     * POST /v1/families/me/folded-places — 이 장소의 인원 비교 문장 접기(B4).
     * 몸체 {"place_id": "<id>"}(domain.foldedPlaceRequestBody). 성공 응답
     * {"folded_place_ids": [...]}. 실패 시 호출부는 문장을 그대로 둔다(기존 동작 유지).
     */
    open suspend fun postFoldedPlace(
        familyCode: String?,
        pin: String?,
        placeId: String,
    ): Result<String> =
        postJson("/v1/families/me/folded-places", familyCode, pin, foldedPlaceRequestBody(placeId))

    /**
     * GET /v1/families/me/prefs — 인원 비교 문장 접기 설정 조회(B4, PIN 필요).
     * 계약 응답: {"party_line_hidden_places": ["gj-museum", …], "party_line_all_hidden": false}.
     * 저장 행이 없어도 서버가 이 초기값(빈 목록·false)으로 답한다. 파싱은 호출부 몫
     * (ui.parked.parsePartyLinePrefs). 호출 실패(백엔드 미착지·네트워크)면 호출부가
     * 로컬 SharedPreferences 폴백으로 전환한다(계약 — ui.parked.PartyFoldLocalStore).
     */
    open suspend fun getPrefs(familyCode: String?, pin: String?): Result<String> =
        getJson("/v1/families/me/prefs", familyCode, pin)

    /**
     * PUT /v1/families/me/prefs — 인원 비교 문장 접기 설정 저장(B4, PIN 필요).
     * PUT 은 '전체 교체'다 — 현재 값 전체(접힌 장소 목록 + 전체 끄기 플래그)를 항상 두 필드에
     * 담아 보낸다([PartyLinePrefsBody]). 서버가 같은 동작의 POST 별칭(/v1/families/me/prefs)도
     * 제공하지만 계약 이름은 PUT 이다. 401·403 은 AuthException(가족 인증 — owner 전용 아님).
     *
     * @param hiddenPlaceIds 문장을 접어 둔 장소 id 전체(이 장소를 합친 목록 — 호출부 조립).
     * @param allHidden      모든 장소에서 문장을 끄는 가족 설정(웹 가족 관리의 스위치 값).
     */
    open suspend fun putPrefs(
        familyCode: String?,
        pin: String?,
        hiddenPlaceIds: List<String>,
        allHidden: Boolean,
    ): Result<String> =
        bodyJson(
            "PUT",
            "/v1/families/me/prefs",
            familyCode,
            pin,
            json.encodeToString(PartyLinePrefsBody(hiddenPlaceIds, allHidden)),
        )

    /**
     * GET /v1/places/{id}/then-now — 장소 단위 그때와 지금 원본(E2·2026-11-11 신규).
     *
     * 원본 JSON 문자열을 그대로 돌려준다(파싱은 소비자 몫 — 이 파일은 전송 계층만 담당).
     * 서버 실측 응답 형태(계약 — 소비자는 이 키로만 읽고, 없으면 해당 기능을 숨긴다):
     *   { "place_id": "gj-museum",
     *     "past":     { "year": 1987, "note": "…", "photo_url": "…",
     *                   "party_size": null, "vehicle_label": null, "markers": [...] },
     *     "now":      { "year": 2026, "note": "…", "photo_url": "…",
     *                   "party_size": 4, "vehicle_label": "…", "markers": [...] },
     *     "heritage": { … } }
     * POST /v1/visits 성공 응답의 then_now 필드도 같은 모양이다(방문 직후 화면이 소비).
     *
     * 이름이 getThenNow 가 아닌 이유: 기존 B2 의 getThenNow(familyCode, pin, placeId)는
     * /v1/families/me/then-now/{place_id} 용으로 이미 ui 에서 위치 인자로 호출 중이라,
     * 같은 이름·비슷한 인자의 오버로드를 두면 그 호출이 모호해져 컴파일이 깨진다.
     *
     * @param placeId    장소 id(경로 조각 — encodedSegment 로 감싼다).
     * @param familyCode X-Family-Code 헤더. null·빈값이면 헤더 없이 보낸다(서버 401).
     * @param pin        X-Family-Pin 헤더(열람 권한 — 00문서 10.6). 401·403 은 AuthException.
     */
    open suspend fun getPlaceThenNow(
        placeId: String,
        familyCode: String?,
        pin: String?,
    ): Result<String> = getJson("/v1/places/${encodedSegment(placeId)}/then-now", familyCode, pin)

    /**
     * GET /v1/places/{id}/voice-letters — 장소의 예시 음성 편지 목록(T9-2 계열, 공개).
     *
     * PIN 이 필요 없는 공개 엔드포인트다. 원본 JSON 문자열 반환. 서버 실측 응답 형태:
     *   [ {"id":"letter-demo-1", "sealed_label":"지난 방문에 남긴 한마디",
     *      "simulated":true, "audio_url":"assets/seed/letter_demo.ogg"} ]
     * audio_url 은 앱 내장 시드 에셋 경로다(서버 업로드 아님 — 라우터 주석 기준).
     */
    suspend fun getVoiceLetters(placeId: String): Result<String> =
        getJson("/v1/places/${encodedSegment(placeId)}/voice-letters", familyCode = null, pin = null)

    /**
     * GET 공용 도우미 — 2xx 면 원본 몸체 문자열, 그 외·예외는 전부 Result.failure.
     * 401·403 만 AuthException 으로 답한다(PIN 누락·오류 = 재입력 신호). 헤더 값은 요청에만
     * 쓰고 저장·로그하지 않는다(postVisit 의 familyCode 처리와 같은 규칙).
     */
    private suspend fun getJson(
        path: String,
        familyCode: String?,
        pin: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = newConnection("GET", path)
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = GET_TIMEOUT_MS
                familyCode?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("X-Family-Code", it)
                }
                pin?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("X-Family-Pin", it)
                }
                val code = conn.responseCode
                if (code == 401 || code == 403) {
                    throw AuthException("가족 인증 실패(HTTP $code)")
                }
                if (code !in 200..299) {
                    // 에러 몸체는 읽지 않고 버린다 — 코드만 실패 사유로 남긴다(값 미보존 규칙).
                    runCatching { conn.errorStream?.close() }
                    throw IOException("GET $path HTTP $code")
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * 몸체 실어 보내는 공용 도우미(2026-11-11 신규 — heir·folded-places·prefs). JSON 몸체를
     * 실어 보내고 2xx 면 원본 응답 문자열을 돌려준다. POST(postJson)와 PUT(putPrefs)이 함께 쓴다.
     *  - 401 → AuthException(PIN 재입력 신호 — getJson 과 동일 관례).
     *  - 403 → [ownerOnly] 가 true 면 [OwnerRequiredException](heir 지정은 owner 전용),
     *           false 면 AuthException.
     *  - 그 외 → IOException. 에러 몸체는 읽지 않고 버린다(값 미보존 규칙).
     */
    private suspend fun bodyJson(
        method: String,
        path: String,
        familyCode: String?,
        pin: String?,
        body: String,
        ownerOnly: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = newConnection(method, path)
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = POST_TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                familyCode?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("X-Family-Code", it)
                }
                pin?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("X-Family-Pin", it)
                }
                conn.outputStream.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                val code = conn.responseCode
                if (code == 403 && ownerOnly) {
                    throw OwnerRequiredException("owner 권한 필요(HTTP 403)")
                }
                if (code == 401 || code == 403) {
                    throw AuthException("가족 인증 실패(HTTP $code)")
                }
                if (code !in 200..299) {
                    runCatching { conn.errorStream?.close() }
                    throw IOException("$method $path HTTP $code")
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** POST 몸체 도우미 — [bodyJson] 의 POST 위임(heir·folded-places 기존 호출부 유지용). */
    private suspend fun postJson(
        path: String,
        familyCode: String?,
        pin: String?,
        body: String,
        ownerOnly: Boolean = false,
    ): Result<String> = bodyJson("POST", path, familyCode, pin, body, ownerOnly)

    /** 경로 조각용 인코딩 — 장소 id 는 slug 지만 URL 조립 안전을 위해 한 번 더 감싼다. */
    private fun encodedSegment(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8")

    /** 공통 연결 생성. URL 조립 실패도 호출측 runCatching 이 흡수한다. */
    private fun newConnection(method: String, path: String): HttpURLConnection {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.useCaches = false
        return conn
    }

    companion object {
        /**
         * 전송용 Json(E1 재정합 — 2026-11-11 서버 실측). 이 설정은 서버 계약의 일부라
         * 바꾸지 않는다: 요청 몸체의 null 필드는 전송에서 빠지고(encodeDefaults=false +
         * explicitNulls=false), 서버가 응답에 추가로 주는 키(then_now 등)는 무시한다
         * (ignoreUnknownKeys). internal 로 공개한 이유 — 계약 테스트(VisitPayloadContractTest·
         * VisitQueueTest)가 같은 인스턴스로 와이어 계약을 검증한다(설정 흔들림까지 잡는다).
         */
        internal val wireJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

        private const val CONNECT_TIMEOUT_MS = 3_000

        /** health 는 생존 확인이 목적 — 짧게 끊는다(3초). */
        const val HEALTH_TIMEOUT_MS = 3_000

        /** 전송은 몸체 크기가 작아 5초면 충분하다. */
        const val POST_TIMEOUT_MS = 5_000

        /** 가족·장소 조회도 몸체가 작다 — 규정대로 3초로 끊는다. */
        const val GET_TIMEOUT_MS = 3_000
    }
}

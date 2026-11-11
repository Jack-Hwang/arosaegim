package app.arosaegim.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 방문 기록 1건의 서버 전송 몸체(T8).
 *
 * @property placeId      방문한 장소 id(시드의 place id — 좌표는 절대 보내지 않는다).
 * @property courseId     어느 코스에서 찍었는지.
 * @property visitedAtIso 방문 시각(ISO-8601 문자열, UTC).
 * @property source       도착 출처: "fused" | "navi" | "manual".
 * @property vehicleLabel 차종 표시용 라벨(선택). VIN 은 규정상 전송하지 않는다(00문서 9절).
 */
@Serializable
data class VisitPayload(
    val placeId: String,
    val courseId: String,
    val visitedAtIso: String,
    val source: String,
    val vehicleLabel: String? = null,
)

/** POST /v1/visits 성공 응답. stampNo 는 서버가 부여한 스탬프 번호다. */
@Serializable
data class VisitResponse(
    val visitId: String,
    val stampNo: Int,
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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
     * POST /v1/visits — 방문 기록 1건 전송.
     *
     * @param familyCode 가족 코드(T9-1 로그인 연동 시 채움). 있으면 X-Family-Code 헤더로 실어 보낸다.
     *                   헤더 값은 요청에만 쓰고 저장·로그하지 않는다.
     * @return 2xx 면 VisitResponse, 그 외·예외는 전부 Result.failure.
     */
    open suspend fun postVisit(
        v: VisitPayload,
        familyCode: String? = null,
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
                // 요청 몸체는 직렬화해 한 번에 쓴다. 실패 시 예외가 runCatching 으로 흡수된다.
                conn.outputStream.use { out ->
                    out.write(json.encodeToString(v).toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                val code = conn.responseCode
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
     *
     * @param familyCode X-Family-Code 헤더(getMembers 와 동일 규칙).
     * @param pin        X-Family-Pin 헤더 — 세션 메모리 값만 전달, 저장·로그 금지(00문서 10.6).
     */
    open suspend fun getMetrics(familyCode: String?, pin: String?): Result<String> =
        getJson("/v1/families/me/metrics", familyCode, pin)

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

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000

        /** health 는 생존 확인이 목적 — 짧게 끊는다(3초). */
        const val HEALTH_TIMEOUT_MS = 3_000

        /** 전송은 몸체 크기가 작아 5초면 충분하다. */
        const val POST_TIMEOUT_MS = 5_000

        /** 가족·장소 조회도 몸체가 작다 — 규정대로 3초로 끊는다. */
        const val GET_TIMEOUT_MS = 3_000
    }
}

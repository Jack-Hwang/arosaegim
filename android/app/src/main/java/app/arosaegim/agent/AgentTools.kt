package app.arosaegim.agent

import app.arosaegim.domain.DrivingState
import app.arosaegim.domain.Place

/**
 * Gleo 음성 function calling 도구(T3) — 순수 Kotlin 로직.
 *
 * Android·Context 의존이 전혀 없어 JVM 단위 시험(AgentToolsTest)의 대상이다. 서비스
 * (GleoActionService)가 음성 플랫폼의 호출을 받아 앱 상태를 [AgentAppSnapshot] 한 묶음으로
 * 만들어 [AgentTools.dispatch]에 넘기고, [AgentCallResult]의 한 문장을 응답 방송에 실어
 * 되돌려 준다. 분기 판정·문장 조립은 전부 이 파일이 맡고 서비스는 배선만 한다.
 *
 * 개인정보 규칙(G6): 응답 문장은 고정 영어 문구 + 장소 이름만 담는다. 가족 코드·PIN·
 * 가족 구성원 이름·좌표 같은 숫자 패턴은 문장에 절대 넣지 않는다. 안 읽힌 신호
 * (tripKm·partySize 가 null)는 문장에서 통째로 생략한다(가짜 값 금지 — 00문서 11-1과 같은 취지).
 *
 * 문장 규약: 영어 한 문장, 120자 이내. 고정 문구이므로 초과는 장소 이름이 비정상적으로 긴
 * 경우뿐이고, 그래도 자르지 않는다(임의 절단은 의미를 깬다 — 원문 미확인 확정(T0)이므로
 * 문구는 분기표에 적힌 그대로를 쓴다).
 */

/**
 * 도구가 판단에 쓰는 앱 상태 한 묶음. 서비스가 StateFlow·홀더 값을 읽어 채운다.
 * 값 하나하나는 순수 데이터라 JVM 시험에서 자유롭게 조립할 수 있다.
 */
data class AgentAppSnapshot(
    val driving: DrivingState,        // domain/Models.kt의 enum { PARKED, DRIVING }
    val hasSession: Boolean,          // 가족 세션(코드) 접속 여부 — 세 함수 공통 게이트
    val courseStarted: Boolean,       // CourseStartState.courseStarted (스텝0 출발 버튼 플래그)
    val currentTarget: Place?,        // 다음 스탬프 목표. null = 완주(또는 빈 코스)
    val lastArrival: Place?,          // 마지막으로 스탬프 찍은 장소(퀴즈 해제 기준)
    val stampedCount: Int,            // 찍은 스탬프 수
    val stampTotal: Int,              // 코스 전체 장소 수
    val isCompleted: Boolean,         // 코스 완주 여부(분기에 직접 쓰지 않는다 — 완주는 currentTarget null 로 대표)
    val partySize: Int?,              // 오늘 함께 온 인원(좌석 점유 합계). 안 읽히면 null
    val tripKm: Double?,              // 오늘 함께 달린 거리(km). 안 읽히면 null
)

/** 응답 상태. ASKING 은 이번 범위에서 쓰지 않는다(아래 주석 참조). */
enum class AgentStatus { SUCCESS, ASKING, FAIL }

/**
 * 한 번의 도구 호출에 대한 응답. [message] 는 음성으로 읽히는 문장이고 [data] 는
 * 부가 값(현재는 placeId 하나)이다. 필드에 개인정보를 넣지 않는다.
 */
data class AgentCallResult(
    val functionName: String,
    val status: AgentStatus,
    val message: String,              // 영어, 한 문장, 120자 이내
    val mode: String = "voice",
    val data: Map<String, String>? = null,
)

object AgentTools {

    /**
     * 수신 가능한 함수 이름(assets/schema.json 의 actionCalls 와 같은 목록).
     * 앱당 최대 3개 규칙을 지키고, 이름은 고정 — 바꾸지 않는다.
     */
    val ALLOWED = setOf("GET_NEXT_STAMP_PLACE", "START_QUIZ", "GET_TODAY_SUMMARY")

    /** 세션이 없을 때의 공통 안내(세 함수 모두 최우선 판정). */
    private const val NEED_SESSION = "Please sign in with your family code first."

    /** 허용 목록 밖 호출의 공통 거절 문구(서비스 즉시 응답 경로에서도 같은 문장을 쓴다). */
    const val UNSUPPORTED_MESSAGE = "This action is not supported."

    /**
     * 함수 이름 하나를 해당 도구로 돌린다. 허용 목록 밖은 즉시 FAIL — 스냅샷을 읽지 않는다.
     *
     * ASKING 에 대하여: 다중 턴 확인 프로토콜(호출을 받은 뒤 되묻고 재호출로 확정하는 흐름)이
     * 원문 미확인 확정(T0)이라 START_QUIZ 의 실행 가드(G11)는 보류다. 조건이 만족되면
     * 확인 절차 없이 즉시 SUCCESS 로 실행한다 — G11 이 확정되면 여기서 ASKING 이 붙는다.
     */
    fun dispatch(functionName: String, snapshot: AgentAppSnapshot): AgentCallResult = when (functionName) {
        "GET_NEXT_STAMP_PLACE" -> getNextStampPlace(snapshot)
        "START_QUIZ" -> startQuiz(snapshot)
        "GET_TODAY_SUMMARY" -> getTodaySummary(snapshot)
        else -> AgentCallResult(functionName, AgentStatus.FAIL, UNSUPPORTED_MESSAGE)
    }

    /**
     * GET_NEXT_STAMP_PLACE — 현재 코스의 다음 스탬프 장소를 알려 준다.
     *  - 세션 없음 → FAIL(공통 게이트 최우선)
     *  - currentTarget 있음 → SUCCESS "Next stop: {장소 이름}." + data placeId
     *  - currentTarget 없음(완주) → SUCCESS 완주 문구(data 없음)
     */
    fun getNextStampPlace(s: AgentAppSnapshot): AgentCallResult {
        if (!s.hasSession) {
            return AgentCallResult("GET_NEXT_STAMP_PLACE", AgentStatus.FAIL, NEED_SESSION)
        }
        return s.currentTarget?.let { target ->
            AgentCallResult(
                functionName = "GET_NEXT_STAMP_PLACE",
                status = AgentStatus.SUCCESS,
                message = "Next stop: ${target.name}.",
                data = mapOf("placeId" to target.id),
            )
        } ?: AgentCallResult(
            functionName = "GET_NEXT_STAMP_PLACE",
            status = AgentStatus.SUCCESS,
            message = "You have stamped every place on this course.",
        )
    }

    /**
     * START_QUIZ — 마지막으로 스탬프 찍은 장소의 퀴즈를 시작한다(정차 전용).
     * 분기는 아래 순서를 정확히 따른다(우선순위 고정):
     *  1. 세션 없음(공통 게이트)
     *  2. 주행 중 → FAIL
     *  3. 코스 미시작 + 스탬프 0 → FAIL
     *  4. lastArrival 없음(아직 어디에도 도착 안 함) → FAIL
     *  5. 도착 장소에 퀴즈가 없음 → FAIL
     *  6. 그 외 → SUCCESS(QuizStateHolder 시작·정차 화면 전환은 서비스 쪽 부가 동작)
     *
     * 주석 보류: "G11 확인 뒤 실행 가드는 보류" — 다중 턴 확인(ASKING) 프로토콜이 미확인(T0)이라
     * 조건 만족 시 확인 절차 없이 즉시 SUCCESS 로 본다.
     */
    fun startQuiz(s: AgentAppSnapshot): AgentCallResult {
        if (!s.hasSession) {
            return AgentCallResult("START_QUIZ", AgentStatus.FAIL, NEED_SESSION)
        }
        if (s.driving == DrivingState.DRIVING) {
            return AgentCallResult("START_QUIZ", AgentStatus.FAIL, "Quiz is available only when the car is parked.")
        }
        if (!s.courseStarted && s.stampedCount == 0) {
            return AgentCallResult("START_QUIZ", AgentStatus.FAIL, "Please start a course first.")
        }
        val arrival = s.lastArrival
        if (arrival == null) {
            return AgentCallResult("START_QUIZ", AgentStatus.FAIL, "Arrive at a place first to unlock its quiz.")
        }
        if (arrival.quiz.isEmpty()) {
            return AgentCallResult("START_QUIZ", AgentStatus.FAIL, "This place has no quiz.")
        }
        return AgentCallResult(
            functionName = "START_QUIZ",
            status = AgentStatus.SUCCESS,
            message = "Starting the quiz for ${arrival.name}.",
        )
    }

    /**
     * GET_TODAY_SUMMARY — 오늘의 스탬프·거리·인원을 한 문장으로 요약한다.
     *  - 세션 없음 → FAIL(공통 게이트)
     *  - 그 외 → SUCCESS. 문장은 "Stamps {찍은} of {전체}." 로 시작해 tripKm 이 있으면
     *    " Distance {거리} km.", partySize 가 있으면 " Party {인원}." 을 붙인다. null 항목은
     *    문장에서 통째로 생략한다(안 읽히면 숨김 원칙 — 가짜 값 금지).
     *
     * 퀴즈 언급 금지: 누적 퀴즈 카운터가 코드에 없으므로(가짜 집계 금지) 요약에 넣지 않는다.
     */
    fun getTodaySummary(s: AgentAppSnapshot): AgentCallResult {
        if (!s.hasSession) {
            return AgentCallResult("GET_TODAY_SUMMARY", AgentStatus.FAIL, NEED_SESSION)
        }
        var message = "Stamps ${s.stampedCount} of ${s.stampTotal}."
        s.tripKm?.let { message += " Distance $it km." }
        s.partySize?.let { message += " Party $it." }
        return AgentCallResult(
            functionName = "GET_TODAY_SUMMARY",
            status = AgentStatus.SUCCESS,
            message = message,
        )
    }
}

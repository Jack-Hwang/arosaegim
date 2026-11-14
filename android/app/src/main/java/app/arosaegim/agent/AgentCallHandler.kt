package app.arosaegim.agent

import android.content.Context
import android.content.Intent
import app.arosaegim.ArosaegimApp
import app.arosaegim.ui.parked.CourseStartState
import app.arosaegim.ui.parked.ParkedActivity
import app.arosaegim.ui.parked.QuizStateHolder

/**
 * Gleo 음성 function calling 공용 처리기 — 서비스(GleoActionService)와 리시버
 * (GleoActionReceiver)가 같은 계약을 받기 위한 배선 한 묶음이다.
 *
 * 수신 경로가 원문 미확인 확정(T0)이라 서비스·리시버 둘 다 이 [handle] 하나를 거친다:
 *  1. intent 의 functionName 을 읽는다(원문 미확인 — extra 키 'functionName' 은 가정값).
 *  2. intent 가 없거나 extra 가 없으면 null 반환 — 응답 방송 없이 조용히 끝난다(기존 계약).
 *  3. AgentTools.ALLOWED 밖이면 즉시 FAIL(문구는 AgentTools.UNSUPPORTED_MESSAGE 재사용).
 *  4. 허용 목록이면 앱 상태를 [AgentAppSnapshot] 한 묶음으로 읽어 dispatch 하고,
 *     START_QUIZ 가 SUCCESS 로 판정된 이때만 퀴즈 시작 부가 동작을 한다.
 *
 * 동기 규약(7초 게이트 대응): 코루틴·Thread.sleep·네트워크·파일 I/O 가 전혀 없다. StateFlow
 * .value 와 홀더 값의 즉시 읽기와 sendBroadcast 만으로 한 번에 끝나므로, BroadcastReceiver 쪽
 * goAsync 도 필요 없다.
 *
 * 개인정보 규칙: 응답 방송에 실리는 필드(functionName·status·message·mode·placeId)에 가족
 * 코드·PIN·구성원 이름·좌표 같은 개인정보를 넣지 않는다. 장소 이름·장소 id 까지다.
 */
object AgentCallHandler {

    /**
     * 호출 intent 하나를 판정한다. 응답 방송은 이 결과가 null 이 아닐 때 [sendResponse] 로
     * 보낸다(호출자 책임 — 서비스·리시버가 같은 순서로 부른다).
     */
    fun handle(context: Context, intent: Intent?): AgentCallResult? {
        // 원문 미확인, functionName 가정: 호출 extra 키 이름은 'functionName' 이라 가정한다(T0).
        val functionName = intent?.getStringExtra(EXTRA_FUNCTION_NAME)
        if (intent == null || functionName.isNullOrEmpty()) {
            return null // 호출 형식이 아니면 조용히 끝난다 — 응답 방송 없음(아무 일도 없었다는 처리).
        }

        if (functionName !in AgentTools.ALLOWED) {
            // 허용 목록 밖은 스냅샷을 읽지 않고 즉시 거절한다(문구는 AgentTools 와 같은 상수 재사용).
            return AgentCallResult(functionName, AgentStatus.FAIL, AgentTools.UNSUPPORTED_MESSAGE)
        }

        // 허용 목록 도구: 앱 상태를 한 묶음 스냅샷으로 읽는다(전부 값 읽기라 동기·즉시).
        val appContext = context.applicationContext
        val container = (appContext as ArosaegimApp).container
        val session = container.tripSession
        val snapshot = AgentAppSnapshot(
            driving = container.drivingState.state.value,
            hasSession = container.familySessionStore.hasSession(),
            courseStarted = CourseStartState.courseStarted,
            currentTarget = session.currentTarget.value,
            lastArrival = session.lastArrival.value,
            stampedCount = session.stampedCount.value,
            stampTotal = session.course.places.size,
            isCompleted = session.isCompleted.value,
            partySize = session.partySize.value,
            tripKm = session.tripKm.value,
        )
        val result = AgentTools.dispatch(functionName, snapshot)

        // 부가 동작은 START_QUIZ 성공(정차·도착·퀴즈 보유가 모두 확인된 경우)일 때만 한다.
        // lastArrival 은 dispatch 결과 data 가 아니라 tripSession.lastArrival 에서 직접 읽는다.
        if (result.status == AgentStatus.SUCCESS && functionName == FUN_START_QUIZ) {
            session.lastArrival.value?.id?.let { arrivalId ->
                QuizStateHolder.beginQuiz(arrivalId)   // 퀴즈 진행 시작(같은 장소면 이어풀기 보존)
                QuizStateHolder.stepIndex = 1          // 스탬프 카드(스텝1)부터 보기
                CourseStartState.showRecord = false    // 오늘의 기록(스텝5)은 닫는다
                // 정차 화면을 최상단으로 가져온다. ModeRouter 는 수정하지 않는다 — 이 화면
                // 호출은 음성 도구 전용 경로다(주행 상태 판정은 이미 SUCCESS 조건으로 끝났다).
                // applicationContext 에서 띄우므로 NEW_TASK 가 반드시 필요하다.
                Intent(appContext, ParkedActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .let { appContext.startActivity(it) }
            }
        }
        return result
    }

    /** 판정 결과를 응답 방송으로 알린다. data 의 각 entry 는 extra 로 그대로 실린다(개인정보 없음). */
    fun sendResponse(context: Context, result: AgentCallResult) {
        val response = Intent(ACTION_APP_ACTION_RESPONSE)
            .putExtra(EXTRA_FUNCTION_NAME, result.functionName)
            .putExtra(EXTRA_STATUS, result.status.name.lowercase())
            .putExtra(EXTRA_MESSAGE, result.message)
            .putExtra(EXTRA_MODE, result.mode)
        result.data?.forEach { (key, value) -> response.putExtra(key, value) }
        context.sendBroadcast(response)
    }

    // extra·action 상수 묶음. object 선언 안이라 본문 const 가 companion 상수와 같은
    // 정적 역할을 한다(컴파일 등가 — G11 과 무관한 배선 상수다).

    /** 호출 extra 키(원문 미확인 — 'functionName' 가정, T0). */
    private const val EXTRA_FUNCTION_NAME = "functionName"

    /** 응답 방송 extra 키들 — functionName·status·message·mode 를 그대로 되돌린다. */
    private const val EXTRA_STATUS = "status"
    private const val EXTRA_MESSAGE = "message"
    private const val EXTRA_MODE = "mode"

    /**
     * 응답 방송 action — 실측 확정(2026-11-14): dumpsys 에서 ai.umos.ambientai/u10 이 이
     * 액션을 수신하는 리시버를 등록하고 있음을 확인했다. 더 이상 가정값이 아니다.
     */
    private const val ACTION_APP_ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    /** 부가 동작(퀴즈 시작·정차 화면 전환)을 트리거하는 함수 이름. */
    private const val FUN_START_QUIZ = "START_QUIZ"
}

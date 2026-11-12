package app.arosaegim.ui

import android.content.Context
import android.content.Intent
import app.arosaegim.domain.DrivingState
import app.arosaegim.ports.DrivingStateProvider
import app.arosaegim.ui.driving.DrivingActivity
import app.arosaegim.ui.parked.ParkedActivity
import app.arosaegim.ui.parked.DiagnosticsMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 주행/정차 모드 라우터 — DrivingStateProvider.state 를 수집해 해당 화면을
 * FLAG_ACTIVITY_REORDER_TO_FRONT 로 같은 태스크 안에서 최상단으로 올린다(새 태스크 생성 아님).
 * Application 컨텍스트에서 startActivity 하므로 FLAG_ACTIVITY_NEW_TASK 도 함께 쓴다(E3 수정 —
 * NEW_TASK 가 없으면 Activity 아닌 컨텍스트에서 예외가 났고 catch 에 묻혔었다).
 * 이미 마지막으로 띄운 화면이면 재요청하지 않는다(연속 콜백 중복 방지).
 *
 * 세션 게이트(Q28 수정, 2026-11-13): 정차 화면으로의 자동 전환은 가족 세션(코드 접속 또는
 * 둘러보기)이 있을 때만 한다. 세션이 없으면 시작 화면(가족 코드 입력 — 차량에서 글자를 넣는
 * 유일한 곳)을 최상단에 유지해야 하는데, 첫 상태 수집(PARKED)이 곧바로 정차 화면을 덮어
 * 새 설치에서 코드 진입점이 사라졌었다. 주행 화면으로의 전환은 세션과 무관하게 항상 허용한다 —
 * DO 선언으로 주행 중 시스템이 허용하는 안전 화면이므로.
 */
class ModeRouter(context: Context, private val sessionExists: () -> Boolean = { true }) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastRoutedTarget: Class<*>? = null

    private val _lastRouted = MutableStateFlow<String?>(null)

    /**
     * 마지막으로 startActivity 호출이 '성공한' 대상 Activity 클래스명(U9 실측 판정용, 06문서 2번).
     *  - route() 가 startActivity 를 예외 없이 호출하면 대상 simpleName(DrivingActivity/ParkedActivity)을 기록.
     *  - 호출이 실패하면 null 로 되돌린다(마지막 오류는 DiagnosticsMetrics '마지막 오류' 채널에 별도 기록).
     *  - '호출 성공 기록'이지 실제 최상단 Activity 전환까지 보장하지는 않는다 — 최상단은
     *    진단 화면 실행 중 육안(uiautomator dump 등)으로 함께 확인하는 것을 전제로 한다.
     */
    val lastRouted: StateFlow<String?> = _lastRouted.asStateFlow()

    /** 상태 수집 시작. 앱 수명 동안 1회(ArosaegimApp.onCreate 에서 호출). */
    fun bind(provider: DrivingStateProvider) {
        scope.launch {
            provider.state.collect { state -> route(state) }
        }
    }

    /**
     * 진단 화면 U9 용 강제 전환(주행 상태와 무관하게 특정 화면을 앞으로 당김).
     * 세션 게이트(Q28)를 우회한다 — 개발자가 진단 화면에서 명시적으로 누른 전환이므로.
     */
    fun forceBringTo(state: DrivingState) {
        route(state, respectSessionGate = false)
    }

    private fun route(state: DrivingState, respectSessionGate: Boolean = true) {
        val gated = if (respectSessionGate) sessionExists() else true
        val target = resolveTarget(state, gated) ?: return // 세션 없음 — 시작 화면 유지(Q28)
        if (target == lastRoutedTarget) return // 이미 최상단이면 무시
        lastRoutedTarget = target
        // applicationContext 기반 intent(E3 수정, 2026-11-11): Activity 가 아닌 컨텍스트로
        // startActivity 하려면 FLAG_ACTIVITY_NEW_TASK 가 필수다 — 없으면 AndroidRuntimeException 이
        // 발생하고 아래 catch 에서 조용히 사라졌었다. NEW_TASK 를 먼저 붙이고(새 태스크가 아니라
        // 기존 앱 태스크를 재사용한다), REORDER_TO_FRONT 로 기존 액티비티 인스턴스를 최상단으로
        // 올린다(새 인스턴스 생성 아님 — 기존 동작 유지).
        val intent = Intent(appContext, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            appContext.startActivity(intent)
            // U9 실측(06문서 2번): 호출이 성공했을 때만 대상 Activity 클래스명을 기록한다 —
            // SmokeTests.runU9 가 이 기록을 기대값(DrivingActivity/ParkedActivity)과 비교해 판정한다.
            _lastRouted.value = target.simpleName
        } catch (t: Throwable) {
            // 백그라운드 시작 제한 등 실패 시 다음 상태 변화에서 재시도할 수 있게 기록을 되돌린다.
            lastRoutedTarget = null
            _lastRouted.value = null // 실패 기록은 null — U9 가 FAIL 사유로 읽는다.
            // E3: 예외가 조용히 사라지지 않게 마지막 오류 채널(DiagnosticsMetrics 의 1줄
            // SharedPreferences 채널)에 기록한다 — 진단 화면 ④ '마지막 오류'에서 확인 가능.
            // 예외 유형만 남긴다(좌표·코드·개인 식별값 기록 금지, Log 사용 금지 규칙 준수).
            DiagnosticsMetrics.setLastError(appContext, "화면 전환 실패(ModeRouter): ${t.javaClass.simpleName}")
        }
    }

    companion object {
        /**
         * 라우팅 대상 결정(순수 함수 — 단위 테스트 대상). 상태와 세션 존재 여부만으로 판정한다.
         *  - DRIVING → 주행 화면(세션과 무관 — DO 안전 화면 유지).
         *  - PARKED + 세션 있음 → 정차 화면.
         *  - PARKED + 세션 없음 → null(전환하지 않음 — 시작 화면을 최상단에 유지, Q28).
         */
        fun resolveTarget(state: DrivingState, sessionExists: Boolean): Class<*>? =
            when {
                state == DrivingState.DRIVING -> DrivingActivity::class.java
                sessionExists -> ParkedActivity::class.java
                else -> null
            }
    }
}

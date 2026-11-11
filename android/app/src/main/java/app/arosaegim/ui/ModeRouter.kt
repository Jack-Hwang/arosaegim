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
import kotlinx.coroutines.launch

/**
 * 주행/정차 모드 라우터 — DrivingStateProvider.state 를 수집해 해당 화면을
 * FLAG_ACTIVITY_REORDER_TO_FRONT 로 같은 태스크 안에서 최상단으로 올린다(새 태스크 생성 아님).
 * Application 컨텍스트에서 startActivity 하므로 FLAG_ACTIVITY_NEW_TASK 도 함께 쓴다(E3 수정 —
 * NEW_TASK 가 없으면 Activity 아닌 컨텍스트에서 예외가 났고 catch 에 묻혔었다).
 * 이미 마지막으로 띄운 화면이면 재요청하지 않는다(연속 콜백 중복 방지).
 */
class ModeRouter(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastRouted: Class<*>? = null

    /** 상태 수집 시작. 앱 수명 동안 1회(ArosaegimApp.onCreate 에서 호출). */
    fun bind(provider: DrivingStateProvider) {
        scope.launch {
            provider.state.collect { state -> route(state) }
        }
    }

    /** 진단 화면 U9 용 강제 전환(주행 상태와 무관하게 특정 화면을 앞으로 당김). */
    fun forceBringTo(state: DrivingState) {
        route(state)
    }

    private fun route(state: DrivingState) {
        val target = if (state == DrivingState.DRIVING) {
            DrivingActivity::class.java
        } else {
            ParkedActivity::class.java
        }
        if (target == lastRouted) return // 이미 최상단이면 무시
        lastRouted = target
        // applicationContext 기반 intent(E3 수정, 2026-11-11): Activity 가 아닌 컨텍스트로
        // startActivity 하려면 FLAG_ACTIVITY_NEW_TASK 가 필수다 — 없으면 AndroidRuntimeException 이
        // 발생하고 아래 catch 에서 조용히 사라졌었다. NEW_TASK 를 먼저 붙이고(새 태스크가 아니라
        // 기존 앱 태스크를 재사용한다), REORDER_TO_FRONT 로 기존 액티비티 인스턴스를 최상단으로
        // 올린다(새 인스턴스 생성 아님 — 기존 동작 유지).
        val intent = Intent(appContext, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            appContext.startActivity(intent)
        } catch (t: Throwable) {
            // 백그라운드 시작 제한 등 실패 시 다음 상태 변화에서 재시도할 수 있게 기록을 되돌린다.
            lastRouted = null
            // E3: 예외가 조용히 사라지지 않게 마지막 오류 채널(DiagnosticsMetrics 의 1줄
            // SharedPreferences 채널)에 기록한다 — 진단 화면 ④ '마지막 오류'에서 확인 가능.
            // 예외 유형만 남긴다(좌표·코드·개인 식별값 기록 금지, Log 사용 금지 규칙 준수).
            DiagnosticsMetrics.setLastError(appContext, "화면 전환 실패(ModeRouter): ${t.javaClass.simpleName}")
        }
    }
}

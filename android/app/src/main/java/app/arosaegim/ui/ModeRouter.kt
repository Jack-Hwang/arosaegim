package app.arosaegim.ui

import android.content.Context
import android.content.Intent
import app.arosaegim.domain.DrivingState
import app.arosaegim.ports.DrivingStateProvider
import app.arosaegim.ui.driving.DrivingActivity
import app.arosaegim.ui.parked.ParkedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 주행/정차 모드 라우터 — DrivingStateProvider.state 를 수집해 해당 화면을
 * FLAG_ACTIVITY_REORDER_TO_FRONT 로 같은 태스크 안에서 최상단으로 올린다(새 태스크 생성 아님).
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
        // applicationContext 기반 intent — REORDER_TO_FRONT 로 기존 액티비티 인스턴스를 재사용한다.
        val intent = Intent(appContext, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            appContext.startActivity(intent)
        } catch (t: Throwable) {
            // 백그라운드 시작 제한 등 실패 시 다음 상태 변화에서 재시도할 수 있게 기록을 되돌린다.
            lastRouted = null
        }
    }
}

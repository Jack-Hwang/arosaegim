package app.arosaegim.fallback

import app.arosaegim.domain.DrivingState
import app.arosaegim.ports.DrivingStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 수동 주행 상태(데모 모드) — 에뮬레이터에서 주행 규제 콜백(U7 미검증)이 동작하지 않거나
 * UxRestrictionsDrivingState 시작이 실패했을 때, 진단 화면의 토글로 PARKED/DRIVING 을
 * 강제 전환해 화면 전환 흐름(U9)을 검증한다.
 */
class ManualDrivingState : DrivingStateProvider {

    private val _state = MutableStateFlow(DrivingState.PARKED)
    override val state: StateFlow<DrivingState> = _state.asStateFlow()

    /** 데모 토글용 수동 전환. */
    fun set(state: DrivingState) {
        _state.value = state
    }

    override fun start() {
        // 수동 모드는 시작 동작이 없다. 선택 즉시 사용 가능.
    }

    override fun stop() {
        _state.value = DrivingState.PARKED
    }
}

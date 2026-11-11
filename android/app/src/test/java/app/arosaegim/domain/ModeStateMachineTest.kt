package app.arosaegim.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 모드 상태기계 단위 테스트. StateFlow를 동기 갱신하므로 .value 직접 비교로 검증한다.
 */
class ModeStateMachineTest {

    @Test
    fun `초기 모드는 PARKED 이다`() {
        assertEquals(UiMode.PARKED, ModeStateMachine().mode.value)
    }

    @Test
    fun `생성자에 DRIVING을 주면 DRIVING에서 시작한다`() {
        val machine = ModeStateMachine(initial = DrivingState.DRIVING)
        assertEquals(UiMode.DRIVING, machine.mode.value)
    }

    @Test
    fun `DRIVING 신호면 DRIVING 모드로 전환된다`() {
        val machine = ModeStateMachine()
        machine.onDrivingState(DrivingState.DRIVING)
        assertEquals(UiMode.DRIVING, machine.mode.value)
    }

    @Test
    fun `연속 동일 신호 3회는 mode 값 변화가 없다`() {
        // PARKED 상태에서 PARKED 신호 3회 → PARKED 유지
        val parked = ModeStateMachine()
        val before = parked.mode.value
        repeat(3) { parked.onDrivingState(DrivingState.PARKED) }
        assertEquals(before, parked.mode.value)

        // DRIVING 상태에서 DRIVING 신호 3회 → DRIVING 유지 (재방출 없음)
        val driving = ModeStateMachine(DrivingState.DRIVING)
        val beforeDriving = driving.mode.value
        repeat(3) { driving.onDrivingState(DrivingState.DRIVING) }
        assertEquals(beforeDriving, driving.mode.value)
    }

    @Test
    fun `PARKED에서 DRIVING으로 복귀 후 PARKED로 복원된다`() {
        val machine = ModeStateMachine()
        machine.onDrivingState(DrivingState.DRIVING)
        assertEquals(UiMode.DRIVING, machine.mode.value)

        machine.onDrivingState(DrivingState.PARKED)
        assertEquals("복귀 시 PARKED로 돌아온다", UiMode.PARKED, machine.mode.value)

        // 반복 복귀-출발 사이클에서도 모드는 결정적으로 복원된다
        machine.onDrivingState(DrivingState.DRIVING)
        assertEquals(UiMode.DRIVING, machine.mode.value)
        machine.onDrivingState(DrivingState.PARKED)
        assertEquals(UiMode.PARKED, machine.mode.value)
    }
}

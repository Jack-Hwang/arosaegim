package app.arosaegim.domain

import app.arosaegim.gearToDriving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 주행 상태 병합기 단위 테스트(A2, 2026-11-11).
 *
 * 규칙 검증: 1차(DO, OFFICIAL)를 존중하되 보조(기어)와 어긋나면 더 제한적인 주행 우선 —
 * 보조를 못 읽었으면(null) 1차만으로 판정한다. 기어 D→P 전환 시 1차 DO 해제 콜백이 없어도
 * 보조(PARK)가 정차 판정을 내릴 수 있는지(정차 화면 자동 전환의 핵심 경로)를 함께 본다.
 */
class DrivingStateMergerTest {

    @Test
    fun `초기 상태는 PARKED다`() {
        val merger = DrivingStateMerger()
        assertEquals(DrivingState.PARKED, merger.state.value)
    }

    @Test
    fun `보조가 없으면 1차만으로 판정한다`() {
        val merger = DrivingStateMerger()
        merger.onPrimary(DrivingState.DRIVING)
        assertEquals(DrivingState.DRIVING, merger.state.value)
        merger.onPrimary(DrivingState.PARKED)
        assertEquals(DrivingState.PARKED, merger.state.value)
    }

    @Test
    fun `1차 정차에 보조 주행이면 주행으로 판정한다 - 어긋나면 더 제한적 우선`() {
        val merger = DrivingStateMerger()
        merger.onPrimary(DrivingState.PARKED)
        merger.onAuxiliaryDriving(true) // 기어 D — DO 해제 콜백이 아직 안 와도 주행으로 본다.
        assertEquals(DrivingState.DRIVING, merger.state.value)
    }

    @Test
    fun `1차 주행에 보조 정차여도 주행을 유지한다 - 더 제한적 우선`() {
        val merger = DrivingStateMerger()
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false) // 기어 P — 단, DO 가 아직 주행을 요구하면 존중한다.
        assertEquals(DrivingState.DRIVING, merger.state.value)
    }

    @Test
    fun `보조를 못 읽었으면 판정에 영향을 주지 않는다`() {
        val merger = DrivingStateMerger()
        merger.onAuxiliaryDriving(null) // FAILED·타임아웃 등 — 무시.
        merger.onPrimary(DrivingState.PARKED)
        assertEquals(DrivingState.PARKED, merger.state.value)
        merger.onPrimary(DrivingState.DRIVING)
        assertEquals(DrivingState.DRIVING, merger.state.value)
    }

    @Test
    fun `기어 P는 1차 정차와 함께 정차 판정을 유지한다`() {
        val merger = DrivingStateMerger()
        merger.onPrimary(DrivingState.PARKED)
        merger.onAuxiliaryDriving(false)
        assertEquals(DrivingState.PARKED, merger.state.value)
    }

    @Test
    fun `보조 주행 후 보조 정차로 돌아오면 1차 기준으로 되돌아간다`() {
        val merger = DrivingStateMerger()
        merger.onPrimary(DrivingState.PARKED)
        merger.onAuxiliaryDriving(true)
        assertEquals(DrivingState.DRIVING, merger.state.value)
        merger.onAuxiliaryDriving(false) // 기어 D→P — 보조만으로 정차 복귀(1차 PARKED 유지).
        assertEquals(DrivingState.PARKED, merger.state.value)
    }

    @Test
    fun `기어 상태 문자열을 보조 주행 판정으로 환산한다`() {
        assertTrue(gearToDriving("GEAR_DRIVE") == true)
        assertFalse(gearToDriving("GEAR_PARK") == true)
        assertFalse(gearToDriving("GEAR_REVERSE") == true)
        assertFalse(gearToDriving("GEAR_NEUTRAL") == true)
        // 못 읽은 값은 전부 null — 병합 판정에 영향 없음(가짜 값 금지).
        assertNull(gearToDriving(null))
        assertNull(gearToDriving("UNKNOWN"))
        assertNull(gearToDriving("FAILED:IllegalStateException: x"))
        assertNull(gearToDriving("NOT_INITIALIZED"))
        assertNull(gearToDriving("ERROR:Exception: y"))
    }
}

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
 *
 * Q26(2026-11-13) 추가 검증: 기어 P 가 보류 시간(기본 5초 — 2초 폴링 3회) 이상 연속 확인되면
 * 1차(DO)가 주행을 주장해도 PARKED 로 해제한다(정차 유지 해제). 보류 기간엔 기존 규칙(주행 우선)이
 * 그대로 적용되고, 보류 시각은 기어 false 최초 수신 기준이라 1차 반복 호출로 리셋되지 않는다.
 * 시간 의존 판정이라 가짜 시계를 주입해 검증한다(벽시계 의존 금지와 같은 취지).
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

    @Test
    fun `1차 주행에 보조 정차가 보류 시간 미만이면 주행을 유지한다 - Q26 보수적`() {
        var nowMs = 0L
        val merger = DrivingStateMerger(nowMs = { nowMs }) // 기본 보류 5초(기어 폴링 3회).
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false) // 기어 P 최초 확인 — 정차 보류 구간 시작.
        nowMs = 4_999L // 5초 미만 — 짧은 P 일시 읽음일 수 있으므로 기존 규칙(주행 우선) 유지.
        merger.onPrimary(DrivingState.DRIVING)
        assertEquals(DrivingState.DRIVING, merger.state.value)
    }

    @Test
    fun `1차 주행에 보조 정차가 보류 시간 이상이면 PARKED로 해제한다 - Q26`() {
        var nowMs = 0L
        val merger = DrivingStateMerger(nowMs = { nowMs })
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false) // 기어 P 최초 확인 — 정차 보류 구간 시작.
        nowMs = 5_000L // 5초 경과(폴링 3회 연속 P) — DO 잔존을 오류로 보고 정차 해제.
        merger.onPrimary(DrivingState.DRIVING) // 1차가 여전히 주행을 주장해도.
        assertEquals(DrivingState.PARKED, merger.state.value)
    }

    @Test
    fun `해제 후 보조 주행 재수신이면 즉시 주행으로 돌아간다 - Q26`() {
        var nowMs = 0L
        val merger = DrivingStateMerger(nowMs = { nowMs })
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false)
        nowMs = 5_000L
        merger.onPrimary(DrivingState.DRIVING)
        assertEquals(DrivingState.PARKED, merger.state.value)
        merger.onAuxiliaryDriving(true) // 기어 P→D — 보류 해제 + 기존 규칙(주행 우선) 즉시 반영.
        assertEquals(DrivingState.DRIVING, merger.state.value)
    }

    @Test
    fun `해제 후 보조를 못 읽으면 보류가 풀리고 1차 기준으로 판정한다 - Q26`() {
        var nowMs = 0L
        val merger = DrivingStateMerger(nowMs = { nowMs })
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false)
        nowMs = 5_000L
        merger.onPrimary(DrivingState.DRIVING)
        assertEquals(DrivingState.PARKED, merger.state.value)
        merger.onAuxiliaryDriving(null) // FAILED·타임아웃 — null 은 판정을 흔들지 않는다(무시).
        assertEquals(DrivingState.DRIVING, merger.state.value) // 보류 해제 + 1차(주행) 존중.
    }

    @Test
    fun `1차 반복 호출은 정차 보류 시각을 리셋하지 않는다 - Q26 기어 기준`() {
        var nowMs = 0L
        val merger = DrivingStateMerger(nowMs = { nowMs })
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false) // 기어 P 최초 확인 — 보류 시각은 여기서 고정.
        nowMs = 3_000L
        merger.onPrimary(DrivingState.DRIVING) // 3초 — 아직 보류 미만.
        assertEquals(DrivingState.DRIVING, merger.state.value)
        nowMs = 5_000L
        merger.onPrimary(DrivingState.DRIVING) // 1차만 반복 호출해도 보류는 계속 진행된다.
        assertEquals(DrivingState.PARKED, merger.state.value)
    }

    @Test
    fun `보류 구간 재확인은 보류 시각을 리셋하지 않는다 - Q26 기어 기준`() {
        var nowMs = 0L
        val merger = DrivingStateMerger(nowMs = { nowMs })
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false) // 보류 시작(0ms).
        nowMs = 3_000L
        merger.onAuxiliaryDriving(false) // 같은 P 구간 재확인 — 최초 시각 유지.
        nowMs = 5_000L
        merger.onPrimary(DrivingState.DRIVING) // 리셋됐다면(3000ms 기준) 2초뿐이라 DRIVING 이어야 한다.
        assertEquals(DrivingState.PARKED, merger.state.value)
    }

    @Test
    fun `해제 후 1차 정차 보고 시 PARKED를 유지한다 - Q26 자연 복귀`() {
        var nowMs = 0L
        val merger = DrivingStateMerger(nowMs = { nowMs })
        merger.onPrimary(DrivingState.DRIVING)
        merger.onAuxiliaryDriving(false)
        nowMs = 5_000L
        merger.onPrimary(DrivingState.DRIVING)
        assertEquals(DrivingState.PARKED, merger.state.value)
        merger.onPrimary(DrivingState.PARKED) // DO 가 정차를 보고 — 내부 값 유지로 자연 복귀.
        assertEquals(DrivingState.PARKED, merger.state.value)
    }
}

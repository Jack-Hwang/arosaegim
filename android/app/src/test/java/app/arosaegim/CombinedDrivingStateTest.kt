package app.arosaegim

import app.arosaegim.domain.DrivingState
import app.arosaegim.ports.DrivingStateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 기어 보조 폴링 결합 주행 상태 제공자 단위 테스트(A2, 2026-11-11).
 *
 * 검증 포인트:
 *  1. 1차(UxRestrictions 자리의 가짜) 상태 변화가 결합 상태로 그대로 흐른다.
 *  2. 기어 폴링 결과(GEAR_PARK/GEAR_DRIVE)가 보조 판정으로 반영된다 —
 *     기어 D→P 로 돌아오면 DO 해제 콜백 없이도 결합 상태가 PARKED 로 되돌아간다
 *     (정차 화면 자동 전환의 핵심 경로).
 *  3. 못 읽은 기어 결과(FAILED·NOT_INITIALIZED)는 판정에 영향을 주지 않는다.
 *
 * 폴링 주간은 테스트용으로 짧게(20ms) 주입하고 runBlocking 의 실시간 delay 로 2~4회 폴링을 기다린다.
 */
class CombinedDrivingStateTest {

    /** 1차 제공자 자리의 가짜 — UxRestrictionsDrivingState 대신 state 를 직접 바꿔 본다. */
    private class FakePrimary : DrivingStateProvider {
        private val _state = MutableStateFlow(DrivingState.PARKED)
        override val state: StateFlow<DrivingState> = _state.asStateFlow()
        var startCount = 0
        var stopCount = 0
        override fun start() { startCount++ }
        override fun stop() { stopCount++ }
        fun set(state: DrivingState) { _state.value = state }
    }

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `1차 상태 변화가 결합 상태로 흐른다`() = runBlocking {
        val primary = FakePrimary()
        val gearAnswer = AtomicReference<String?>("FAILED:NotReady") // 못 읽음 — 무시되어야 한다
        val gearCalls = AtomicInteger(0)
        val combined = CombinedDrivingState(
            primary = primary,
            gearQuery = { onResult ->
                gearCalls.incrementAndGet()
                onResult(gearAnswer.get())
            },
            scope = testScope,
            pollPeriodMs = 20,
        )
        combined.start()
        assertEquals(1, primary.startCount) // 1차 start 는 결합 제공자가 대신 부른다.

        primary.set(DrivingState.DRIVING)
        delay(60)
        assertEquals(DrivingState.DRIVING, combined.state.value) // 못 읽은 보조는 영향 없음 — 1차만 반영.

        primary.set(DrivingState.PARKED)
        delay(60)
        assertEquals(DrivingState.PARKED, combined.state.value)

        combined.stop()
        assertEquals(1, primary.stopCount) // stop 도 위임된다.
    }

    @Test
    fun `기어 폴링이 D에서 P로 돌아오면 DO 해제 콜백 없이도 정차로 되돌아간다`() = runBlocking {
        val primary = FakePrimary() // 1차(DO)는 PARKED 로 고정 — 해제 콜백이 없는 환경을 재현한다.
        val gearAnswer = AtomicReference<String?>("GEAR_DRIVE")
        val combined = CombinedDrivingState(
            primary = primary,
            gearQuery = { onResult -> onResult(gearAnswer.get()) },
            scope = testScope,
            pollPeriodMs = 20,
        )
        combined.start()
        delay(60)
        assertEquals(DrivingState.DRIVING, combined.state.value) // 기어 D — 보조가 주행 판정.

        gearAnswer.set("GEAR_PARK") // D→P — DO 콜백 없이 기어만 바뀐 상황.
        delay(60)
        assertEquals(DrivingState.PARKED, combined.state.value) // 정차 화면 자동 전환 근거.

        assertEquals("GEAR_PARK", combined.lastGearRaw) // 원문 기록은 표시 전용으로 남는다.
        combined.stop()
    }

    @Test
    fun `못 읽은 기어 결과는 판정을 흔들지 않는다`() = runBlocking {
        val primary = FakePrimary()
        primary.set(DrivingState.DRIVING) // 1차가 주행이라고 본다.
        val gearAnswer = AtomicReference<String?>("NOT_INITIALIZED") // 콜드 스타트 — 못 읽음.
        val combined = CombinedDrivingState(
            primary = primary,
            gearQuery = { onResult -> onResult(gearAnswer.get()) },
            scope = testScope,
            pollPeriodMs = 20,
        )
        combined.start()
        delay(60)
        assertEquals(DrivingState.DRIVING, combined.state.value) // 보조 무시 — 1차 존중.
        combined.stop()
    }
}

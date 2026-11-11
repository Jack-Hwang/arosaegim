package app.arosaegim

import app.arosaegim.domain.Place
import app.arosaegim.ports.RouteEvent
import app.arosaegim.ports.RouteService
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 내비 재전환 단위 테스트(계획 문서 조치 8 — DEV_LOG F35·F50·Q15).
 *
 * SwitchableRouteService 와 NaviSwitchOverWatcher 는 Android 의존이 없으므로 JVM 에서 검증한다.
 * AppContainer 의 3초 대기(runBlocking + withTimeoutOrNull)는 Android Context 가 필요해 여기서
 * 다루지 않고, "대기 이후 늦은 초기화 성공" 시나리오는 감시기에 MutableStateFlow 로 주입해 재현한다.
 * 스코프는 TripSessionTest 와 같은 규약 — kotlinx-coroutines-test 미사용, Unconfined 주입.
 *
 * 검증 포인트:
 *  1. 전환 전 호출은 Fake(fallback)로 전달된다.
 *  2. 전환 후 호출은 Pleos(real)로 전달된다.
 *  3. 일방향 보장 — 재전환·되돌림 시도는 전부 무시된다.
 *  4. 늦은 ok → 전환+콜백, 늦은 failed → 전환 없음(거짓 표기 금지).
 */
class SwitchableRouteServiceTest {

    /* ---------------- 테스트용 가짜 구현 ---------------- */

    /** 호출·이벤트를 기록하는 가짜 RouteService(fake·real 공용). */
    private class RecordingRouteService : RouteService {
        val requested = mutableListOf<String>()
        val cancelCount = AtomicInteger(0)

        private val _events = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 16)
        override val events: Flow<RouteEvent> = _events.asSharedFlow()

        fun emit(event: RouteEvent) {
            assertTrue(_events.tryEmit(event))
        }

        override suspend fun startRoute(to: Place): Result<Unit> {
            requested.add(to.id)
            return Result.success(Unit)
        }

        override fun cancel() {
            cancelCount.incrementAndGet()
        }
    }

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private fun place(id: String = "p1") = Place(
        id = id, name = "테스트 장소", address = "테스트 주소",
        lat = 35.828983, lon = 129.228361, arriveRadiusM = 120,
        narration = "", quiz = emptyList(),
    )

    /* ---------------- 1·2·3: 위임과 일방향 ---------------- */

    @Test
    fun `1_전환 전에는 초기 위임자(Fake)로 전달된다`() = runBlocking {
        val fake = RecordingRouteService()
        val real = RecordingRouteService()
        val wrapper = SwitchableRouteService(fake, testScope)

        wrapper.startRoute(place("gj-museum"))
        wrapper.cancel()

        assertEquals(listOf("gj-museum"), fake.requested)
        assertEquals(1, fake.cancelCount.get())
        assertEquals(0, real.requested.size)
        assertFalse(wrapper.isSwitched)
        assertEquals(fake.javaClass.simpleName, wrapper.activeName.value) // 이름은 아직 Fake(초기 위임자)
    }

    @Test
    fun `2_전환 후에는 Pleos(real)로 전달된다`() = runBlocking {
        val fake = RecordingRouteService()
        val real = RecordingRouteService()
        val wrapper = SwitchableRouteService(fake, testScope)

        assertTrue(wrapper.switchTo(real))
        assertTrue(wrapper.isSwitched)
        assertTrue(wrapper.switched.value)
        assertEquals(real.javaClass.simpleName, wrapper.activeName.value)

        wrapper.startRoute(place("gj-museum"))
        wrapper.cancel()

        assertEquals(listOf("gj-museum"), real.requested)
        assertEquals(1, real.cancelCount.get())
        assertEquals(0, fake.requested.size) // 전환 뒤 Fake 는 다시 불리지 않는다
    }

    @Test
    fun `3_재전환은 일방향 — 두 번째 switchTo는 전부 무시된다`() = runBlocking {
        val fake = RecordingRouteService()
        val first = RecordingRouteService()
        val second = RecordingRouteService()
        val wrapper = SwitchableRouteService(fake, testScope)

        assertTrue(wrapper.switchTo(first))
        assertFalse(wrapper.switchTo(second)) // 이미 전환됨 — 재전환 금지
        assertFalse(wrapper.switchTo(first)) // 같은 real 로의 재시도도 무시
        assertFalse(wrapper.switchTo(fake)) // 되돌림(Fake 복귀)은 절대 안 된다

        wrapper.startRoute(place("x"))
        assertEquals(listOf("x"), first.requested)
        assertEquals(0, second.requested.size)
        assertEquals(0, fake.requested.size) // 일방향: Pleos → Fake 없음
    }

    @Test
    fun `4_초기 위임자 자신으로의 전환은 무시된다`() = runBlocking {
        val fake = RecordingRouteService()
        val wrapper = SwitchableRouteService(fake, testScope)

        assertFalse(wrapper.switchTo(fake))
        assertFalse(wrapper.isSwitched)
    }

    /* ---------------- 5: 이벤트 릴레이 ---------------- */

    @Test
    fun `5_이벤트는 활성 위임자의 것을 릴레이한다`() = runBlocking {
        val fake = RecordingRouteService()
        val real = RecordingRouteService()
        val wrapper = SwitchableRouteService(fake, testScope)

        val seen = mutableListOf<RouteEvent>()
        val job = testScope.launch { wrapper.events.collect { seen.add(it) } }
        yield() // Unconfined — launch 가 즉시 구독을 마쳤음을 보장

        fake.emit(RouteEvent.Ready) // 전환 전: Fake 이벤트도 그대로 흐른다
        assertTrue(wrapper.switchTo(real))
        real.emit(RouteEvent.Error("late")) // 전환 후: Pleos 이벤트로 교체된다
        job.cancel()

        assertEquals(
            listOf<RouteEvent>(RouteEvent.Ready, RouteEvent.Error("late")),
            seen,
        )
    }

    /* ---------------- 6·7·8: 늦은 초기화 감시기 ---------------- */

    @Test
    fun `6_감시기는 늦은 ok에서 전환하고 콜백을 부른다`() = runBlocking {
        val fake = RecordingRouteService()
        val real = RecordingRouteService()
        val initState = MutableStateFlow("pending")
        val wrapper = SwitchableRouteService(fake, testScope)
        var switchedCallback = 0

        NaviSwitchOverWatcher(
            initState = initState.asStateFlow(),
            switchable = wrapper,
            real = real,
            scope = testScope,
            onSwitched = { switchedCallback++ },
        ).start()

        assertEquals(0, switchedCallback) // pending 동안은 대기
        initState.value = "ok" // 3초 대기 뒤에 온 초기화 성공 콜백을 재현

        assertTrue(wrapper.isSwitched)
        assertEquals(1, switchedCallback)
        wrapper.startRoute(place("late"))
        assertEquals(listOf("late"), real.requested) // 전환 뒤 호출은 Pleos 로 간다
        assertEquals(0, fake.requested.size)
    }

    @Test
    fun `7_감시기는 늦은 failed에서 전환하지 않고 Fake를 유지한다`() = runBlocking {
        val fake = RecordingRouteService()
        val real = RecordingRouteService()
        val initState = MutableStateFlow("pending")
        val wrapper = SwitchableRouteService(fake, testScope)
        var failedCallback = 0

        NaviSwitchOverWatcher(
            initState = initState.asStateFlow(),
            switchable = wrapper,
            real = real,
            scope = testScope,
            onInitFailed = { failedCallback++ },
        ).start()

        initState.value = "failed" // 늦게 온 초기화 실패 콜백을 재현

        assertFalse(wrapper.isSwitched) // 재전환 없음
        assertEquals(1, failedCallback)
        wrapper.startRoute(place("still"))
        assertEquals(listOf("still"), fake.requested) // Fake 로 머문다('구현: 대체' 표기 유지)
        assertEquals(0, real.requested.size)
    }

    @Test
    fun `8_감시기는 이미 ok인 상태에서 즉시 전환한다`() = runBlocking {
        val fake = RecordingRouteService()
        val real = RecordingRouteService()
        val initState = MutableStateFlow("ok")
        val wrapper = SwitchableRouteService(fake, testScope)

        NaviSwitchOverWatcher(
            initState = initState.asStateFlow(),
            switchable = wrapper,
            real = real,
            scope = testScope,
        ).start()

        assertTrue(wrapper.isSwitched)
    }
}

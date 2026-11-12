package app.arosaegim.session

import app.arosaegim.data.ApiClient
import app.arosaegim.data.LocalVisit
import app.arosaegim.data.VisitQueue
import app.arosaegim.data.VisitStore
import app.arosaegim.domain.Course
import app.arosaegim.domain.Place
import app.arosaegim.ports.ArrivalDetector
import app.arosaegim.ports.ArrivalEvent
import app.arosaegim.ports.Narrator
import app.arosaegim.ports.RouteEvent
import app.arosaegim.ports.RouteService
import app.arosaegim.ports.TripSignalProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * TripSession 단위 테스트(T5) — 도착 파이프라인(스탬프·기록·음성·다음 감시)을 검증한다.
 *
 * 제약 준수:
 *  - kotlinx-coroutines-test 미사용. 스코프에 Dispatchers.Unconfined 를 주입해
 *    도착 이벤트 처리가 emit 스레드에서 동기 실행되게 하고, 남은 타이밍은
 *    awaitUntil(조건 폴링)로 기다린다.
 *  - VisitStore 는 실제 filesDir 를 쓰지 않는다. 임시 파일 생성자로 메모리상
 *    임시 파일을 가리키고, append 호출을 기록하는 하위 클래스로 관찰한다.
 *
 * T7/T8 추가 케이스(6~8):
 *  - visitQueue 주입 시 도착 → 큐 적재 1건 + flush 시도(offline-first 유지).
 *  - routeService 주입 시 목표 변경 → startRoute 호출 횟수.
 *  - 두 가짜의 실패 경로는 delay(1) 로 잠깐 양보한다. 실제 앱(Dispatchers.Main)에서는
 *    scope.launch 가 도착 문장 이후에 실행되므로, 테스트도 같은 순서를 재현하기 위함이다.
 */
class TripSessionTest {

    /* ---------------- 테스트용 가짜 구현 ---------------- */

    /** emit() 으로 도착을 직접 발행할 수 있는 가짜 감지기. watch/stop 호출도 기록한다. */
    private class FakeArrivalDetector : ArrivalDetector {
        private val _arrivals = MutableSharedFlow<ArrivalEvent>(extraBufferCapacity = 64)
        override val arrivals: Flow<ArrivalEvent> = _arrivals.asSharedFlow()

        val watched = mutableListOf<String>()
        var stopCount = 0

        fun emit(placeId: String, source: String = "fused") {
            assertTrue(_arrivals.tryEmit(ArrivalEvent(placeId, source)))
        }

        override fun watch(target: Place) {
            watched.add(target.id)
        }

        override fun stop() {
            stopCount++
        }
    }

    /** 호출(문장)을 기록하는 가짜 음성. */
    private class FakeNarrator : Narrator {
        val spoken = mutableListOf<String>()
        override val isAvailable: Boolean = true
        override suspend fun speak(text: String): Result<Unit> {
            spoken.add(text)
            return Result.success(Unit)
        }

        override fun stop() = Unit
    }

    /** append 를 기록하는 VisitStore 대체. 저장 파일은 테스트 임시 디렉터리의 파일이다. */
    private class RecordingVisitStore(file: File) : VisitStore(file) {
        val appended = mutableListOf<LocalVisit>()
        override fun append(visit: LocalVisit) {
            appended.add(visit)
            super.append(visit) // JSON 직렬화 경로도 실제로 통과시킨다(깨짐 감지).
        }

        override fun clear() {
            super.clear()
            appended.clear() // 관찰 목록도 함께 비워 초기화 검증이 가능하게 한다.
        }
    }

    /** startRoute 호출을 기록하는 가짜 RouteService(T7). placeId 만 기록한다(좌표 미기록). */
    private class RecordingRouteService(private val fail: Boolean = false) : RouteService {
        val requested = mutableListOf<String>()

        private val _events = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 16)
        override val events: Flow<RouteEvent> = _events.asSharedFlow()

        override suspend fun startRoute(to: Place): Result<Unit> {
            requested.add(to.id)
            if (fail) {
                delay(1) // 실제 내비처럼 잠깐 걸리게 — 도착 문장 이후 실패 문장 순서 재현.
                return Result.failure(IOException("테스트: 경로 요청 실패"))
            }
            return Result.success(Unit)
        }

        override fun cancel() = Unit
    }

    /**
     * enqueue/flush 를 기록하는 가짜 VisitQueue(T8). 실제 파일·네트워크를 쓰지 않는다.
     * flush 는 항상 실패로 답한다 — offline-first(전송 실패 시 큐 보존)를 검증하기 위함.
     */
    private class RecordingVisitQueue :
        VisitQueue(newTempFile(), ApiClient(baseUrl = "http://localhost:0")) {
        val enqueued = mutableListOf<LocalVisit>()

        /** E1: 세션이 enqueue 에 건네준 course_run_id·pin 기록(공백 여부만 검증한다). */
        val courseRunIds = mutableListOf<String?>()
        val pins = mutableListOf<String?>()
        var flushCalls = 0

        private val _pending = MutableStateFlow(0)
        override val pendingCount: StateFlow<Int> = _pending.asStateFlow()

        override fun enqueue(
            visit: LocalVisit,
            familyCode: String?,
            courseRunId: String?,
            pin: String?,
        ) {
            enqueued.add(visit)
            courseRunIds.add(courseRunId)
            pins.add(pin)
            _pending.value = enqueued.size
        }

        override suspend fun flush(): Result<Int> {
            flushCalls++
            delay(1) // 실제 네트워크처럼 잠깐 걸리게 — 도착 문장 이후 안내 문장 순서 재현.
            return Result.failure(IOException("테스트: 항상 오프라인"))
        }

        override fun clear() {
            enqueued.clear()
            _pending.value = 0
        }
    }

    /**
     * 차량 신호 가짜(T5-1·T6-2). 첫 번째 odometerKm() 호출을 코스 시작 스냅샷으로,
     * 이후 호출을 완주 시 조회로 답한다. startKm/endKm/party 에 null 을 주면 '읽기 실패'를 재현한다.
     */
    private class FakeTripSignalProvider(
        private val startKm: Double? = 1_000.0,
        private val endKm: Double? = 1_042.7,
        private val party: Int? = 4,
    ) : TripSignalProvider {
        var reads = 0 // odometerKm() 총 호출 횟수(시작 1회 + 완주 1회 = 2 기대).

        override suspend fun partySize(): Int? = party

        override suspend fun odometerKm(): Double? {
            reads++
            return if (reads == 1) startKm else endKm
        }
    }

    /* ---------------- 픽스처 ---------------- */

    private fun place(id: String): Place = Place(
        id = id, name = "장소-$id", address = "테스트 주소 $id",
        lat = 35.0, lon = 129.0, arriveRadiusM = 50,
        narration = "해설 $id", quiz = emptyList(),
    )

    /** 3장소 코스: p1 → p2 → p3 순서 규칙 검증용. */
    private fun course(): Course = Course(
        id = "course-test", title = "테스트 코스",
        places = listOf(place("p1"), place("p2"), place("p3")),
    )

    /** 세션 + 협력자 묶음. 세션 start() 까지 해 둔다. route/queue/signals 는 선택(T7/T8/T5-1 검증용). */
    private class Fixture(
        course: Course,
        val route: RouteService? = null,
        val queue: VisitQueue? = null,
        val signals: TripSignalProvider? = null,
        routeReady: StateFlow<Boolean>? = null, // A7: 늦은 초기화 전환 신호(없으면 null).
    ) {
        val fused = FakeArrivalDetector()
        val manual = FakeArrivalDetector()
        val narrator = FakeNarrator()
        val store = RecordingVisitStore(newTempFile())
        val session = TripSession(
            course = course,
            fusedArrival = fused,
            manualArrival = manual,
            narrator = narrator,
            visitStore = store,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            routeService = route, // T7 — null 이면 기존 동작만 한다.
            visitQueue = queue, // T8 — null 이면 기존 동작만 한다.
            tripSignals = signals, // T5-1·T6-2 — null 이면 기존 동작만 한다(기존 테스트 호환).
            routeBecameReady = routeReady, // A7 — null 이면 기존 동작만 한다.
        )

        init {
            session.start()
        }
    }

    /** 조건이 만족될 때까지 잠깐씩 폴링한다(coroutines-test 미사용 대체). */
    private suspend fun awaitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue("조건 만족 실패(타임아웃 ${timeoutMs}ms)", System.currentTimeMillis() < deadline)
            delay(5)
        }
    }

    /* ---------------- 케이스 ---------------- */

    @Test
    fun `1_첫 목표 도착시 스탬프_방문기록_음성이 각 1회씩 처리된다`() = runBlocking {
        val f = Fixture(course())
        assertEquals("시작 시 첫 목표를 감시한다", listOf("p1"), f.fused.watched)

        f.fused.emit("p1")
        awaitUntil { f.session.stampedCount.value == 1 }

        assertEquals(1, f.session.stampedCount.value)
        assertEquals("p1", f.session.lastArrival.value?.id)
        assertEquals("다음 목표는 p2", "p2", f.session.currentTarget.value?.id)
        assertEquals("방문 기록 1건", 1, f.store.appended.size)
        assertEquals("p1", f.store.appended[0].placeId)
        assertEquals("course-test", f.store.appended[0].courseId)
        assertEquals("fused", f.store.appended[0].source)
        assertTrue("시각이 기록된다", f.store.appended[0].visitedAtEpochMs > 0)
        assertEquals("음성 1회", 1, f.narrator.spoken.size)
        assertTrue("문장은 장소명 포함", f.narrator.spoken[0].contains("장소-p1"))
        assertEquals("한 줄 알림도 같은 문장", f.narrator.spoken[0], f.session.lastEventLine.value)
        assertTrue("다음 목표로 감시가 재등록된다", f.fused.watched.contains("p2"))
    }

    @Test
    fun `2_같은 장소 재도착은 무시되어 상태가 변하지 않는다`() = runBlocking {
        val f = Fixture(course())
        f.fused.emit("p1")
        awaitUntil { f.session.stampedCount.value == 1 }

        // 같은 장소 재도착(중복) — 처리 여유 후에도 값이 그대로여야 한다.
        f.fused.emit("p1")
        delay(50)
        assertEquals(1, f.session.stampedCount.value)
        assertEquals(1, f.store.appended.size)
        assertEquals(1, f.narrator.spoken.size)
        assertEquals("p2", f.session.currentTarget.value?.id)
    }

    @Test
    fun `3_순서 위반 도착은 무시된다`() = runBlocking {
        val f = Fixture(course())
        f.fused.emit("p1")
        awaitUntil { f.session.stampedCount.value == 1 }

        // 지금 목표는 p2 인데 p3 에 먼저 도착 → 무시.
        f.fused.emit("p3")
        delay(50)
        assertEquals(1, f.session.stampedCount.value)
        assertEquals(1, f.store.appended.size)
        assertEquals(1, f.narrator.spoken.size)
        assertEquals("p2", f.session.currentTarget.value?.id)

        // 수동 도착도 같은 규칙(위반은 무시) — source 가 달라도 규칙은 동일하다.
        f.manual.emit("p3", source = "manual")
        delay(50)
        assertEquals(1, f.session.stampedCount.value)
    }

    @Test
    fun `4_모든 장소 도착시 완주_감시가 중단된다`() = runBlocking {
        val f = Fixture(course())
        f.fused.emit("p1")
        awaitUntil { f.session.stampedCount.value == 1 }
        f.fused.emit("p2")
        awaitUntil { f.session.stampedCount.value == 2 }
        f.fused.emit("p3")
        awaitUntil { f.session.isCompleted.value }

        assertTrue("완주 플래그", f.session.isCompleted.value)
        assertNull("완주 후 목표는 null", f.session.currentTarget.value)
        assertEquals(3, f.session.stampedCount.value)
        assertEquals(3, f.store.appended.size)
        assertEquals(3, f.narrator.spoken.size)
        assertTrue("완주 시 감시 중지(stop)가 불린다", f.fused.stopCount >= 1)
    }

    @Test
    fun `5_resetCourse_후 진행과 기록이 처음으로 돌아간다`() = runBlocking {
        val f = Fixture(course())
        f.fused.emit("p1")
        awaitUntil { f.session.stampedCount.value == 1 }

        f.session.resetCourse()
        awaitUntil { f.session.stampedCount.value == 0 }

        assertEquals(0, f.session.stampedCount.value)
        assertEquals("첫 목표로 복귀", "p1", f.session.currentTarget.value?.id)
        assertEquals("방문 기록도 초기화", 0, f.store.appended.size)
        assertNull("한 줄 알림 초기화", f.session.lastEventLine.value)
        assertTrue("재시작 후 다시 감시", f.fused.watched.contains("p1"))
    }

    @Test
    fun `6_visitQueue_주입시 도착하면 큐에 1건 적재되고 전송을 시도한다`() = runBlocking {
        val queue = RecordingVisitQueue()
        val f = Fixture(course(), queue = queue)

        f.fused.emit("p1")
        awaitUntil { queue.flushCalls >= 1 }

        assertEquals("도착 1회 → 큐 적재 1건", 1, queue.enqueued.size)
        assertEquals("p1", queue.enqueued[0].placeId)
        assertEquals("course-test", queue.enqueued[0].courseId)
        assertEquals("fused", queue.enqueued[0].source)
        // E1: 코스 주행 식별자(course_run_id)는 매 start 마다 새로 발급되어 도착마다 실린다.
        assertTrue(
            "코스 주행 식별자가 enqueue 로 전달된다",
            !queue.courseRunIds[0].isNullOrBlank(),
        )
        assertTrue("전송(flush) 시도가 있었다", queue.flushCalls >= 1)
        // offline-first: flush 실패(서버 없음 가정)여도 기록은 큐에 남아 재전송 대기한다.
        assertEquals("전송 실패 시 큐에 유지", 1, queue.pendingCount.value)
        awaitUntil { f.session.lastEventLine.value == "서버 미연결, 기록은 이 기기에 저장됐어요" }
        assertEquals("주행 진행은 영향 없음", 1, f.session.stampedCount.value)
    }

    @Test
    fun `7_routeService_주입시 시작 목표와 바뀐 목표마다 startRoute를 호출한다`() = runBlocking {
        val route = RecordingRouteService()
        val f = Fixture(course(), route = route)

        // E5: 세션 시작 — 첫 목표 p1 로 1회 호출된다(기존에는 시작 직후 호출이 없었다).
        assertEquals("시작하면 첫 목표 1회 호출", listOf("p1"), route.requested)

        f.fused.emit("p1")
        awaitUntil { route.requested.size == 2 }
        assertEquals("p1 → p2 변경 — 새 목표로 요청", listOf("p1", "p2"), route.requested)

        f.fused.emit("p2")
        awaitUntil { route.requested.size == 3 }
        assertEquals("p2 → p3 변경", listOf("p1", "p2", "p3"), route.requested)

        f.fused.emit("p3")
        awaitUntil { f.session.isCompleted.value }
        assertEquals("완주 후 목표 null — 추가 요청 없음", 3, route.requested.size)
    }

    @Test
    fun `8_routeService_호출 실패시 주행은 유지되고 한 줄 안내만 남는다`() = runBlocking {
        val route = RecordingRouteService(fail = true)
        val f = Fixture(course(), route = route)

        f.fused.emit("p1")
        // 실제 launch 큐잉 순서대로: 도착 문장 → 실패 안내 문장이 마지막에 남는다.
        awaitUntil { f.session.lastEventLine.value == "내비 경로 요청 실패 — 앱은 계속 동작해요" }
        assertEquals("스탬프·진행은 정상", 1, f.session.stampedCount.value)
        assertEquals("다음 목표 감시도 정상", "p2", f.session.currentTarget.value?.id)
    }

    /** 완주까지 3장소를 순서대로 도착시킨다. */
    private suspend fun completeAll(f: Fixture) {
        f.fused.emit("p1")
        awaitUntil { f.session.stampedCount.value == 1 }
        f.fused.emit("p2")
        awaitUntil { f.session.stampedCount.value == 2 }
        f.fused.emit("p3")
        awaitUntil { f.session.isCompleted.value }
        delay(30) // 완주 시 launch 되는 odometer 재조회가 끝날 때까지 양보한다.
    }

    @Test
    fun `9_tripSignals_주입시 시작 스냅샷과 완주 차이로 tripKm과 partySize가 계산된다`() = runBlocking {
        val signals = FakeTripSignalProvider(startKm = 1_000.0, endKm = 1_042.7, party = 4)
        val f = Fixture(course(), signals = signals)

        // T6-2: start() 에서 인원 수를 1회 조회해 노출한다.
        awaitUntil { f.session.partySize.value == 4 }
        assertEquals("오늘 함께 온 인원(좌석 점유 합계)", 4, f.session.partySize.value)
        assertEquals("tripKm 은 아직 계산 전", null, f.session.tripKm.value)

        completeAll(f)

        assertEquals("T5-1: 완주 시 차이(1042.7 - 1000.0)", 42.7, f.session.tripKm.value!!, 1e-9)
        assertEquals("odometer 조회는 시작 1회 + 완주 1회", 2, signals.reads)
    }

    @Test
    fun `10_tripSignals_읽기 실패시 tripKm과 partySize는 null로 남는다`() = runBlocking {
        // (a) 시작 스냅샷 실패 — 완주 값을 받아도 차이를 만들지 않는다(둘 중 하나 null 이면 null).
        val failStart = FakeTripSignalProvider(startKm = null, endKm = 1_042.7, party = 4)
        val f1 = Fixture(course(), signals = failStart)
        awaitUntil { f1.session.partySize.value == 4 }
        completeAll(f1)
        assertNull("시작 스냅샷이 null 이면 tripKm 도 null", f1.session.tripKm.value)
        assertEquals("인원은 정상 노출", 4, f1.session.partySize.value)

        // (b) 완주 시 조회 실패 + 인원 읽기 실패 — 전부 null 로 유지된다(가짜 값 금지).
        val failEnd = FakeTripSignalProvider(startKm = 1_000.0, endKm = null, party = null)
        val f2 = Fixture(course(), signals = failEnd)
        assertNull("인원을 못 읽으면 null", f2.session.partySize.value)
        completeAll(f2)
        assertNull("완주 조회가 null 이면 tripKm 도 null", f2.session.tripKm.value)
    }

    @Test
    fun `11_경로가 늦게 준비되면 전환 시점에 현재 목표를 다시 요청한다`() = runBlocking {
        // (a) 준비 안 된 상태로 시작 → 늦은 전환(false→true) 시 현재 목표(p2) 재요청.
        val route = RecordingRouteService()
        val ready = MutableStateFlow(false)
        val f = Fixture(course(), route = route, routeReady = ready)

        assertEquals("시작 요청(E5) 1회", listOf("p1"), route.requested)

        f.fused.emit("p1")
        awaitUntil { route.requested.size == 2 } // p1 도착 → 목표 p2 로 변경 요청.
        assertEquals(listOf("p1", "p2"), route.requested)

        ready.value = true // A7: 늦은 초기화 전환 — 진행 중인 현재 목표(p2)를 다시 요청한다.
        awaitUntil { route.requested.size == 3 }
        assertEquals("전환 시 현재 목표 p2 재요청", listOf("p1", "p2", "p2"), route.requested)
        assertEquals("주행 진행은 영향 없음", 1, f.session.stampedCount.value)

        // (b) 이미 준비 상태(true)로 시작 — 시작 요청 1회로 충분, 중복 재요청이 없어야 한다.
        val route2 = RecordingRouteService()
        val f2 = Fixture(course(), route = route2, routeReady = MutableStateFlow(true))
        delay(50)
        assertEquals("준비 상태로 시작 — 시작 요청 1회만", listOf("p1"), route2.requested)
    }

    @Test
    fun `12_외부에서 감지기가 stop돼도 rewatch하면 현재 목표 감시와 도착 판정이 복구된다`() =
        runBlocking {
            val f = Fixture(course())
            f.fused.emit("p1")
            awaitUntil { f.session.stampedCount.value == 1 }
            assertEquals("p2", f.session.currentTarget.value?.id)

            // A3 결함 재현: 진단 관찰이(구 구조에서는) 감지기 stop 을 불렀다고 가정한다.
            f.fused.stop()

            // 복구 호출 — 현재 목표(p2)의 감시가 다시 등록된다.
            f.session.rewatchCurrentTarget()
            assertEquals("현재 목표 p2 가 다시 watch 된다", "p2", f.fused.watched.last())

            // 복구 뒤 도착 판정은 계속 된다(기준: 관찰을 꺼도 다음 장소 도착이 판정된다).
            f.fused.emit("p2")
            awaitUntil { f.session.stampedCount.value == 2 }
            assertEquals("다음 목표 p3 로 진행 유지", "p3", f.session.currentTarget.value?.id)
        }

    @Test
    fun `13_세션 수집이 중단된 뒤 rewatch하면 수집과 감시가 함께 복구된다`() = runBlocking {
        val f = Fixture(course())
        f.fused.emit("p1")
        awaitUntil { f.session.stampedCount.value == 1 }

        f.session.stop() // 백그라운드 복귀 등으로 수집이 끊긴 상태.
        f.fused.emit("p2") // 수집자가 없어 무시된다(SharedFlow replay=0).
        delay(50)
        assertEquals("중단 중 도착은 처리되지 않는다", 1, f.session.stampedCount.value)

        // rewatch → 수집이 비어 있으면 start() 경유로 감시 등록까지 함께 복구한다.
        f.session.rewatchCurrentTarget()
        assertTrue("복구 시 현재 목표 감시도 등록된다", f.fused.watched.last() == "p2")
        f.fused.emit("p2")
        awaitUntil { f.session.stampedCount.value == 2 }
        assertEquals("복구 뒤 진행도 정상", "p3", f.session.currentTarget.value?.id)
    }

    /**
     * 06문서 고칠 것 5번(2026-11-12): 재시작 후 진행 복원.
     * 이전 실행이 남긴 visits.json 기록 1건(p1)으로 세션을 만들면 스탬프 1/3·다음 목표 p2 로
     * 시작해야 한다. Fixture 의 저장소는 항상 새 임시 파일이라 여기서는 세션을 직접 만든다 —
     * 생성(복원) → start() 순서는 ArosaegimApp.onCreate 와 같다.
     */
    @Test
    fun `14_재시작 시 visits_json 기록으로 스탬프 진행을 복원한다`() = runBlocking {
        val file = newTempFile()
        // 이전 실행의 방문 기록 1건을 파일에 미리 적재한다(앱 재시작 상황 재현).
        VisitStore(file).append(
            LocalVisit(
                placeId = "p1",
                courseId = "course-test",
                visitedAtEpochMs = 1_000L,
                source = "fused",
            ),
        )

        val fused = FakeArrivalDetector()
        val store = RecordingVisitStore(file) // readAll 은 파일을 읽는다(하위 클래스도 같은 계약).
        val session = TripSession(
            course = course(),
            fusedArrival = fused,
            manualArrival = FakeArrivalDetector(),
            narrator = FakeNarrator(),
            visitStore = store,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        session.start()

        assertEquals("저장된 방문 1건이 스탬프 1/3 으로 복원된다", 1, session.stampedCount.value)
        assertEquals("다음 목표는 2번째 장소 p2", "p2", session.currentTarget.value?.id)
        assertEquals("마지막 도착 장소도 함께 복원된다", "p1", session.lastArrival.value?.id)
        assertTrue("start 는 복원된 목표(p2)부터 감시한다", fused.watched.contains("p2"))
        assertEquals("복원은 방문 기록을 새로 적재하지 않는다", 0, store.appended.size)
        assertNull("복원은 도착이 아니므로 한 줄 알림을 만들지 않는다", session.lastEventLine.value)

        // 복원 상태에서 다음 도착(p2)이 이어서 정상 처리된다(순서 규칙 유지).
        fused.emit("p2")
        awaitUntil { session.stampedCount.value == 2 }
        assertEquals("복원 뒤 진행도 정상", "p3", session.currentTarget.value?.id)
        assertEquals("이어진 도착만 기록에 추가된다", 1, store.appended.size)
    }
}

/** 테스트 전용 임시 파일 — deprecated createTempFile 대신 NIO 를 쓴다. */
private fun newTempFile(): File =
    File(Files.createTempDirectory("arosaegim-test").toFile(), "visits.json")

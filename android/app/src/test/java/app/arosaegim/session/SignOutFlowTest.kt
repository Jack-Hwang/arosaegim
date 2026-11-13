package app.arosaegim.session

import app.arosaegim.data.ApiClient
import app.arosaegim.data.LocalVisit
import app.arosaegim.data.SessionCredentials
import app.arosaegim.data.VisitQueue
import app.arosaegim.data.VisitStore
import app.arosaegim.domain.Course
import app.arosaegim.domain.Place
import app.arosaegim.ports.ArrivalDetector
import app.arosaegim.ports.ArrivalEvent
import app.arosaegim.ports.Narrator
import app.arosaegim.ui.parked.CourseStartState
import app.arosaegim.ui.parked.QuizStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 로그아웃 정리 일원화(T1, 2026-11-14) — SignOutFlow.run 의 순서·범위·부분 실패 규약을 검증한다.
 *
 * 제약 준수(기존 TripSessionTest·SignOutCleanupTest 스타일):
 *  - kotlinx-coroutines-test 미사용. TripSession 스코프에 Dispatchers.Unconfined 를 주입해
 *    도착 처리가 emit 스레드에서 동기 실행되게 하고, 남은 타이밍은 awaitUntil(폴링)로 기다린다.
 *  - FamilySessionStore 는 Context(SharedPreferences) 의존이라 JVM 에서 못 만든다 —
 *    FamilySessionCleaner 위임 가짜(FakeFamilySession)로 코드·PIN 해제를 검증한다.
 *    PIN 보관은 진짜 SessionCredentials(순수 Kotlin)를 쓴다.
 *  - QuizStateHolder·CourseStartState 는 프로세스 수명 싱글톤 object — 시험 순서 독립성을 위해
 *    각 시험에서 먼저 reset() 한다.
 *  - VisitQueue 는 진짜(임시 파일)를 쓴다 — clearMemoryState 가 실제 메모리 상태(대기 건수·
 *    PIN 보관·거절 기록)를 건드리는 경로까지 통과시킨다. flush 만 동기 성공으로 대체해
 *    전송 실패 문장(비동기 IO)이 시험 결과 줄 검증과 경합하지 않게 한다(오프라인 전송 경로의
 *    보존·재전송 규약은 VisitQueueTest·TripSessionTest 가 이미 검증한다).
 *
 * 검증 포인트(시험 목록 고정):
 *  ⒜ 가족 코드 접속 + 스탬프 2건 + 퀴즈 진행 + 코스 시작 상태에서 run → 전부 초기화,
 *     코드·PIN null, 두 파일(visits.json·visit_queue.json) 없음.
 *  ⒝ 파일 하나(visits.json 자리에 디렉터리)가 지워지지 않아도 나머지는 지워지고,
 *     결과 줄에 실패가 남는다(부분 실패 규약).
 *  ⒞ 둘러보기(guest) 모드에서도 같은 정리가 실행된다.
 *  ⒟ 큐는 메모리만 비우고 파일은 ②의 결과로 없다 — run 재호출도 예외 없다(멱등),
 *     stop 먼저라 resetCourse 뒤 감시 재시작이 없다(로그아웃 상태로 도착은 무시된다).
 */
class SignOutFlowTest {

    /* ---------------- 테스트용 가짜 구현 ---------------- */

    /** emit() 으로 도착을 직접 발행할 수 있는 가짜 감지기(TripSessionTest 와 같은 방식). */
    private class FakeArrivalDetector : ArrivalDetector {
        private val _arrivals = MutableSharedFlow<ArrivalEvent>(extraBufferCapacity = 64)
        override val arrivals: Flow<ArrivalEvent> = _arrivals.asSharedFlow()

        fun emit(placeId: String, source: String = "fused") {
            assertTrue(_arrivals.tryEmit(ArrivalEvent(placeId, source)))
        }

        override fun watch(target: Place) = Unit

        override fun stop() = Unit
    }

    /** 호출(문장)을 흡수하는 가짜 음성. */
    private class FakeNarrator : Narrator {
        override val isAvailable: Boolean = true
        override suspend fun speak(text: String): Result<Unit> = Result.success(Unit)
        override fun stop() = Unit
    }

    /**
     * 가족 세션 가짜 — FamilySessionStore 의 JVM 검증 가능 부분(코드 존재·게스트 플래그·
     * 세션 PIN 메모리)을 같은 계약으로 흉내 낸다. signOut 은 코드·세션 PIN·거절 상태를 해제한다.
     * 게스트 플래그는 기존 FamilySessionStore.signOut 규약대로 지우지 않는다(로그아웃 뒤에도
     * 둘러보기 모드 유지 — 이 시험의 관심사는 '게스트여도 정리가 실행되는가'다).
     */
    private class FakeFamilySession : FamilySessionCleaner {
        val credentials = SessionCredentials()
        var code: String? = null
        var guest = false
        var signOutCalls = 0

        override fun hasSession(): Boolean = code != null || guest

        override fun signOut() {
            signOutCalls++
            code = null // prefs.remove(KEY_CODE) 흉내.
            credentials.clear() // 세션 메모리(PIN·거절 상태) 해제 — '로그아웃 = 열람 권한 상실'.
        }
    }

    /**
     * flush 를 동기 성공으로 대체하는 큐 — enqueue·clearMemoryState 등 실제 메모리·파일 로직은
     * 진짜를 쓰고, 전송만 시험에서 대기하지 않게 한다(결과 줄 검증과의 경합 제거).
     */
    private class NoFlushQueue(file: File, api: ApiClient) : VisitQueue(file, api) {
        override suspend fun flush(): Result<Int> = Result.success(0)
    }


    /** 임시 디렉터리 한 개로 세션·큐·가족 세션·정리 흐름을 묶어 만든다. */
    private class Fixture {
        val dir: File = Files.createTempDirectory("arosaegim-signout-flow").toFile()
        val visitsFile = File(dir, "visits.json")
        val queueFile = File(dir, "visit_queue.json")
        val family = FakeFamilySession()
        val fused = FakeArrivalDetector()
        val store = VisitStore(visitsFile)
        val queue = NoFlushQueue(queueFile, ApiClient(baseUrl = "http://localhost:0"))
        val session = TripSession(
            course = Course(
                id = "course-signout", title = "정리 시험 코스",
                places = listOf(place("p1"), place("p2"), place("p3")),
            ),
            fusedArrival = fused,
            manualArrival = FakeArrivalDetector(),
            narrator = FakeNarrator(),
            visitStore = store,
            visitQueue = queue,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        val flow = SignOutFlow(
            familyStore = family,
            filesDir = dir,
            tripSession = session,
            visitQueue = queue,
        )

        fun visit(placeId: String, minute: Long): LocalVisit = LocalVisit(
            placeId = placeId,
            courseId = "course-signout",
            visitedAtEpochMs = 1_700_000_000_000L + minute * 60_000L,
            source = "fused",
        )

        /** 도착 1건 처리(stamp) — flush 가 동기라 대기 없이 즉시 귀결된다. */
        fun stampOnce(placeId: String) {
            session.start()
            fused.emit(placeId)
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

    /**
     * ⒜ 가족 코드 접속 상태에서 스탬프 2건·퀴즈 진행·코스 시작 상태를 만들고 run —
     * 스탬프 0·코스 시작 상태 초기·퀴즈 처음·코드·PIN null·두 파일 없음·큐 메모리 0.
     */
    @Test
    fun `1_가족 접속 상태에서 run하면 스탬프_퀴즈_코스_큐_파일이 모두 초기화된다`() = runBlocking {
        QuizStateHolder.reset()
        CourseStartState.reset()
        val f = Fixture()
        f.family.code = "A37K9Q2M" // 예시 가짜 코드(실제 가족 코드 금지).
        f.family.credentials.setPin("230444")

        f.stampOnce("p1")
        awaitUntil { f.session.stampedCount.value == 1 }
        f.stampOnce("p2")
        awaitUntil { f.session.stampedCount.value == 2 }
        // 도착 2건이 큐(전송 실패로 보존)와 방문 기록 파일을 만들었는지 사전 확인.
        awaitUntil { f.queue.pendingCount.value == 2 }
        assertTrue("방문 기록 파일이 있다", f.visitsFile.exists())
        assertTrue("큐 파일이 있다", f.queueFile.exists())

        // 프로세스 수명 홀더 상태 조성.
        QuizStateHolder.beginQuiz("p1")
        QuizStateHolder.quizIndex = 1
        QuizStateHolder.answered = true
        QuizStateHolder.chosen = 2
        CourseStartState.startCourse()

        val lines = f.flow.run()

        // 여행 세션: 스탬프·마지막 도착·한 줄 알림 초기화(진행은 첫 목표로).
        assertEquals("스탬프 0", 0, f.session.stampedCount.value)
        assertEquals("다음 목표는 첫 장소", "p1", f.session.currentTarget.value?.id)
        assertNull("마지막 도착 초기화", f.session.lastArrival.value)
        assertNull("한 줄 알림 초기화", f.session.lastEventLine.value)
        // 퀴즈 진행 처음으로.
        assertNull(QuizStateHolder.quizPlaceId)
        assertEquals(0, QuizStateHolder.quizIndex)
        assertNull(QuizStateHolder.answered)
        assertNull(QuizStateHolder.chosen)
        // 코스 시작·기록 열람 상태 초기.
        assertFalse(CourseStartState.courseStarted)
        assertFalse(CourseStartState.showRecord)
        // 가족 세션(코드·PIN) 해제.
        assertEquals("signOut 단계 1회", 1, f.family.signOutCalls)
        assertNull(f.family.credentials.codeOrNull())
        assertNull("PIN 은 세션 메모리에서도 해제", f.family.credentials.pinOrNull())
        // 두 파일 없음 + 큐 메모리 0.
        assertFalse("방문 기록 파일 없음", f.visitsFile.exists())
        assertFalse("큐 파일 없음", f.queueFile.exists())
        assertEquals("큐 메모리 대기 건수 0", 0, f.queue.pendingCount.value)
        // 결과 줄: ① 1줄 + ② 2줄 + ③~⑦ 5줄 = 8줄, 전부 성공 표기.
        assertEquals(8, lines.size)
        assertEquals("가족 세션(코드·PIN): 해제됨", lines[0])
        assertEquals("visits.json: 삭제됨", lines[1])
        assertEquals("visit_queue.json: 삭제됨", lines[2])
        assertTrue("나머지 단계는 초기화됨", lines.drop(3).all { it.endsWith("초기화됨") })
    }

    /**
     * ⒝ visits.json 자리를 디렉터리로 만들어 삭제를 막으면 — 나머지(큐 파일·메모리 상태)는
     * 지워지고, 결과 줄에 실패가 남는다(SignOutCleanupTest 의 부분 실패 재현 + 흐름 통과).
     */
    @Test
    fun `2_한 파일이 지워지지 않아도 나머지 정리는 계속되고 실패가 기록된다`() {
        QuizStateHolder.reset()
        CourseStartState.reset()
        val f = Fixture()
        f.family.code = "A37K9Q2M" // 예시 가짜 코드 — 파일 실패와 무관하게 세션 해제 줄이 '해제됨'이어야 한다.
        f.family.credentials.setPin("230444")
        f.visitsFile.mkdir() // 같은 이름의 디렉터리 — 안에 파일을 넣어 delete 가 false 를 반환하게 한다.
        java.io.File(f.visitsFile, "locked").writeText("keep")
        f.queue.enqueue(f.visit("p1", minute = 0), familyCode = "A37K9Q2M")
        assertTrue(f.queueFile.exists())

        val lines = f.flow.run()

        assertEquals("visits.json: 삭제 실패", lines[1])
        assertEquals("visit_queue.json: 삭제됨", lines[2])
        assertTrue("막아 둔 디렉터리는 남는다(사실 기록)", f.visitsFile.exists())
        assertFalse("나머지 큐 파일은 지워진다", f.queueFile.exists())
        assertEquals("파일 실패가 메모리 정리를 막지 않는다", 0, f.queue.pendingCount.value)
        assertNull("퀴즈 진행도 초기화된다", QuizStateHolder.quizPlaceId)
        assertFalse("코스 시작 상태도 초기화된다", CourseStartState.courseStarted)
        assertEquals("가족 세션 줄은 그대로 성공", "가족 세션(코드·PIN): 해제됨", lines[0])
        assertTrue("나머지 단계는 초기화됨", lines.drop(3).all { it.endsWith("초기화됨") })
    }

    /** ⒞ 둘러보기(guest) 모드(가족 코드 없음)에서도 같은 전체 정리가 실행된다. */
    @Test
    fun `3_둘러보기 모드에서도 run은 전체 정리를 수행한다`() = runBlocking {
        QuizStateHolder.reset()
        CourseStartState.reset()
        val f = Fixture()
        f.family.guest = true // 가족 코드 없이 둘러보기.

        f.stampOnce("p1")
        awaitUntil { f.session.stampedCount.value == 1 }
        awaitUntil { f.queue.pendingCount.value == 1 }
        QuizStateHolder.beginQuiz("p1")
        CourseStartState.startCourse()

        val lines = f.flow.run()

        assertEquals("둘러보기도 세션으로 본다 — signOut 단계가 실행된다", 1, f.family.signOutCalls)
        assertEquals("가족 세션(코드·PIN): 해제됨", lines[0])
        assertEquals("스탬프 0", 0, f.session.stampedCount.value)
        assertNull(QuizStateHolder.quizPlaceId)
        assertFalse(CourseStartState.courseStarted)
        assertFalse(f.visitsFile.exists())
        assertFalse(f.queueFile.exists())
        assertEquals(0, f.queue.pendingCount.value)
    }

    /**
     * ⒟ 큐는 메모리만 비우고 파일은 ②의 결과로 없다(순서 의존 확인) — run 재호출도 예외 없이
     * "없음" 줄로 답한다(멱등). stop 이 먼저라 resetCourse 뒤 감시 재시작이 없어, 정리된 세션은
     * 이후 도착을 처리하지 않는다.
     */
    @Test
    fun `4_큐 메모리만 비워지고 run 재호출도 예외 없다`() = runBlocking {
        QuizStateHolder.reset()
        CourseStartState.reset()
        val f = Fixture()
        f.family.code = "A37K9Q2M"
        f.family.credentials.setPin("230444")

        f.stampOnce("p1")
        awaitUntil { f.session.stampedCount.value == 1 }
        awaitUntil { f.queue.pendingCount.value == 1 }

        f.flow.run()

        assertFalse("②가 큐 파일을 지웠다", f.queueFile.exists())
        assertEquals("⑦이 메모리 대기 건수를 0 으로", 0, f.queue.pendingCount.value)
        // 파일이 이미 없는 상태(② 뒤 ⑦의 순서 의존)에서 메모리 정리 재호출도 예외가 없다.
        f.queue.clearMemoryState()
        assertEquals(0, f.queue.pendingCount.value)

        val second = f.flow.run()
        assertEquals("재호출: 세션 없음 줄", "가족 세션(코드·PIN): 없음(해제할 세션 없음)", second[0])
        assertEquals("재호출: 방문 기록 파일 없음 줄", "visits.json: 없음(삭제할 파일 없음)", second[1])
        assertEquals("재호출: 큐 파일 없음 줄", "visit_queue.json: 없음(삭제할 파일 없음)", second[2])
        assertTrue("재호출에도 나머지 단계는 초기화됨", second.drop(3).all { it.endsWith("초기화됨") })

        // stop 이 먼저라 resetCourse 끝의 감시 재시작은 걸리지 않는다 — 정리된 세션은 도착을 무시.
        f.fused.emit("p1")
        delay(100)
        assertEquals("정리된 세션은 도착을 처리하지 않는다", 0, f.session.stampedCount.value)
    }
}

/** 시험용 장소 — 파일 스코프라 중첩 클래스(Fixture)에서도 보인다. */
private fun place(id: String): Place = Place(
    id = id, name = "장소-$id", address = "테스트 주소 $id",
    lat = 35.0, lon = 129.0, arriveRadiusM = 50,
    narration = "해설 $id", quiz = emptyList(),
)

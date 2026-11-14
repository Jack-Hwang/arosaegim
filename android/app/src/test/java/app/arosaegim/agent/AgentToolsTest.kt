package app.arosaegim.agent

import app.arosaegim.domain.DrivingState
import app.arosaegim.domain.Place
import app.arosaegim.domain.QuizItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AgentTools 단위 시험(T5) — Gleo 음성 도구의 분기표(문구·상태 고정)를 검증한다.
 *
 * AgentTools 는 Android·Context 의존이 없는 순수 Kotlin이라 JVM 시험으로 충분하다.
 * 스냅샷은 헬퍼 [snapshot](기본값 + override 인자)으로 만들고, 장소는 [place]
 * (TripSessionTest 의 place() 헬퍼 형태 — 파일 스코프 private fun)로 만든다.
 * 테스트용 장소 좌표는 35.0/129.0 을 쓴다 — 문장에 좌표 숫자 패턴이 새어 나오는지
 * 잡아 내는 12번 케이스(G6)의 감쇠기 역할까지 겸한다.
 */
class AgentToolsTest {

    /* ---------------- 헬퍼(파일 스코프) ---------------- */

    /** 문항 하나 — 도착 장소가 '퀴즈 보유' 상태임을 나타내는 최소 구성. */
    private fun quizItem(): QuizItem = QuizItem(
        q = "이 장소의 문제는?", choices = listOf("보기1", "보기2"), answer = 0, explain = "해설",
    )

    /** 테스트용 장소(TripSessionTest 의 place() 형태). quiz 를 넘기면 퀴즈 보유 장소가 된다. */
    private fun place(id: String, quiz: List<QuizItem> = emptyList()): Place = Place(
        id = id, name = "장소-$id", address = "테스트 주소 $id",
        lat = 35.0, lon = 129.0, arriveRadiusM = 50,
        narration = "해설 $id", quiz = quiz,
    )

    /** 기본 스냅샷: 세션 있음·정차·코스 시작됨·다음 목표 p2·마지막 도착 p1(퀴즈 보유). */
    private fun snapshot(
        driving: DrivingState = DrivingState.PARKED,
        hasSession: Boolean = true,
        courseStarted: Boolean = true,
        currentTarget: Place? = place("p2"),
        lastArrival: Place? = place("p1", quiz = listOf(quizItem())),
        stampedCount: Int = 1,
        stampTotal: Int = 3,
        isCompleted: Boolean = false,
        partySize: Int? = null,
        tripKm: Double? = null,
    ): AgentAppSnapshot = AgentAppSnapshot(
        driving = driving,
        hasSession = hasSession,
        courseStarted = courseStarted,
        currentTarget = currentTarget,
        lastArrival = lastArrival,
        stampedCount = stampedCount,
        stampTotal = stampTotal,
        isCompleted = isCompleted,
        partySize = partySize,
        tripKm = tripKm,
    )

    /* ---------------- 케이스 ---------------- */

    @Test
    fun `1_세션 없으면 GET_NEXT_STAMP_PLACE 는 fail`() {
        val result = AgentTools.dispatch("GET_NEXT_STAMP_PLACE", snapshot(hasSession = false))
        assertEquals(AgentStatus.FAIL, result.status)
        assertEquals("Please sign in with your family code first.", result.message)
    }

    @Test
    fun `2_세션과 다음 목표가 있으면 success 와 장소 이름`() {
        val result = AgentTools.dispatch("GET_NEXT_STAMP_PLACE", snapshot())
        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("Next stop: 장소-p2.", result.message)
        assertEquals("p2", result.data?.get("placeId"))
    }

    @Test
    fun `3_완주라면 currentTarget 이 null 일 때 success 와 완주 문구`() {
        val result = AgentTools.dispatch(
            "GET_NEXT_STAMP_PLACE",
            snapshot(currentTarget = null, isCompleted = true, stampedCount = 3, stampTotal = 3),
        )
        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("You have stamped every place on this course.", result.message)
        assertEquals(null, result.data)
    }

    @Test
    fun `4_주행 중이면 START_QUIZ 는 fail`() {
        val result = AgentTools.dispatch(
            "START_QUIZ",
            snapshot(driving = DrivingState.DRIVING),
        )
        assertEquals(AgentStatus.FAIL, result.status)
        assertEquals("Quiz is available only when the car is parked.", result.message)
    }

    @Test
    fun `5_코스 미시작과 스탬프 0이면 START_QUIZ 는 fail`() {
        val result = AgentTools.dispatch(
            "START_QUIZ",
            snapshot(courseStarted = false, stampedCount = 0),
        )
        assertEquals(AgentStatus.FAIL, result.status)
        assertEquals("Please start a course first.", result.message)
    }

    @Test
    fun `6_lastArrival 이 null 이면 START_QUIZ 는 fail`() {
        val result = AgentTools.dispatch("START_QUIZ", snapshot(lastArrival = null))
        assertEquals(AgentStatus.FAIL, result.status)
        assertEquals("Arrive at a place first to unlock its quiz.", result.message)
    }

    @Test
    fun `7_도착 장소의 퀴즈가 비어 있으면 START_QUIZ 는 fail`() {
        val result = AgentTools.dispatch(
            "START_QUIZ",
            snapshot(lastArrival = place("p1")), // quiz 비어 있음
        )
        assertEquals(AgentStatus.FAIL, result.status)
        assertEquals("This place has no quiz.", result.message)
    }

    @Test
    fun `8_정차와 도착과 퀴즈가 있으면 START_QUIZ 는 success`() {
        val result = AgentTools.dispatch("START_QUIZ", snapshot())
        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("Starting the quiz for 장소-p1.", result.message)
    }

    @Test
    fun `9_GET_TODAY_SUMMARY 는 스탬프 수를 포함한다`() {
        val result = AgentTools.dispatch(
            "GET_TODAY_SUMMARY",
            snapshot(stampedCount = 2, stampTotal = 5, tripKm = 12.5, partySize = 4),
        )
        assertEquals(AgentStatus.SUCCESS, result.status)
        // null 이 아닌 항목은 모두 문장에 붙는다(거리·인원 포함 순서 고정).
        assertEquals("Stamps 2 of 5. Distance 12.5 km. Party 4.", result.message)
        assertTrue(result.message.contains("Stamps 2 of 5"))
    }

    @Test
    fun `10_인원과 거리가 null 이면 해당 항목은 문장에서 통째로 생략된다`() {
        val result = AgentTools.dispatch("GET_TODAY_SUMMARY", snapshot())
        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("Stamps 1 of 3.", result.message)
        assertFalse(result.message.contains("Distance"))
        assertFalse(result.message.contains("Party"))
    }

    @Test
    fun `11_ALLOWED 밖 이름은 dispatch 에서 fail`() {
        val result = AgentTools.dispatch("OPEN_TRUNK", snapshot())
        assertEquals(AgentStatus.FAIL, result.status)
        assertEquals("OPEN_TRUNK", result.functionName)
        assertEquals("This action is not supported.", result.message)
    }

    @Test
    fun `12_세 함수 메시지에 좌표 숫자 패턴이 없다_G6`() {
        // 테스트 장소의 좌표가 35.0/129.0 이므로, 문장에 좌표가 새면 아래 패턴에 걸린다.
        val latPattern = Regex("35\\.")
        val lonPattern = Regex("129\\.")
        AgentTools.ALLOWED.forEach { functionName ->
            val result = AgentTools.dispatch(functionName, snapshot())
            assertFalse("좌표 패턴 감지($functionName): ${result.message}", latPattern.containsMatchIn(result.message))
            assertFalse("좌표 패턴 감지($functionName): ${result.message}", lonPattern.containsMatchIn(result.message))
        }
    }

    @Test
    fun `13_모든 응답 문장은 120자 이내다`() {
        // 성공·실패 대표 경로 전부를 대상으로 문장 길이 규약(120자 이내)을 확인한다.
        val samples = listOf(
            AgentTools.getNextStampPlace(snapshot()),
            AgentTools.getNextStampPlace(snapshot(hasSession = false)),
            AgentTools.getNextStampPlace(snapshot(currentTarget = null)),
            AgentTools.startQuiz(snapshot()),
            AgentTools.startQuiz(snapshot(driving = DrivingState.DRIVING)),
            AgentTools.startQuiz(snapshot(lastArrival = null)),
            AgentTools.startQuiz(snapshot(lastArrival = place("p1"))),
            AgentTools.getTodaySummary(snapshot(stampedCount = 12, stampTotal = 15, tripKm = 123.4, partySize = 8)),
            AgentTools.dispatch("UNKNOWN_ACTION", snapshot()),
        )
        samples.forEach { result ->
            assertTrue("문장 120자 초과(${result.functionName}): ${result.message}", result.message.length <= 120)
        }
    }
}

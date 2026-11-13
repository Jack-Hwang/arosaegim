package app.arosaegim.ui.parked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * 퀴즈 진행 보존 홀더 시험(P2 — 주행 중 화면 전환·복귀 이어풀기, 2026-11-14).
 *
 * 주행 중에는 ParkedActivity 가 distractionOptimized 를 선언하지 않아 시스템이 화면을
 * 가리고 Activity 를 파괴할 수 있다. 퀴즈 진행이 이 홀더(프로세스 수명)에 남아 있어야
 * 정차 복귀 시 처음부터 다시 풀지 않는다 — 그 계약을 검증한다.
 */
class QuizStateHolderTest {

    @Test
    fun `같은 장소면 퀴즈 진행을 보존한다 — 주행 복귀 이어풀기`() {
        QuizStateHolder.reset()
        QuizStateHolder.beginQuiz("gj-museum")
        QuizStateHolder.quizIndex = 1
        QuizStateHolder.answered = true
        QuizStateHolder.chosen = 2

        // 주행 복귀 후 같은 장소로 다시 시작해도 진행은 그대로다(Activity 재생성과 무관).
        QuizStateHolder.beginQuiz("gj-museum")
        assertEquals(1, QuizStateHolder.quizIndex)
        assertEquals(true, QuizStateHolder.answered)
        assertEquals(2, QuizStateHolder.chosen)
    }

    @Test
    fun `장소가 바뀌면 문항 인덱스와 답안을 초기화한다`() {
        QuizStateHolder.reset()
        QuizStateHolder.beginQuiz("gj-museum")
        QuizStateHolder.quizIndex = 1
        QuizStateHolder.answered = false
        QuizStateHolder.chosen = 0

        QuizStateHolder.beginQuiz("gj-daereungwon")
        assertEquals(0, QuizStateHolder.quizIndex)
        assertNull(QuizStateHolder.answered)
        assertNull(QuizStateHolder.chosen)
        assertEquals("gj-daereungwon", QuizStateHolder.quizPlaceId)
    }

    @Test
    fun `clamp는 문항 수가 줄었을 때 인덱스를 목록 안으로 보정한다`() {
        QuizStateHolder.reset()
        QuizStateHolder.beginQuiz("gj-museum")
        QuizStateHolder.quizIndex = 5

        QuizStateHolder.clamp(2)
        assertEquals(1, QuizStateHolder.quizIndex)

        QuizStateHolder.clamp(0)
        assertEquals(0, QuizStateHolder.quizIndex)
    }
}

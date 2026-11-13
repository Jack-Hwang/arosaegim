package app.arosaegim.pleos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 좌석 점유 집계 순수 함수 [countOccupied] 단위 테스트(Q27 견고화).
 *
 * 키는 좌석 식별자 역할의 Int(1~9)를 쓴다 — 함수가 키 타입에 의존하지 않으므로 Pleos SDK
 * 클래스를 로딩하지 않고 JVM 에서 돈다.
 * 값 규약: true = 점유, false = 빈 좌석, null = 조회 실패·타임아웃.
 */
class SeatCountingTest {

    /** 9석(키 1..9) 답을 만든다. occupied = 점유 좌석, failed = 실패(null) 좌석, 나머지는 빈 좌석. */
    private fun answers9(occupied: Set<Int> = emptySet(), failed: Set<Int> = emptySet()): Map<Int, Boolean?> =
        (1..9).associateWith {
            when (it) {
                in failed -> null
                in occupied -> true
                else -> false
            }
        }

    @Test
    fun `0명 - 9석 전부 false 답이면 0을 돌려준다`() {
        assertEquals(0, countOccupied(answers9()))
    }

    @Test
    fun `1명 - 답한 좌석 중 true 한 개만 합산한다`() {
        assertEquals(1, countOccupied(answers9(occupied = setOf(2))))
    }

    @Test
    fun `9명 - 전 좌석 true 면 9를 돌려준다`() {
        assertEquals(9, countOccupied(answers9(occupied = (1..9).toSet())))
    }

    @Test
    fun `빈 답 - 모든 좌석이 실패 null 이면 null 이다`() {
        assertNull(countOccupied(answers9(failed = (1..9).toSet())))
    }

    @Test
    fun `빈 답 - 요청 좌석이 아예 없는 빈 맵도 null 이다`() {
        assertNull(countOccupied(emptyMap<Int, Boolean?>()))
    }

    @Test
    fun `null 혼합 - 실패 좌석은 제외하고 성공 답의 true 만 합산한다`() {
        // 9석 중 2석 실패(응답률 7/9 = 78% — 통과), 점유 1석 → 1.
        assertEquals(1, countOccupied(answers9(occupied = setOf(5), failed = setOf(1, 9))))
    }

    @Test
    fun `응답률 미달 - 9석 요청에 4석 응답이면 답이 있어도 null 이다`() {
        // 9석 중 5석 실패(응답률 4/9 = 44% < MIN_RESPONSE_RATIO), 답한 4석 중 true 2석 —
        // 합산을 거부한다. F73 의 '오늘은 1명' 오표시가 이 상황에서 나온다(Q27 방어선).
        assertNull(countOccupied(answers9(occupied = setOf(1, 2), failed = (5..9).toSet())))
    }

    @Test
    fun `응답률 경계 - 정확히 50% 응답은 통과한다`() {
        // 2석 요청 1석 응답 = 50% (미만이 아님) → 성공 답의 true 를 합산한다.
        assertEquals(1, countOccupied(mapOf(1 to true, 2 to null)))
    }

    @Test
    fun `capability 부분 목록 - 받은 좌석만 집계하고 미제공 좌석은 추측하지 않는다`() {
        // capability 가 1석만 줬고 그 좌석이 false 답이면 0 — 나머지 8석을 가정해 세지 않는다.
        assertEquals(0, countOccupied(mapOf(1 to false)))
    }
}

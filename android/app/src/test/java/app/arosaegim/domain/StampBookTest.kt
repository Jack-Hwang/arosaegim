package app.arosaegim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 스탬프북 단위 테스트(03문서 5-1).
 * 검증 대상: 진행률(내림), 완성 배지, 정의에 없는 placeId 무시, 중복 스탬프 무시.
 */
class StampBookTest {

    /** 장소 3개(p1~p3) 스탬프북 정의 픽스처. */
    private fun def(vararg placeIds: String): StampBookDef = StampBookDef(
        id = "gyeongju-silla", title = "경주 신라 이야기", region = "경주", country = "KR",
        placeIds = placeIds.toList(), badge = "신라 이야기꾼",
    )

    @Test
    fun `1개 찍으면 진행률은 내림 33이다`() {
        val book = StampBook(def("p1", "p2", "p3"))
        book.addStamp("p1")

        assertEquals(1, book.stampedPlaceIds.size)
        assertEquals("1/3 × 100 = 33.3… → 내림 33", 33, book.progressPercent)
        assertFalse(book.isCompleted)
    }

    @Test
    fun `2개 찍으면 진행률은 내림 66이다`() {
        val book = StampBook(def("p1", "p2", "p3"))
        book.addStamp("p1")
        book.addStamp("p2")

        assertEquals("2/3 × 100 = 66.6… → 내림 66", 66, book.progressPercent)
        assertFalse(book.isCompleted)
    }

    @Test
    fun `모두 찍으면 progressPercent는 100이고 earnedBadge는 배지명이다`() {
        val book = StampBook(def("p1", "p2", "p3"))
        book.addStamp("p1")
        book.addStamp("p2")
        book.addStamp("p3")

        assertEquals(100, book.progressPercent)
        assertTrue(book.isCompleted)
        assertEquals("완성 시 정의의 배지 이름을 돌려준다", "신라 이야기꾼", book.earnedBadge)
    }

    @Test
    fun `미완성이면 earnedBadge는 null이다`() {
        val book = StampBook(def("p1", "p2", "p3"))
        book.addStamp("p1")

        assertNull("미완성 스탬프북에는 배지가 없다", book.earnedBadge)
    }

    @Test
    fun `정의에 없는 placeId는 무시된다`() {
        val book = StampBook(def("p1", "p2", "p3"))

        book.addStamp("pX")   // 정의에 없는 장소
        book.addStamp("")     // 빈 문자열도 정의에 없으므로 무시

        assertTrue("정의 밖 스탬프는 아무 변화가 없다", book.stampedPlaceIds.isEmpty())
        assertEquals(0, book.progressPercent)
        assertFalse(book.isCompleted)
    }

    @Test
    fun `중복 스탬프는 무시되고 진행률이 변하지 않는다`() {
        val book = StampBook(def("p1", "p2", "p3"))
        book.addStamp("p1")
        assertEquals(33, book.progressPercent)

        book.addStamp("p1")   // 중복
        book.addStamp("p1")   // 재중복

        assertEquals("중복 addStamp 는 진행률을 바꾸지 않는다", 33, book.progressPercent)
        assertEquals(setOf("p1"), book.stampedPlaceIds)
        assertFalse(book.isCompleted)
    }

    @Test
    fun `완성 후 같은 장소를 다시 찍어도 상태가 변하지 않는다`() {
        val book = StampBook(def("p1", "p2", "p3"))
        book.addStamp("p1")
        book.addStamp("p2")
        book.addStamp("p3")

        book.addStamp("p1") // 완성 상태에서 중복 도착

        assertEquals(100, book.progressPercent)
        assertEquals("신라 이야기꾼", book.earnedBadge)
        assertEquals(3, book.stampedPlaceIds.size)
    }

    @Test
    fun `빈 정의는 완성으로 보지 않는다`() {
        val book = StampBook(def()) // placeIds 0개

        assertEquals("정의 장소가 0개면 진행률도 0", 0, book.progressPercent)
        assertFalse("빈 정의로 배지를 주는 사고를 방지한다", book.isCompleted)
        assertNull(book.earnedBadge)
    }

    @Test
    fun `stampedPlaceIds는 찍은 순서대로 유지되고 외부 수정에 안전하다`() {
        val book = StampBook(def("p1", "p2", "p3"))
        book.addStamp("p2")
        book.addStamp("p1")

        assertEquals("집합이지만 찍은 순서를 보존한다", listOf("p2", "p1"), book.stampedPlaceIds.toList())
    }
}

package app.arosaegim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 코스 진행 단위 테스트. Place 헬퍼로 간결한 픽스처를 사용한다.
 */
class CourseProgressTest {

    /** 테스트용 장소 픽스처. */
    private fun place(id: String): Place = Place(
        id = id, name = "장소-$id", address = "테스트 주소 $id",
        lat = 35.0, lon = 129.0, arriveRadiusM = 50,
        narration = "해설 $id", quiz = emptyList(),
    )

    /** 3개 장소(p1→p2→p3) 코스 픽스처. */
    private fun threeCourse(): Course = Course(
        id = "course-1", title = "테스트 코스",
        places = listOf(place("p1"), place("p2"), place("p3")),
    )

    @Test
    fun `3곳 모두 스탬프하면 완주되고 currentTarget은 null이다`() {
        val progress = CourseProgress(threeCourse())
        progress.markStamped("p1")
        progress.markStamped("p2")
        progress.markStamped("p3")

        assertTrue(progress.isCompleted)
        assertNull(progress.currentTarget)
        assertEquals(3, progress.completedPlaces.size)
    }

    @Test
    fun `1곳만 찍으면 다음 목표는 2번째 장소이다`() {
        val progress = CourseProgress(threeCourse())
        progress.markStamped("p1")

        assertFalse(progress.isCompleted)
        assertEquals("p2", progress.currentTarget?.id)
        assertEquals(listOf("p1"), progress.completedPlaces.map { it.id })
    }

    @Test
    fun `다음 목적지는 찍은 만큼 건너뛰어 계산된다`() {
        val progress = CourseProgress(threeCourse())

        // 초기 목표는 코스 첫 장소
        assertEquals("p1", progress.currentTarget?.id)

        progress.markStamped("p1")
        assertEquals("p2", progress.currentTarget?.id)

        progress.markStamped("p2")
        assertEquals("p3", progress.currentTarget?.id)
    }

    @Test
    fun `코스에 없는 placeId는 무시된다`() {
        val progress = CourseProgress(threeCourse())
        progress.markStamped("pX")
        progress.markStamped("")

        assertTrue(progress.completedPlaces.isEmpty())
        assertEquals("p1", progress.currentTarget?.id)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `중복 markStamped는 무시된다`() {
        val progress = CourseProgress(threeCourse())
        progress.markStamped("p1")
        progress.markStamped("p1")
        progress.markStamped("p1")

        assertEquals(1, progress.completedPlaces.size)
        assertEquals("p2", progress.currentTarget?.id)
    }

    @Test
    fun `completedPlaces는 스탬프를 찍은 순서대로 유지된다`() {
        val progress = CourseProgress(threeCourse())
        progress.markStamped("p2")
        progress.markStamped("p1")

        assertEquals(listOf("p2", "p1"), progress.completedPlaces.map { it.id })
    }
}

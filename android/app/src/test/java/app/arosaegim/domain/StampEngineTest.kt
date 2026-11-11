package app.arosaegim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 스탬프 엔진 단위 테스트 (03문서 5-1 시나리오).
 * 거리/반경 경계 판정("거리 == 반경 이내(<=)면 도착")은 Haversine 호출자의
 * 책임이므로, 테스트에서는 callerArrival 헬퍼가 호출자 역할을 모킹한다.
 */
class StampEngineTest {

    /** 호출자 역할 모킹: Haversine 거리가 반경 이내(<=)일 때만 엔진에 placeId를 전달한다. */
    private fun callerArrival(
        engine: StampEngine,
        userLat: Double,
        userLon: Double,
        place: Place,
        currentTargetPlaceId: String?,
    ): StampResult? {
        val dist = Haversine.distanceM(userLat, userLon, place.lat, place.lon)
        return if (dist <= place.arriveRadiusM) {
            engine.registerArrival(place.id, currentTargetPlaceId)
        } else {
            null // 반경 밖 도착이 아니므로 엔진에 전달하지 않는다
        }
    }

    /** 테스트용 장소 픽스처. */
    private fun place(id: String): Place = Place(
        id = id, name = "장소-$id", address = "테스트 주소 $id",
        lat = 35.0, lon = 129.0, arriveRadiusM = 50,
        narration = "해설 $id", quiz = emptyList(),
    )

    @Test
    fun `1_반경 안에서 동일 placeId 연속 도착 2회는 1회만 스탬프된다`() {
        val engine = StampEngine()

        val first = callerArrival(engine, 35.0, 129.0, place("p1"), currentTargetPlaceId = "p1")
        val second = callerArrival(engine, 35.0, 129.0, place("p1"), currentTargetPlaceId = "p1")

        assertNotNull(first)
        assertEquals("p1", first?.placeId)
        assertEquals(1, first?.stampNo)
        assertNull("재진입 중복 도착은 null", second)
        assertEquals(setOf("p1"), engine.stampedPlaceIds)
    }

    @Test
    fun `2_재진입 중복 방지_A획득후_B획득하고_다시_A에 도착하면 null이다`() {
        val engine = StampEngine()
        assertEquals(1, engine.registerArrival("A", "A")?.stampNo)
        assertEquals(2, engine.registerArrival("B", "B")?.stampNo)

        // 다시 A 지점에 물리적으로 도착해도 목표는 C이므로 스탬프되지 않는다
        assertNull(engine.registerArrival("A", "C"))
        // 목표가 A로 돌아와도 이미 획득한 장소는 다시 획득할 수 없다
        assertNull(engine.registerArrival("A", "A"))
        assertEquals(setOf("A", "B"), engine.stampedPlaceIds)
    }

    @Test
    fun `3_코스 순서 규칙_현재 목표가 아닌 장소 도착은 null이다`() {
        val engine = StampEngine()
        assertEquals(1, engine.registerArrival("p1", "p1")?.stampNo)

        // 다음 목표는 p2인데 p3에 먼저 도착 → 무시
        assertNull(engine.registerArrival("p3", "p2"))
        assertTrue(engine.stampedPlaceIds.contains("p1"))
        assertEquals(1, engine.stampedPlaceIds.size)
    }

    @Test
    fun `4_완주 후 currentTargetPlaceId가 null이면 도착은 항상 null이다`() {
        val engine = StampEngine()
        engine.registerArrival("p1", "p1")
        engine.registerArrival("p2", "p2")

        // 코스 완주(null 목표) 이후의 도착 신호는 어떤 장소든 무시한다
        assertNull(engine.registerArrival("p3", null))
        assertNull(engine.registerArrival("p1", null))
        assertEquals(setOf("p1", "p2"), engine.stampedPlaceIds)
    }

    @Test
    fun `5_reset 후 다시 획득 가능하고 stampNo가 1부터 다시 시작된다`() {
        val engine = StampEngine()
        engine.registerArrival("A", "A")
        engine.registerArrival("B", "B")

        engine.reset()
        assertTrue("reset 후 기록이 비어야 한다", engine.stampedPlaceIds.isEmpty())

        val again = engine.registerArrival("A", "A")
        assertNotNull("reset 후에는 같은 장소도 다시 획득 가능", again)
        assertEquals(1, again?.stampNo)
    }

    @Test
    fun `호출자 경계 판정_거리가 반경 이내여야만 엔진에 전달된다`() {
        val engine = StampEngine()
        // 적도(위도 0)에서는 경도 1도 ≈ 111,194.93m 이다.
        val boundary = Place(
            id = "p1", name = "경계 장소", address = "테스트 주소 p1",
            lat = 0.0, lon = 129.0, arriveRadiusM = 0,
            narration = "해설", quiz = emptyList(),
        )

        // 반경 111,194m: 실거리 111,194.93 > 반경 → 호출자가 엔진을 호출하지 않는다.
        val outside = boundary.copy(arriveRadiusM = 111_194)
        assertNull(
            "거리 > 반경이면 도착이 아니다",
            callerArrival(engine, 0.0, 130.0, outside, currentTargetPlaceId = "p1"),
        )
        assertTrue(engine.stampedPlaceIds.isEmpty())

        // 반경 111,195m: 실거리 111,194.93 <= 반경 → 도착("<=이면 도착" 경계 규칙).
        val inside = boundary.copy(arriveRadiusM = 111_195)
        val result = callerArrival(engine, 0.0, 130.0, inside, currentTargetPlaceId = "p1")
        assertNotNull("거리 <= 반경이면 도착으로 판정한다", result)
        assertEquals(1, result?.stampNo)
    }
}

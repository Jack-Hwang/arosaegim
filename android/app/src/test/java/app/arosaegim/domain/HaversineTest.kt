package app.arosaegim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Haversine 거리 계산 단위 테스트.
 * 기준값: (0,0)-(0,1) 경도 1도 거리 = 2π·6,371,000/360 ≈ 111,194.93m (±300m 허용).
 */
class HaversineTest {

    @Test
    fun `경도 1도 거리는 약 111194_9m 이다`() {
        // 적도에서 경도 1도 = 지구 둘레의 1/360
        val d = Haversine.distanceM(0.0, 0.0, 0.0, 1.0)
        assertEquals(111194.9, d, 300.0)
    }

    @Test
    fun `동일 좌표 거리는 0 이다`() {
        assertEquals(0.0, Haversine.distanceM(35.828983, 129.228361, 35.828983, 129.228361), 1e-9)
    }

    @Test
    fun `국립경주박물관과 대릉원 사이는 1_4km에서 1_9km 사이다`() {
        val museum = Haversine.distanceM(35.828983, 129.228361, 35.8383, 129.2112)
        val reverse = Haversine.distanceM(35.8383, 129.2112, 35.828983, 129.228361)
        assertTrue("실제 거리=${museum}m", museum in 1400.0..1900.0)
        // 대칭성: (a,b)와 (b,a) 거리는 동일하다
        assertEquals(museum, reverse, 1e-6)
    }

    @Test
    fun `위도 방향 거리는 남북으로 대칭이다`() {
        // 북쪽으로 갈 때와 남쪽으로 갈 때 거리 동일
        val north = Haversine.distanceM(35.0, 129.0, 36.0, 129.0)
        val south = Haversine.distanceM(36.0, 129.0, 35.0, 129.0)
        assertEquals(north, south, 1e-6)
    }
}

package app.arosaegim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ThenNowResolver 단위 테스트 — 과거 기록 유무에 따른 null/ThenNow 결정과 yearsAgo 계산,
 * 여러 과거 후보 중 가장 오래된 것 선택, 연도 표시(label 우선) 규칙(00문서 10.4).
 */
class ThenNowResolverTest {

    @Test
    fun `과거 기록이 없으면 null을 반환한다`() {
        val result = ThenNowResolver.resolve(
            pastYear = null, pastNote = null, pastPhoto = null,
            pastVehicle = null, nowVehicle = "2025년형 차량", nowYear = 2025,
        )
        assertNull("pastYear가 null이면 '첫 방문'으로 null", result)
    }

    @Test
    fun `과거가 있으면 yearsAgo를 계산해 반환한다`() {
        val result = ThenNowResolver.resolve(
            pastYear = 1975, pastNote = "1975년 개관 당시 풍경", pastPhoto = "past.jpg",
            pastVehicle = "옛날 지프", nowVehicle = "현대 지프", nowYear = 2025,
        )
        assertNotNull(result)
        assertEquals(1975, result?.pastYear)
        assertEquals("yearsAgo = nowYear - pastYear", 50, result?.yearsAgo)
        assertEquals("1975년 개관 당시 풍경", result?.pastNote)
        assertEquals("past.jpg", result?.pastPhoto)
        assertEquals("옛날 지프", result?.pastVehicle)
        assertEquals("현대 지프", result?.nowVehicle)
    }

    @Test
    fun `pastYear가 0 이하면 null을 반환한다`() {
        assertNull(ThenNowResolver.resolve(0, "메모", null, null, null, 2025))
        assertNull(ThenNowResolver.resolve(-100, null, "p.jpg", "옛 차", "지금 차", 2025))
    }

    @Test
    fun `nowYear보다 과거 연도가 크면 미래 과거로 보고 null을 반환한다`() {
        val result = ThenNowResolver.resolve(
            pastYear = 2030, pastNote = "메모", pastPhoto = null,
            pastVehicle = null, nowVehicle = null, nowYear = 2025,
        )
        assertNull("nowYear < pastYear 인 데이터 오류 방어", result)
    }

    @Test
    fun `pastYear와 nowYear가 같으면 yearsAgo는 0이다`() {
        val result = ThenNowResolver.resolve(2025, null, null, null, null, 2025)
        assertNotNull(result)
        assertEquals(0, result?.yearsAgo)
    }

    @Test
    fun `과거 연도만 있고 나머지 필드가 null이어도 ThenNow를 반환한다`() {
        val result = ThenNowResolver.resolve(1990, null, null, null, null, 2025)
        assertNotNull("pastYear만 유효하면 과거 기록으로 인정한다", result)
        assertEquals(35, result?.yearsAgo)
        assertNull(result?.pastNote)
        assertNull(result?.pastPhoto)
    }

    // --- resolveFromRecords: 여러 과거 후보 규칙(00문서 10.4) ---

    @Test
    fun `여러 과거 후보 중 가장 오래된 것을 선택한다`() {
        val past = listOf(
            PastRecord(year = 1995, note = "중반", photo = "a.jpg", vehicleLabel = "그때 차"),
            PastRecord(year = 1975, note = "가장 오래됨", photo = "b.jpg", vehicleLabel = "옛 차"),
            PastRecord(year = 2010, note = "가장 최근", photo = "c.jpg", vehicleLabel = "최근 차"),
        )

        val result = ThenNowResolver.resolveFromRecords(past, nowVehicle = "지금 차", nowYear = 2025)

        assertNotNull(result)
        assertEquals("year가 가장 작은(가장 오래된) 기록을 선택한다", 1975, result?.pastYear)
        assertEquals(50, result?.yearsAgo)
        assertEquals("가장 오래됨", result?.pastNote)
        assertEquals("옛 차", result?.pastVehicle)
        assertEquals("지금 차", result?.nowVehicle)
    }

    @Test
    fun `year가 null인 후보는 제외하고 남은 후보로 계산한다`() {
        val past = listOf(
            PastRecord(year = null, note = "연도 모름", photo = null, vehicleLabel = null),
            PastRecord(year = 1990, note = "1990년", photo = null, vehicleLabel = "옛 차"),
        )

        val result = ThenNowResolver.resolveFromRecords(past, nowVehicle = null, nowYear = 2025)

        assertNotNull(result)
        assertEquals(1990, result?.pastYear)
        assertEquals(35, result?.yearsAgo)
    }

    @Test
    fun `모든 후보가 제외되면 null을 반환한다`() {
        // year 전부 null
        val allNull = listOf(PastRecord(year = null, note = null, photo = null, vehicleLabel = null))
        assertNull("후보가 전부 제외되면 null(첫 방문 취급)", ThenNowResolver.resolveFromRecords(allNull, null, 2025))

        // year 전부 미래 과거
        val allFuture = listOf(PastRecord(year = 2030, note = null, photo = null, vehicleLabel = null))
        assertNull(ThenNowResolver.resolveFromRecords(allFuture, null, 2025))

        // year 전부 0 이하
        val allInvalid = listOf(PastRecord(year = 0, note = null, photo = null, vehicleLabel = null))
        assertNull(ThenNowResolver.resolveFromRecords(allInvalid, null, 2025))

        // 후보 목록 자체가 비어 있으면 null
        assertNull(ThenNowResolver.resolveFromRecords(emptyList(), null, 2025))
    }

    @Test
    fun `label이 있으면 ThenNow의 label로 옮겨진다`() {
        val past = listOf(
            PastRecord(year = 1994, note = "메모", photo = null, vehicleLabel = "그때 차",
                label = "1990년대 중반"),
        )

        val result = ThenNowResolver.resolveFromRecords(past, nowVehicle = "지금 차", nowYear = 2025)

        assertNotNull(result)
        assertEquals("연도 표시는 label 우선(00문서 10.4)", "1990년대 중반", result?.label)
    }

    @Test
    fun `label이 없으면 label은 null이지만 yearsAgo는 계산된다`() {
        val past = listOf(
            PastRecord(year = 1990, note = "메모", photo = null, vehicleLabel = null, label = null),
        )

        val result = ThenNowResolver.resolveFromRecords(past, nowVehicle = null, nowYear = 2025)

        assertNotNull(result)
        assertNull("label 없으면 화면이 '현재 연도 − yearsAgo'로 계산하도록 null 유지", result?.label)
        assertEquals(35, result?.yearsAgo)
    }

    @Test
    fun `단일 resolve에 label을 넘기면 ThenNow의 label로 채워진다`() {
        val withLabel = ThenNowResolver.resolve(
            pastYear = 1994, pastNote = null, pastPhoto = null, pastVehicle = null,
            nowVehicle = null, nowYear = 2025, label = "1990년대 중반",
        )
        assertEquals("1990년대 중반", withLabel?.label)

        val withoutLabel = ThenNowResolver.resolve(
            pastYear = 1994, pastNote = null, pastPhoto = null, pastVehicle = null,
            nowVehicle = null, nowYear = 2025,
        )
        assertNull("label 미전달 시 기존 6인자 호출과 동일하게 null이다", withoutLabel?.label)
    }
}

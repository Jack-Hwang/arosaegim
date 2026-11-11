package app.arosaegim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가족 기록 신규 API 파서 단위 테스트(B1~B4, 2026-11-11).
 *
 * 계약 검증: 서버 응답에 필드가 없거나 파싱에 실패하면 '기능 숨김'(null/Unknown/빈 목록)이고,
 * 절대 추측으로 값을 만들지 않는다. 요청 몸체(JSON)도 계약 모양 그대로인지 확인한다.
 */
class FamilyApiParsersTest {

    /* ---------------- B2·B4: then-now 장소 상태 ---------------- */

    @Test
    fun `then-now 응답 전체 필드를 읽는다`() {
        val state = parseThenNowPlaceState(
            """{"visit_ordinal": 3, "past_visit_count": 2,
               "fold_person_comparison_all": false, "folded": true}""",
        )
        require(state != null)
        assertEquals(3, state.visitOrdinal)
        assertEquals(2, state.pastVisitCount)
        assertEquals(false, state.foldAll)
        assertEquals(true, state.folded)
    }

    @Test
    fun `then-now 의 visit_ordinal 이 없으면 null - 기능 숨김`() {
        assertNull(parseThenNowPlaceState("""{"past_visit_count": 2, "folded": false}"""))
        assertNull(parseThenNowPlaceState("not json"))
        assertNull(parseThenNowPlaceState("""{"visit_ordinal": "문자열"}"""))
    }

    @Test
    fun `folded 와 foldAll 중 하나라도 true 면 인원 비교 문장을 숨긴다`() {
        val folded = ThenNowPlaceState(2, 1, foldAll = false, folded = true)
        val foldAll = ThenNowPlaceState(2, 1, foldAll = true, folded = false)
        val open = ThenNowPlaceState(2, 1, foldAll = false, folded = false)
        assertTrue(isPersonComparisonFolded(folded))
        assertTrue(isPersonComparisonFolded(foldAll))
        assertFalse(isPersonComparisonFolded(open))
        assertFalse(isPersonComparisonFolded(null)) // 조회 실패 시 기존 동작 — 문장을 숨기지 않는다.
    }

    @Test
    fun `접기 버튼은 then-now 조회 성공 전역설정 아닐 때만 보인다`() {
        val open = ThenNowPlaceState(2, 1, foldAll = false, folded = false)
        assertTrue(shouldShowFoldButton(open))
        assertFalse(shouldShowFoldButton(ThenNowPlaceState(2, 1, foldAll = true, folded = false)))
        assertFalse(shouldShowFoldButton(ThenNowPlaceState(2, 1, foldAll = false, folded = true)))
        assertFalse(shouldShowFoldButton(null)) // 조회 실패 — 버튼만 숨기고 문장은 기존대로(계약).
    }

    /* ---------------- B3: 함께 달린 거리 누적 ---------------- */

    @Test
    fun `family_total_trip_km 을 읽는다 - 감싸기 둘 다 흡수`() {
        assertEquals(1240.0, parseFamilyTotalTripKm("""{"family_total_trip_km": 1240}""")!!, 0.001)
        assertEquals(1240.5, parseFamilyTotalTripKm("""{"metrics":{"family_total_trip_km":1240.5}}""")!!, 0.001)
        assertEquals(12.0, parseFamilyTotalTripKm("""{"family_total_trip_km": "12"}""")!!, 0.001)
    }

    @Test
    fun `family_total_trip_km 이 없거나 이상하면 null - 누적 줄 숨김`() {
        assertNull(parseFamilyTotalTripKm("""{"stamp_books": {}}"""))
        assertNull(parseFamilyTotalTripKm("""{"family_total_trip_km": null}"""))
        assertNull(parseFamilyTotalTripKm("not json"))
    }

    @Test
    fun `km 표시 문자열은 소수점 정리와 천 단위 콤마를 적용한다`() {
        assertEquals("12", formatFamilyTotalTripKm(12.0))
        assertEquals("1,240", formatFamilyTotalTripKm(1240.0))
        assertEquals("1,240.5", formatFamilyTotalTripKm(1240.5))
        assertEquals("32.3", formatFamilyTotalTripKm(32.3000000001))
    }

    @Test
    fun `km 이 0 이하 또는 비정상이면 null - 누적 줄 숨김`() {
        assertNull(formatFamilyTotalTripKm(0.0))
        assertNull(formatFamilyTotalTripKm(-3.0))
        assertNull(formatFamilyTotalTripKm(Double.NaN))
        assertNull(formatFamilyTotalTripKm(Double.POSITIVE_INFINITY))
    }

    /* ---------------- B1: heir ---------------- */

    @Test
    fun `heir 응답을 읽는다 - 미지정 null 과 지정된 id 를 구별한다`() {
        assertEquals(HeirStatus.Known("m-2"), parseHeirStatus("""{"member_id": "m-2"}"""))
        assertEquals(HeirStatus.Known(null), parseHeirStatus("""{"member_id": null}"""))
    }

    @Test
    fun `heir 의 member_id 필드가 없으면 Unknown - 질문하지 않는다`() {
        assertEquals(HeirStatus.Unknown, parseHeirStatus("{}"))
        assertEquals(HeirStatus.Unknown, parseHeirStatus("""{"other": 1}"""))
        assertEquals(HeirStatus.Unknown, parseHeirStatus("not json"))
    }

    @Test
    fun `members 응답에서 heir 후보(구성원 id·닉네임)를 읽는다`() {
        val body = """
            [ {"id":1, "nickname":"아빠", "seat_role":"driver"},
              {"id":2, "nickname":"할머니", "seat_role":null},
              {"nickname":"id 없는 구성원"} ]
        """.trimIndent()
        val candidates = parseHeirCandidates(body)
        assertEquals(2, candidates.size)
        assertEquals("1", candidates[0].memberId)
        assertEquals("아빠", candidates[0].nickname)
        assertEquals("2", candidates[1].memberId)
        assertEquals("할머니", candidates[1].nickname)
    }

    @Test
    fun `members 감싼 응답과 숫자가 아닌 id도 흡수한다`() {
        val body = """{"members":[ {"id":"m-9", "nickname":"엄마"} ]}"""
        val candidates = parseHeirCandidates(body)
        assertEquals(1, candidates.size)
        assertEquals("m-9", candidates[0].memberId)
    }

    @Test
    fun `members 파싱 실패는 빈 목록 - 질문 카드 숨김`() {
        assertTrue(parseHeirCandidates("not json").isEmpty())
        assertTrue(parseHeirCandidates("""{}""").isEmpty())
        assertTrue(parseHeirCandidates("""[ {"nickname":"id 없음"} ]""").isEmpty())
    }

    @Test
    fun `요청 몸체는 계약 모양 그대로다`() {
        assertEquals("""{"member_id":"m-2"}""", heirRequestBody("m-2"))
        assertEquals("""{"place_id":"gj-museum"}""", foldedPlaceRequestBody("gj-museum"))
    }
}

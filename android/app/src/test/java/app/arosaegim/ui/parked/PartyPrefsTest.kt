package app.arosaegim.ui.parked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4 인원 비교 문장 접어 두기 — 순수 로직 단위 테스트(2026-11-12).
 *
 * 계약(요구 문서 07의 2절 B4):
 *  - GET /v1/families/me/prefs 응답({"party_line_hidden_places": [...], "party_line_all_hidden"})
 *    을 파싱한다. 필드가 빠져도 서버 초기값(빈 목록·false)으로 채우고, 깨진 응답은 null — 로컬 폴백.
 *  - 접힘 목록에 placeId 가 있으면 그 장소에서만 문장을 숨긴다. 다른 장소 무영향.
 *  - '모든 장소 끄기'가 true 면 어느 장소에서나 숨긴다.
 *  - PUT 은 전체 교체라 현재 목록에 이 장소를 합쳐 보낸다(멱등).
 *
 * SharedPreferences 폴백(PartyFoldLocalStore)은 android 프레임워크 의존이라 여기서 부르지 않는다
 * (단말 계약 키 folded_party_<placeId>·all_party_folded 는 상수로만 고정된다).
 */
class PartyPrefsTest {

    // ── parsePartyLinePrefs ──

    @Test
    fun `정상 응답을 파싱한다`() {
        val prefs = parsePartyLinePrefs(
            """{"party_line_hidden_places": ["gj-museum", "daereungwon"], "party_line_all_hidden": false}""",
        )
        assertEquals(setOf("gj-museum", "daereungwon"), prefs?.hiddenPlaceIds)
        assertEquals(false, prefs?.allHidden)
    }

    @Test
    fun `모든 장소 끄기 플래그를 읽는다`() {
        val prefs = parsePartyLinePrefs(
            """{"party_line_hidden_places": [], "party_line_all_hidden": true}""",
        )
        assertTrue(prefs?.allHidden == true)
    }

    @Test
    fun `필드가 빠지면 서버 초기값으로 채운다_null 이 아니다`() {
        // 백엔드 계약: 저장 행이 없어도 초기값 객체로 답하지만, 필드별 기본값도 흡수한다(방어).
        val prefs = parsePartyLinePrefs("{}")
        assertEquals(emptySet<String>(), prefs?.hiddenPlaceIds)
        assertEquals(false, prefs?.allHidden)
    }

    @Test
    fun `빈 문자열 항목은 목록에서 걸러낸다`() {
        val prefs = parsePartyLinePrefs(
            """{"party_line_hidden_places": ["gj-museum", "", "  "]}""",
        )
        assertEquals(setOf("gj-museum"), prefs?.hiddenPlaceIds)
    }

    @Test
    fun `깨진 JSON_배열_응답은 null 이다`() {
        assertNull(parsePartyLinePrefs("not-json"))
        assertNull(parsePartyLinePrefs("[1,2,3]")) // 객체 계약 — 배열은 null(로컬 폴백 전환).
    }

    // ── partyLineHiddenFor ──

    @Test
    fun `접힘 목록에 있는 장소만 숨긴다`() {
        val prefs = PartyLinePrefs(hiddenPlaceIds = setOf("gj-museum"), allHidden = false)
        assertTrue(partyLineHiddenFor("gj-museum", prefs))
        assertFalse(partyLineHiddenFor("cheomseongdae", prefs)) // 다른 장소 무영향(계약).
    }

    @Test
    fun `모든 장소 끄기면 어느 장소에서나 숨긴다`() {
        val prefs = PartyLinePrefs(hiddenPlaceIds = emptySet(), allHidden = true)
        assertTrue(partyLineHiddenFor("gj-museum", prefs))
        assertTrue(partyLineHiddenFor("cheomseongdae", prefs))
    }

    @Test
    fun `설정_장소가_없으면 숨기지 않는다`() {
        assertFalse(partyLineHiddenFor("gj-museum", null)) // 미조회·실패 = 기존 동작 유지.
        assertFalse(partyLineHiddenFor(null, PartyLinePrefs(setOf("gj-museum"), false)))
    }

    // ── partyPrefsPlacesWithPlace ──

    @Test
    fun `현재 목록에 이 장소를 합친다`() {
        val current = PartyLinePrefs(setOf("gj-museum"), false)
        assertEquals(setOf("gj-museum", "cheomseongdae"), partyPrefsPlacesWithPlace(current, "cheomseongdae"))
    }

    @Test
    fun `이미 접어 둔 장소는 멱등이다`() {
        val current = PartyLinePrefs(setOf("gj-museum"), false)
        assertEquals(setOf("gj-museum"), partyPrefsPlacesWithPlace(current, "gj-museum"))
    }

    @Test
    fun `설정이 없으면 이 장소 하나만 본다`() {
        assertEquals(setOf("gj-museum"), partyPrefsPlacesWithPlace(null, "gj-museum"))
    }

    // ── 로컬 폴백 키 계약(값 고정 — 단말 저장 키는 요구 문서 계약이다) ──

    @Test
    fun `폴백 저장소 키 이름이 계약과 같다`() {
        assertEquals("folded_party_", PartyFoldLocalStore.KEY_FOLDED_PREFIX)
        assertEquals("all_party_folded", PartyFoldLocalStore.KEY_ALL_FOLDED)
        assertEquals("family_party_prefs", PartyFoldLocalStore.PREFS_NAME)
    }
}

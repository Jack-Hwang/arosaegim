package app.arosaegim.ui.settings

import app.arosaegim.ui.parked.PartyLinePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * 설정 화면 순수 상태 로직 단위 테스트(13번 문서 T4 설정 — JVM, 2026-11-14).
 *
 * SettingsState 의 계약을 검증한다(서버 계약 — GET·PUT /v1/families/me/prefs 의
 * party_line_all_hidden, 기존 PartyPrefs API 재사용):
 *  1) 스위치 현재 값 판정 — 서버 값이 있으면 언제나 그 값이 이기고(라벨이 '보기'라 반대값),
 *     없으면(null = 세션 없음·조회 실패) 기존 동작대로 기기 안 임시값으로 판정한다.
 *  2) 토글 시 다음 상태 — '보임 ↔ 숨김' 반전.
 *  3) PUT 전체 교체 때 접어 둔 장소 목록을 유지한다(순서 보존).
 *  4) 가족 코드 줄은 접속 중 코드가 있을 때만 보인다(값은 마스킹 표시).
 */
class SettingsStateTest {

    @Test
    fun `서버 값이 있으면 서버 값으로 스위치를 판정한다`() {
        // 서버가 '모든 장소 숨김'(party_line_all_hidden=true)이라 답하면 스위치는 꺼진 모양.
        assertFalse(SettingsState.partyLineVisible(serverAllHidden = true, deviceAllHidden = false))
        // 서버가 false 면 스위치는 켜진 모양 — 기기 임시값이 반대여도 서버 값이 이긴다.
        assertTrue(SettingsState.partyLineVisible(serverAllHidden = false, deviceAllHidden = true))
    }

    @Test
    fun `서버 값이 없으면 기기 임시값으로 판정한다`() {
        // 서버에 닿지 않은 상태(null) — 기존 동작 유지: 기기 안 임시값의 반대가 스위치 모양.
        assertFalse(SettingsState.partyLineVisible(serverAllHidden = null, deviceAllHidden = true))
        assertTrue(SettingsState.partyLineVisible(serverAllHidden = null, deviceAllHidden = false))
    }

    @Test
    fun `토글하면 보임과 숨김이 바뀐다`() {
        assertFalse(SettingsState.nextPartyLineVisible(currentVisible = true))
        assertTrue(SettingsState.nextPartyLineVisible(currentVisible = false))
    }

    @Test
    fun `토글 저장에 접어 둔 장소 목록을 유지한다`() {
        // PUT 은 전체 교체라 목록을 같이 보내지 않으면 접어 둔 장소가 서버 초기값으로 덮어써진다.
        val prefs = PartyLinePrefs(hiddenPlaceIds = setOf("gj-museum", "daereungwon"), allHidden = false)
        assertEquals(
            listOf("gj-museum", "daereungwon"),
            SettingsState.keptHiddenPlaceIds(prefs),
        )
        // 서버 값을 못 읽은 상태(null)면 빈 목록 — 접을 만한 목록이 없으므로 초기값 교체와 같다.
        assertEquals(emptyList<String>(), SettingsState.keptHiddenPlaceIds(null))
    }

    @Test
    fun `접속 중 코드가 있을 때만 가족 코드 줄을 보인다`() {
        assertTrue(SettingsState.familyCodeRowVisible("ZZ99X7"))
        assertFalse("둘러보기 모드(코드 없음)는 줄을 숨긴다", SettingsState.familyCodeRowVisible(null))
        assertFalse("빈 값도 줄을 숨긴다", SettingsState.familyCodeRowVisible(""))
        assertFalse("공백만 있는 값도 없음과 같다", SettingsState.familyCodeRowVisible("   "))
    }
}

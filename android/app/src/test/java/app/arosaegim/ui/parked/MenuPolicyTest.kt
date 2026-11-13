package app.arosaegim.ui.parked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * 메뉴 판정 시험(F10 T2, 2026-11-14) — 규칙 ⒜~⒟ × 창 방향 × 주행/정차 × 세션 표.
 *
 * 규칙(F10 수용 기준):
 *  ⒜ 주행(D)이면 버튼 숨김·패널 닫힘(AC07).
 *  ⒝ 정차 + 가로 창이면 열 때 본문 폭이 패널만큼 줄어든다(BODY_SHRINK).
 *  ⒞ 세로(1:1) 창이면 본문 위에 겹쳐 열리고 왼쪽이 어둡다(OVERLAY — AC17).
 *  ⒟ 세션 없음·시작 화면이면 버튼 없음(+패널 강제 닫힘 — AC05).
 *
 * 판정은 전부 순수 함수라 JVM 에서 바로 검증한다. 홀더([MenuPanelState])는 열림·닫힘만 확인한다.
 */
class MenuPolicyTest {

    /** 기본값: 정차 + 세션 있음 + 정차 화면 + 가로 창(2560×1440 차량 화면의 축소 가정). */
    private fun policy(
        driving: Boolean = false,
        hasSession: Boolean = true,
        onStartScreen: Boolean = false,
        width: Int = 1280,
        height: Int = 720,
        open: Boolean = false,
    ) = MenuPolicy.resolve(
        MenuPolicyInputs(
            driving = driving,
            hasSession = hasSession,
            onStartScreen = onStartScreen,
            windowWidthDp = width,
            windowHeightDp = height,
            panelRequestedOpen = open,
        ),
    )

    // ── ⒜ 주행(D)이면 버튼 숨김·패널 닫힘 ──
    @Test
    fun `주행이 되면 메뉴 버튼이 사라지고 패널은 닫힌다`() {
        val wide = policy(driving = true, open = true, width = 1280, height = 720)
        assertFalse(wide.showMenuButton)
        assertEquals(MenuPanelMode.CLOSED, wide.panelMode)

        val tall = policy(driving = true, open = true, width = 720, height = 720)
        assertFalse(tall.showMenuButton)
        assertEquals(MenuPanelMode.CLOSED, tall.panelMode)
    }

    @Test
    fun `정차와 세션이 있으면 메뉴 버튼이 보인다 — 창 방향과 무관`() {
        assertTrue(policy(width = 2560, height = 1440).showMenuButton)
        assertTrue(policy(width = 720, height = 720).showMenuButton)
        assertTrue(policy(width = 600, height = 800).showMenuButton)
    }

    // ── ⒝ 정차 + 가로 창 → 본문 축소 모드 ──
    @Test
    fun `정차와 가로 창이면 패널이 열릴 때 본문 축소 모드다`() {
        assertEquals(MenuPanelMode.BODY_SHRINK, policy(width = 1280, height = 720, open = true).panelMode)
        assertEquals(MenuPanelMode.BODY_SHRINK, policy(width = 2560, height = 1440, open = true).panelMode)
    }

    // ── ⒞ 세로(1:1) 창 → 겹침 + 왼쪽 어둡게 모드 ──
    @Test
    fun `세로와 1대1 창이면 패널이 본문 위에 겹치는 모드다`() {
        assertEquals(MenuPanelMode.OVERLAY, policy(width = 720, height = 720, open = true).panelMode)
        assertEquals(MenuPanelMode.OVERLAY, policy(width = 600, height = 800, open = true).panelMode)
    }

    // ── ⒟ 세션 없음·시작 화면이면 버튼 없음 ──
    @Test
    fun `세션이 없으면 버튼이 없고 패널도 열리지 않는다`() {
        val out = policy(hasSession = false, open = true)
        assertFalse(out.showMenuButton)
        assertEquals(MenuPanelMode.CLOSED, out.panelMode)
    }

    @Test
    fun `시작 화면이면 버튼이 없고 패널도 열리지 않는다`() {
        val out = policy(onStartScreen = true, open = true)
        assertFalse(out.showMenuButton)
        assertEquals(MenuPanelMode.CLOSED, out.panelMode)
    }

    @Test
    fun `닫힘 요청이면 정차 세션 가로 창이라도 패널은 닫혀 있다`() {
        assertEquals(MenuPanelMode.CLOSED, policy(width = 1280, height = 720, open = false).panelMode)
    }

    @Test
    fun `패널 폭은 창 폭의 27퍼센트이고 240dp보다 작아지지 않는다`() {
        assertEquals(270, MenuPolicy.panelWidthDp(1000)) // 1000 × 27% = 270
        assertEquals(540, MenuPolicy.panelWidthDp(2000)) // 2000 × 27% = 540
        assertEquals(240, MenuPolicy.panelWidthDp(800)) // 800 × 27% = 216 → 최소 240
        assertEquals(240, MenuPolicy.MIN_PANEL_WIDTH_DP)
    }

    @Test
    fun `진행 중 코스가 있으면 코스 목록 모드로 열고 없으면 스텝0에 맡긴다`() {
        assertEquals(CourseSelectMode.STEP0_FRESH, MenuPolicy.courseSelectMode(courseListNeeded = false))
        assertEquals(CourseSelectMode.COURSE_LIST_ONLY, MenuPolicy.courseSelectMode(courseListNeeded = true))
    }

    @Test
    fun `가족 코드는 앞 두 자리와 뒤 두 자리만 남기고 가운데를 가린다`() {
        assertEquals("A3••••2M", MenuPolicy.maskFamilyCode("A37K9Q2M")) // 8자리(서버 발급 규격 예시)
        assertEquals("A3••2M", MenuPolicy.maskFamilyCode("A37K2M")) // 6자리(초창기 호환 예시)
        assertNull(MenuPolicy.maskFamilyCode(null)) // 둘러보기 — 줄 숨김
        assertNull(MenuPolicy.maskFamilyCode("   ")) // 공백만 — 줄 숨김
        assertEquals("•••", MenuPolicy.maskFamilyCode("abc")) // 규격 밖 짧은 값 — 전부 가림
    }

    @Test
    fun `패널 열림 홀더는 닫힌 채로 시작하고 열고 닫을 수 있다`() {
        MenuPanelState.close()
        assertFalse(MenuPanelState.isOpen)
        MenuPanelState.open()
        assertTrue(MenuPanelState.isOpen)
        MenuPanelState.close()
        assertFalse(MenuPanelState.isOpen)
    }
}

package app.arosaegim.ui.parked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * 홈 내용·홈 게이트 시험(F10 T3, 2026-11-14).
 *
 * [HomeContent.resolve] — 진행 중 코스 있음/없음/완주 직후, 기록 있음/없음, 서버 응답 없음
 * (거리 값 없음 — 오류 문구 없이 기기 값으로 카드, AC23).
 * [HomeGate.shouldShowHome] — 세션·주행·도착 직후·사용자 선택의 표(AC09 콜드·재진입 홈,
 * AC20 도착 장면 보호 — 스탬프 카드가 먼저).
 *
 * 판정은 전부 순수 함수라 JVM 에서 바로 검증한다(Compose·Android 의존 없음).
 */
class HomeContentTest {

    /** 기본값: 세션 있음 + 진행 중 코스 없음 + 기록 없음 + 거리 값 없음. */
    private fun inputs(
        hasSession: Boolean = true,
        courseName: String = "세종 유산 코스",
        stampedCount: Int = 0,
        stampTotal: Int = 3,
        targetName: String? = null,
        completed: Boolean = false,
        lastVisit: String? = null,
        distanceText: String? = null,
    ) = HomeInputs(
        hasSession = hasSession,
        courseName = courseName,
        stampedCount = stampedCount,
        stampTotal = stampTotal,
        currentTargetName = targetName,
        completed = completed,
        lastVisitPlaceName = lastVisit,
        distanceText = distanceText,
    )

    private fun gate(
        hasSession: Boolean = true,
        driving: Boolean = false,
        arrivalUnseen: Boolean = false,
        homeSelected: Boolean? = null,
    ) = HomeGate.shouldShowHome(
        HomeGateInputs(
            hasSession = hasSession,
            driving = driving,
            arrivalUnseen = arrivalUnseen,
            homeSelected = homeSelected,
        ),
    )

    // ── ① 이어서 하기 카드(AC10) ──
    @Test
    fun `진행 중 코스가 있으면 이어서 하기 카드를 만든다`() {
        val model = HomeContent.resolve(
            inputs(stampedCount = 1, targetName = "다음 장소 예시"),
        )
        val resume = model.resume
        assertTrue(resume != null)
        assertEquals("세종 유산 코스", resume!!.courseName)
        assertEquals(1, resume.stampCount)
        assertEquals(3, resume.stampTotal)
        assertEquals("다음 장소 예시", resume.nextPlaceName)
        assertFalse(model.firstRecord)
    }

    @Test
    fun `다음 목표가 없으면 이어서 하기 카드의 다음 장소 줄만 숨긴다`() {
        val model = HomeContent.resolve(inputs(stampedCount = 1, targetName = null))
        assertTrue(model.resume != null)
        assertNull(model.resume!!.nextPlaceName)
    }

    @Test
    fun `거리 값이 있으면 이어서 하기 카드의 거리 줄이 생긴다`() {
        val model = HomeContent.resolve(
            inputs(stampedCount = 1, targetName = "다음 장소 예시", distanceText = "3.2km"),
        )
        assertEquals("3.2km", model.resume!!.distanceText)
    }

    // ── 완주 직후 ──
    @Test
    fun `완주 직후에는 이어서 하기 카드가 없고 지난 방문 카드가 있다`() {
        val model = HomeContent.resolve(
            inputs(stampedCount = 3, completed = true, lastVisit = "마지막 장소 예시"),
        )
        assertNull(model.resume) // 이어 갈 목표가 없다 — 이어서 하기 카드를 만들지 않는다.
        assertEquals("마지막 장소 예시", model.pastVisit!!.placeName)
        assertFalse(model.firstRecord)
    }

    // ── ② 지난 방문 카드 ──
    @Test
    fun `지난 방문 기록이 있으면 지난 방문 카드를 만든다`() {
        val model = HomeContent.resolve(inputs(lastVisit = "지난 장소 예시"))
        assertEquals("지난 장소 예시", model.pastVisit!!.placeName)
        assertFalse(model.firstRecord)
    }

    @Test
    fun `지난 방문 기록이 없으면 지난 방문 카드를 만들지 않는다`() {
        val model = HomeContent.resolve(inputs())
        assertNull(model.pastVisit)
    }

    // ── ③ 첫 기록 모드(AC11) ──
    @Test
    fun `진행 중 코스가 없고 기록도 없으면 첫 기록 모드다`() {
        val model = HomeContent.resolve(inputs())
        assertNull(model.resume)
        assertNull(model.pastVisit)
        assertTrue(model.firstRecord)
    }

    @Test
    fun `코스를 시작했지만 스탬프가 아직 없으면 이어서 하기 카드가 먼저다`() {
        val model = HomeContent.resolve(
            inputs(stampedCount = 0, targetName = "첫 목표 예시"),
        )
        assertTrue(model.resume != null)
        assertFalse(model.firstRecord)
    }

    // ── 세션 여부 ──
    @Test
    fun `세션이 없으면 어떤 카드도 만들지 않는다`() {
        val model = HomeContent.resolve(
            inputs(hasSession = false, stampedCount = 1, targetName = "다음 장소 예시", lastVisit = "지난 장소 예시"),
        )
        assertNull(model.resume)
        assertNull(model.pastVisit)
        assertFalse(model.firstRecord)
    }

    // ── 서버 응답 없음(AC23) ──
    @Test
    fun `서버 응답이 없어도 거리 줄만 숨기고 오류 문구는 만들지 않는다`() {
        // 거리 값 없음(distanceText = null) — 카드 자체는 기기 값으로 그대로 만들어진다.
        val model = HomeContent.resolve(
            inputs(stampedCount = 2, targetName = "다음 장소 예시", distanceText = null),
        )
        assertTrue(model.resume != null)
        assertNull(model.resume!!.distanceText)
        assertEquals(2, model.resume!!.stampCount)
        assertEquals("다음 장소 예시", model.resume!!.nextPlaceName)
    }

    // ── 홈 게이트(AC09 콜드·재진입 / AC20 도착 장면 보호) ──
    @Test
    fun `세션과 정차에 도착도 선택도 없으면 홈이 첫 화면이다`() {
        assertTrue(gate())
    }

    @Test
    fun `세션이 없으면 홈을 보여 주지 않는다`() {
        assertFalse(gate(hasSession = false))
    }

    @Test
    fun `주행 중에는 홈이 닫힌다`() {
        assertFalse(gate(driving = true))
    }

    @Test
    fun `도착 직후에는 홈이 스탬프 카드를 가로채지 않는다`() {
        assertFalse(gate(arrivalUnseen = true))
    }

    @Test
    fun `사용자가 홈을 선택하면 도착 여부와 무관하게 본문 선택이 이긴다`() {
        assertTrue(gate(homeSelected = true))
        assertFalse(gate(homeSelected = false))
    }
}

package app.arosaegim.ui

import app.arosaegim.domain.DrivingState
import app.arosaegim.ui.driving.DrivingActivity
import app.arosaegim.ui.parked.ParkedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ModeRouter.resolveTarget — 세션 게이트(Q28 수정, 2026-11-13)의 순수 판정 단위 테스트.
 *
 * 규칙:
 *  - DRIVING → 주행 화면(세션과 무관 — DO 선언 안전 화면 유지).
 *  - PARKED + 세션 있음(가족 코드 또는 둘러보기) → 정차 화면.
 *  - PARKED + 세션 없음 → null(전환 보류 — 시작 화면(가족 코드 입력)을 최상단에 유지).
 */
class ModeRouterResolveTest {

    @Test
    fun `주행 상태는 세션이 없어도 주행 화면으로 라우팅한다`() {
        assertEquals(
            DrivingActivity::class.java,
            ModeRouter.resolveTarget(DrivingState.DRIVING, sessionExists = false),
        )
    }

    @Test
    fun `주행 상태는 세션이 있어도 주행 화면으로 라우팅한다`() {
        assertEquals(
            DrivingActivity::class.java,
            ModeRouter.resolveTarget(DrivingState.DRIVING, sessionExists = true),
        )
    }

    @Test
    fun `정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)`() {
        assertNull(ModeRouter.resolveTarget(DrivingState.PARKED, sessionExists = false))
    }

    @Test
    fun `정차 상태에서 세션이 있으면 정차 화면으로 라우팅한다`() {
        assertEquals(
            ParkedActivity::class.java,
            ModeRouter.resolveTarget(DrivingState.PARKED, sessionExists = true),
        )
    }
}

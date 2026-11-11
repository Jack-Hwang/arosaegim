package app.arosaegim.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 이어받을 사람 질문 정책 단위 테스트(B1, 시안 19 — 2026-11-11).
 *
 * 계약: 코스 완주 직후 + 비주행 + '나중에 정하기' 미누름 + 서버가 미지정임을 확인 + 구성원 버튼
 * 가능 — 모두 만족할 때만 물어본다. 정하지 않아도 배지·완주 기록은 유지된다(이 정책은 질문만 제어).
 */
class HeirQuestionTest {

    private fun inputs(
        completed: Boolean = true,
        driving: Boolean = false,
        deferred: Boolean = false,
        heirStatus: HeirStatus = HeirStatus.Known(null), // 서버 조회 성공 + 미지정.
        members: Boolean = true,
    ) = HeirQuestionInputs(
        courseCompleted = completed,
        driving = driving,
        deferred = deferred,
        heirStatus = heirStatus,
        membersAvailable = members,
    )

    @Test
    fun `완주 비주행 미지정 구성원있음이면 물어본다`() {
        assertTrue(HeirQuestionPolicy.shouldAsk(inputs()))
    }

    @Test
    fun `주행 중에는 물어보지 않는다`() {
        assertFalse(HeirQuestionPolicy.shouldAsk(inputs(driving = true)))
    }

    @Test
    fun `이 코스에서 나중에 정하기를 눌렀으면 다시 물어보지 않는다`() {
        assertFalse(HeirQuestionPolicy.shouldAsk(inputs(deferred = true)))
    }

    @Test
    fun `이미 정해져 있으면 다시 물어보지 않는다 - 재문 금지 조건`() {
        assertFalse(HeirQuestionPolicy.shouldAsk(inputs(heirStatus = HeirStatus.Known("m-2"))))
    }

    @Test
    fun `서버 조회 실패 모르면 물어보지 않는다 - 추측 금지`() {
        assertFalse(HeirQuestionPolicy.shouldAsk(inputs(heirStatus = HeirStatus.Unknown)))
    }

    @Test
    fun `구성원 버튼을 만들 수 없으면 물어보지 않는다`() {
        assertFalse(HeirQuestionPolicy.shouldAsk(inputs(members = false)))
    }

    @Test
    fun `완주하지 않았으면 물어보지 않는다`() {
        assertFalse(HeirQuestionPolicy.shouldAsk(inputs(completed = false)))
    }
}

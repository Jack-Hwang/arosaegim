package app.arosaegim.ui.parked

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1 'PIN 없음 → 안내 전환' 판정 단위 테스트(2026-11-12).
 *
 * 계약(요구 문서 07의 2절 B1): 차량은 PIN 을 저장하지 않아(00문서 10.6) POST heir 를 부를 수 없는
 * 때가 있다. 그때 질문 버튼을 통째로 숨기지 말고 '스마트폰의 가족 관리에서 정해 주세요' 안내
 * 카드로 전환한다. 질문 카드 본 판정(서버 heir·구성원 조회 성공분)은 domain.HeirQuestionPolicy
 * 와 HeirQuestionTest 가 담당한다 — 이 판정은 그 밖의 PIN 없음 경로다.
 */
class HeirPinGuideTest {

    private fun inputs(
        connected: Boolean = true,
        hasPin: Boolean = false,
        completed: Boolean = true,
        driving: Boolean = false,
        deferred: Boolean = false,
    ) = shouldShowHeirPinGuide(
        familyConnected = connected,
        hasPin = hasPin,
        courseCompleted = completed,
        driving = driving,
        deferredThisCourse = deferred,
    )

    @Test
    fun `가족에 연결됐고 PIN이 없으면 완주 직후 안내로 전환한다`() {
        assertTrue(inputs())
    }

    @Test
    fun `PIN이 있으면 안내가 아니라 정책 경로다`() {
        assertFalse(inputs(hasPin = true))
    }

    @Test
    fun `가족 연결이 없으면 보이지 않는다`() {
        assertFalse(inputs(connected = false))
    }

    @Test
    fun `코스를 완주하기 전에는 보이지 않는다`() {
        assertFalse(inputs(completed = false))
    }

    @Test
    fun `주행 중에는 보이지 않는다`() {
        assertFalse(inputs(driving = true))
    }

    @Test
    fun `이 코스에서 나중에 정하기를 눌렀으면 다시 보이지 않는다`() {
        assertFalse(inputs(deferred = true))
    }
}

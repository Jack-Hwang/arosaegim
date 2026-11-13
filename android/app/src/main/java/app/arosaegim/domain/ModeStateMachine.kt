package app.arosaegim.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 화면에 표시할 모드. 주행 중 = DRIVING, 정차 중 = PARKED. */
enum class UiMode { PARKED, DRIVING }

/**
 * 주행/정차 화면 모드 상태기계.
 *
 * 참고용 순수 모델이다 — 운영 코드는 이 클래스를 호출하지 않고, 실제 화면 전환은
 * ui/ModeRouter 가 담당한다. 병합 규칙·복귀 복원 계약을 시험으로 고정해 두는 용도로
 * 남겨 둔다(삭제하지 않는다).
 *
 * 계약 (복귀 복원):
 * - PARKED → DRIVING → PARKED 로 복귀하면 UiMode는 다시 PARKED로 돌아온다.
 * - 정차 시점의 콘텐츠(퀴즈 진행, 네러티스크롤 위치 등)의 보존은 이 상태기계의
 *   책임이 아니다. 그 보존은 이 기계 밖(UI/화면 소유자)에서 이루어져야 하며,
 *   본 클래스는 모드 자체만 반영한다.
 *
 * 동기 갱신: MutableStateFlow의 value를 직접 대입하므로 테스트에서
 * mode.value를 즉시 읽어 검증할 수 있다(코루틴 테스트 불필요).
 */
class ModeStateMachine(initial: DrivingState = DrivingState.PARKED) {

    private val _mode = MutableStateFlow(
        if (initial == DrivingState.DRIVING) UiMode.DRIVING else UiMode.PARKED
    )

    /** 현재 UI 모드. 같은 값 재방출이 없으므로 구독자는 값 변경만 받는다. */
    val mode: StateFlow<UiMode> = _mode.asStateFlow()

    /**
     * 주행 상태 신호를 반영한다.
     * 같은 값의 반복 입력은 무시한다(대입하지 않음 — StateFlow distinct 특성에
     * 의존하지 않고 명시적으로 차단하여 재방출이 없음을 보장).
     */
    fun onDrivingState(state: DrivingState) {
        val next = if (state == DrivingState.DRIVING) UiMode.DRIVING else UiMode.PARKED
        if (_mode.value != next) {
            _mode.value = next
        }
    }
}

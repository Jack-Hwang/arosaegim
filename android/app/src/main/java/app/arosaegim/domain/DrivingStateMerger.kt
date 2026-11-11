package app.arosaegim.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 상태 병합기(A2, 2026-11-11) — 1차 신호(주행 규제 DO, OFFICIAL)와 2차 신호(기어, 보조)를
 * 하나의 [DrivingState] 로 합친다.
 *
 * 규칙(00문서 F42·진단 화면 ⑨ '보조 신호 메모'의 기존 규칙 유지):
 *  - 1차는 CarUxRestrictions(DO) 수신 경로다(OFFICIAL — 01문서 9절). 값은 그대로 존중한다.
 *  - 기어는 보조 신호다. **두 신호가 어긋나면 더 제한적인 쪽(주행)을 우선한다.**
 *    1차=주행·보조=정차 → 주행(1차 존중) / 1차=정차·보조=주행 → 주행(보조 존중).
 *  - 보조 신호를 못 읽었으면(null) 1차만으로 판정한다 — 못 읽은 신호로 판정을 흔들지 않는다
 *    (가짜 값 금지와 같은 취지).
 *
 * 동기 갱신: MutableStateFlow.value 직접 대입이라 테스트에서 즉시 읽어 검증할 수 있다.
 * 같은 값 재대입은 StateFlow 가 흡수한다(재방출 없음).
 *
 * 왜 이 병합이 필요한가(A2 배경): 기존 구현은 DO 수신 하나뿐이어서, 에뮬레이터가 D→P 전환 때
 * DO 해제 콜백을 주지 않거나(수신 여부 자체가 U7 '부분' 미완결) DO 수신이 아예 없는 환경에서는
 * 화면이 주행(또는 정차)에 머물렀다. 기어는 U5 실측으로 GEAR_PARK 수신이 확인된(PASS) 신호라
 * 보조 판정 근거로 삼는다. 기어 폴링은 CombinedDrivingState 가 담당하고, 이 클래스는 병합 규칙만
 * 담당한다(순수 Kotlin — JVM 단위 테스트 대상).
 */
class DrivingStateMerger(initial: DrivingState = DrivingState.PARKED) {

    /** 마지막 1차 신호(DO). 초기값 PARKED — 수신 전엔 정차로 본다(파생 상태 금지). */
    private var primary: DrivingState = initial

    /**
     * 마지막 보조 신호(기어). true=기어 D(주행), false=기어 P/R/N(정차), null=못 읽음(무시).
     */
    private var auxiliary: Boolean? = null

    private val _state = MutableStateFlow(initial)

    /** 병합된 현재 주행 상태(ModeRouter 가 이 흐름을 수집해 화면을 전환한다). */
    val state: StateFlow<DrivingState> = _state.asStateFlow()

    /** 1차(DO) 신호 반영. */
    fun onPrimary(state: DrivingState) {
        primary = state
        recompute()
    }

    /** 보조(기어) 신호 반영. null 은 '못 읽음'이며 판정에 영향을 주지 않는다. */
    fun onAuxiliaryDriving(driving: Boolean?) {
        auxiliary = driving
        recompute()
    }

    private fun recompute() {
        // 어긋나면 더 제한적인 쪽(주행) 우선 — 1차가 주행이거나 보조가 주행이면 주행.
        val driving = primary == DrivingState.DRIVING || auxiliary == true
        _state.value = if (driving) DrivingState.DRIVING else DrivingState.PARKED
    }
}

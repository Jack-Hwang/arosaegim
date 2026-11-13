package app.arosaegim.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 상태 병합기(A2, 2026-11-11 / Q26, 2026-11-13) — 1차 신호(주행 규제 DO, OFFICIAL)와
 * 2차 신호(기어, 보조)를 하나의 [DrivingState] 로 합친다.
 *
 * 규칙(00문서 F42·진단 화면 ⑨ '보조 신호 메모'의 기존 규칙 유지):
 *  - 1차는 CarUxRestrictions(DO) 수신 경로다(OFFICIAL — 01문서 9절). 값은 그대로 존중한다.
 *  - 기어는 보조 신호다. **두 신호가 어긋나면 더 제한적인 쪽(주행)을 우선한다.**
 *    1차=주행·보조=정차 → 주행(1차 존중) / 1차=정차·보조=주행 → 주행(보조 존중).
 *  - 보조 신호를 못 읽었으면(null) 1차만으로 판정한다 — 못 읽은 신호로 판정을 흔들지 않는다
 *    (가짜 값 금지와 같은 취지).
 *
 * Q26 정차 유지 해제 규칙(2026-11-13): 에뮬레이터에서 기어 D→P 전환 직후 CarUxRestrictions(DO)가
 * "Requires DO? true" 로 잔존하는 사례가 있었다(DEV_LOG Q26 실증). 위 기존 규칙은 이때 주행 모드에
 * 머물러 정차 화면으로 돌아오지 않는다. 그래서 **보조 신호(기어)가 false(정차)로 확인된 상태가
 * [parkReleaseHoldMs] 이상 연속 유지되면** 1차(DO)가 주행을 주장해도 PARKED 로 판정한다.
 * 근거: 기어 P에서 차가 움직일 수 없다는 물리 사실 — 기어 폴링(CombinedDrivingState, 2초 주기)이
 * P를 3회 연속 읽으면(기본 5초) DO 잔존은 오류로 본다.
 *  - 보류 기간(경과 < [parkReleaseHoldMs])에는 기존 규칙(어긋나면 더 제한적인 주행 우선)을
 *    그대로 적용한다 — 짧은 P 일시 읽음(신호 지연·오류)에 보수적으로 대응한다.
 *  - 보류 시각은 보조 false 최초 수신 시각이며, true·null 수신 시 해제된다. 1차(onPrimary)
 *    반복 호출은 보류 시각을 리셋하지 않는다 — 정차 보류는 기어 신호 기준이다.
 *  - 해제 규칙이 발동해도 내부 1차·보조 값은 그대로 유지한다. 이후 1차가 정차를 보고하면
 *    기존 규칙으로 자연 복귀한다.
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
class DrivingStateMerger(
    initial: DrivingState = DrivingState.PARKED,
    /** 현재 시각 공급. 기본은 벽시계 — JVM 단위 테스트에서는 가짜 시계를 주입한다(Q26). */
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    /** 정차 유지 해제 보류 시간(ms). 기어 P 연속 확인 최소 시간(Q26 기본 5초). */
    private val parkReleaseHoldMs: Long = PARK_RELEASE_HOLD_MS,
) {

    /** 마지막 1차 신호(DO). 초기값 PARKED — 수신 전엔 정차로 본다(파생 상태 금지). */
    private var primary: DrivingState = initial

    /**
     * 마지막 보조 신호(기어). true=기어 D(주행), false=기어 P/R/N(정차), null=못 읽음(무시).
     */
    private var auxiliary: Boolean? = null

    /** 보조 false(정차) 연속 구간의 최초 확인 시각. true·null 수신 시 null 로 해제된다(Q26). */
    private var auxParkSinceMs: Long? = null

    private val _state = MutableStateFlow(initial)

    /** 병합된 현재 주행 상태(ModeRouter 가 이 흐름을 수집해 화면을 전환한다). */
    val state: StateFlow<DrivingState> = _state.asStateFlow()

    /** 1차(DO) 신호 반영. 반복 호출은 정차 보류 시각을 리셋하지 않는다(Q26 — 기어 기준). */
    fun onPrimary(state: DrivingState) {
        primary = state
        recompute()
    }

    /** 보조(기어) 신호 반영. null 은 '못 읽음'이며 판정에 영향을 주지 않는다. */
    fun onAuxiliaryDriving(driving: Boolean?) {
        auxiliary = driving
        // false 연속 구간 시작을 기록한다. 이미 false 면 같은 구간이라 시각을 유지한다(리셋 금지).
        auxParkSinceMs = if (driving == false) auxParkSinceMs ?: nowMs() else null
        recompute()
    }

    private fun recompute() {
        val sinceMs = auxParkSinceMs
        // Q26 정차 유지 해제: 기어 P 가 보류 시간 이상 이어졌으면 1차(DO) 주행 잔존을 오류로 본다.
        val parkHoldElapsed = if (sinceMs != null) nowMs() - sinceMs else 0L
        val parkReleaseActive = sinceMs != null && parkHoldElapsed >= parkReleaseHoldMs
        val driving = if (parkReleaseActive) {
            false // 1차가 주행을 주장해도 PARKED 로 해제(내부 1차·보조 값은 유지 — 자연 복귀 대기).
        } else {
            // 어긋나면 더 제한적인 쪽(주행) 우선 — 보류 기간에도 그대로 적용(보수적).
            primary == DrivingState.DRIVING || auxiliary == true
        }
        _state.value = if (driving) DrivingState.DRIVING else DrivingState.PARKED
    }

    private companion object {
        /** Q26 기본 보류 시간. 기어 폴링(2초 주기)이 3회 연속 P면(5초) DO 잔존을 오류로 본다. */
        const val PARK_RELEASE_HOLD_MS = 5_000L
    }
}

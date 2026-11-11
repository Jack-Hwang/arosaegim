package app.arosaegim

import app.arosaegim.domain.DrivingState
import app.arosaegim.domain.DrivingStateMerger
import app.arosaegim.ports.DrivingStateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 주행 상태 제공자(A2, 2026-11-11) — 1차 UxRestrictions(DO, OFFICIAL) + 2차 기어 보조 폴링을
 * 하나의 흐름으로 합친다. 기존 규칙(어긋나면 더 제한적인 주행 우선)은 [DrivingStateMerger] 가
 * 담당하고, 이 클래스는 두 신호의 수집·수명만 담당한다.
 *
 * 배경(DEV_LOG F42·F51·U7 '부분'): 기존엔 DO 수신 하나뿐이었고, D→P 전환 때 DO 해제 콜백이
 * 오지 않거나 DO 수신 자체가 없는 환경에서는 ModeRouter 가 정차 화면(ParkedActivity)으로
 * 돌아오지 않았다. 기어는 U5 실측으로 GEAR_PARK 수신이 확인됐으므로(PASS), 이 신호를 2초 간격으로
 * 다시 읽어 보조 판정에 반영한다. DO 수신이 정상인 환경에서는 기존 동작과 완전히 같다(1차 우선).
 *
 * - start() 는 1차 [primary].start() 를 먼저 부른다(Car.createCar 메인 스레드 규약 —
 *   AppContainer 가 Application.onCreate(메인)에서 호출한다).
 * - 기어 조회는 [gearQuery] 로 위임한다(PleosVehicleInfoProvider.readGearStateOnly — 표시 전용·
 *   저장 금지 규칙과 동일한 경로). 결과 문자열은 [gearToDriving] 로 환산한다:
 *   GEAR_DRIVE → 주행(true), GEAR_PARK/REVERSE/NEUTRAL → 정차(false), 그 외(FAILED·타임아웃·
 *   못 읽음 null) → 무시(병합 판정에 영향 없음).
 * - 폴링은 주입받은 [scope] 에서 돈다. stop()/scope 취소 시 함께 끊긴다.
 */
class CombinedDrivingState(
    private val primary: DrivingStateProvider,
    private val gearQuery: ((String?) -> Unit) -> Unit,
    private val scope: CoroutineScope,
    private val pollPeriodMs: Long = GEAR_POLL_PERIOD_MS,
) : DrivingStateProvider {

    private val merger = DrivingStateMerger()

    override val state: StateFlow<DrivingState>
        get() = merger.state

    private var pollJob: Job? = null

    /** 마지막 기어 조회 결과 원문(진단 화면 표시 전용 — 저장·전송 금지). */
    var lastGearRaw: String? = null
        private set

    override fun start() {
        primary.start()
        // 1차(DO) 상태 수집 — 병합기의 1차 입력으로 흘려 보낸다.
        scope.launch {
            primary.state.collect { merger.onPrimary(it) }
        }
        // 2차(기어) 폴링 — 읽을 때마다 보조 입력을 갱신한다. 조회 실패(null)는 무시된다.
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                gearQuery { gear ->
                    lastGearRaw = gear
                    merger.onAuxiliaryDriving(gearToDriving(gear))
                }
                delay(pollPeriodMs)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        primary.stop()
        // 병합 상태는 PARKED 로 되돌린다(1차 stop 규약과 동일 — 파생 상태 금지).
        merger.onPrimary(DrivingState.PARKED)
        merger.onAuxiliaryDriving(null)
    }

    private companion object {
        /** 기어 보조 폴링 주간. 도착 음성·주행 화면 표시와 겹치지 않게 충분히 느리게. */
        const val GEAR_POLL_PERIOD_MS = 2_000L
    }
}

/**
 * 기어 상태 문자열을 보조 주행 판정으로 환산한다(순수 함수 — JVM 단위 테스트 대상).
 *
 * @return GEAR_DRIVE → true(주행), GEAR_PARK/GEAR_REVERSE/GEAR_NEUTRAL → false(정차),
 *         그 외(null·UNKNOWN·FAILED:…·NOT_INITIALIZED·ERROR:…) → null(못 읽음 — 무시).
 */
fun gearToDriving(gear: String?): Boolean? = when (gear) {
    "GEAR_DRIVE" -> true
    "GEAR_PARK", "GEAR_REVERSE", "GEAR_NEUTRAL" -> false
    else -> null
}

package app.arosaegim.pleos

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context

/**
 * Vehicle SDK 2.3.2 어댑터 — Diagnostics U5 전용 조회 기능.
 *
 * 선택 정책: Pleos CarInfo 는 AAR 확인 결과 VIN/CRN 조회만 제공하고 제조사·연식 getter 가
 * 없다. VIN 은 규정상 읽지 않는다(읽기·저장·전송 금지) → VehicleInfoProvider 포트는
 * ProfileVehicleInfo(수동 프로필)가 맡고, 이 클래스는 화면 진단용 capability/기어 조회만 노출한다.
 * 두 조회 결과 모두 화면 표시로만 쓰고 저장하지 않는다.
 */
class PleosVehicleInfoProvider(context: Context) {

    private val vehicle: Vehicle = Vehicle(context.applicationContext)
    private var initialized = false

    /** 마지막 초기화 오류(U5 표시용). */
    var lastError: String? = null
        private set

    /** 메인 스레드에서 1회 호출(AppContainer 가 Application.onCreate 에서 수행). */
    fun initialize(): Boolean {
        if (initialized) return true
        initialized = try {
            vehicle.initialize()
            true
        } catch (t: Throwable) {
            lastError = "Vehicle.initialize 실패: ${t.javaClass.simpleName}: ${t.message ?: ""}"
            false
        }
        return initialized
    }

    /**
     * Diagnostics U5 용: VIN capability 의 AccessType(NONE/READ/WRITE/READ_WRITE) 문자열만 확인.
     * ★ VIN 값 자체(getVINInfo/getCRNInfo)는 절대 호출하지 않는다 — capability 확인만으로
     *   값을 읽지 않는다. 결과는 표시 전용이며 저장·전송하지 않는다.
     */
    fun checkVinCapabilityOnly(onResult: (String) -> Unit) {
        if (!initialized) {
            onResult("NOT_INITIALIZED${lastError?.let { ": $it" } ?: ""}")
            return
        }
        try {
            vehicle.getCarInfo().checkVINInfoCapabilities(
                onComplete = { config ->
                    onResult(config.accessType?.name ?: "UNKNOWN")
                },
                onFailed = { e ->
                    onResult("FAILED:${e.javaClass.simpleName}: ${e.message ?: ""}")
                },
            )
        } catch (t: Throwable) {
            onResult("ERROR:${t.javaClass.simpleName}: ${t.message ?: ""}")
        }
    }

    /**
     * Diagnostics U5 용: 기어 상태 문자열(GEAR_PARK 등)만 반환.
     * 기어 상태는 민감정보가 아니며 화면 표시 전용 — 저장 금지.
     * U5 미검증: 권한(CAR_DRIVE 추정)과 에뮬레이터 동작은 미확인. 실패 시 사유를 문자열로 돌려준다.
     */
    fun readGearStateOnly(onResult: (String) -> Unit) {
        if (!initialized) {
            onResult("NOT_INITIALIZED")
            return
        }
        try {
            vehicle.getGear().getCurrentGearSelectionState(
                onComplete = { gear ->
                    onResult(gear?.name ?: "UNKNOWN")
                },
                onFailed = { e ->
                    onResult("FAILED:${e.javaClass.simpleName}: ${e.message ?: ""}")
                },
            )
        } catch (t: Throwable) {
            onResult("ERROR:${t.javaClass.simpleName}: ${t.message ?: ""}")
        }
    }

    /**
     * 종료 시 일괄 해제. 규칙(01문서): 콜백(onComplete/onFailed)이 끝나기 전에 release 금지 —
     * 호출자(AppContainer.closeAll)는 스모크 테스트 콜백이 모두 끝난 뒤 이 함수를 호출해야 한다.
     */
    fun release() {
        if (!initialized) return
        initialized = false
        try {
            vehicle.release()
        } catch (t: Throwable) {
            lastError = "Vehicle.release 실패: ${t.javaClass.simpleName}"
        }
    }
}

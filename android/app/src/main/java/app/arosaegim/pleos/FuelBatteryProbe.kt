package app.arosaegim.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.content.Context
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 연료·EV 배터리 capability 실측 전용 프로브(07문서 3절 D — '실측만 하고 구현은 보류').
 *
 * 용도: 진단 화면 U15·U16 이 Fuel.checkFuelPercentCapabilities 와
 * EvBattery.checkEvBatteryStateOfChargeCapabilities 호출이 '성공하는지'만 기록하게 한다.
 * 기획서의 "그때는 휘발유차, 오늘은 전기차로 왔어요" 문장은 '계획'이며, 구현 여부는 이 실측
 * 결과를 보고 정한다 — 그래서 이 프로브는 값 읽기·기능 구현을 전혀 하지 않는다(범위 고정).
 *
 * 시그니처 출처(추측 금지 — Vehicle-2.3.2.aar 를 javap 으로 직접 확인, 2026-11-12):
 *  - Vehicle(Context) 생성자 / initialize() / release() / getFuel() / getEvBattery()
 *  - Fuel.checkFuelPercentCapabilities(onComplete: (CapabilityConfig) -> Unit,
 *                                      onFailed: (Exception) -> Unit): void
 *  - EvBattery.checkEvBatteryStateOfChargeCapabilities(위와 같은 콜백 2개): void
 *  - CapabilityConfig.accessType: AccessType(NONE / READ / WRITE / READ_WRITE)
 *
 * 권한(AAR 매니페스트 확인 결과, 2026-11-12): SDK 매니페스트는
 * ai.pleos.playground.service.vehicle.VEHICLE_BINDING 하나만 요구한다 — Fuel·EvBattery 별도
 * 권한은 AAR 에 없다. 앱 매니페스트의 pleos.car.permission.CAR_ENERGY(에너지 계열)로 커버한다고
 * 보며, 실측에서 FAILED:SecurityException 이 나오면 그때 권한을 재확인한다(추측으로 추가 금지).
 *
 * 수명 모델: 진단 화면 전용 공용 인스턴스 1개([shared]) — Activity 재생성마다 Vehicle 서비스
 * 바인딩이 늘어나지 않게 한다. release() 는 두지 않는다. SDK 규칙(01문서)상 콜백이 끝나기 전에
 * release 하면 안 되는데, 공용 인스턴스는 프로세스 수명까지 살아 있으므로 해제 타이밍이 없다
 * (AppContainer.pleosVehicle 과 같은 수명 모델 — closeAll 시점이 없는 진단 전용 차이만 있다).
 */
class FuelBatteryProbe private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** 첫 확인 시점에 1회 만든다(화면 진입 즉시 서비스 바인딩하지 않게 지연 초기화). */
    private var vehicle: Vehicle? = null

    /** 마지막 초기화 실패 사유(NOT_INITIALIZED 결과에 붙여 화면으로 전달한다). */
    private var initError: String? = null

    companion object {
        /** 콜백 1회 대기 상한(작업 지시: 3초 타임아웃). */
        const val TIMEOUT_MS = 3_000L

        /** 타임아웃 결과 문자열(판정은 SmokeTests 가 AccessType 집합과 비교해 한다). */
        const val RESULT_TIMEOUT = "타임아웃"

        @Volatile
        private var shared: FuelBatteryProbe? = null

        /** 진단 화면 전용 공용 인스턴스 — ViewModel 은 이 팩토리로만 얻는다. */
        fun shared(context: Context): FuelBatteryProbe = shared ?: synchronized(this) {
            shared ?: FuelBatteryProbe(context).also { shared = it }
        }
    }

    /**
     * U15용: Fuel.checkFuelPercentCapabilities 만 확인한다.
     * ★ getFuelPercent(연료 값)는 절대 호출하지 않는다 — capability 확인만이 이 클래스의 전부다.
     * 반환: AccessType 이름(NONE/READ/WRITE/READ_WRITE) 또는 실패 사유 문자열.
     */
    suspend fun checkFuelCapabilities(): String = checkCapabilities("Fuel") { vehicle, done ->
        vehicle.getFuel().checkFuelPercentCapabilities(
            onComplete = { config -> done(capabilityName(config)) },
            onFailed = { e -> done(failedReason(e)) },
        )
    }

    /**
     * U16용: EvBattery.checkEvBatteryStateOfChargeCapabilities 만 확인한다.
     * ★ getEvBatteryStateOfCharge(SOC 값)는 절대 호출하지 않는다.
     */
    suspend fun checkEvBatteryCapabilities(): String = checkCapabilities("EvBattery") { vehicle, done ->
        vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(
            onComplete = { config -> done(capabilityName(config)) },
            onFailed = { e -> done(failedReason(e)) },
        )
    }

    // --- 내부 골격 ---

    /**
     * capability 확인 공용 골격 — 초기화 확인 → SDK 호출(runCatching 흡수) → 3초 타임아웃.
     * 콜백은 문자열 결과(done)로 이어지며, 타임아웃·취소 후 늦게 도착한 콜백은 조용히 버린다
     * (이미 취소된 continuation 에 resume 하면 IllegalStateException — SmokeTests.askOrNull 과
     * 같은 보호 방식).
     */
    private suspend fun checkCapabilities(
        domain: String,
        start: (Vehicle, (String) -> Unit) -> Unit,
    ): String {
        val vehicle = vehicleOrNull()
            ?: return "NOT_INITIALIZED: ${initError ?: "원인 미상"}"
        return runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val done: (String) -> Unit = { result ->
                        cont.resume(result) { /* 취소로 값 폐기 — 무시 */ }
                    }
                    start(vehicle, done)
                }
            } ?: RESULT_TIMEOUT
        }.getOrElse { t -> "ERROR[$domain]:${t.javaClass.simpleName}: ${t.message ?: ""}" }
    }

    /** Vehicle 지연 초기화. 실패해도 크래시 내지 않고 사유를 저장해 다음 클릭에서 재시도한다. */
    private fun vehicleOrNull(): Vehicle? {
        vehicle?.let { return it }
        return try {
            Vehicle(appContext).also { candidate ->
                candidate.initialize() // AAR 2.3.2 확인 시그니처 — 초기화는 PleosVehicleInfoProvider 와 동일.
                vehicle = candidate
                initError = null
            }
        } catch (t: Throwable) {
            initError = "Vehicle 초기화 실패: ${t.javaClass.simpleName}: ${t.message ?: ""}"
            null
        }
    }

    /** CapabilityConfig → AccessType 이름 1개. 값 읽기가 아니라 권한 확인 결과만 꺼낸다. */
    private fun capabilityName(config: CapabilityConfig): String =
        config.accessType?.name ?: "UNKNOWN"

    /** onFailed 예외 → 사유 문자열(예외 유형·메시지 — 개인 식별값 없음). */
    private fun failedReason(e: Exception): String =
        "FAILED:${e.javaClass.simpleName}: ${e.message ?: ""}"
}

package app.arosaegim.pleos

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import app.arosaegim.domain.DrivingState
import app.arosaegim.ports.DrivingStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 상태 제공자 — Pleos SDK 가 주행 규제 상태를 제공하지 않아(01문서),
 * 안드로이드 오토모티브 표준 API(CarUxRestrictionsManager)로 대체한 구현.
 *
 * 핵심 흐름:
 *  start() → Car.createCar → CAR_UX_RESTRICTION_SERVICE 매니저 획득 → 리스너 등록
 *  리스너에서 isRequiresDistractionOptimization == true 이면 DRIVING, 아니면 PARKED.
 *
 * U7 미검증: 이 방식이 Pleos Connect 에뮬레이터에서 실제로 동작하는지는 U7 스모크 테스트로 확인.
 * android.car.jar 는 compileOnly 이므로 클래스가 없는 환경에서는 NoClassDefFoundError 등이
 * 발생할 수 있다 → 모든 단계를 try/catch(Throwable) 로 감싸고, 실패 시 state 는 PARKED 를
 * 유지한 채 [startError] 에 마지막 오류 문자열을 남겨 Diagnostics 화면(U7)이 원인을 표시한다.
 *
 * start() 는 메인 스레드 필요(Car.createCar 규약) → AppContainer 가 Application.onCreate(메인)에서 호출.
 */
class UxRestrictionsDrivingState(context: Context) : DrivingStateProvider {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(DrivingState.PARKED)
    override val state: StateFlow<DrivingState> = _state.asStateFlow()

    /** start() 실패 원인 문자열(마지막 오류). 성공 시 null. Diagnostics 가 그대로 표시한다. */
    var startError: String? = null
        private set

    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var listener: CarUxRestrictionsManager.OnUxRestrictionsChangedListener? = null
    private var started = false

    override fun start() {
        if (started) return
        try {
            val newCar = Car.createCar(appContext)
            val mgr = newCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                as? CarUxRestrictionsManager
                ?: throw IllegalStateException("CAR_UX_RESTRICTION_SERVICE 매니저 획득 실패")

            val newListener =
                CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                    // 주행 규제(DO) 요구 중이면 주행 중으로 판정한다. 값은 저장하지 않고 흐름으로만 전달.
                    _state.value =
                        if (restrictions?.isRequiresDistractionOptimization == true) {
                            DrivingState.DRIVING
                        } else {
                            DrivingState.PARKED
                        }
                }

            mgr.registerListener(newListener)
            uxManager = mgr
            listener = newListener
            car = newCar
            started = true
            startError = null
        } catch (t: Throwable) {
            // U7 미검증: ClassNotFoundException, 매니저 null, 시스템 서비스 부재 등 전부 여기서 흡수.
            startError = "${t.javaClass.simpleName}: ${t.message ?: "원인 미제공"}"
            releaseInternal()
            _state.value = DrivingState.PARKED // 실패 시에도 PARKED 유지(파생 상태 금지)
        }
    }

    override fun stop() {
        if (!started) return
        releaseInternal()
        _state.value = DrivingState.PARKED
    }

    /** 자원 해제. 항목별 try/catch — 일부 해제 실패가 전체 정리를 막지 않게 한다. */
    private fun releaseInternal() {
        started = false
        try {
            // API-35 android.car.jar 는 무인자 unregisterListener() 만 제공 → 전체 해제 후 연결 종료.
            uxManager?.unregisterListener()
        } catch (t: Throwable) {
            startError = "unregisterListener 실패: ${t.javaClass.simpleName}"
        }
        listener = null
        uxManager = null
        try {
            car?.disconnect()
        } catch (t: Throwable) {
            startError = "Car.disconnect 실패: ${t.javaClass.simpleName}"
        }
        car = null
    }
}

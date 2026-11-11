package app.arosaegim.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import app.arosaegim.domain.Haversine
import app.arosaegim.domain.Place
import app.arosaegim.ports.ArrivalDetector
import app.arosaegim.ports.ArrivalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 화면 거리 표시용 최신 위치 묶음(T5 신규).
 *
 * ⚠ 저장 금지 규칙(00문서 2-2 §7): 위치는 도착 판정과 화면 거리 표시에만 쓰고
 * 절대 파일·서버·로그에 남기지 않는다. 이 값은 메모리(StateFlow)에만 존재한다.
 */
data class LatestLocation(
    val lat: Double,
    val lon: Double,
    val timestampMs: Long,
)

/**
 * ArrivalDetector 의 Pleos FusedLocation 1.1.3 구현.
 *
 * 핵심 흐름:
 *  1차 — addProximityAlert(목표 좌표, arriveRadiusM) 등록. ENTER 만 도착으로 판정.
 *  2차 — 근접 알림 등록/콜백이 실패하면 위치 콜백(registerFusedLocationCallback) +
 *        Haversine 자체 거리 계산으로 진입/이탈을 감지한다(01문서 U3 대체 규정).
 *  (T5 확장) 위치 콜백은 폴백과 무관하게 watch() 때 항상 등록한다 — 수신 위치는
 *  [latestLocation] 으로 주행 화면의 "약 N.Nkm" 거리 표시에만 쓰인다(저장 금지).
 *
 * 반경 단위(radiusMeters=미터로 추정)는 01문서 [UNKNOWN] → U3 에서 실측한다.
 * FUSED_LOCATION 권한은 dangerous → 에뮬레이터에서 `pm grant` 필요(U3 미검증).
 */
class FusedArrivalDetector(context: Context) : ArrivalDetector {

    private val fused = FusedLocation(context.applicationContext)

    /** FusedLocation 지원 여부(U4). isSupported() 호출 자체가 실패하면 false 로 본다. */
    val supported: Boolean = runCatching { fused.isSupported() }.getOrDefault(false)

    private val _arrivals = MutableSharedFlow<ArrivalEvent>(extraBufferCapacity = 16)
    override val arrivals: Flow<ArrivalEvent> = _arrivals.asSharedFlow()

    /** 진입/이탈 문자열("ENTER"/"EXIT") — U3 가 실시간 표시한다. */
    private val _transitions = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val transitions: SharedFlow<String> = _transitions.asSharedFlow()

    /** 현재 동작 모드: "idle" | "proximity" | "haversine". */
    private val _mode = MutableStateFlow("idle")
    val mode: StateFlow<String> = _mode.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * 최신 위치(T5 신규) — 주행 화면 거리 표시 전용. 메모리에서만 유지하고 저장하지 않는다.
     * 위치 콜백이 등록되어 수신되는 동안에만 갱신되고, stop() 에서 비워 다음 감시를 깨끗이 시작한다.
     */
    private val _latestLocation = MutableStateFlow<LatestLocation?>(null)
    val latestLocation: StateFlow<LatestLocation?> = _latestLocation.asStateFlow()

    private var initialized = false
    private var currentTarget: Place? = null
    private var currentAlertId: String? = null
    private var insideArea = false

    // ── A3 진단 관찰(스모크 U3) 전용 수명주기(2026-11-11) ──
    // 기존엔 U3 가 watch()/stop() 을 그대로 써서 관찰을 끄면 여정 감시(도착 판정 근접 알림)까지
    // 함께 해제됐다. 여정 감시는 TripSession 이 [watch] 로 등록하고 [stop] 으로만 끊는다.
    // 진단 관찰은 아래 [startObservation]/[stopObservation] 으로 완전히 분리한다 — 별도 근접
    // 알림(alertId 별도), 별도 리스너, 별도 표시 흐름. 관찰을 꺼도 여정 감시와 다음 장소
    // 도착 판정은 그대로 유지된다. 관찰은 관찰일 뿐 도착 이벤트(arrivals)를 발행하지 않는다
    // (도착·스탬프는 여정 감시의 본역 — 이중 발행 방지).
    private val _observationTransitions = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** 진단 관찰 전용 진입/이탈 문자열("ENTER"/"EXIT") — U3 가 실시간 표시한다. */
    val observationTransitions: SharedFlow<String> = _observationTransitions.asSharedFlow()

    /** 진단 관찰 전용 모드: "idle" | "proximity". 여정 감시의 [mode] 와 별개다. */
    private val _observationMode = MutableStateFlow("idle")
    val observationMode: StateFlow<String> = _observationMode.asStateFlow()

    /** 진단 관찰 전용 마지막 오류(여정 감시의 [lastError] 와 섞지 않는다). */
    private val _observationLastError = MutableStateFlow<String?>(null)
    val observationLastError: StateFlow<String?> = _observationLastError.asStateFlow()

    private var observationAlertId: String? = null

    /** 진단 관찰 전용 근접 알림 리스너 — 등록/전이/오류만 관찰 흐름에 흘리고 끝난다. */
    private val observationListener = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) {
            observationAlertId = alertId
            _observationMode.value = "proximity"
        }

        override fun onTransition(event: ProximityAlertEvent) {
            // 이전 관찰의 alertId 로 온 알림은 무시한다(stopObservation 직후 늦게 온 콜백 방지).
            if (event.alertId != observationAlertId) return
            val enter = event.transition == ProximityTransition.ENTER
            _observationTransitions.tryEmit(if (enter) "ENTER" else "EXIT")
        }

        override fun onError(exception: Exception) {
            // 관찰 실패는 여정 감시에 영향을 주지 않는다(폴백 전환 없음 — A3 분리 원칙).
            _observationLastError.value =
                "관찰 근접알림 onError: ${exception.javaClass.simpleName}: ${exception.message ?: ""}"
            _observationMode.value = "idle"
        }
    }

    /** 위치 콜백이 SDK 에 등록되어 있는지(거리 표시 + 폴백 판정 공용). */
    private var locationRegistered = false

    /** Haversine 폴백 판정 모드 여부(근접 알림 실패 시 true — 이때만 진입/이탈을 판정한다). */
    private var haversineActive = false

    /** 1차: SDK 근접 알림 리스너. */
    private val proximityListener = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) {
            currentAlertId = alertId
            _mode.value = "proximity"
        }

        override fun onTransition(event: ProximityAlertEvent) {
            // 이전 watch 의 alertId 로 온 알림은 무시한다(watch 교체 직후 늦게 온 콜백 방지).
            if (event.alertId != currentAlertId) return
            val enter = event.transition == ProximityTransition.ENTER
            _transitions.tryEmit(if (enter) "ENTER" else "EXIT")
            if (enter) {
                currentTarget?.let { _arrivals.tryEmit(ArrivalEvent(it.id, "fused")) }
            }
        }

        override fun onError(exception: Exception) {
            // 근접 알림 실패(권한 미부여 등) → Haversine 폴백 전환(01문서 U3 대체 규정).
            _lastError.value =
                "근접알림 onError: ${exception.javaClass.simpleName}: ${exception.message ?: ""}"
            startHaversineFallback()
        }
    }

    /**
     * 2차: 위치 콜백 — 두 가지 역할을 한다.
     *  ① (T5 확장) 수신 위치를 [latestLocation] 에 흘려 주행 화면 거리 표시에 쓰게 한다.
     *     근접 알림 성공/실패와 무관하게 항상 갱신한다.
     *  ② 폴백 모드([haversineActive])일 때만 거리를 직접 계산해 진입/이탈을 감지한다.
     */
    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val loc = location ?: return
            // ① 거리 표시용 최신 위치. 메모리에서만 유지(저장·전송·로그 금지 규칙).
            _latestLocation.value =
                LatestLocation(loc.latitude, loc.longitude, System.currentTimeMillis())

            // ② 폴백 도착 판정 — 근접 알림이 정상 동작 중이면 하지 않는다(1차가 도착을 알린다).
            if (!haversineActive) return
            val target = currentTarget ?: return
            // 거리 값은 도착 판정에만 사용한다(위치 경로 저장·전송·로그 출력 금지 규칙).
            val distance = Haversine.distanceM(loc.latitude, loc.longitude, target.lat, target.lon)
            val inside = distance <= target.arriveRadiusM
            if (inside != insideArea) {
                insideArea = inside
                _transitions.tryEmit(if (inside) "ENTER" else "EXIT")
                if (inside) {
                    _arrivals.tryEmit(ArrivalEvent(target.id, "fused"))
                }
            }
        }

        override fun onError(exception: Exception) {
            // 권한 미부여 등으로 위치 수신 자체가 불가하면 거리 표시·폴백 모두 생략한다.
            // 주행 흐름을 막지 않는다(조용히 기록만 남긴다 — Diagnostics 표시용).
            _latestLocation.value = null
            _lastError.value = "위치콜백 onError: ${exception.javaClass.simpleName}"
        }
    }

    /** 목표 장소 감시 시작. 이미 watch 중이면 기존 근접 알림을 제거하고 교체한다. */
    override fun watch(target: Place) {
        removeCurrentAlert()
        currentTarget = target
        insideArea = false
        haversineActive = false

        if (!supported) {
            // 공식 문서: 사용 전 isSupported() 확인 필수. 미지원이면 기능을 제공하지 않는다.
            _lastError.value = "U4: FusedLocation isSupported=false — 근접 알림/위치 콜백 모두 불가"
            _mode.value = "idle"
            return
        }

        if (!initialized) {
            try {
                fused.initialize() // 동기 초기화(1회)
                initialized = true
            } catch (t: Throwable) {
                _lastError.value = "initialize 실패: ${t.javaClass.simpleName}: ${t.message ?: ""}"
                _mode.value = "idle"
                return
            }
        }

        _mode.value = "idle" // onRegistered 수신 전까지 대기 상태

        // (T5 확장) 위치 콜백은 근접 알림 성공/실패와 무관하게 항상 등록을 시도한다.
        // 수신 위치는 거리 표시에 쓰이고, 근접 알림이 실패하면 폴백 판정에도 쓰인다.
        ensureLocationCallbackRegistered()

        val request = ProximityAlertRequest(
            latitude = target.lat,
            longitude = target.lon,
            radiusMeters = target.arriveRadiusM.toFloat(), // 반경 단위 실측은 U3 과제(01문서 [UNKNOWN])
            transitionTypes = setOf(ProximityTransition.ENTER, ProximityTransition.EXIT),
        )
        try {
            fused.addProximityAlert(request, proximityListener)
        } catch (t: Throwable) {
            _lastError.value =
                "addProximityAlert 예외: ${t.javaClass.simpleName}: ${t.message ?: ""}"
            startHaversineFallback()
        }
    }

    /**
     * 진단 관찰 전용 근접 알림 등록(A3) — 여정 감시([watch])와 독립이다.
     *
     * - 별도 alertId·별도 리스너로 등록하므로 [stopObservation] 은 여정 감시의 근접 알림을
     *   건드리지 않는다. 관찰 중에도 여정 감시의 도착 판정(ENTER → arrivals 발행)은 계속 된다.
     * - 관찰은 도착 이벤트를 발행하지 않는다 — 관찰의 목적은 U3 의 ENTER/EXIT 수신 확인이다.
     * - 근접 알림 등록이 실패해도 Haversine 폴백으로 전환하지 않는다(여정 감시 전용 로직).
     *
     * @return 등록 결과 문자열("등록 요청", "미지원" 등) — U3 상세 로그용. 상태 자체는
     *         [observationMode]·[observationLastError] 흐름으로 확인한다.
     */
    fun startObservation(target: Place): String {
        stopObservation() // 이전 관찰이 있으면 먼저 해제한다(여정 감시는 그대로).
        if (!supported) {
            _observationLastError.value = "U4: FusedLocation isSupported=false — 관찰 등록 불가"
            _observationMode.value = "idle"
            return "미지원"
        }
        if (!initialized) {
            try {
                fused.initialize() // 동기 초기화(1회) — 여정 감시와 같은 인스턴스를 재사용한다.
                initialized = true
            } catch (t: Throwable) {
                _observationLastError.value =
                    "관찰 initialize 실패: ${t.javaClass.simpleName}: ${t.message ?: ""}"
                _observationMode.value = "idle"
                return "초기화 실패"
            }
        }
        _observationMode.value = "idle" // onRegistered 수신 전까지 대기 상태.
        val request = ProximityAlertRequest(
            latitude = target.lat,
            longitude = target.lon,
            radiusMeters = target.arriveRadiusM.toFloat(),
            transitionTypes = setOf(ProximityTransition.ENTER, ProximityTransition.EXIT),
        )
        return try {
            fused.addProximityAlert(request, observationListener)
            _observationLastError.value = null
            "등록 요청"
        } catch (t: Throwable) {
            _observationLastError.value =
                "관찰 addProximityAlert 예외: ${t.javaClass.simpleName}: ${t.message ?: ""}"
            _observationMode.value = "idle"
            "등록 실패"
        }
    }

    /**
     * 진단 관찰 중단(A3) — 관찰 근접 알림만 해제하고 여정 감시는 그대로 둔다.
     * 이것이 '관찰을 꺼도 다음 장소 도착 판정이 계속 된다'의 구현 지점이다.
     */
    fun stopObservation() {
        val id = observationAlertId ?: return
        try {
            fused.removeProximityAlert(id)
        } catch (t: Throwable) {
            _observationLastError.value =
                "관찰 removeProximityAlert 실패: ${t.javaClass.simpleName}"
        }
        observationAlertId = null
        _observationMode.value = "idle"
    }

    /**
     * 감시 중단. 규칙: removeProximityAlert/unregister 까지만 하고 fused.release() 는 하지
     * 않는다(재사용). 전체 해제는 [release] 로 분리했다(AppContainer.closeAll 전용).
     */
    override fun stop() {
        removeCurrentAlert()
        stopLocationCallback()
        currentTarget = null
        insideArea = false
        haversineActive = false
        _mode.value = "idle"
        _latestLocation.value = null // 마지막 위치는 감시 종료와 함께 버린다(저장 금지 규칙).
        // A3: 여정 감시 중단이어도 진단 관찰은 건드리지 않는다(수명주기 분리).
    }

    /** closeAll 전용 전체 해제 — 리스너 해제를 먼저 하고 release 한다(관찰 포함 전부). */
    fun release() {
        stop()
        stopObservation() // A3 관찰 근접 알림도 함께 해제한다.
        try {
            fused.release()
        } catch (t: Throwable) {
            _lastError.value = "FusedLocation release 실패: ${t.javaClass.simpleName}"
        }
    }

    private fun removeCurrentAlert() {
        val id = currentAlertId ?: return
        try {
            fused.removeProximityAlert(id)
        } catch (t: Throwable) {
            _lastError.value = "removeProximityAlert 실패: ${t.javaClass.simpleName}"
        }
        currentAlertId = null
    }

    private fun startHaversineFallback() {
        if (!supported || haversineActive) return
        // watch() 때 이미 등록을 시도했으나, 등록 실패 후 폴백 전환 케이스를 위해 다시 보장한다.
        ensureLocationCallbackRegistered()
        haversineActive = true
        _mode.value = "haversine"
        _lastError.value = (_lastError.value ?: "") + " → Haversine 폴백 전환"
    }

    /** 위치 콜백을 아직 등록하지 않았다면 등록한다(거리 표시 + 폴백 공용). */
    private fun ensureLocationCallbackRegistered() {
        if (locationRegistered) return
        try {
            fused.registerFusedLocationCallback(locationListener)
            locationRegistered = true
        } catch (t: Throwable) {
            // 등록 실패 시 근접 알림(1차)은 계속 동작할 수 있으므로 조용히 생략하고 기록만 남긴다.
            _lastError.value = "위치콜백 등록 실패: ${t.javaClass.simpleName}: ${t.message ?: ""}"
        }
    }

    private fun stopLocationCallback() {
        if (!locationRegistered) return
        try {
            fused.unregisterFusedLocationCallback(locationListener)
        } catch (t: Throwable) {
            _lastError.value = "unregister 실패: ${t.javaClass.simpleName}"
        }
        locationRegistered = false
    }
}

package app.arosaegim.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import app.arosaegim.domain.Place
import app.arosaegim.ports.RouteEvent
import app.arosaegim.ports.RouteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * RouteService 의 Pleos NaviHelper 3.0.5 구현.
 *
 * 핵심 흐름: 생성 → initialize(콜백 버전, 비동기) → 성공 시 addListener →
 * startRoute(RouteInfo) → 이벤트 콜백을 RouteEvent 로 변환해 SharedFlow 로 발행.
 *
 * 설계 근거(01문서 + 공식 샘플 NaviHelperViewModel):
 *  - requestRoute 의 좌표 인자 순서가 버전마다 바뀌었으므로 반드시 "이름 있는 인자"로 호출.
 *  - NaviHelperEventListener 의 모든 콜백은 default 구현(AAR javap 확인) → 필요한 것만 override.
 *  - 요청이 정상 접수되어도 경로 탐색 완료를 뜻하지 않는다(공식 문서).
 *  - DestinationArrivedInfo 에는 placeId 가 없다(duration/distance/name/address 뿐, AAR 확인) →
 *    Arrived(placeId = null) 로만 보고. 도착 판정 본역은 FusedArrivalDetector 이다.
 */
class PleosNaviRouteService(context: Context) : RouteService {

    private val appContext = context.applicationContext

    // 선언 순서 주의: helper 초기화에서 실패 사유를 기록하므로 _initError 가 먼저 와야 한다.
    private val _initError = MutableStateFlow<String?>(null)

    private val helper: NaviHelper? = runCatching { NaviHelper(appContext) }
        .onFailure { t ->
            _initError.value = "NaviHelper 생성 실패: ${t.javaClass.simpleName}: ${t.message ?: ""}"
        }
        .getOrNull()

    /** 초기화 실패 사유. null = 아직 실패 통보 없음(비동기 콜백이므로 null 이 곧 성공은 아님). */
    val initError: StateFlow<String?> = _initError.asStateFlow()

    /** 초기화 진행 상태: "pending" → "ok" | "failed". AppContainer 선택 대기/U2 표시용. */
    private val _initState = MutableStateFlow("pending")
    val initState: StateFlow<String> = _initState.asStateFlow()

    private val _events = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 32)
    override val events: Flow<RouteEvent> = _events.asSharedFlow()

    /** 마지막 내비 콜백 이력(U2 표시용). 값은 이벤트 종류와 공개 정보(시간/거리 수치)만 담는다. */
    private val _lastStatus = MutableStateFlow<String?>(null)
    val lastStatus: StateFlow<String?> = _lastStatus.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _lastStatus.value = "onRouteStarted"
            _events.tryEmit(RouteEvent.Started)
        }

        override fun onRoutingCompleted() {
            // 주의: 요청 정상 접수(경로 안내 준비)를 뜻하며 목적지 도착이 아니다.
            _lastStatus.value = "onRoutingCompleted"
            _events.tryEmit(RouteEvent.Ready)
        }

        override fun onRouteCancelled() {
            _lastStatus.value = "onRouteCancelled"
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            // placeId 필드가 없어 null 로만 전달. duration/distance 는 공개 수치이므로 표시 허용.
            _lastStatus.value = "onDestinationArrived(duration=${info.duration}, distance=${info.distance})"
            _events.tryEmit(RouteEvent.Arrived(placeId = null))
        }

        override fun onError(errorCode: NaviErrorCode) {
            _lastStatus.value = "onError(${errorCode.name})"
            _events.tryEmit(RouteEvent.Error(errorCode.name))
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            _lastStatus.value = "onNavigationStatus(${status.name})"
        }
    }

    init {
        // init 블록에서는 return 을 쓸 수 없으므로 if 로 분기한다.
        val h = helper
        if (h == null) {
            _initState.value = "failed"
        } else try {
            // 콜백(비동기) 버전 initialize 선호 — 실패 사유를 initError 로 남길 수 있다.
            h.initialize(
                {
                    // 초기화 성공 후 리스너 등록(공식 문서 순서: 생성 → initialize → addListener).
                    runCatching { h.addListener(listener) }
                        .onFailure { t ->
                            _initError.value = "addListener 실패: ${t.javaClass.simpleName}"
                            _initState.value = "failed"
                        }
                    if (_initState.value != "failed") {
                        _initState.value = "ok"
                    }
                },
                { t ->
                    _initError.value =
                        "initialize 실패: ${t.javaClass.simpleName}: ${t.message ?: "원인 미제공"}"
                    _initState.value = "failed"
                },
            )
        } catch (t: Throwable) {
            _initError.value = "initialize 예외: ${t.javaClass.simpleName}: ${t.message ?: ""}"
            _initState.value = "failed"
        }
    }

    /**
     * 경로 요청. 성공은 "호출에 예외가 없었다"만 의미하며 경로 탐색 완료가 아니다.
     * 실제 결과는 [events] 로 온다(U1 에서 관찰).
     */
    override suspend fun startRoute(to: Place): Result<Unit> = withContext(Dispatchers.Main) {
        val h = helper ?: return@withContext Result.failure(
            IllegalStateException("NaviHelper 생성 실패 상태"),
        )
        runCatching {
            // U1 핵심 관찰점: poiId/poiSubId/poiProvider 를 빈 문자열로 호출
            // (01문서: 참가팀 1곳 이상이 빈 값 호출 성공을 보고 — 실측 필요).
            // 좌표는 항상 이름 있는 인자(3.0.5 에서 (lon,lat)→(lat,lon) 순서 변경 이력 대응).
            h.requestRoute(
                RouteInfo(
                    latitude = to.lat,
                    longitude = to.lon,
                    poiName = to.name,
                    poiId = "",
                    poiSubId = "",
                    address = to.address,
                    poiProvider = "",
                ),
            )
        }.map { }
    }

    override fun cancel() {
        val h = helper ?: return
        runCatching { h.cancelRoute() }
            .onFailure { _lastStatus.value = "cancelRoute 실패: ${it.javaClass.simpleName}" }
    }

    /**
     * 종료 시 일괄 해제(AppContainer.closeAll 전용, RouteService 인터페이스 밖의 부가 기능).
     * 규칙: SDK 콜백(리스너)을 먼저 해제한 뒤 release 한다.
     */
    fun release() {
        val h = helper ?: return
        try {
            h.removeListener(listener)
        } catch (t: Throwable) {
            _lastStatus.value = "removeListener 실패: ${t.javaClass.simpleName}"
        }
        try {
            h.release()
        } catch (t: Throwable) {
            _lastStatus.value = "release 실패: ${t.javaClass.simpleName}"
        }
    }
}

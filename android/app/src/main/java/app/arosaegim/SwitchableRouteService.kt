package app.arosaegim

import app.arosaegim.domain.Place
import app.arosaegim.ports.RouteEvent
import app.arosaegim.ports.RouteService
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 내비 재전환 래퍼(계획 문서 조치 8 — DEV_LOG F35·F50·Q15 대응).
 *
 * 배경: AppContainer 의 선택 대기(INIT_WAIT_MS=3s) 안에 NaviHelper 초기화 콜백이 오지 않으면
 * RouteService 는 Fake 로 고정됐다. 대기 시간을 더 늘리는 대신, 초기화가 늦게라도 성공하면
 * Fake → Pleos 구현으로 갈아끼운다. 성공하지 않으면 Fake 로 머무른다(기존 '구현: 대체' 표기 유지).
 *
 * 규약:
 *  - 일방향 전환(Fake → Pleos 만 허용). Pleos 에서 다시 Fake 로 돌아가는 경로는 없다.
 *  - 스레드 안전: SDK 콜백 스레드에서 switchTo 가 불려도 AtomicReference CAS 로 단일 전환을
 *    보장한다. 두 번째 switchTo 호출은 첫 전환 뒤 항상 false 를 반환한다.
 *  - events 릴레이: [relayScope] 로 활성 위임자(delegate)의 이벤트를 자체 SharedFlow 로 흘려
 *    TripSession 등 기존 소비자가 래퍼 하나만 구독하면 된다. 전환 시 릴레이를 교체한다.
 */
class SwitchableRouteService(
    fallback: RouteService,
    private val relayScope: CoroutineScope,
) : RouteService {

    /** 일방향 전환의 기준점. CAS 는 항상 이 초기 위임자에서만 시작한다. */
    private val initial: RouteService = fallback

    private val delegate = AtomicReference(initial)

    private val _events = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 32)
    override val events: Flow<RouteEvent> = _events.asSharedFlow()

    private var relayJob: Job? = null

    /** 전환 완료 여부(진단 화면 표시용). true 이면 다시 false 로는 절대 돌아가지 않는다. */
    private val _switched = MutableStateFlow(false)
    val switched: StateFlow<Boolean> = _switched.asStateFlow()

    /** 현재 활성 위임자의 단순 클래스명(진단 화면 표시용). */
    private val _activeName = MutableStateFlow(initial.javaClass.simpleName)
    val activeName: StateFlow<String> = _activeName.asStateFlow()

    /** 재전환이 이미 일어났는지(동기 확인용). */
    val isSwitched: Boolean get() = _switched.value

    init {
        startRelay(initial) // 전환 전 구간의 이벤트(Fake 의 Started/Ready 등)도 그대로 흘려 보낸다.
    }

    /**
     * 일방향 재전환: 초기 위임자(fallback) → [real].
     *
     * @return 전환 성공 true. 이미 전환됐거나(두 번째 호출), real 이 초기 위임자 자신이면 false.
     * SDK 콜백 스레드에서 불려도 CAS 덕분에 최대 1회만 성공한다.
     */
    fun switchTo(real: RouteService): Boolean {
        if (real === initial) return false // 자기 자신으로의 전환은 무의미 — 전환으로 세지 않는다.
        if (!delegate.compareAndSet(initial, real)) return false // 이미 전환됨 — 일방향 보장.
        _switched.value = true
        _activeName.value = real.javaClass.simpleName
        startRelay(real)
        return true
    }

    /** 현재 활성 위임자의 이벤트를 자체 SharedFlow 로 릴레이한다(전환 시 교체). */
    private fun startRelay(service: RouteService) {
        relayJob?.cancel()
        relayJob = relayScope.launch {
            service.events.collect { _events.tryEmit(it) }
        }
    }

    /** 호출 시점의 활성 위임자로 전달한다(전환 직후 요청이 Fake 로 가는 경합은 무해 — fire-and-forget). */
    override suspend fun startRoute(to: Place): Result<Unit> = delegate.get().startRoute(to)

    override fun cancel() = delegate.get().cancel()
}

/**
 * 늦은 NaviHelper 초기화 감시기(조치 8) — [initState] 가 "pending" 에서 빠져나오는 순간을 관찰해
 * ok 면 [switchable] 을 [real] 로 재전환한다.
 *
 * - AppContainer 의 3초 선택 대기(runBlocking)가 pending 으로 끝나 Fake 를 선택한 뒤에도
 *   이 감시기는 계속 기다린다(대기 한도 없음 — 앱 종료 시 scope.cancel 이 정리한다).
 * - "failed" 로 끝나면 전환하지 않는다: U1/U2 실측은 naviRoute 직접 호출로 검증됐으므로
 *   '구현: 대체(Fake)' 표기는 솔직하게 유지한다(거짓 표기 금지).
 * - JVM 테스트 가능 구조: StateFlow 관찰과 전환·콜백만 담당하므로 Android 의존이 없다
 *   (SwitchableRouteServiceTest 참조).
 */
class NaviSwitchOverWatcher(
    private val initState: StateFlow<String>,
    private val switchable: SwitchableRouteService,
    private val real: RouteService,
    private val scope: CoroutineScope,
    private val onSwitched: () -> Unit = {},
    private val onInitFailed: () -> Unit = {},
) {

    /** 감시 시작. 반환 Job 을 저장하지 않아도 scope.cancel 로 함께 취소된다. */
    fun start(): Job = scope.launch {
        val lateState = initState.first { it != "pending" }
        when (lateState) {
            "ok" -> if (switchable.switchTo(real)) onSwitched()
            else -> onInitFailed()
        }
    }
}

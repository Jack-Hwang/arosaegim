package app.arosaegim.session

import app.arosaegim.data.LocalVisit
import app.arosaegim.data.VisitQueue
import app.arosaegim.data.VisitStore
import app.arosaegim.domain.Course
import app.arosaegim.domain.CourseProgress
import app.arosaegim.domain.Place
import app.arosaegim.domain.StampEngine
import app.arosaegim.ports.ArrivalDetector
import app.arosaegim.ports.ArrivalEvent
import app.arosaegim.ports.Narrator
import app.arosaegim.ports.RouteEvent
import app.arosaegim.ports.RouteService
import app.arosaegim.ports.TripSignalProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 여행 세션(T5) — 도착 → 스탬프 → 기록 → 음성 → 다음 목표 감시의 한 바퀴를 묶는다.
 *
 * 도착 파이프라인(한국어 주석 규약):
 *  1. arrivals(FusedLocation 근접 알림 1차 + 실패 시 Haversine 폴백, 수동 도착도 같은 규칙) 수신
 *  2. StampEngine.registerArrival(placeId, 현재 목표 id) — 순서 위반·중복 도착은 null 로 무시
 *  3. CourseProgress.markStamped — 진행(다음 목표·완주 여부) 갱신
 *  4. VisitStore.append — 로컬 방문 기록(좌표 없음: 장소 id + 시각만)
 *     4b. T8: VisitQueue.enqueue + flush 시도 — 서버 전송 실패는 큐에 보존(offline-first)
 *  5. narrator.speak("…에 도착했어요. 스탬프를 찍었어요.") — 음성 한 문장
 *  6. 다음 목표가 생기면 fusedArrival.watch(다음 장소) 재등록, 완주 시 watch 중지
 *     T7: 목표가 바뀔 때마다 routeService.startRoute(새 목표) 를 fire-and-forget 으로 호출
 *     (도착 판정은 ArrivalDetector 본역 — 내비 이벤트를 기다리지 않는다)
 *
 * 수명 규칙: 세션은 Application 스코프로 산다. Activity 가 stop 되어도 세션은 멈추지 않는다
 * (백그라운드 복귀 상태 유지 규칙). 전체 해제는 AppContainer.closeAll() 이 [stop] 을 부른다.
 *
 * 코루틴은 주입받은 [scope] 에서 돈다 — 테스트가 스코프를 바꿔 끼워 검증할 수 있다.
 *
 * T7(구간별 내비 요청)·T8(오프라인 큐 동기화)은 선택 기능이다. [routeService]·[visitQueue] 는
 * 기본값 null — null 이면 기존 동작만 한다(기존 테스트·호출부 호환 유지).
 *
 * T5-1(오늘 함께 달린 거리)·T6-2(함께 온 인원)도 선택 기능이다. [tripSignals] 는 기본값 null —
 * null 이면 tripKm·partySize 가 계속 null 로 머문다(가짜 값 금지; UI 는 null 이면 숨긴다).
 *  - start() 시 odometerKm() 을 한 번 기억(스냅샷)하고, 완주(isCompleted=true) 되는 순간 다시
 *    읽어 차이를 tripKm 로 노출한다. 시작·완주 중 하나라도 null 이면 tripKm 도 null 이다.
 *  - start() 시 partySize() 도 조회해 노출하고(T6-2), 매 도착 시점에 다시 조회해 갱신한다 —
 *    좌석 점유는 여정 중 변할 수 있다(중간 탑승·하차). 재조회 실패 시 기존 값을 유지한다.
 *    (좌석 점유 합계 — 사람 식별 정보 없음.)
 *
 * E1 재정합(2026-11-11 서버 실측): 도착 기록을 서버에 보낼 때 PIN([pinProvider])과 코스 주행
 * 식별자([courseRunId])를 함께 넘긴다. PIN 은 세션 메모리에서만 읽고 어디에도 저장하지 않는다
 * (00문서 10.6). courseRunId 는 start() 때 1회 발급해 도착마다 course_run_id 로 실린다 —
 * 서버가 '같은 코스 주행의 거리를 한 번만 합산'하는 기준키이며, 세션 재시작(새 start)마다
 * 새 값이 되어 재시작 앞뒤 주행이 한 주행으로 뭉개지지 않는다.
 */
class TripSession(
    val course: Course,
    private val fusedArrival: ArrivalDetector,
    private val manualArrival: ArrivalDetector?, // Diagnostics 수동 도착 연결용(없으면 null)
    private val narrator: Narrator,
    private val visitStore: VisitStore,
    private val scope: CoroutineScope,
    private val routeService: RouteService? = null, // T7: 목표 변경 시 내비 경로 요청(없으면 null)
    private val visitQueue: VisitQueue? = null, // T8: 방문 기록 오프라인 전송 큐(없으면 null)
    // T5-1·T6-2: 차량 신호(없으면 null). public val — 진단 화면(DiagnosticsViewModel)이
    // tripSignals?.partySize() / tripSignals?.odometerKm() 로 U11·U13 스모크 조회를 한다.
    val tripSignals: TripSignalProvider? = null,
    // E1: 전송 시점의 세션 PIN 공급자(예: { familySessionStore.pinOrNull() }). 메모리 전용 —
    // 저장·로그 금지(00문서 10.6). 기본 null 공급자라 기존 호출부는 그대로 컴파일된다.
    private val pinProvider: () -> String? = { null },
    // A7: 늦은 초기화 전환 신호 — SwitchableRouteService 가 Fake→Pleos 로 갈아끼워졌을 때
    // true 가 되는 StateFlow(없으면 null). true 로 바뀌는 순간 현재 목표의 경로를 다시 요청한다.
    private val routeBecameReady: StateFlow<Boolean>? = null,
) {

    /** 진행 상태 계산(다음 목표·완주)과 스탬프 규칙(장소당 1회·순서)은 도메인에 위임한다. */
    private var progress = CourseProgress(course)
    private val stampEngine = StampEngine()

    private val _currentTarget = MutableStateFlow(progress.currentTarget)
    val currentTarget: StateFlow<Place?> = _currentTarget.asStateFlow()

    private val _lastArrival = MutableStateFlow<Place?>(null)
    val lastArrival: StateFlow<Place?> = _lastArrival.asStateFlow()

    private val _stampedCount = MutableStateFlow(progress.completedPlaces.size)
    val stampedCount: StateFlow<Int> = _stampedCount.asStateFlow()

    private val _isCompleted = MutableStateFlow(progress.isCompleted)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    /** 화면 한 줄 알림용(예: "국립경주박물관에 도착했어요. 스탬프를 찍었어요."). */
    private val _lastEventLine = MutableStateFlow<String?>(null)
    val lastEventLine: StateFlow<String?> = _lastEventLine.asStateFlow()

    /** T5-1: 오늘 함께 달린 거리(km 가정 — 단위는 U13 실측 과제). 시작~완주 차이. 안 읽히면 null. */
    private val _tripKm = MutableStateFlow<Double?>(null)
    val tripKm: StateFlow<Double?> = _tripKm.asStateFlow()

    /** T6-2: 오늘 함께 온 인원(좌석 점유 합계 — 좌석별·사람별 정보는 저장하지 않는다). */
    private val _partySize = MutableStateFlow<Int?>(null)
    val partySize: StateFlow<Int?> = _partySize.asStateFlow()

    /** T5-1 스냅샷: 코스 시작 시점의 주행거리계 값. null 이면 아직 읽지 못한(또는 미지원) 상태다. */
    private var startOdometerKm: Double? = null

    /**
     * E1: 이 코스 주행(start 1회)의 식별자. start() 가 실제로 시작될 때마다 새로 발급하므로
     * 세션 재시작(resetCourse·재진입) 시 새 값이 된다 — 서버가 같은 주행의 거리를 한 번만
     * 합산하는 기준키다. 도착마다 큐 enqueue 를 통해 course_run_id 로 전송된다.
     */
    private var courseRunId: String = UUID.randomUUID().toString()

    /** arrivals 수집 Job 묶음. stop() 시 취소하고, start() 로 다시 만든다. */
    private var collectJobs: List<Job> = emptyList()

    /** 음성 문구 형식. 세션은 Context 를 갖지 않으므로 상수로 둔다(문구 리소스화는 T9 정리 대상). */
    private fun arrivalLine(place: Place): String {
        // T6-2: 좌석 점유로 읽은 인원이 있으면 문장에 함께 온 인원을 붙인다(00문서 11절 — 인원 수만 사용).
        val n = _partySize.value
        return if (n != null) {
            "${place.name}에 도착했어요. 오늘은 ${n}명이 함께 왔어요. 스탬프를 찍었어요."
        } else {
            "${place.name}에 도착했어요. 스탬프를 찍었어요."
        }
    }

    /** 세션 시작: arrivals 수집을 시작하고 현재 목표 감시를 등록한다. 중복 호출은 무시한다. */
    fun start() {
        if (collectJobs.isNotEmpty()) return // 이미 수집 중
        // E1: 실제로 새로 시작하는 시작마다 새 주행 식별자를 발급한다(재시작 = 새 주행).
        courseRunId = UUID.randomUUID().toString()
        collectJobs = buildList {
            add(scope.launch { fusedArrival.arrivals.collect { onArrival(it) } })
            manualArrival?.let { manual ->
                add(scope.launch { manual.arrivals.collect { onArrival(it) } })
            }
            // T7: 내비 오류 이벤트(Error)를 수동으로 지켜본다. 경로 요청 자체는 fire-and-forget이고
            // 이 수집은 '기다림'이 아니라 실패를 화면 한 줄로만 보고하는 수동 감시다.
            routeService?.let { service ->
                add(
                    scope.launch {
                        service.events.collect { event ->
                            if (event is RouteEvent.Error) {
                                // 오류 코드는 표시하지 않는다(좌표·코드 로깅 금지 규칙).
                                _lastEventLine.value = ROUTE_FAIL_LINE
                            }
                        }
                    },
                )
            }
            // A7: 늦은 초기화 전환 감시 — SwitchableRouteService 가 Fake→Pleos 로 전환되어
            // routeBecameReady 가 false→true 가 되는 순간, 진행 중인 현재 목표의 경로를 다시
            // 요청한다. 시작 요청(E5)이 Fake 로 갔다면 실제 내비는 그 목표를 모르므로, 실제
            // 구현이 준비된 시점에 한 번 더 요청해야 첫 구간 안내가 이어진다. 이미 준비 상태로
            // 시작했다면 E5 의 시작 요청 1회로 충분 — 중복 재요청하지 않는다.
            routeBecameReady?.let { ready ->
                add(
                    scope.launch {
                        if (!ready.value) {
                            ready.first { it }
                            _currentTarget.value?.let { requestRouteFor(it) }
                        }
                    },
                )
            }
        }
        // 코스 완주 전이면 지금 목표부터 감시한다(재시작·재진입 모두 커버).
        progress.currentTarget?.let { fusedArrival.watch(it) }

        // E5: 첫 목표에도 내비 경로를 요청한다. 기존에는 목표가 '바뀔 때'만 요청해서 시작 직후의
        // 첫 구간 안내가 빠졌다. 재시작·중간 재진입으로 이미 진행 중인 목표도 여기서 1회 요청되고
        // (요청이 겹쳐도 내비가 같은 목적지를 재계산할 뿐 — 무해), 완주 상태(목표 null)면 생략한다.
        progress.currentTarget?.let { requestRouteFor(it) }

        // T5-1: 코스 시작 시 주행거리계를 한 번 기억한다. 스냅샷이 이미 있으면(중간 재진입)
        // 다시 읽지 않는다 — 중간에 읽으면 완주 차이가 잘못 나온다.
        if (tripSignals != null && startOdometerKm == null) {
            scope.launch { startOdometerKm = runCatching { tripSignals.odometerKm() }.getOrNull() }
        }
        // T6-2: 인원 수는 start 당 1회 조회한다. 아직 못 읽었을 때만 다시 시도한다.
        if (tripSignals != null && _partySize.value == null) {
            scope.launch { _partySize.value = runCatching { tripSignals.partySize() }.getOrNull() }
        }
    }

    /**
     * 감시·수집만 중단한다. 스탬프·방문 기록 등 진행 상태는 남긴다
     * (백그라운드 복귀 후 start() 로 이어 갈 수 있게).
     */
    fun stop() {
        collectJobs.forEach { it.cancel() }
        collectJobs = emptyList()
        fusedArrival.stop()
        manualArrival?.stop()
    }

    /**
     * A3(2026-11-12): 현재 목표의 여정 감시(근접 알림)를 다시 등록한다.
     *
     * 진단 화면의 근접 관찰(U3)은 FusedArrivalDetector.startObservation/stopObservation 으로
     * 여정 감시와 분리되어 있지만, 감시기가 어떤 경로로든 stop 된 사고에 대비한 복구
     * 수단이다 — 진단 화면이 관찰을 중단할 때 호출해 '관찰을 꺼도 다음 장소의 도착 판정이
     * 유지·복구된다'를 닫아 준다. 등록 동작은 도착 처리(onArrival 6단계)의 watch 재등록과
     * 같아서 같은 목표를 다시 요청하는 중복은 무해하다(E5 의 '무해' 근거와 같다).
     */
    fun rewatchCurrentTarget() {
        if (collectJobs.isEmpty()) {
            start() // 수집까지 끊겼으면 start 가 감시 등록까지 다시 한다(중복 호출 무시 규약).
            return
        }
        _currentTarget.value?.let { fusedArrival.watch(it) }
    }

    /** 스탬프·진행 초기화(데모 재시작). 로컬 방문 기록도 함께 지운다(VisitStore.clear 용도 참조). */
    fun resetCourse() {
        val wasStarted = collectJobs.isNotEmpty() // 수집 중이던 세션이면 초기화 후 바로 이어 시작한다.
        stop() // 진행 중 감시/수집을 먼저 끊는다
        stampEngine.reset()
        // CourseProgress 는 리셋 API 가 없으므로 새로 만들어 진행을 처음부터 다시 계산한다.
        progress = CourseProgress(course)
        _lastArrival.value = null
        _lastEventLine.value = null
        // T5-1: 재시작은 새로운 여행이므로 거리 스냅샷·완주 차이를 다시 잡는다.
        startOdometerKm = null
        _tripKm.value = null
        // T6-2: 인원도 다시 읽는다(좌석 점유는 재시작 사이에 바뀔 수 있다).
        _partySize.value = null
        visitStore.clear()
        syncProgressFlows()
        if (wasStarted) start() // 데모 재시작 — 첫 목표 감시까지 start() 가 다시 등록한다.
    }

    /** 도착 이벤트 하나를 파이프라인으로 처리한다(fused·manual 공용, 규칙 동일). */
    private suspend fun onArrival(event: ArrivalEvent) {
        // (2) 스탬프 규칙: 지금 목표가 아니거나 이미 찍은 장소면 무시한다.
        val result = stampEngine.registerArrival(event.placeId, _currentTarget.value?.id)
            ?: return
        val place = course.places.firstOrNull { it.id == result.placeId } ?: return

        // T6-2 개선: 좌석 점유 인원은 여정 중 변할 수 있다(중간 탑승·하차). start() 의 1회
        // 조회만으로는 도착 안내 문장의 인원이 오래된 값이 된다. 그래서 매 도착 시점에 다시
        // 조회해 _partySize 를 갱신한다. 무시된 도착(순서 위반·중복)은 위의 return 으로 걸러지므로
        // 조회도 일어나지 않는다. 재조회 실패(예외·null) 시에는 마지막으로 읽은 값을 유지한다 —
        // null 로 덮어쓰지 않는다(가짜 값·정보 유실 방지, 00문서 11-1 T5-1 '가짜 값 금지'와 같은 취지).
        tripSignals?.let { signals ->
            runCatching { signals.partySize() }.getOrNull()?.let { fresh ->
                _partySize.value = fresh
            }
        }

        // (3) 진행 갱신 — 다음 목표/완주 여부가 여기서 계산된다.
        progress.markStamped(place.id)
        syncProgressFlows()

        // (4) 로컬 방문 기록. 좌표는 절대 저장하지 않는다(VisitStore 규약).
        val visit = LocalVisit(
            placeId = place.id,
            courseId = course.id,
            visitedAtEpochMs = System.currentTimeMillis(),
            source = event.source,
            // vehicleLabel 은 T9-1(차량 프로필 연동)에서 채운다. VIN 은 저장 금지 규칙.
            vehicleLabel = null,
        )
        visitStore.append(visit)

        // (4b) T8: 같은 기록을 오프라인 큐에 적재하고 즉시 전송을 시도한다(offline-first).
        // flush 실패(서버 없음·네트워크 오류·PIN 미입력)는 조용히 — 기록은 큐에 남아 다음 flush
        // 기회에 재전송된다(02문서 4절). 화면에는 안내 한 줄만 남긴다.
        // E1: 서버 계약에 맞춘 코스 주행 식별자(course_run_id)와 세션 PIN(메모리 전용 — 저장
        // 금지, 00문서 10.6)을 함께 넘긴다. PIN 은 전송(flush) 순간에만 쓰이고 파일에 남지 않는다.
        visitQueue?.enqueue(
            visit,
            familyCode = null,
            courseRunId = courseRunId,
            pin = pinProvider(),
        )
        scope.launch {
            val sent = visitQueue?.flush()
            if (sent?.isFailure == true) {
                _lastEventLine.value = QUEUE_OFFLINE_LINE
            }
        }

        // 화면 한 줄 알림 — 화면 전환·팝업 없이 이 문장만 바뀐다.
        _lastEventLine.value = arrivalLine(place)

        // (5) 음성 한 문장. 실패는 흡수한다(음성 실패가 주행 흐름을 막지 않는다).
        narrator.speak(arrivalLine(place))

        // (6) 다음 목표로 감시 대상을 옮긴다. 완주면 감시를 중단한다.
        val next = _currentTarget.value
        if (next != null) {
            fusedArrival.watch(next)
        } else {
            fusedArrival.stop()
        }
    }

    /** CourseProgress 의 진행 값을 노출 StateFlow 로 복사한다. */
    private fun syncProgressFlows() {
        val previousTarget = _currentTarget.value
        val nextTarget = progress.currentTarget
        val wasCompleted = _isCompleted.value
        _currentTarget.value = nextTarget
        _stampedCount.value = progress.completedPlaces.size
        _isCompleted.value = progress.isCompleted
        _lastArrival.value = progress.completedPlaces.lastOrNull()

        // T5-1: 완주(isCompleted=true) 되는 순간 주행거리를 다시 읽어 시작 차이를 계산한다.
        // 시작 스냅샷·완주 값 중 하나라도 null 이면 tripKm 도 null 로 둔다(가짜 값 금지).
        if (progress.isCompleted && !wasCompleted && tripSignals != null) {
            scope.launch {
                val start = startOdometerKm ?: return@launch
                val end = runCatching { tripSignals.odometerKm() }.getOrNull() ?: return@launch
                _tripKm.value = end - start
            }
        }

        // T7: 목표가 바뀔 때마다 새 목표로 내비 경로를 요청한다. 완주로 null 이 된 경우는 제외.
        if (nextTarget != null && nextTarget != previousTarget) {
            requestRouteFor(nextTarget)
        }
    }

    /**
     * T7: 새 목표 장소로 내비 경로를 요청한다(fire-and-forget).
     *
     * - 결과를 기다리지 않는다. 요청 접수 실패만 여기서 알리고, 경로 안내 결과 이벤트(Ready 등)는
     *   기다리지 않는다 — 도착 판정 본역은 ArrivalDetector 이고, [01문서 REPORTED] 에뮬레이터에서는
     *   내비가 위치를 안 따라가 도착 이벤트가 보조 수단일 뿐이기 때문이다.
     * - 호출 실패는 화면 한 줄로만 알린다. 주행 흐름을 끊지 않는다(오류 코드·좌표는 표시·로그 금지).
     */
    private fun requestRouteFor(target: Place) {
        val service = routeService ?: return
        scope.launch {
            val result = service.startRoute(target)
            if (result.isFailure) {
                _lastEventLine.value = ROUTE_FAIL_LINE
            }
        }
    }

    private companion object {
        /** T7: 내비 경로 요청 실패 시 화면 한 줄. 오류 코드·좌표는 담지 않는다. */
        const val ROUTE_FAIL_LINE = "내비 경로 요청 실패 — 앱은 계속 동작해요"

        /** T8: 서버 전송 실패 시 화면 한 줄 — 기록이 큐에 남아 재전송 대기임을 알린다. */
        const val QUEUE_OFFLINE_LINE = "서버 미연결, 기록은 이 기기에 저장됐어요"
    }
}

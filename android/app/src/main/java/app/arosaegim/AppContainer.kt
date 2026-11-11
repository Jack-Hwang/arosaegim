package app.arosaegim

import android.content.Context
import app.arosaegim.data.ApiClient
import app.arosaegim.data.SeedContent
import app.arosaegim.data.SeedRepository
import app.arosaegim.data.VisitQueue
import app.arosaegim.data.VisitStore
import app.arosaegim.domain.Course
import app.arosaegim.fallback.AndroidTtsNarrator
import app.arosaegim.fallback.FakeRouteService
import app.arosaegim.fallback.FallbackTripSignalProvider
import app.arosaegim.fallback.FallbackVoiceLetterPlayer
import app.arosaegim.fallback.ManualArrivalDetector
import app.arosaegim.fallback.ManualDrivingState
import app.arosaegim.fallback.ProfileVehicleInfo
import app.arosaegim.pleos.FusedArrivalDetector
import app.arosaegim.pleos.PleosNaviRouteService
import app.arosaegim.pleos.PleosTripSignalProvider
import app.arosaegim.pleos.PleosTtsNarrator
import app.arosaegim.pleos.PleosVehicleInfoProvider
import app.arosaegim.pleos.PleosVoiceLetterPlayer
import app.arosaegim.pleos.UxRestrictionsDrivingState
import app.arosaegim.ports.DrivingStateProvider
import app.arosaegim.ports.Narrator
import app.arosaegim.ports.RouteService
import app.arosaegim.ports.TripSignalProvider
import app.arosaegim.ports.VehicleInfoProvider
import app.arosaegim.ports.VoiceLetterPlayer
import app.arosaegim.session.TripSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 수동 의존성 조립기. 포트별로 Pleos 구현을 먼저 시도하고, 초기화 실패 시 폴백을 고른 뒤
 * 선택 결과를 [adapterNotes] 에 기록한다(Diagnostics ① 섹션 표시 + DEV_LOG 대응).
 *
 * 생성 제약: 반드시 Application.onCreate(메인 스레드)에서 생성할 것.
 * - UxRestrictionsDrivingState.start() 는 Car.createCar 규약상 메인 스레드가 필요하고,
 * - Pleos SDK 초기화 호출도 메인 스레드를 전제한다.
 * 아래 선택 대기(runBlocking)는 포트당 최대 3초로 제한한다(F35 실측 반영).
 * 주의: SDK 콜백이 메인 핸들러로 전달되는 빌드에서는 이 대기가 타임아웃으로 끝날 수 있다 —
 * 그 경우 보수적으로 폴백을 선택하며, U1/U2/U6 에서 직접 재검증할 수 있다.
 */
class AppContainer(private val appContext: Context) {

    private val notes = linkedMapOf<String, String>()

    /** 포트명 → 선택된 구현/사유(Diagnostics ① 섹션과 DEV_LOG 이 읽는다). */
    val adapterNotes: Map<String, String> get() = notes.toMap()

    // Pleos 어댑터 — 선택 결과와 무관하게 만들어 둔다(스모크 U1~U6 이 각각 직접 검증).
    // pleosVehicle 은 진단 화면 U5·U12 용 capability/기어 조회 전용이다. VehicleInfoProvider 선택에서는
    // 빠져 있다 — 01문서 10절: CarInfo 는 VIN/CRN 조회만 제공하므로 'PleosVehicleInfoProvider 는
    // 만들지 않는다(선택에서 제외)'. 차량 프로필은 ProfileVehicleInfo 고정이다.
    val pleosVehicle = PleosVehicleInfoProvider(appContext)
    val pleosTts = PleosTtsNarrator(appContext)
    val naviRoute = PleosNaviRouteService(appContext)
    val fusedArrival = FusedArrivalDetector(appContext)

    // T5-1·T6-2(Pleos Seat·Odometer)·T9-2(예시 음성 재생) 어댑터. 선택은 init 에서 한다.
    val pleosTripSignals = PleosTripSignalProvider(appContext)

    // 폴백 구현(항상 사용 가능).
    val manualDriving = ManualDrivingState()
    val manualArrival = ManualArrivalDetector()

    // 로컬 방문 기록(T5). closeAll 에서 지우지 않는다 — 기록은 차를 떠나도 남는 앱 데이터다.
    val visitStore = VisitStore(appContext)

    // 백엔드 클라이언트(T8). 서버 없는 환경이 기본 — 모든 호출 실패는 Result.failure 로 흡수된다.
    val apiClient = ApiClient()

    // 오프라인 전송 큐(T8). closeAll 에서 지우지 않는다 — 미전송 기록은 다음 실행의 flush 가 맡는다.
    val visitQueue = VisitQueue(appContext, apiClient)

    // 내장 시드 저장소 — 정차 화면(그때와 지금·스탬프북·맛집) 등 UI가 직접 읽는다.
    val seedRepository = SeedRepository()

    // 선택된 구현.
    val drivingState: DrivingStateProvider
    val routeService: RouteService
    val narrator: Narrator
    val vehicleInfo: VehicleInfoProvider
    val tripSignals: TripSignalProvider
    val voiceLetterPlayer: VoiceLetterPlayer

    /**
     * 여행 세션(T5) — 내장 시드의 코스로 만든다. 세션 수명은 Application 과 같고
     * start() 는 ArosaegimApp.onCreate 에서 1회 호출한다(Activity 가 stop 해도 유지).
     */
    val tripSession: TripSession

    init {
        pleosVehicle.initialize() // U5·U12 진단 조회의 전제. 실패는 lastError 로 남아 화면에 표시된다.

        // 1) 주행 상태: 표준 UxRestrictions 시도 → start 실패 시 수동 모드.
        val ux = UxRestrictionsDrivingState(appContext)
        ux.start() // 메인 스레드 전제 — 본 클래스 생성 규약 참조.
        drivingState = if (ux.startError == null) {
            notes["DrivingState"] = "UxRestrictionsDrivingState (표준 CarUxRestrictions — U7 미검증)"
            ux
        } else {
            notes["DrivingState"] =
                "ManualDrivingState — UxRestrictions 시작 실패: ${ux.startError ?: "원인 미제공"}"
            manualDriving
        }

        // 2) 경로: NaviHelper 초기화 콜백을 짧게 기다려 성공/실패 판정.
        routeService = selectRouteService()

        // 3) 안내 음성: Pleos TTS onReady 대기 → 미수신 시 Android 기본 TTS.
        narrator = selectNarrator(appContext)

        // 4) 차량 정보: VehicleInfoProvider 는 ProfileVehicleInfo 고정이다. Pleos 선택 로직은 없다 —
        //    01문서 10절: CarInfo 는 VIN/CRN 조회만 제공(AAR javap 확인), VIN 은 규정상 읽지 않는다.
        //    pleosVehicle 프로퍼티는 진단 화면 U5·U12 용으로만 남겨 둔다(선택 대상 아님).
        vehicleInfo = ProfileVehicleInfo(model = "가상 차량(수동 프로필)", modelYear = 2025)
        notes["VehicleInfo"] =
            "ProfileVehicleInfo — CarInfo 는 VIN/CRN뿐(01문서 10절), VIN 은 규정상 미사용"

        // 5) 시드: 코스와 T5-1·T6-2·T9-2 데이터(heritage·voice_letter_demo·seat_roles)를 함께 읽는다.
        //    실패 시 빈 코스로 보수적으로 복구한다(00문서 2-3 §16: 앱은 시드로 끝까지 돌아야 한다).
        val seed = loadSeed()

        // 6) 차량 신호(T5-1·T6-2): Pleos Vehicle 초기화 성공 시 Pleos, 실패 시 폴백(수동 입력·null).
        tripSignals = selectTripSignals()

        // 7) 예시 음성 편지(T9-2): assets 에 오디오 파일이 있어야 Pleos 재생. 없으면 텍스트 폴백.
        voiceLetterPlayer = if (seed.voiceLetters.isNotEmpty()) {
            val pleosPlayer = PleosVoiceLetterPlayer(appContext, seed.voiceLetters)
            if (pleosPlayer.isAvailable) {
                notes["VoiceLetter"] = "PleosVoiceLetterPlayer (assets 재생 — 예시 ${seed.voiceLetters.size}건)"
                pleosPlayer
            } else {
                notes["VoiceLetter"] = "FallbackVoiceLetterPlayer — assets 오디오 파일 없음(텍스트 편지 표시)"
                FallbackVoiceLetterPlayer()
            }
        } else {
            notes["VoiceLetter"] = "FallbackVoiceLetterPlayer — 시드에 voice_letter_demo 없음(텍스트 편지 표시)"
            FallbackVoiceLetterPlayer()
        }

        // 8) 여행 세션(T5): 선택된 구현과 시드 코스로 만든다.
        tripSession = TripSession(
            course = seed.course,
            fusedArrival = fusedArrival,
            manualArrival = manualArrival, // 진단 화면 '수동 도착' 버튼도 같은 파이프라인을 탄다.
            narrator = narrator,
            visitStore = visitStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            // T7: 선택된 구현(Pleos 성공/폴백 무관). 경로 요청은 fire-and-forget 이므로
            //     FakeRouteService 를 받아도 주행 흐름에 영향이 없다.
            routeService = routeService,
            // T8: 방문 기록 오프라인 큐 — 도착마다 적재·전송 시도(실패 시 큐 보존).
            visitQueue = visitQueue,
            // T5-1·T6-2: 차량 신호(Seat 인원·Odometer 거리). null 이 아니어도 안 읽히면 null 로 흐른다.
            tripSignals = tripSignals,
        )
    }

    /** 내장 시드 전체를 읽는다. 읽기 실패(자산 누락·JSON 파싱 오류) 시 빈 코스로 복구한다. */
    private fun loadSeed(): SeedContent {
        return try {
            seedRepository.load(appContext)
        } catch (t: Throwable) {
            notes["TripSession"] = "시드 로드 실패 — 빈 코스로 복구: ${t.javaClass.simpleName}"
            SeedContent(
                course = Course(id = "empty", title = "", places = emptyList()),
                places = emptyList(),
                thenNowDemo = null,
                offersDemo = emptyList(),
                draftQuizKeys = emptySet(),
                unverifiedCoordPlaceIds = emptySet(),
            )
        }
    }

    /**
     * 차량 신호(T5-1·T6-2) 선택: Pleos Vehicle 초기화 성공 → PleosTripSignalProvider,
     * 실패 → 폴백(수동 입력·null). 초기화가 성공해도 개별 신호는 안 읽히면 null 로 흐른다
     * (U11·U13 실측 전까지 값 유무는 에뮬레이터가 결정한다).
     */
    private fun selectTripSignals(): TripSignalProvider {
        return if (pleosTripSignals.isInitialized()) {
            notes["TripSignals"] = "PleosTripSignalProvider (Seat 점유 합산 + Odometer — U11·U13 미검증)"
            pleosTripSignals
        } else {
            val reason = pleosTripSignals.lastError ?: "Vehicle 초기화 실패"
            notes["TripSignals"] = "FallbackTripSignalProvider — Pleos 폴백: $reason"
            FallbackTripSignalProvider()
        }
    }

    private fun selectRouteService(): RouteService {
        val state = runBlocking {
            withTimeoutOrNull(INIT_WAIT_MS) {
                naviRoute.initState.first { it != "pending" }
            } ?: "pending"
        }
        return when (state) {
            "ok" -> {
                notes["RouteService"] = "PleosNaviRouteService (NaviHelper 3.0.5 초기화 성공)"
                naviRoute
            }
            else -> {
                val reason = if (state == "pending") {
                    "초기화 콜백 ${INIT_WAIT_MS}ms 미수신"
                } else {
                    "초기화 실패: ${naviRoute.initError.value ?: "원인 미제공"}"
                }
                notes["RouteService"] =
                    "FakeRouteService — NaviHelper $reason (U1/U2 는 naviRoute 직접 검증)"
                FakeRouteService()
            }
        }
    }

    private fun selectNarrator(appContext: Context): Narrator {
        val ready = runBlocking {
            withTimeoutOrNull(INIT_WAIT_MS) {
                pleosTts.readyState.first { it }
            } ?: false
        }
        return if (ready) {
            notes["Narrator"] = "PleosTtsNarrator (Mode.ON_DEVICE, onReady 확인)"
            pleosTts
        } else {
            val reason = pleosTts.initError.value ?: "onReady ${INIT_WAIT_MS}ms 미수신(U6 미검증)"
            notes["Narrator"] = "AndroidTtsNarrator — Pleos TTS 폴백: $reason"
            AndroidTtsNarrator(appContext)
        }
    }

    /**
     * 앱 종료 시 일괄 해제.
     * 규칙: SDK 콜백(리스너/진행 중인 speak·조회 콜백)이 끝나기 전에 release 하지 않는다(01문서).
     * 각 release 함수가 내부에서 "리스너 해제 → release" 순서를 지킨다.
     */
    fun closeAll() {
        tripSession.stop() // 세션 감시/수집을 먼저 끊어 release 후 콜백 유입을 막는다.
        fusedArrival.stop() // 진행 중 watch 중단
        fusedArrival.release() // 리스너 해제 후 FusedLocation.release
        naviRoute.release() // removeListener 후 release
        pleosTts.release() // removeEventListener 후 release
        pleosVehicle.release() // U5 콜백 완료 후 호출 전제
        pleosTripSignals.release() // T5-1·T6-2 조회 콜백은 tripSession.stop() 이 끝낸 뒤 해제한다.
        (voiceLetterPlayer as? PleosVoiceLetterPlayer)?.release() // 재생 중 MediaPlayer 정리.
        drivingState.stop()
        // visitStore 는 유지한다: 방문 기록은 차량에 남아야 할 데이터가 아니라 가족 기록의
        // 로컬 사본이므로 앱 종료로 지우지 않는다('로그아웃'은 T9-1 화면이 clear 한다).
        // visitQueue 도 유지한다(T8): 미전송 기록은 다음 실행에서 flush 로 재전송 기회를 얻는다.
    }

    private companion object {
        /** 비동기 초기화 콜백 대기 한도(포트당). 데모 안정성 우선 — 늦으면 보수적으로 폴백. */
        const val INIT_WAIT_MS = 3_000L   // [F35 실측] NaviHelper 초기화 콜백이 1.5초보다 늦게 와 Fake 로 선택된 사례 → 3초로 확대
    }
}

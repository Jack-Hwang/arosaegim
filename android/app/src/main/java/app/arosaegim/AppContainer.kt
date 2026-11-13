package app.arosaegim

import android.content.Context
import app.arosaegim.data.ApiClient
import app.arosaegim.data.SeedContent
import app.arosaegim.data.FamilySessionStore
import app.arosaegim.data.SeedRepository
import app.arosaegim.data.VisitQueue
import app.arosaegim.data.VisitStore
import app.arosaegim.domain.Course
import app.arosaegim.fallback.AndroidTtsNarrator
import app.arosaegim.fallback.AssetAudioNarrator
import app.arosaegim.fallback.FakeRouteService
import app.arosaegim.fallback.FallbackTripSignalProvider
import app.arosaegim.fallback.FallbackVoiceLetterPlayer
import app.arosaegim.fallback.ManualArrivalDetector
import app.arosaegim.fallback.ManualDrivingState
import app.arosaegim.fallback.ProfileVehicleInfo
import app.arosaegim.SwitchableRouteService
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
import app.arosaegim.session.FamilySessionCleaner
import app.arosaegim.session.SignOutFlow
import app.arosaegim.session.TripSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 수동 의존성 조립기. 포트별로 Pleos 구현 가능 여부를 판정해 선택하고, 선택 결과를
 * [adapterNotes] 에 기록한다(Diagnostics ① 섹션 표시 + DEV_LOG 대응).
 *
 * 생성 제약: 반드시 Application.onCreate(메인 스레드)에서 생성할 것.
 * - UxRestrictionsDrivingState.start() 는 Car.createCar 규약상 메인 스레드가 필요하고,
 * - Pleos SDK 초기화 호출도 메인 스레드를 전제한다.
 *
 * ⚠ 콜드 스타트 교착 수정(2026-11-11, P0): 예전에는 RouteService·Narrator 선택 때 runBlocking 으로
 * SDK 초기화 콜백을 최대 3초 기다렸다. 에뮬레이터(차량) 부팅 직후 콜드 스타트에서 그 대기가
 * 메인 스레드를 파킹시켜 시스템이 "failed to complete startup"으로 앱을 강제 종료했다
 * (dropbox data_app_anr 3건 이상 — release 빌드에서도 재현). **이제 Application.onCreate 는
 * 어떤 SDK 콜백도 블로킹 대기하지 않는다.** 모든 포트는 동기 판정 가능한 값만 보고 즉시 선택하고,
 * 초기화가 늦게 성공하면 일방향 업그레이드한다:
 *  - RouteService: pending 이면 [SwitchableRouteService](Fake 시작) + [NaviSwitchOverWatcher] (조치 8)
 *  - Narrator: onReady 미확인이면 [SwitchableNarrator](Android TTS 시작) — 준비되면 Pleos로 전환
 * 진단 표기는 거짓이 금지다 — 대기 중엔 '초기화 대기 중 → 성공 시 Pleos / 실패 시 대체'가
 * 실시간 갱신된다(routeSwitchNote·narratorSwitchNote).
 */
class AppContainer(private val appContext: Context) {

    private val notes = linkedMapOf<String, String>()

    /** 포트명 → 선택된 구현/사유(Diagnostics ① 섹션과 DEV_LOG 이 읽는다). */
    val adapterNotes: Map<String, String> get() = notes.toMap()

    // ── 내비 재전환(계획 문서 조치 8, DEV_LOG F35·F50·Q15) + 콜드 스타트 교착 수정 ──
    //
    // NaviHelper 초기화 콜백이 늦어도(또는 콜드 스타트라 아직 안 와도) 앱은 즉시 Fake 로 기동하고,
    // 늦은 성공(ok)을 감시해 Pleos 구현으로 일방향 전환한다. 되돌림(Pleos → Fake)은 없다.
    // 재전환 실패 시 '구현: 대체(Fake)' 표기는 그대로 유지한다(U1/U2 실측이 SDK 호출 성공을
    // 이미 검증했으므로 거짓 표기는 금지).

    /**
     * 업그레이드 감시 전용 스코프. 감시기들은 Main 에서 돈다(init 과 같은 스레드 — notes 갱신
     * 경합 없음). [closeAll] 에서 가장 먼저 취소해 SDK 해제 뒤의 전환 시도를 막는다.
     */
    private val switchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 주행 상태(A2 기어 보조 폴링) 전용 스코프. [CombinedDrivingState] 의 1차 상태 수집과
     * 기어 폴링이 여기서 돈다. [closeAll] 에서 취소해 release 뒤의 폴링·수집을 막는다.
     */
    private val drivingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 내비 재전환 상태 한 줄(진단 화면 ① 섹션이 구독해 실시간 표시).
     * null = 재전환 대상 아님(초기 동기 확인에서 Pleos 를 직접 선택).
     */
    private val _routeSwitchNote = MutableStateFlow<String?>(null)
    val routeSwitchNote: StateFlow<String?> = _routeSwitchNote.asStateFlow()

    /**
     * 안내 음성 전환 상태 한 줄(콜드 스타트 수정 — 진단 화면 ① 섹션 표시).
     * null = 전환 대상 아님(초기 동기 확인에서 Pleos 를 직접 선택).
     */
    private val _narratorSwitchNote = MutableStateFlow<String?>(null)
    val narratorSwitchNote: StateFlow<String?> = _narratorSwitchNote.asStateFlow()

    /**
     * A1(2026-11-12, 00문서 16절): Narrator 사슬에서 사전 제작 오디오가 '기본 경로'임을
     * 알리는 한 줄 — 진단 화면 U6 '구현:' 줄이 표시한다. null = 사슬에 오디오 단이 없다
     * (매핑된 오디오 파일이 없다 — 현재 저장소 상태(Q14), 오디오 파일은 발주자 준비다).
     */
    var narratorAssetNote: String? = null
        private set

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

    // 차량 세션(T9-1): 가족 코드·PIN(휘발성). 진단 화면과 SharedPreferences 로 상태를 공유한다.
    val familySessionStore = FamilySessionStore(appContext)

    // 백엔드 클라이언트(T8). 서버 없는 환경이 기본 — 모든 호출 실패는 Result.failure 로 흡수된다.
    val apiClient = ApiClient()

    // 오프라인 전송 큐(T8). closeAll 에서 지우지 않는다 — 미전송 기록은 다음 실행의 flush 가 맡는다.
    // PIN 은 세션 메모리(00문서 10.6)에서 전송 순간에만 읽는다 — 저장·로그 없음.
    val visitQueue = VisitQueue(appContext, apiClient, pinProvider = { familySessionStore.pinOrNull() })

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

        // 1) 주행 상태: 표준 UxRestrictions(DO, OFFICIAL) 시도 → start 실패 시 수동 모드.
        //    A2(2026-11-11): DO 수신 하나뿐이던 기존 구조에 기어 보조 폴링을 얹은
        //    [CombinedDrivingState] 로 감싼다. 규칙은 기존대로 — 두 신호가 어긋나면 더 제한적인
        //    주행 우선(00문서 F42·진단 ⑨). 기어 경로는 U5 실측(PASS, GEAR_PARK 수신)으로 확인된
        //    PleosVehicleInfoProvider.readGearStateOnly 를 재사용한다(표시 전용·저장 금지 규칙 동일).
        //    D→P 전환 때 DO 해제 콜백이 늦거나 없어도 기어가 정차로 병합 판정해
        //    ModeRouter 가 ParkedActivity 로 자동 복귀시킬 수 있다.
        val ux = UxRestrictionsDrivingState(appContext)
        ux.start() // 메인 스레드 전제 — 본 클래스 생성 규약 참조.
        drivingState = if (ux.startError == null) {
            val combined = CombinedDrivingState(
                primary = ux,
                gearQuery = { onResult -> pleosVehicle.readGearStateOnly(onResult) },
                scope = drivingScope,
            )
            combined.start() // 1차 수집 + 기어 폴링 시작(closeAll 의 drivingScope.cancel 이 정리).
            notes["DrivingState"] =
                "CombinedDrivingState — 1차 UxRestrictions(DO, OFFICIAL) + 기어 보조 2초 폴링(어긋나면 주행 우선)"
            combined
        } else {
            notes["DrivingState"] =
                "ManualDrivingState — UxRestrictions 시작 실패: ${ux.startError ?: "원인 미제공"}"
            manualDriving
        }

        // 2) 경로: 콜드 스타트 P0 수정 — runBlocking 대기 없이 동기 상태만 보고 즉시 선택한다.
        //    pending(콜백 미수신)이어도 앱은 Fake 로 즉시 기동하고, 늦은 초기화 성공 시 Pleos 로
        //    일방향 재전환한다(조치 8 구조 — prepareLateSwitchOver 참조).
        routeService = selectRouteService()

        // 3) 안내 음성: A1(2026-11-12, 00문서 16절) — 사전 제작 오디오가 기본 경로고 음성
        //    합성은 초기화가 성공했을 때만 쓴다. 콜드 스타트 P0 수정(블로킹 대기 없음) 규약은
        //    그대로 유지한다. 시드 장소로 문장→파일 매핑을 만들므로 시드를 먼저 읽는다.
        val seed = loadSeed()
        narrator = selectNarrator(seed)

        // 4) 차량 정보: VehicleInfoProvider 는 ProfileVehicleInfo 고정이다. Pleos 선택 로직은 없다 —
        //    01문서 10절: CarInfo 는 VIN/CRN 조회만 제공(AAR javap 확인), VIN 은 규정상 읽지 않는다.
        //    pleosVehicle 프로퍼티는 진단 화면 U5·U12 용으로만 남겨 둔다(선택 대상 아님).
        vehicleInfo = ProfileVehicleInfo(model = "가상 차량(수동 프로필)", modelYear = 2025)
        notes["VehicleInfo"] =
            "ProfileVehicleInfo — CarInfo 는 VIN/CRN뿐(01문서 10절), VIN 은 규정상 미사용"

        // 5) 시드: 코스와 T5-1·T6-2·T9-2 데이터(heritage·voice_letter_demo·seat_roles)를 함께 읽는다.
        //    E4/A1로 순서 변경 — 안내 음성 사슬(사전 제작 오디오 기본 경로)의 문장→파일 매핑에
        //    시드 장소가 필요해 위 3) 앞으로 옮겼다. 실패 시 빈 코스로 보수적으로 복구한다(00문서 2-3 §16).
        // 6) 차량 신호(T5-1·T6-2): Pleos Vehicle 초기화 성공(동기 확인) 시 Pleos, 실패 시 폴백(수동 입력·null).
        //    콜드 스타트에서 initialize 가 실패해도(차량 서비스 미기동) 폴백으로 즉시 기동한다 —
        //    신호는 안 읽히면 null 이고 화면 문장이 숨겨질 뿐 흐름은 계속 된다(가짜 값 금지).
        tripSignals = selectTripSignals()

        // 7) 예시 음성 편지(T9-2): A1(2026-11-11) — 사전 제작 오디오가 기본 경로다.
        //    assets 에 오디오 파일(시드 audioAsset 또는 사전 제작 voice/letter_demo.wav)이
        //    있어야 재생 가능이고, 없으면 조용히 사용 불가 처리한다(텍스트 편지 표시).
        //    진단 화면 ① 행은 파일 유무 기반으로 '구현: 사전 제작 오디오'를 표시한다.
        //    Pleos TTS 합성은 재생 경로에 쓰지 않는다 — Narrator(도착 안내)만 onReady 확인 후
        //    fallback 으로 쓴다(고인 목소리 합성 금지 원칙과 무관한 데모용 일반 음성이다).
        voiceLetterPlayer = if (seed.voiceLetters.isNotEmpty()) {
            val pleosPlayer = PleosVoiceLetterPlayer(appContext, seed.voiceLetters)
            if (pleosPlayer.isAvailable) {
                notes["VoiceLetter"] =
                    "구현: 사전 제작 오디오(assets) — MediaPlayer 재생 가능(합성 음성 아님)"
                pleosPlayer
            } else {
                notes["VoiceLetter"] =
                    "구현: 사전 제작 오디오 없음 — 재생 생략(텍스트 편지 표시)"
                FallbackVoiceLetterPlayer()
            }
        } else {
            notes["VoiceLetter"] =
                "구현: 사전 제작 오디오 없음 — 재생 생략(시드에 voice_letter_demo 없음)"
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
            // T9-3·E1: 도착 전송 시 세션 PIN(휘발성) 헤더용.
            pinProvider = { familySessionStore.pinOrNull() },
            // E1 전체 경로: 도착 전송 시 세션 가족 코드 헤더용(X-Family-Code) — 큐 아이템에
            // 코드가 실려야 서버가 가족 기록으로 저장한다(2026-11-14 실측: null 이면 401
            // family_code_required 로 큐에만 남는다).
            familyCodeProvider = { familySessionStore.familyCode.value },
            // A7: 늦은 초기화로 Fake→Pleos 전환되면 진행 중 목표의 경로를 다시 요청한다.
            routeBecameReady = (routeService as? SwitchableRouteService)?.switched,
        )
    }

    /**
     * '이 차에서 로그아웃' 정리 흐름(T1, 2026-11-14) — 진단 화면(DiagnosticsViewModel.signOutFromCar)
     * 가 이 흐름 한 번으로 가족 세션(코드·PIN)·로컬 파일·여행 세션(스탬프·진행)·퀴즈·코스 시작·
     * 큐 메모리를 고정된 순서로 지운다. 결과 줄은 진단 화면 세션 결과 목록에 그대로 표시된다.
     * tripSession·visitQueue 가 위 init 에서 만들어진 뒤에 초기화되도록 init 블록 뒤에 둔다
     * (Kotlin 프로퍼티·init 블록은 선언 순서로 초기화된다).
     * [FamilySessionCleaner] 는 FamilySessionStore 로의 좁은 위임 — 동작 변화는 없고, JVM 단위
     * 시험(SignOutFlowTest)을 위한 기존 분리 규약(FamilyCodeValidator·SessionCredentials 와 같은 취지)이다.
     */
    val signOutFlow = SignOutFlow(
        familyStore = object : FamilySessionCleaner {
            override fun hasSession(): Boolean = familySessionStore.hasSession()
            override fun signOut() = familySessionStore.signOut()
        },
        filesDir = appContext.filesDir,
        tripSession = tripSession,
        visitQueue = visitQueue,
    )

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
     * 차량 신호(T5-1·T6-2) 선택: Pleos Vehicle 초기화 성공(동기 판정 — 대기 없음) →
     * PleosTripSignalProvider, 실패 → 폴백(수동 입력·null). 초기화가 성공해도 개별 신호는
     * 안 읽히면 null 로 흐른다(U11·U13 실측 전까지 값 유무는 에뮬레이터가 결정한다).
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

    /**
     * 경로 선택(콜드 스타트 P0 수정): runBlocking 대기를 완전히 제거했다.
     * 현재 동기 상태만 보고 즉시 선택한다 — 초기화 콜백은 백그라운드 감시([NaviSwitchOverWatcher])가
     * 뒤따라 처리하고, 늦은 성공 시 Fake → Pleos 로 일방향 재전환한다.
     */
    private fun selectRouteService(): RouteService {
        return when (naviRoute.initState.value) {
            "ok" -> {
                notes["RouteService"] = "PleosNaviRouteService (NaviHelper 3.0.5 초기화 성공 — 동기 확인)"
                _routeSwitchNote.value = null // 재전환 대상 아님.
                naviRoute
            }
            "failed" -> {
                notes["RouteService"] =
                    "FakeRouteService — NaviHelper 초기화 실패: ${naviRoute.initError.value ?: "원인 미제공"} (U1/U2 는 naviRoute 직접 검증)"
                _routeSwitchNote.value = "초기화 실패 확정 — Fake 유지(재전환 없음)"
                FakeRouteService()
            }
            else -> {
                // 콜드 스타트: 콜백이 아직 안 왔다(또는 영영 안 올 수도 있다). 앱은 Fake 로 즉시
                // 기동하고 감시기가 늦은 성공을 지켜본다(대기 한도 없음 — 앱 수명과 같다).
                notes["RouteService"] =
                    "SwitchableRouteService(현재: FakeRouteService) — NaviHelper 초기화 대기 중(블로킹 없음), 성공 시 Pleos로 재전환 (U1/U2 는 naviRoute 직접 검증)"
                prepareLateSwitchOver()
            }
        }
    }

    /**
     * 조치 8 재전환 준비: Fake 로 선택을 확정하되, 이후에도 NaviHelper 초기화 콜백을 계속 감시해
     * 늦게라도 ok 면 [SwitchableRouteService] 가 Pleos 구현으로 갈아끼운다.
     *  - 일방향(Fake → Pleos 만). 재전환 사실은 [routeSwitchNote] 로 진단 화면에 표시된다.
     *  - failed 로 확정되면 전환 없이 Fake 유지 — '구현: 대체' 표기는 솔직하게 남는다.
     * 감시는 [switchScope](Main) 에서 돈다. initState 는 SDK 콜백 스레드에서 갱신되지만 StateFlow
     * 이므로 관찰은 안전하고, notes 갱신도 Main 에서 이뤄져 init 쓰기와 같은 스레드다.
     */
    private fun prepareLateSwitchOver(): RouteService {
        _routeSwitchNote.value = "초기화 대기 중 — 성공 시 Pleos 사용 / 실패 시 대체 유지"
        val switchable = SwitchableRouteService(FakeRouteService(), switchScope)
        NaviSwitchOverWatcher(
            initState = naviRoute.initState,
            switchable = switchable,
            real = naviRoute,
            scope = switchScope,
            onSwitched = {
                notes["RouteService"] =
                    "PleosNaviRouteService — 지연 초기화 성공, Fake에서 일방향 재전환(조치 8)"
                _routeSwitchNote.value = "내비 재전환: 늦은 초기화 성공 → Pleos 사용"
            },
            onInitFailed = {
                _routeSwitchNote.value = "초기화 실패 확정 — Fake 유지(재전환 없음)"
            },
        ).start()
        return switchable
    }

    /**
     * 안내 음성 선택(A1, 2026-11-12 — 00문서 16절 "음성은 사전 제작 오디오를 기본 경로로,
     * 합성은 초기화가 성공했을 때만"): runBlocking(onReady 대기)은 없다(콜드 스타트 P0 규약 유지).
     *  - 1단(기본): 사전 제작 오디오([AssetAudioNarrator]) — 매핑된 오디오 파일이 하나라도 있을
     *    때만 사슬의 기본 경로가 된다. 매핑이 없어 재생에 실패하면 다음 단계로 넘어간다.
     *    파일이 없으면(현재 저장소 상태, Q14 — 파일은 발주자 준비) 1단 없음 = 기존 구조 그대로라
     *    동작·테스트가 변하지 않는다.
     *  - 2단: Pleos 합성([PleosTtsNarrator]) — onReady(초기화)가 확인됐을 때만 쓰인다.
     *  - 3단(최후 폴백): Android 기본 TTS([AndroidTtsNarrator]). onReady 미확인(콜드 스타트)이면
     *    [SwitchableNarrator] 로 Android TTS 에서 시작해 늦은 onReady 에 Pleos 로 일방향 전환한다.
     * 각 단계 사유는 adapterNotes 의 "Narrator", [narratorAssetNote], [narratorSwitchNote] 로
     * 진단 화면에 표시한다.
     */
    private fun selectNarrator(seed: SeedContent): Narrator {
        // 1단(A1): 시드 장소 기준 문장 → 파일 매핑. 파일이 하나라도 있을 때만 기본 경로가 된다.
        val assetNarrator = AssetAudioNarrator(appContext, buildAssetSentenceMap(seed))
            .takeIf { it.isAvailable }
        narratorAssetNote = assetNarrator?.let {
            "${AssetAudioNarrator.DISPLAY_NAME} — 기본 경로(파일 준비 후). 매핑 없는 문장은 음성 합성으로 진행"
        }

        // 2·3단: 합성 사슬 — Pleos(onReady 확인 후) → Android TTS(최후 폴백).
        val synth: Narrator = if (pleosTts.readyState.value) {
            notes["Narrator"] = if (assetNarrator != null) {
                "사전 제작 오디오 기본(A1) → PleosTtsNarrator (Mode.ON_DEVICE, onReady 확인 — 동기 확인)"
            } else {
                "PleosTtsNarrator (Mode.ON_DEVICE, onReady 확인 — 동기 확인)"
            }
            _narratorSwitchNote.value = null // 전환 대상 아님.
            pleosTts
        } else {
            val switchable = SwitchableNarrator(
                fallback = AndroidTtsNarrator(appContext),
                real = pleosTts,
                readyState = pleosTts.readyState,
                scope = switchScope,
                onUpgraded = {
                    notes["Narrator"] =
                        "합성 단계 전환: PleosTtsNarrator — onReady 늦은 확인, Android TTS에서 일방향 전환(합성은 onReady 확인 후에만 사용)"
                    _narratorSwitchNote.value = "음성 전환: onReady 늦은 성공 → Pleos 사용"
                },
            )
            notes["Narrator"] = if (assetNarrator != null) {
                "사전 제작 오디오 기본(A1) → SwitchableNarrator(현재: AndroidTtsNarrator) — Pleos onReady 대기 중(블로킹 없음)"
            } else {
                "SwitchableNarrator(현재: AndroidTtsNarrator) — Pleos TTS onReady 대기 중(블로킹 없음), 확인 시 Pleos로 전환"
            }
            _narratorSwitchNote.value = "초기화 대기 중 — onReady 성공 시 Pleos 사용 / 실패 시 대체 유지"
            switchable
        }

        // 사슬 조립(A1): 매핑된 문장은 오디오로 먼저 재생하고, 실패(매핑 없음·파일 없음)면
        // 합성 사슬로 넘어간다. 오디오 파일이 없으면 1단 없음 — 반환값이 기존과 완전히 같다.
        return if (assetNarrator != null) {
            AssetAudioFirstNarrator(assetNarrator, synth) { line ->
                notes["Narrator"] = "Narrator 사슬(A1) — $line"
                _narratorSwitchNote.value = line
            }
        } else {
            synth
        }
    }

    /**
     * 시드 장소 → 사전 제작 오디오 파일 매핑(E4). 도착 문장("{장소명}에 도착했어요. 스탬프를
     * 찍었어요.")은 TripSession.arrivalLine 과 같은 형식이며, '다시 듣기'(place.narration)도 같은
     * 규약의 narration_<장소id> 파일로 매핑한다. 파일명 규약은 assets/audio/README.md 를 따르고,
     * 실제 파일 존재 여부는 AssetAudioNarrator 가 확인한다(없는 파일은 failure).
     */
    private fun buildAssetSentenceMap(seed: SeedContent): Map<String, String> = buildMap {
        seed.course.places.forEach { place ->
            put("${place.name}에 도착했어요. 스탬프를 찍었어요.", "audio/arrival_${place.id}.ogg")
            if (place.narration.isNotBlank()) {
                put(place.narration, "audio/narration_${place.id}.ogg")
            }
        }
    }

    /**
     * 앱 종료 시 일괄 해제.
     * 규칙: SDK 콜백(리스너/진행 중인 speak·조회 콜백)이 끝나기 전에 release 하지 않는다(01문서).
     * 각 release 함수가 내부에서 "리스너 해제 → release" 순서를 지킨다.
     */
    fun closeAll() {
        switchScope.cancel() // 업그레이드 감시(내비 재전환·음성 전환)를 가장 먼저 끊는다 — SDK 해제 뒤 전환 시도 방지.
        drivingScope.cancel() // A2 기어 폴링·1차 수집을 끊는다 — release 뒤 조회 콜백 유입 방지.
        tripSession.stop() // 세션 감시/수집을 먼저 끊어 release 후 콜백 유입을 막는다.
        fusedArrival.stop() // 진행 중 watch 중단
        fusedArrival.release() // 리스너 해제 후 FusedLocation.release
        naviRoute.release() // removeListener 후 release
        pleosTts.release() // removeEventListener 후 release
        pleosVehicle.release() // U5 콜백 완료 후 호출 전제
        pleosTripSignals.release() // T5-1·T6-2 조회 콜백은 tripSession.stop() 이 끝낸 뒤 해제한다.
        (voiceLetterPlayer as? PleosVoiceLetterPlayer)?.release() // 재생 중 MediaPlayer 정리.
        narrator.stop() // 진행 중 speak(SwitchableNarrator면 활성 위임자)를 끊는다.
        drivingState.stop()
        // visitStore 는 유지한다: 방문 기록은 차량에 남아야 할 데이터가 아니라 가족 기록의
        // 로컬 사본이므로 앱 종료로 지우지 않는다('로그아웃'은 T9-1 화면이 clear 한다).
        // visitQueue 도 유지한다(T8): 미전송 기록은 다음 실행에서 flush 로 재전송 기회를 얻는다.
    }
}

/**
 * Narrator 사슬의 기본 경로 래퍼(A1, 2026-11-12 — 00문서 16절 "사전 제작 오디오 재생을 기본
 * 경로로 넣는다. 음성 합성은 초기화가 성공했을 때만 쓴다"): speak 마다 사전 제작 오디오
 * ([asset])를 먼저 시도하고, 매핑된 파일이 없어 실패하면 음성 합성 사슬([synth])로 넘어간다.
 * E4 의 AssetFallbackNarrator(합성 먼저 → 오디오 재시도)의 순서를 뒤집은 것이다.
 *
 * [synth] 는 Pleos(onReady 확인 후) → Android TTS 순서의 합성 사슬이거나 그 자체다 — 초기화가
 * 실패한 합성은 speak 실패로 조용히 흡수된다(기존 폴백 규약 동일, 주행 흐름을 막지 않는다).
 * 오디오 파일이 없는 현재(Q14)에는 이 래퍼가 아예 끼이지 않아 동작이 기존과 같다.
 */
internal class AssetAudioFirstNarrator(
    private val asset: Narrator,
    /** 뒤 단계(합성 사슬). 진단 화면 표시(DiagnosticsViewModel)가 읽어 그대로 보여 준다. */
    val synth: Narrator,
    private val onChainNote: (String) -> Unit = {},
) : Narrator {

    /** 활성 사실만 답한다 — 오디오·합성이 모두 못 쓰면 '못 쓴다'로 답한다(거짓 표기 금지). */
    override val isAvailable: Boolean
        get() = asset.isAvailable || synth.isAvailable

    override suspend fun speak(text: String): Result<Unit> {
        // 1단(기본): 사전 제작 오디오. 매핑된 파일 재생이 성공하면 합성은 쓰지 않는다.
        val audio = asset.speak(text)
        if (audio.isSuccess) return audio
        // 2단 이하: 음성 합성 사슬 — 오디오 매핑이 없어도(파일 미준비 Q14) 여기로 흐른다.
        val spoken = synth.speak(text)
        onChainNote(
            if (spoken.isSuccess) "${AssetAudioNarrator.DISPLAY_NAME} 매핑 없음 → 음성 합성 재생"
            else "${AssetAudioNarrator.DISPLAY_NAME} 매핑 없음 · 음성 합성도 실패(초기화 미완료)",
        )
        return spoken
    }

    override fun stop() {
        asset.stop()
        synth.stop()
    }
}

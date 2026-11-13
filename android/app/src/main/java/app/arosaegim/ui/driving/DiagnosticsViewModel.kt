package app.arosaegim.ui.driving

import android.content.Context
import androidx.lifecycle.ViewModel
import app.arosaegim.AppContainer
import app.arosaegim.AssetAudioFirstNarrator
import app.arosaegim.R
import app.arosaegim.SwitchableNarrator
import app.arosaegim.SwitchableRouteService
import app.arosaegim.data.FamilySessionStore
import app.arosaegim.data.SeedRepository
import app.arosaegim.data.SignOutCleanup
import app.arosaegim.domain.DrivingState
import app.arosaegim.domain.Place
import app.arosaegim.fallback.AssetAudioNarrator
import app.arosaegim.fallback.ManualDrivingState
import app.arosaegim.pleos.FuelBatteryProbe
import app.arosaegim.pleos.PleosTripSignalProvider
import app.arosaegim.ports.Narrator
import app.arosaegim.ui.ModeRouter
import app.arosaegim.ui.parked.DiagnosticsMetrics
import app.arosaegim.ui.parked.HeirQuestionMemory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 진단 화면 상태 관리.
 * - U1~U14 실행/결과 목록, 어댑터 선택 요약(구현/검증 분리), 수동 제어, 마지막 오류,
 *   기어 상태(Vehicle·Gear), T12+ 점수 지표를 노출한다.
 * - viewModelScope 대신 자체 스코프를 쓴다(onCleared 에서 취소). lifecycle-viewmodel-ktx
 *   확장 의존을 피하기 위한 선택이며 동작은 동일하다.
 * - 관찰형 테스트(U3/U7/U10)는 실행 중 재클릭하면 취소되어 PASS 로 마무리된다.
 * - U12(문)·U14(배터리)는 실행하지 않는다 — '참고 — 사용 안 함' 고정 표시만 둔다(00문서 11-2).
 */
class DiagnosticsViewModel(
    private val container: AppContainer,
    private val modeRouter: ModeRouter,
    appContext: Context,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * U15·U16(연료·EV 배터리 지원 실측, 07문서 3절 D)용 프로브 — 공용 인스턴스. Vehicle 서비스
     * 연결은 첫 U15·U16 클릭 때 이뤄진다(진단 화면 진입 즉시 바인딩하지 않음). D1·D2 가 앱 공용
     * Vehicle(AppContainer.pleosVehicle)을 재사용하는 것과 달리, 이 프로브는 Vehicle 을 직접
     * 초기화해 SDK 원 호출 경로를 그대로 실측한다(값은 양쪽 모두 읽지 않는다).
     */
    private val fuelBatteryProbe = FuelBatteryProbe.shared(appContext)

    private val _results = MutableStateFlow(initialResults(appContext))
    val results: StateFlow<List<SmokeResult>> = _results.asStateFlow()

    /** 포트명 → 검증 근거가 되는 스모크 테스트 id(01문서 8절 기준 수동 매핑). */
    private val verifyTestByPort: Map<String, String> = mapOf(
        "DrivingState" to "U7",
        "RouteService" to "U1",
        "Narrator" to "U6",
        "VehicleInfo" to "U5",
        "TripSession" to "U10",
    )

    /**
     * ① 어댑터 선택 한 줄(04문서 시안 11 — 항목마다 '구현'/'검증' 꼬리표 2개).
     * 기존 adapterNotes(한 줄에 구현·검증이 섞인 문자열)를 구현/검증으로 나눠 노출한다.
     * 검증 상태는 [AdapterRow.verifyTestId] 가 가리키는 스모크 결과로 화면이 계산한다
     * (status==PASS → '확인함', 그 외 → '실측 전').
     */
    data class AdapterRow(val port: String, val impl: String, val verifyTestId: String?)

    /** ① 섹션 렌더용: 포트별 구현 노트 + 검증 근거 테스트 id 매핑. 생성 시점 스냅샷. */
    val adapterRows: List<AdapterRow> = container.adapterNotes.entries.map { (port, note) ->
        AdapterRow(port, note, verifyTestByPort[port])
    }

    /**
     * 내비 재전환 상태(조치 8) — AppContainer 가 갱신하는 StateFlow 를 그대로 노출한다.
     * 늦은 NaviHelper 초기화 성공 시 Fake → Pleos 재전환 사실을 화면이 실시간으로 보여 준다.
     * null = 재전환 대상 아님(초기 동기 확인에서 Pleos 를 직접 선택).
     */
    val routeSwitchNote: StateFlow<String?> = container.routeSwitchNote

    /**
     * 안내 음성 전환 상태(콜드 스타트 교착 수정, 2026-11-11) — Android TTS에서 시작해 Pleos
     * onReady 지연 확인 시 일방향 전환하는 사실을 실시간으로 보여 준다.
     * null = 전환 대상 아님(초기 동기 확인에서 Pleos 를 직접 선택).
     */
    val narratorSwitchNote: StateFlow<String?> = container.narratorSwitchNote

    /** ③ 스모크 카드의 '구현:' 줄 텍스트(id → 선택된 어댑터/구현 한 줄). 생성 시점 스냅샷. */
    val smokeImplNotes: Map<String, String> = mapOf(
        "U1" to u1ImplLine(),
        "U2" to "${container.naviRoute.javaClass.simpleName} (NaviHelper 어댑터 직접)",
        // A3: 관찰은 startObservation 전용 수명주기 — 중단해도 여정 감시(도착 판정)는 유지된다.
        "U3" to "FusedArrivalDetector.startObservation (진단 관찰 — 여정 감시와 분리)",
        "U4" to "${container.fusedArrival.javaClass.simpleName} (FusedLocation 지원 확인)",
        "U5" to "PleosVehicleInfoProvider (Vehicle capability·기어 조회 전용)",
        "U6" to u6ImplLine(),
        "U7" to "${container.drivingState.javaClass.simpleName} (DrivingStateProvider)",
        "U8" to "ApiClient (java.net HttpURLConnection)",
        "U9" to "ModeRouter (FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_REORDER_TO_FRONT)",
        "U10" to "${container.naviRoute.javaClass.simpleName} (내비 도착 이벤트 관찰)",
        // U11·U13: TripSession 이 받아 둔 TripSignalProvider 를 경유해 조회한다(고정 API).
        "U11" to "TripSignalProvider.partySize() (TripSession.tripSignals 경유)",
        "U12" to "사용 안 함(로드맵 — 조회·수신 실행 없음)",
        "U13" to "TripSignalProvider.odometerKm() (TripSession.tripSignals 경유)",
        "U14" to "사용 안 함(로드맵 — 조회·수신 실행 없음)",
        // D 실측 전용(2026-11-11): capability 확인만 하고 값은 읽지 않는다. 화면 문장 생성 등
        // 기능 구현은 보류 범위다 — 성공/실패 기록·표시만 한다.
        "D1" to "PleosVehicleInfoProvider.checkFuelCapabilityOnly (capability 확인만·값 미읽음)",
        "D2" to "PleosVehicleInfoProvider.checkEvBatteryCapabilityOnly (capability 확인만·값 미읽음)",
        // U15·U16(2026-11-12, 07문서 3절 D): FuelBatteryProbe 가 Vehicle 을 직접 초기화해 SDK 원
        // 호출 경로를 실측한다 — D1·D2(앱 공용 Vehicle 재사용)와 판정 기준은 같다(값 미읽음).
        "U15" to "FuelBatteryProbe (Vehicle.getFuel 직접 — Pleos/값 미읽음)",
        "U16" to "FuelBatteryProbe (Vehicle.getEvBattery 직접 — Pleos/값 미읽음)",
    )

    /** 현재 주행 상태 제공자 이름(수동 토글이 실제 반영되는지 확인용). */
    val drivingProviderName: String = container.drivingState.javaClass.simpleName

    /**
     * U1 카드의 '구현:' 줄. 재전환 래퍼(SwitchableRouteService)가 선택된 경우엔 현재 위임자까지
     * 붙여 거짓 표기를 막는다(예: "SwitchableRouteService(현재: FakeRouteService)").
     */
    private fun u1ImplLine(): String {
        val service = container.routeService
        return if (service is SwitchableRouteService) {
            "${service.javaClass.simpleName}(현재: ${service.activeName.value}) (RouteService)"
        } else {
            "${service.javaClass.simpleName} (RouteService)"
        }
    }

    /**
     * U6 카드의 '구현:' 줄. 콜드 스타트 교착 수정(2026-11-11)으로 Narrator 는 SwitchableNarrator
     * (Android TTS 시작 → Pleos onReady 확인 시 일방향 전환)일 수 있다 — 현재 활성 위임자를
     * 함께 표시해 거짓 표기를 막는다. A1(2026-11-12): 사전 제작 오디오 기본 사슬
     * (AssetAudioFirstNarrator)이면 '사전 제작 오디오(파일 준비 후 기본)'를 앞세우고 뒤 단계
     * (합성 사슬)를 이어 표시한다(AppContainer.narratorAssetNote).
     */
    private fun u6ImplLine(): String {
        val narrator = container.narrator
        // A1: 사전 제작 오디오가 기본 경로로 끼워진 사슬 — 오디오 단을 앞세워 표시한다.
        if (narrator is AssetAudioFirstNarrator) {
            val note = container.narratorAssetNote
                ?: "${AssetAudioNarrator.DISPLAY_NAME} — 기본 경로(파일 준비 후)"
            return "$note → 합성 대체: ${narratorLine(narrator.synth)}"
        }
        return narratorLine(narrator) + (container.narratorAssetNote?.let { " + $it" } ?: "")
    }

    /** Narrator 한 줄 표기 — SwitchableNarrator 면 현재 활성 위임자까지 붙인다(거짓 표기 금지). */
    private fun narratorLine(narrator: Narrator): String =
        if (narrator is SwitchableNarrator) {
            "${narrator.javaClass.simpleName}(현재: ${narrator.activeName.value}) (Narrator)"
        } else {
            "${narrator.javaClass.simpleName} (Narrator)"
        }

    /** 목표 장소 후보 — 시드 3곳(gj-museum 등). 시드 로드 실패 시 내장 최소 후보로 대체. */
    val places: List<Place>

    private val _selectedPlaceId = MutableStateFlow("")
    val selectedPlaceId: StateFlow<String> = _selectedPlaceId.asStateFlow()

    private val _manualLog = MutableStateFlow<List<String>>(emptyList())
    val manualLog: StateFlow<List<String>> = _manualLog.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * '기어 상태(Vehicle·Gear)' 행(01문서 10절 — 차종·연식 행 대체). PleosVehicleInfoProvider
     * 에 readGearStateOnly 가 있으므로 그 조회 결과를 표시 전용으로만 둔다(저장 금지).
     * null = 아직 조회 안 함('실측 전').
     */
    private val _gearState = MutableStateFlow<String?>(null)
    val gearState: StateFlow<String?> = _gearState.asStateFlow()

    /** T12+ 점수 지표(로컬 카운터). 화면 진입 시 refreshMetrics() 로 다시 읽는다. */
    private val _metrics = MutableStateFlow<Map<String, Int>>(emptyMap())
    val metrics: StateFlow<Map<String, Int>> = _metrics.asStateFlow()

    // ── T12+ 지표 서버 값(2026-11-11) ──

    /**
     * 지표 키 → 값 출처. "server"(서버 조회 성공해 값 교체) 또는 "local"·없음(로컬 카운터).
     * 출처는 ⑦ 섹션에서 화면 문자열('서버'/'로컬 카운터')로 바뀌어 표시된다.
     */
    private val _metricSources = MutableStateFlow<Map<String, String>>(emptyMap())
    val metricSources: StateFlow<Map<String, String>> = _metricSources.asStateFlow()

    /** 지표 키 값 출처: 서버(성공 시). 로컬은 기본값이라 별도 표시하지 않는다. */
    private val SOURCE_SERVER = "server"

    /** ⑦ 지표 섹션 상태 한 줄(세션 없음·가져오기 실패 안내). null 이면 줄을 숨긴다. */
    private val _metricsNote = MutableStateFlow<String?>(null)
    val metricsNote: StateFlow<String?> = _metricsNote.asStateFlow()

    /**
     * 서버 조회 오류 1줄(정차 화면 T9-3 이름표 조회가 SharedPreferences 채널로 남긴 것 포함 —
     * ④ '마지막 오류'에 함께 표시한다). refreshMetrics() 때 다시 읽는다.
     */
    private val _serverLastError = MutableStateFlow<String?>(null)
    val serverLastError: StateFlow<String?> = _serverLastError.asStateFlow()

    /** 실행 중인(관찰형) 테스트 job — 재클릭 시 취소한다. */
    private val runningJobs = mutableMapOf<String, Job>()

    // --- 차량 세션(T9-1) ---
    // FamilySessionStore 는 AppContainer 밖에서 직접 만든다(AppContainer 는 병렬 수정 중이라
    // 수정 금지 우회). SharedPreferences("family_session") 를 공유하므로 StartActivity 가 만든
    // 인스턴스와 저장 상태는 일치한다(FamilySessionStore 주석 참조).
    private val appCtx = appContext.applicationContext
    private val sessionStore = FamilySessionStore(appCtx)

    /** 세션 상태 한 줄(코드 있음/없음·게스트). 코드 값을 그대로 노출하지 않고 존재만 표시한다. */
    private val _sessionStatus = MutableStateFlow("")
    val sessionStatus: StateFlow<String> = _sessionStatus.asStateFlow()

    /** '이 차에서 로그아웃' 결과 한 줄 목록(파일별 삭제 결과). */
    private val _sessionResult = MutableStateFlow<List<String>>(emptyList())
    val sessionResult: StateFlow<List<String>> = _sessionResult.asStateFlow()

    init {
        val loaded = try {
            SeedRepository().load(appContext).places
        } catch (t: Throwable) {
            emptyList()
        }
        places = if (loaded.isNotEmpty()) {
            loaded
        } else {
            // 시드 로드 실패 시에도 진단은 계속 — 내장 1곳(화면 표시 전용. 저장·전송 없음).
            listOf(
                Place(
                    id = "gj-museum", name = "국립경주박물관", address = "경북 경주시 일정로 186",
                    lat = 35.828983, lon = 129.228361, arriveRadiusM = 120,
                    narration = "", quiz = emptyList(),
                ),
            )
        }
        _selectedPlaceId.value = places.firstOrNull()?.id ?: ""
        refreshSessionState()
        refreshMetrics()
    }

    fun selectPlace(id: String) {
        _selectedPlaceId.value = id
    }

    private fun selectedPlace(): Place? = places.firstOrNull { it.id == _selectedPlaceId.value }

    /** 수동 모드 토글 — 선택된 제공자가 Manual 일 때만 실제 상태가 바뀐다(U7 이 그 사실을 표시). */
    fun setManualDriving(driving: Boolean) {
        val state = if (driving) DrivingState.DRIVING else DrivingState.PARKED
        container.manualDriving.set(state)
        addManualLog(
            "수동 모드 → $state (적용 제공자: $drivingProviderName" +
                if (container.drivingState is ManualDrivingState) ")" else " — Manual 아님, 미적용)",
        )
    }

    /** 수동 도착 버튼 — ManualArrivalDetector 로 source="manual" 도착 이벤트를 발행한다. */
    fun manualArrive() {
        val place = selectedPlace() ?: return
        container.manualArrival.arriveNow(place.id)
        addManualLog("수동 도착 이벤트 발행: ${place.id} (source=manual)")
    }

    /** U1~U14 버튼 처리. 관찰형(U3/U7/U10)은 실행 중 재클릭 시 취소한다. */
    fun onTestClicked(id: String) {
        // U12(문)·U14(배터리)는 '참고 — 사용 안 함'(00문서 11-2 범위 고정). 조회·수신을 실행하지
        // 않고 고정 안내만 다시 쓴다. 상태는 IDLE 유지 — 실행한 테스트가 아니므로 PASS 도 아니다.
        if (id == "U12" || id == "U14") {
            update(id) { result ->
                result.copy(detail = listOf("참고 — 사용 안 함(로드맵). 조회·수신을 실행하지 않는다"), updatedAt = now())
            }
            return
        }
        runningJobs.remove(id)?.let { job ->
            job.cancel()
            if (id == "U3") {
                // A3(2026-11-12): 관찰(U3) 중단 뒤 여정 감시를 재보장한다. 관찰은 여정 감시와
                // 분리된 수명주기(startObservation/stopObservation)라 원래 건드리지 않지만,
                // '관찰을 꺼도 다음 장소 도착 판정이 유지·복구된다'를 재등록으로 방어적으로 닫는다.
                container.tripSession.rewatchCurrentTarget()
            }
            finish(id, passed = true, extra = listOf("관찰 중단(사용자 재클릭)"))
            return
        }
        val place = selectedPlace()
        if (place == null) {
            setFailed(id, listOf("목표 장소 없음 — 시드 로드 확인"))
            return
        }
        setRunning(id)
        runningJobs[id] = scope.launch {
            try {
                // onDetail 은 해당 테스트(id)의 상세로만 반영된다.
                val outcome = dispatchTest(id, container, modeRouter, place) { line ->
                    appendDetail(id, line)
                }
                finish(id, outcome.passed)
            } catch (cancelled: CancellationException) {
                // 관찰형 테스트의 정상 종료 경로(재클릭 취소). finish 는 onTestClicked 에서 처리했다.
                throw cancelled
            } catch (t: Throwable) {
                setFailed(id, listOf("예외: ${t.javaClass.simpleName}: ${t.message ?: ""}"))
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    // --- 차량 세션(T9-1) 동작 ---

    /** 세션 상태 한 줄을 다시 읽는다. 진단 화면 onResume 과 버튼 처리 후에 호출한다. */
    fun refreshSessionState() {
        val code = sessionStore.familyCode.value
        _sessionStatus.value = when {
            code != null -> "가족 코드 저장됨(${code.length}자리)"
            sessionStore.isGuestMode -> "게스트(둘러보기) 모드"
            else -> "세션 없음"
        }
    }

    /**
     * '기어 상태(Vehicle·Gear)' 조회 버튼 처리. PleosVehicleInfoProvider.readGearStateOnly 를
     * 그대로 쓴다(U5 항목 안의 gear 결과와 같은 경로). 결과는 표시 전용 — 저장·전송 금지.
     */
    fun queryGearState() {
        scope.launch {
            container.pleosVehicle.readGearStateOnly { gear -> _gearState.value = gear }
        }
    }

    /** T12+ 지표를 SharedPreferences("diagnostics_metrics")에서 다시 읽는다(진단 화면 진입 시). */
    fun refreshMetrics() {
        _metrics.value = DiagnosticsMetrics.readAll(appCtx)
        // 정차 화면(T9-3)이 남긴 서버 조회 오류 1줄도 함께 읽는다(④ '마지막 오류' 표시).
        _serverLastError.value = DiagnosticsMetrics.lastError(appCtx)
    }

    /**
     * T12+ 지표 '서버 값 가져오기'(2026-11-11). [지표 새로고침] 버튼 처리.
     *
     * 가족 코드·PIN 세션이 있을 때만 apiClient.getMetrics 를 호출한다(세션 없으면 호출 금지 —
     * 00문서 13절: 없으면 숨김, 로컬 값 유지).
     *  - 성공: 서버 값으로 지표를 교체(DiagnosticsMetrics.set)하고 출처를 '서버'로 표시.
     *  - 실패: 로컬 카운터를 그대로 유지하고 출처 '로컬' 유지 + ④ lastError 에 1줄 기록.
     *
     * ⚠ 병렬 신규 API 의존(지정 시그니처 — 미착지 시 이 함수와 parseMetricValues 만 손본다):
     *   ApiClient.getMetrics(familyCode, pin): Result<String>, FamilySessionStore.pinOrNull()
     */
    fun refreshMetricsFromServer() {
        val code = sessionStore.familyCode.value
        // ⚠ 병렬 신규 API — 메모리 전용 PIN(저장 금지, 00문서 10.6).
        val pin = sessionStore.pinOrNull()
        if (code == null || pin == null) {
            // 세션 없음(게스트·미연결·PIN 휘발 소실) — 서버 호출 대신 로컬 값 유지 안내만.
            _metricsNote.value = appCtx.getString(R.string.diag_metrics_no_session)
            return
        }
        scope.launch {
            // ⚠ 병렬 신규 API — 원본 JSON 문자열을 Result 로 받는다.
            container.apiClient.getMetrics(code, pin).fold(
                onSuccess = { body ->
                    val serverValues = parseMetricValues(body)
                    if (serverValues.isEmpty()) {
                        // 읽을 지표가 없으면 교체하지 않는다(가짜 값 금지 — 로컬 유지).
                        setMetricsFetchFailed("지표 서버 조회 실패: 응답에 지표 없음")
                    } else {
                        serverValues.forEach { (key, value) ->
                            DiagnosticsMetrics.set(appCtx, key, value)
                            _metricSources.value = _metricSources.value + (key to SOURCE_SERVER)
                        }
                        DiagnosticsMetrics.clearLastError(appCtx)
                        _metricsNote.value = null
                        refreshMetrics() // 교체된 값을 다시 읽고 ④ 오류 줄도 비운다.
                    }
                },
                onFailure = { t ->
                    setMetricsFetchFailed("지표 서버 조회 실패: ${t.javaClass.simpleName}")
                },
            )
        }
    }

    /** 지표 서버 조회 실패 — 로컬 값·출처('로컬')를 유지하고 ④ '마지막 오류'에 1줄을 남긴다. */
    private fun setMetricsFetchFailed(reason: String) {
        _metricsNote.value = appCtx.getString(R.string.diag_metrics_fetch_failed)
        _lastError.value = reason
    }

    /**
     * '이 차에서 로그아웃'(T9-1) — 가족 코드·세션 PIN·캐시·큐 삭제(A8 검증 대상 경로).
     *
     * 삭제 대상 3가지(00문서 2-2·10.6):
     *  1. 가족 코드 — FamilySessionStore.signOut() 이 SharedPreferences("family_session")에서 지운다.
     *     (A8 강화: 같은 signOut() 이 세션 메모리 자격 증명(SessionCredentials 의 PIN·세션 내 코드)을
     *      전부 해제한다 — '로그아웃 = 열람 권한 완전 상실'.)
     *  2. 방문 기록 파일(visits.json)·전송 대기 큐(visit_queue.json) — [SignOutCleanup] 이
     *     filesDir 아래에서 직접 지운다. VisitStore/VisitQueue 인스턴스를 만들지 않는다 —
     *     인스턴스를 새로 만들면 메모리 캐시와 파일 상태가 어긋날 수 있기 때문이다. 파일만 지우면
     *     다음 append 가 새 파일을 만들므로 기존 파이프라인 동작에 영향이 없다.
     *  3. 세션 메모리(B1 '나중에 정하기' 플래그 등) — [HeirQuestionMemory.clearAll()] 로 소멸시킨다.
     *     재방문 시 다시 물어도 되는 계약(세션 메모리만, 영속 금지)에 맞는 정리다.
     *
     * 완료 확인 절차(주석): 로그아웃 후 '파일 목록으로 확인' —
     *   adb shell run-as app.arosaegim ls files/
     * 완료 기준: 앱 데이터에 가족 코드(family_session)·방문 기록(visits.json)·큐가 남지 않는다.
     * 삭제 로직 자체는 JVM 단위 테스트로 검증한다(SignOutCleanupTest).
     */
    fun signOutFromCar() {
        sessionStore.signOut()
        val lines = mutableListOf("가족 코드 삭제 완료") // PIN·세션 코드 해제는 signOut() 내부에서 함께 끝난다.
        lines.addAll(SignOutCleanup.deleteFiles(appCtx.filesDir))
        HeirQuestionMemory.clearAll()
        _sessionResult.value = lines
        refreshSessionState()
    }

    // --- SmokeTests 실행 분배 ---

    private suspend fun dispatchTest(
        id: String,
        container: AppContainer,
        modeRouter: ModeRouter,
        place: Place,
        onDetail: (String) -> Unit,
    ): Outcome = when (id) {
        "U1" -> SmokeTests.runU1(container, place, onDetail)
        "U2" -> SmokeTests.runU2(container, onDetail)
        "U3" -> { SmokeTests.runU3(container, place, onDetail); Outcome(passed = true) }
        "U4" -> SmokeTests.runU4(container, onDetail)
        "U5" -> SmokeTests.runU5(container, onDetail)
        "U6" -> SmokeTests.runU6(container, onDetail)
        "U7" -> { SmokeTests.runU7(container, onDetail); Outcome(passed = true) }
        "U8" -> SmokeTests.runU8(onDetail)
        "U9" -> SmokeTests.runU9(modeRouter, onDetail)
        "U10" -> { SmokeTests.runU10(container, onDetail); Outcome(passed = true) }
        // U11·U13은 SmokeTests.kt(공유 파일)를 만지지 않기 위해 이 ViewModel 안에서 실행한다.
        "U11" -> runU11(onDetail)
        "U13" -> runU13(onDetail)
        // D 실측 전용(2026-11-11): capability 확인만 하고 값은 절대 읽지 않는다.
        // 결과(성공/실패)를 기록·표시하는 것까지가 범위다(기어 상태 카드 같은 기존 스타일).
        "D1" -> runCapabilityCheck("D1", "Fuel.checkFuelPercentCapabilities", onDetail) {
            container.pleosVehicle.checkFuelCapabilityOnly(it)
        }
        "D2" -> runCapabilityCheck("D2", "EvBattery.checkEvBatteryStateOfChargeCapabilities", onDetail) {
            container.pleosVehicle.checkEvBatteryCapabilityOnly(it)
        }
        // U15·U16: FuelBatteryProbe 의 suspend 래퍼(3초 타임아웃·runCatching)를 통한 실측.
        // 판정은 SmokeTests 쪽 AccessType 집합 비교(D1·D2 의 runCapabilityCheck 와 동일 기준).
        "U15" -> SmokeTests.runU15(fuelBatteryProbe, onDetail)
        "U16" -> SmokeTests.runU16(fuelBatteryProbe, onDetail)
        else -> {
            onDetail("알 수 없는 테스트 id: $id")
            Outcome(passed = false)
        }
    }

    /**
     * D 실측 전용 capability 확인(D1·D2). 결과 문자열이 capability AccessType(NONE/READ/…)이면
     * 성공 — 실측 PASS 로 기록한다. FAILED·타임아웃·NOT_INITIALIZED 는 실패로 기록한다.
     * 값(getFuelPercent 등)은 읽지 않고, 화면 문장 생성 등 기능 구현도 하지 않는다(범위 고정).
     */
    private suspend fun runCapabilityCheck(
        id: String,
        apiName: String,
        onDetail: (String) -> Unit,
        check: ((String) -> Unit) -> Unit,
    ): Outcome {
        onDetail("capability 확인만 실행(값 미읽음·기능 구현 없음 — 실측 전용): $apiName")
        val result = askOrNull(3_000L, check) ?: "타임아웃"
        onDetail("capability 결과 = $result")
        val passed = result in setOf("NONE", "READ", "WRITE", "READ_WRITE")
        onDetail(
            if (passed) "판정: PASS(capability 확인 성공 — 실측 기록만 남긴다)"
            else "판정: FAIL(capability 확인 실패/미응답 — 결과만 기록한다)",
        )
        return Outcome(passed)
    }

    // --- U11·U13(TripSignalProvider 경유 조회 — 01문서 8절) ---

    /**
     * U11 좌석 점유: TripSignalProvider.partySize() 를 조회해 인원 수로 환산 가능한지 확인.
     * 인원 수만 사용한다(좌석별·사람별 값은 조회·저장·표시하지 않는다 — 00문서 11-2).
     * 안 읽히면( null ) 화면 문장이 숨겨지는 것이 설계 동작이며, 이 테스트 판정은 FAIL 로 남겨
     * Virtual Console 'Seat Occupancy' 재조작을 유도한다.
     */
    private suspend fun runU11(onDetail: (String) -> Unit): Outcome {
        onDetail("Virtual Console 'Seat Occupancy' 조작 후 재실행 — TripSession.tripSignals 경유 조회")
        val size = container.tripSession.tripSignals?.partySize()
        onDetail("partySize() = ${size ?: "안 읽힘(null)"}")
        // Q27(2026-11-13): 요청·성공·실패 좌석 수 한 줄 — 응답률 미달(null) 원인 판별용. 표시 전용.
        (container.tripSession.tripSignals as? PleosTripSignalProvider)
            ?.lastSeatDiagnostics
            ?.let { onDetail(it) }
        return if (size != null) {
            onDetail("판정: PASS(인원 수 환산 성공 — 정차 화면 문장 표시)")
            Outcome(true)
        } else {
            onDetail("판정: FAIL — 안 읽힘. 화면은 문장을 숨김(설계 동작). 재조작 후 재실행")
            Outcome(false)
        }
    }

    /**
     * U13 주행거리계: TripSignalProvider.odometerKm() 를 조회해 단위·값·표시 가능 여부를 본다.
     * Virtual Console 'Odometer' 조작으로 값 변화를 재확인한다. 안 읽히면 거리 문장은 숨겨진다.
     */
    private suspend fun runU13(onDetail: (String) -> Unit): Outcome {
        onDetail("Virtual Console 'Odometer' 조작 후 재실행 — TripSession.tripSignals 경유 조회")
        val km = container.tripSession.tripSignals?.odometerKm()
        onDetail(
            if (km != null) "odometerKm() = ${"%.1f".format(km)} km"
            else "odometerKm() = 안 읽힘(null)",
        )
        return if (km != null) {
            onDetail("판정: PASS(값 표시 가능 — 완주 화면 거리 문장에 사용)")
            Outcome(true)
        } else {
            onDetail("판정: FAIL — 안 읽힘. 거리 문장은 숨김(설계 동작). 재조작 후 재실행")
            Outcome(false)
        }
    }

    // --- 상태 갱신 도구 ---

    /** SDK 콜백 1회 대기(SmokeTests.askOrNull 과 같은 보호 방식 — 타임아웃 후 늦은 콜백 무시). */
    private suspend fun askOrNull(
        timeoutMs: Long,
        ask: ((String) -> Unit) -> Unit,
    ): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            ask { answer ->
                cont.resume(answer) { /* 취소로 값 폐기 — 무시 */ }
            }
        }
    }

    private fun appendDetail(id: String, line: String) {
        update(id) { result ->
            result.copy(detail = (result.detail + line).takeLast(40))
        }
    }

    private fun setRunning(id: String) = update(id) { result ->
        result.copy(status = SmokeResult.Status.RUNNING, detail = listOf("실행 중…"), updatedAt = now())
    }

    private fun setFailed(id: String, lines: List<String>) {
        update(id) { result ->
            result.copy(status = SmokeResult.Status.FAIL, detail = lines, updatedAt = now())
        }
        _lastError.value = "$id FAIL — ${lines.firstOrNull() ?: "원인 미제공"}"
    }

    private fun finish(id: String, passed: Boolean, extra: List<String> = emptyList()) {
        update(id) { result ->
            result.copy(
                status = if (passed) SmokeResult.Status.PASS else SmokeResult.Status.FAIL,
                detail = (result.detail + extra).takeLast(40),
                updatedAt = now(),
            )
        }
        if (!passed) {
            _lastError.value = "$id FAIL — 상세는 ③ 섹션 참조"
        }
    }

    private fun update(id: String, transform: (SmokeResult) -> SmokeResult) {
        updateResult { result -> if (result.id == id) transform(result) else result }
    }

    private fun updateResult(transform: (SmokeResult) -> SmokeResult) {
        _results.update { list -> list.map(transform) }
    }

    private fun addManualLog(line: String) {
        _manualLog.update { (it + line).takeLast(30) }
    }

    private fun now(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    /**
     * 초기 스모크 목록. U15·U16 라벨은 strings 리소스로 둔다(다른 항목과 달리 신규 문구 —
     * 실측 뒤 문구가 바뀔 수 있어 리소스로 관리). appContext 는 프로퍼티 초기화 시점에만
     * 접근 가능한 생성자 파라미터라 인자로 받는다(멤버 함수에서 직접 참조 불가).
     */
    private fun initialResults(appContext: Context): List<SmokeResult> = listOf(
        SmokeResult("U1", "내비 경로 요청(poiId 빈 값)"),
        SmokeResult("U2", "내비 초기화 상태"),
        SmokeResult("U3", "근접 알림 관찰(재클릭=중단)"),
        SmokeResult("U4", "FusedLocation 지원 여부"),
        SmokeResult("U5", "Vehicle capability/기어"),
        SmokeResult("U6", "TTS 안내 음성"),
        SmokeResult("U7", "주행 상태 관찰(재클릭=중단)"),
        SmokeResult("U8", "호스트 서버 통신"),
        SmokeResult("U9", "화면 전환 3회"),
        SmokeResult("U10", "도착 이벤트 관찰(재클릭=중단)"),
        // U11~U14(01문서 8절 후반부). U12·U14 는 범위 고정으로 '사용 안 함' 고정 표시만 둔다.
        SmokeResult("U11", "좌석 점유 → 인원(partySize)"),
        SmokeResult(
            "U12",
            "문 상태(참고 — 사용 안 함)",
            detail = listOf("참고 — 사용 안 함(로드맵). 조회·수신을 실행하지 않는다"),
        ),
        SmokeResult("U13", "주행거리계(odometerKm)"),
        SmokeResult(
            "U14",
            "배터리(참고 — 사용 안 함)",
            detail = listOf("참고 — 사용 안 함(로드맵). 조회·수신을 실행하지 않는다"),
        ),
        // D 실측 전용(2026-11-11): Fuel·EvBattery capability 성공/실패 기록·표시만 한다.
        // 기어 상태 카드와 같은 기존 스타일. 값 읽기·문장 생성 등 기능 구현은 보류다.
        SmokeResult("D1", "연료 capability 실측(기능 구현 없음)"),
        SmokeResult("D2", "EV 배터리 capability 실측(기능 구현 없음)"),
        // U15·U16(2026-11-12, 07문서 3절 D): FuelBatteryProbe 직접 실측 — capability 성공/실패만
        // 기록한다. 구현(화면 문장 생성 등)은 실측 결과를 보고 정한다(범위 고정).
        SmokeResult("U15", appContext.getString(R.string.diag_u15_fuel_support)),
        SmokeResult("U16", appContext.getString(R.string.diag_u16_evbattery_support)),
    )

    private companion object {
        // 로그아웃 삭제 파일명은 SignOutCleanup.FILES_TO_DELETE 로 한 곳에서 관리한다(A8).
    }
}

// ── T12+ 서버 지표 파싱(kotlinx.serialization — 저장 전 검증용, 화면 표시 전용) ──

/** getMetrics 원본 JSON 파싱 전용 Json(모르는 키 무시). */
private val diagJson = Json { ignoreUnknownKeys = true }

/** 서버 값으로 교체를 허용하는 지표 키 6개(DiagnosticsMetrics 와 같은 키 이름). */
private val KNOWN_METRIC_KEYS = setOf(
    DiagnosticsMetrics.KEY_FAMILY_CODES,
    DiagnosticsMetrics.KEY_THENNOW_VIEWS,
    DiagnosticsMetrics.KEY_RECEIPTS_VERIFIED,
    DiagnosticsMetrics.KEY_REBOOK_GAP_DAYS,
    DiagnosticsMetrics.KEY_STAMPBOOK_COMPLETIONS,
    DiagnosticsMetrics.KEY_HANDOVER_DESIGNATED,
)

/**
 * getMetrics 원본 JSON을 지표 키 → 정수 Map 으로 바꾼다. 응답 형태 방어:
 *  - {"thennow_views":3,...} 최상위 객체 또는 {"metrics":{...}} 감싸기 둘 다 흡수한다.
 *  - 알려진 6개 지표 키만 반영하고(모르는 키 무시), 정수로 읽히는 값만 취한다.
 *  - 실패·빈 응답은 빈 Map → 호출부가 로컬 값을 유지한다(가짜 값 금지).
 */
internal fun parseMetricValues(body: String): Map<String, Int> = runCatching {
    val root = diagJson.parseToJsonElement(body).jsonObject
    val obj = (root["metrics"] as? JsonObject) ?: root
    obj.mapNotNull { (key, value) ->
        if (key !in KNOWN_METRIC_KEYS) return@mapNotNull null
        val number = (value as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        key to number
    }.toMap()
}.getOrDefault(emptyMap())

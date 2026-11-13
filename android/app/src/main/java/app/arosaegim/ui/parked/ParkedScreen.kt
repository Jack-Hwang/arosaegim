package app.arosaegim.ui.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.arosaegim.AppContainer
import app.arosaegim.R
import app.arosaegim.data.OwnerRequiredException
import app.arosaegim.domain.HeirCandidate
import app.arosaegim.domain.HeirQuestionInputs
import app.arosaegim.domain.HeirQuestionPolicy
import app.arosaegim.domain.HeirStatus
import app.arosaegim.domain.ThenNowPlaceState
import app.arosaegim.domain.formatFamilyTotalTripKm
import app.arosaegim.domain.isPersonComparisonFolded
import app.arosaegim.domain.parseFamilyTotalTripKm
import app.arosaegim.domain.parseHeirCandidates
import app.arosaegim.domain.parseHeirStatus
import app.arosaegim.domain.parseThenNowPlaceState
import app.arosaegim.domain.shouldShowFoldButton
import app.arosaegim.fallback.ManualDrivingState
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.aroColors
import kotlinx.coroutines.launch

/**
 * 코스 시작·기록 열람 로컬 플래그(00문서 12절 T6, 2026-11-11).
 *
 * QuizStateHolder 와 같은 방식의 object 홀더다 — 주행 전환 시 ParkedActivity 가
 * 시스템에 의해 재생성돼도 이 값은 프로세스 수명 동안 보존된다.
 *  - courseStarted: 스텝0(코스 선택 화면)에서 출발 버튼을 눌렀는지. false 고 스탬프가
 *    0개면 ParkedScreen 은 스텝0(코스 선택)을 먼저 보여 준다(00문서 12절 스텝0).
 *  - showRecord: 스텝5(오늘의 기록)을 보고 있는지. 코스 완주 시 ParkedScreen 이
 *    자동으로 켜고, 스텝4의 '오늘의 기록 보기' 버튼으로도 켠다.
 */
object CourseStartState {

    /** 코스를 시작했는지(스텝0 → 스텝1 진입 플래그). */
    var courseStarted by mutableStateOf(false)

    /** 스텝5(오늘의 기록) 표시 여부. */
    var showRecord by mutableStateOf(false)

    /** 코스 선택 화면의 출발 버튼 처리 — 스텝1(스탬프 카드)부터 본다. */
    fun startCourse() {
        courseStarted = true
        showRecord = false
    }

    /** '오늘의 기록 보기' 버튼(스텝4) 처리 — 스텝5로 이동한다. */
    fun openRecord() {
        showRecord = true
    }

    /** '처음으로' — 코스 세션 리셋과 함께 스텝0(코스 선택)으로 돌아간다. */
    fun reset() {
        courseStarted = false
        showRecord = false
    }
}

/*
 * 정차(PARKED) 화면 본체 — 탭이 아닌 세로 스텝 네비게이션(00문서 ParkedActivity 절·12절 T6).
 *   스텝0 코스 선택(시안 02) → 스텝1 스탬프 획득 카드 → 스텝2 퀴즈(문제 1개씩)
 *   → 스텝3 그때와 지금 → 스텝4 스탬프북·제휴처(시안 08) → 스텝5 오늘의 기록(시안 09).
 *   스텝0 은 스탬프가 0개이고 코스를 시작하지 않았을 때(로컬 플래그 CourseStartState)만
 *   먼저 보이고, 완주(스탬프북 완성) 시 마지막에 스텝5 가 표시된다.
 *   (마지막 목표가 남아 있으면) "다음: {장소}" 안내로 다음 방문을 잇는다.
 *
 * 안전 규칙(04문서): 퀴즈는 정답 채점만 하고 시간 제한·점수·효과음이 없다. 본문 문단은
 * 3줄 이내, 메모는 2줄. 색은 토큰만, 애니메이션 없음. 주행 중에는 이 Activity 가
 * DO 미선언이라 시스템적으로 가려지므로, 스텝·퀴즈 진행은 QuizStateHolder 로,
 * 코스 시작·기록 열람은 CourseStartState 로 보존한다.
 *
 * 파일 구성(Q20 분리 — 본문 그대로의 순수 이동): 스텝1·2 컴포저블은 ParkedSteps.kt,
 * 스텝5(오늘의 기록)는 ParkedRecordStep.kt, 서버 응답 파싱 헬퍼(T9-3 members·E2 then-now)는
 * ParkedServerParsing.kt 에 있다. 이 파일은 화면 껍데기(상태 수집·효과·스텝 분기)와
 * CourseStartState 상태 홀더만 둔다.
 */
@Composable
fun ParkedScreen(container: AppContainer, onExitToDriving: () -> Unit) {
    val session = container.tripSession
    val context = LocalContext.current
    // 시드 파일 1회 읽기. 실패 시 빈 코스로 복구된다(SeedRepository 계약).
    val seed = remember { container.seedRepository.load(context) }
    val course = session.course

    val lastArrival by session.lastArrival.collectAsState()
    val currentTarget by session.currentTarget.collectAsState()
    val stampedCount by session.stampedCount.collectAsState()
    val isCompleted by session.isCompleted.collectAsState()
    // T6-2: 좌석 점유 → 오늘 인원 / T12 보조: 오늘 함께 달린 거리(TripSession 이
    // TripSignalProvider 를 받아 노출하는 StateFlow — 고정 API: partySize, tripKm).
    val partySizeNow by session.partySize.collectAsState()
    val tripKm by session.tripKm.collectAsState()

    // 데모 모드 꼬리표: 수동 주행 상태 구현(폴백)이면 '데모 모드'(04문서 상태별 처리).
    val isDemoMode = remember { container.drivingState is ManualDrivingState }

    // T9-2: 이 장소에 남긴 예시 음성 편지 1건. seed.voiceLetters 는 저장소 시드의
    // SeedContent 확장 필드(고정 API) — 파싱 전이면 빈 목록으로 볼 수도 있어 null 허용.
    val voiceLetter = remember(seed.voiceLetters, lastArrival?.id) {
        seed.voiceLetters.firstOrNull { it.placeId == lastArrival?.id }
    }
    // VoiceLetterPlayer 어댑터(AppContainer 확장 필드 — 고정 API).
    val voiceLetterPlayer = container.voiceLetterPlayer

    // ── T9-3 자리 이름표 서버 연결(2026-11-11) ──
    // FamilySessionStore 를 기존 패턴(직접 인스턴스화 — SessionHold.kt 참조)으로 얻는다.
    val familyStore = rememberFamilyStore(context)
    // seat_role → 이름 Map. null = 이름표 카드 숨김(세션 없음·조회 실패 — 00문서 13절).
    var seatTags by remember { mutableStateOf<Map<String, String>?>(null) }
    // 401/403(코드·PIN 불일치) 안내 플래그 — 이름표 대신 카드 안 작은 안내 한 줄. 진단 lastError 로는 가지 않는다.
    var seatAuthError by remember { mutableStateOf(false) }

    // 도착 장소가 바뀔 때만 퀴즈 진행을 새로 시작하고, 문항 수 변화를 보정한다.
    // 같은 장소 재진입(주행 복귀)이면 QuizStateHolder 가 진행을 그대로 보존해 이어서 보여 준다.
    val quizzes = lastArrival?.quiz ?: emptyList()
    LaunchedEffect(lastArrival?.id, quizzes.size) {
        lastArrival?.let { QuizStateHolder.beginQuiz(it.id) }
        QuizStateHolder.clamp(quizzes.size)
    }

    // ── T9-3: 도착 장소가 바뀔 때 서버 members(seat_role)를 다시 조회한다 ──
    // 규칙(00문서 13절): 가족 코드·PIN 세션이 없으면(게스트·미연결·PIN 휘발 소실) 호출하지
    // 않고 이름표를 숨긴다. 그 외 조회 실패도 이름표를 숨기고, 원인 1줄만 진단 화면 ④
    // '마지막 오류'로 남긴다(SharedPreferences 공유 채널 — DiagnosticsMetrics.lastError).
    LaunchedEffect(lastArrival?.id) {
        seatTags = null
        seatAuthError = false
        if (lastArrival?.id == null) return@LaunchedEffect
        val code = familyStore.familyCode.value
        // ⚠ 병렬 신규 API(FamilySessionStore.setPin/pinOrNull — 메모리 전용 PIN, 지정 시그니처).
        val pin = familyStore.pinOrNull()
        if (code == null || pin == null) return@LaunchedEffect // 세션 없음 → 스킵(숨김)
        // ⚠ 병렬 신규 API(ApiClient.getMembers(familyCode, pin): Result<String>) — 지정 시그니처.
        container.apiClient.getMembers(code, pin).fold(
            onSuccess = { body ->
                seatAuthError = false
                seatTags = parseSeatTags(body)
                // 성공하면 이전 실패 기록 1줄을 지운다(진단 ④가 '오류 없음'으로 갱신된다).
                DiagnosticsMetrics.clearLastError(context)
            },
            onFailure = { t ->
                seatTags = null
                if (isAuthFailure(t)) {
                    // 코드·PIN 불일치 — 이름표 대신 카드 안 작은 안내. lastError 기록 없음.
                    seatAuthError = true
                } else {
                    // 그 외 실패(네트워크·서버) — 이름표 숨김 + 진단 화면 마지막 오류 1줄.
                    seatAuthError = false
                    DiagnosticsMetrics.setLastError(
                        context,
                        "T9-3 이름표 조회 실패: ${t.javaClass.simpleName}",
                    )
                }
            },
        )
    }

    // T12+: 그때와 지금 열람 수 — 스텝3(그때와 지금)에 들어올 때마다 +1 한다.
    // 값은 Diagnostics ⑦ 지표 섹션이 SharedPreferences("diagnostics_metrics")에서 읽는다.
    val scope = rememberCoroutineScope()
    LaunchedEffect(QuizStateHolder.stepIndex) {
        if (QuizStateHolder.stepIndex.coerceIn(0, 3) == 2) {
            DiagnosticsMetrics.increment(context, DiagnosticsMetrics.KEY_THENNOW_VIEWS)
        }
    }
    // T12+: 스탬프북 완성 — 완주로 전환될 때 한 번만 +1 하고, 완주 시 마지막 화면을
    // 스텝5(오늘의 기록)로 바꾼다(00문서 12절). 재생성·재진입에서 중복 계산하지 않는다.
    var completionCounted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            if (!completionCounted) {
                completionCounted = true
                DiagnosticsMetrics.increment(context, DiagnosticsMetrics.KEY_STAMPBOOK_COMPLETIONS)
            }
            CourseStartState.showRecord = true
        }
    }

    // ───────────── 가족 기록 신규 기능(B1~B4, 2026-11-11) — 모두 정차 화면 전용, 세션(코드+PIN)이
    // 있을 때만 서버를 호출한다. 응답에 필드가 없거나 조회 실패면 해당 기능을 숨긴다(가짜 금지) ─────────────

    // B1: 이어받을 사람(heir) 상태 — 서버 heir 조회 성공분 / 등록 구성원 / 진행 상태.
    var heirKnownStatus by remember { mutableStateOf<HeirStatus?>(null) }
    var heirCandidates by remember { mutableStateOf<List<HeirCandidate>>(emptyList()) }
    var heirBusy by remember { mutableStateOf(false) }
    var heirDesignatedName by remember { mutableStateOf<String?>(null) }
    var heirOwnerGuide by remember { mutableStateOf(false) }
    var heirPickFailed by remember { mutableStateOf(false) }

    // B2·B4: 이 장소의 then-now 부가 상태(visit_ordinal·folded·fold_person_comparison_all).
    var thenNowState by remember { mutableStateOf<ThenNowPlaceState?>(null) }

    // B4: 가족 prefs(인원 비교 문장 접기 — GET /v1/families/me/prefs). null = 미조회·세션 없음.
    var partyPrefs by remember { mutableStateOf<PartyLinePrefs?>(null) }

    // B3: 가족 누적 거리(metrics.family_total_trip_km). null = 미조회·실패·필드 없음 → 줄 숨김.
    var familyTotalKm by remember { mutableStateOf<Double?>(null) }

    // B2·B4: 도착 장소가 바뀔 때 then-now 부가 상태를 조회한다. 세션 없으면 호출하지 않는다.
    LaunchedEffect(lastArrival?.id) {
        thenNowState = null
        val placeId = lastArrival?.id ?: return@LaunchedEffect
        val code = familyStore.familyCode.value
        val pin = familyStore.pinOrNull()
        if (code == null || pin == null) return@LaunchedEffect
        container.apiClient.getThenNow(code, pin, placeId).fold(
            onSuccess = { body -> thenNowState = parseThenNowPlaceState(body) },
            onFailure = { thenNowState = null }, // 실패 = 기능 숨김(기존 동작 유지 — 추측 금지).
        )
    }

    // B4: 도착 장소가 바뀔 때 가족 prefs(인원 비교 문장 접기)를 조회한다. 세션 없으면 호출하지
    // 않는다(접기 버튼은 then-now 조회 성공분과 함께만 보인다 — 기존 동작). 서버 조회가 실패하면
    // (⚠ 병렬 의존: 백엔드 prefs 미착지·네트워크 오류) 로컬 SharedPreferences 폴백으로 전환한다.
    // 서버 연동이 정착하면 폴백은 실패 때만 읽히고, 서버 값이 있으면 언제나 이긴다
    // (PartyFoldLocalStore 주석 계약).
    LaunchedEffect(lastArrival?.id) {
        partyPrefs = null
        val placeId = lastArrival?.id ?: return@LaunchedEffect
        val code = familyStore.familyCode.value
        val pin = familyStore.pinOrNull()
        if (code == null || pin == null) return@LaunchedEffect
        val fromServer = container.apiClient.getPrefs(code, pin).fold(
            onSuccess = { body -> parsePartyLinePrefs(body) },
            onFailure = { null }, // 실패 = 로컬 폴백(가짜 서버 값 금지).
        )
        partyPrefs = fromServer ?: PartyLinePrefs(
            hiddenPlaceIds = PartyFoldLocalStore.readFoldedPlaceIds(context),
            allHidden = PartyFoldLocalStore.readAllHidden(context),
        )
    }

    // ── E2(2026-11-11): 스텝3(그때와 지금) 서버 과거 기록 조회 ──
    // ThenNowStep 진입(스텝 지수 2) 시 1회 조회해 serverPast 로 넘긴다. 표시 우선순위(02문서 4절):
    // 서버 past > 시드 then_now_demo(예시 폴백) > 첫 방문 문구. 조회는 가족 코드·PIN 세션이 있을
    // 때만 하고, 실패·세션 없음은 serverPast 를 null 로 두어 기존 예시 동작으로 조용히 돌아간다
    // (진단 lastError 미기록 — 조회 실패가 사용자에게 거슬리지 않게). remember(lastArrival?.id)
    // 키라 장소가 바뀌면 값도 함께 초기화된다.
    var serverPast by remember(lastArrival?.id) { mutableStateOf<ServerThenNowInfo?>(null) }
    // 마지막으로 성공해 본 장소 id. 실패·미조회 장소는 스텝을 다시 열 때 한 번만 재시도한다
    // (같은 스텝에 머무는 동안은 LaunchedEffect 키가 바뀌지 않아 재요청이 일어나지 않는다).
    var serverPastPlaceId by remember(lastArrival?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(lastArrival?.id, QuizStateHolder.stepIndex) {
        val placeId = lastArrival?.id ?: return@LaunchedEffect
        if (QuizStateHolder.stepIndex.coerceIn(0, 3) != 2) return@LaunchedEffect // 스텝3 진입 때만.
        if (serverPastPlaceId == placeId) return@LaunchedEffect // 같은 장소 성공분 재사용(재조회 금지).
        val code = familyStore.familyCode.value
        val pin = familyStore.pinOrNull()
        if (code == null || pin == null) return@LaunchedEffect // 세션 없음 → 스킵(예시 폴백).
        fetchThenNowBody(container.apiClient, placeId, code, pin).fold(
            onSuccess = { body ->
                serverPast = parseThenNowServerData(body)
                serverPastPlaceId = placeId // 성공만 기록 — 파싱 실패(null)도 '과거 없음' 응답으로 본다.
            },
            onFailure = { serverPast = null }, // 조용히 예시 폴백(기존 동작 유지 — 진단 미기록).
        )
    }

    // B1·B3: 코스 완주 직후 1회 — heir 상태·구성원 목록·누적 거리를 조회한다.
    // 완주가 풀리면(처음으로 → 새 코스) 조회 플래그를 되돌려 다음 완주 때 다시 물을 수 있게 한다.
    var familyRecordFetched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCompleted) {
        if (!isCompleted) {
            familyRecordFetched = false
            return@LaunchedEffect
        }
        if (familyRecordFetched) return@LaunchedEffect
        familyRecordFetched = true
        val code = familyStore.familyCode.value
        val pin = familyStore.pinOrNull()
        if (code == null || pin == null) {
            // 세션 없음 → 서버 조회(heir·구성원·누적 거리)를 생략하고 해당 줄·버튼을 숨긴다.
            // PIN 없음(차량은 PIN 저장 금지)의 안내 카드 전환은 heirPinGuideVisible 이
            // 담당한다 — 여기선 조회만 건너뛴다(00문서 T9-3 방식).
            return@LaunchedEffect
        }
        container.apiClient.getHeir(code, pin).fold(
            onSuccess = { body -> heirKnownStatus = parseHeirStatus(body) },
            onFailure = { heirKnownStatus = null }, // 조회 실패 = 질문하지 않는다(추측 금지).
        )
        container.apiClient.getMembers(code, pin).fold(
            onSuccess = { body -> heirCandidates = parseHeirCandidates(body) },
            onFailure = { heirCandidates = emptyList() }, // 구성원을 못 읽으면 질문하지 않는다.
        )
        container.apiClient.getMetrics(code, pin).fold(
            onSuccess = { body -> familyTotalKm = parseFamilyTotalTripKm(body) },
            onFailure = { familyTotalKm = null }, // 누적값 없음 → 줄 숨김(가짜 값 금지).
        )
    }

    // B3 보완: 완주 이전에 스텝5(오늘의 기록)를 열었을 때도 가족 누적 거리를 물어본다 — 위 조회는
    // 완주 시 1회라, 코스 도중에 '오늘의 기록 보기'를 누르면 누적 줄이 계속 숨겨진다. 값이 이미
    // 있으면 재조회하지 않고, 읽지 못하면 줄은 숨은 채(가짜 값 금지 — 계약).
    LaunchedEffect(CourseStartState.showRecord, isCompleted) {
        if (!CourseStartState.showRecord || isCompleted) return@LaunchedEffect
        if (familyTotalKm != null) return@LaunchedEffect
        val code = familyStore.familyCode.value
        val pin = familyStore.pinOrNull()
        if (code == null || pin == null) return@LaunchedEffect
        container.apiClient.getMetrics(code, pin).fold(
            onSuccess = { body -> familyTotalKm = parseFamilyTotalTripKm(body) },
            onFailure = { }, // 실패 → 줄 숨김 유지(조용히 흡수).
        )
    }

    // B1: 주행 상태 — 주행 중에는 질문하지 않는다(계약). ParkedActivity 는 주행 중 가려지지만
    // 전환 직후 잠깐 그려질 수 있어 정책 입력으로도 막는다.
    val drivingStateNow by container.drivingState.state.collectAsState()

    // B1 질문 판정(정책은 순수 도메인 — HeirQuestionPolicy). 계산값이라 상태로 따로 두지 않는다.
    val heirShouldAsk = HeirQuestionPolicy.shouldAsk(
        HeirQuestionInputs(
            courseCompleted = isCompleted,
            driving = drivingStateNow == app.arosaegim.domain.DrivingState.DRIVING,
            deferred = HeirQuestionMemory.isDeferred(course.id),
            heirStatus = heirKnownStatus ?: HeirStatus.Unknown,
            membersAvailable = heirCandidates.isNotEmpty(),
        ),
    )

    // B1: PIN 없음 안내 판정(순수 함수 — HeirPinGuideTest). 차량은 PIN 을 저장하지 않아 프로세스
    // 재시작 후 PIN 이 사라진다(00문서 10.6). 그때는 POST heir 를 부를 수 없으므로 질문 버튼을
    // 숨기지 말고 '스마트폰의 가족 관리에서 정해 주세요' 안내 카드로 전환한다(계약 — 403 owner
    // 안내와 같은 안내 문자열을 재용한다). 가족 연결이 없으면 배지 자리도 없어 숨긴다.
    val heirPinGuideVisible = shouldShowHeirPinGuide(
        familyConnected = familyStore.familyCode.value != null,
        hasPin = familyStore.pinOrNull() != null,
        courseCompleted = isCompleted,
        driving = drivingStateNow == app.arosaegim.domain.DrivingState.DRIVING,
        deferredThisCourse = HeirQuestionMemory.isDeferred(course.id),
    )

    // B4: 이 장소의 인원 비교 문장 접힘 — 가족 prefs(접힘 목록·'모든 장소 끄기'). then-now 응답의
    // 접기 플래그(기존 경로)와 OR 한다 — 어느 쪽이든 이 장소에서 문장은 다시 나오지 않는다.
    val partyLineFolded = partyLineHiddenFor(lastArrival?.id, partyPrefs)

    // B1: 구성원 선택 → POST heir. 403은 owner 권한 없음(차량 세션의 정상 경로) — 버튼을 숨기지
    // 않고 안내 한 줄만 추가한다. 성공 시 지정 완료 상태로 바꾸고 진단 KPI(물려주기 지정 수)를 올린다.
    fun pickHeir(candidate: HeirCandidate) {
        val code = familyStore.familyCode.value ?: return
        val pin = familyStore.pinOrNull() ?: return
        heirBusy = true
        heirPickFailed = false
        scope.launch {
            container.apiClient.postHeir(code, pin, candidate.memberId).fold(
                onSuccess = {
                    heirBusy = false
                    heirOwnerGuide = false
                    heirKnownStatus = HeirStatus.Known(candidate.memberId) // 재문 금지 조건 갱신.
                    heirDesignatedName = candidate.nickname
                    DiagnosticsMetrics.increment(context, DiagnosticsMetrics.KEY_HANDOVER_DESIGNATED)
                },
                onFailure = { t ->
                    heirBusy = false
                    when (t) {
                        is OwnerRequiredException -> heirOwnerGuide = true
                        else -> heirPickFailed = true
                    }
                },
            )
        }
    }

    // B4: 접기 → PUT /v1/families/me/prefs(전체 교체 — 현재 값에 이 장소를 합쳐 두 필드를 모두
    // 보낸다). 성공 시 응답(저장된 값)으로 상태를 갱신해 이 장소 문장을 즉시 숨긴다. 실패 시
    // 로컬 폴백(PartyFoldLocalStore — 서버 연동 예정, 위 조회 이펙트 주석 참조)에 남기고
    // 화면에서는 그래도 즉시 숨긴다. 다른 장소의 접기는 목록에 그대로 있어 영향이 없다(계약).
    fun foldComparison() {
        val placeId = lastArrival?.id ?: return
        val current = partyPrefs ?: return // prefs 를 못 읽었으면 접지 않는다(추측 금지).
        val code = familyStore.familyCode.value ?: return
        val pin = familyStore.pinOrNull() ?: return
        val mergedPlaces = partyPrefsPlacesWithPlace(current, placeId)
        scope.launch {
            container.apiClient.putPrefs(
                code,
                pin,
                hiddenPlaceIds = mergedPlaces.toList(),
                allHidden = current.allHidden,
            ).fold(
                onSuccess = { body ->
                    partyPrefs = parsePartyLinePrefs(body)
                        ?: current.copy(hiddenPlaceIds = mergedPlaces) // 응답이 이상하면 합친값 유지.
                },
                onFailure = {
                    // ⚠ 병렬 의존(백엔드 prefs): 호출 실패 시에만 로컬 폴백 — 서버 연동 예정.
                    PartyFoldLocalStore.writeFolded(context, placeId)
                    partyPrefs = current.copy(hiddenPlaceIds = mergedPlaces)
                },
            )
        }
    }

    // TripSession 공개 API 는 '찍힌 개수'만 주므로, 코스 방문 순서(= place_ids 순서)의
    // 앞 N개를 찍힌 스탬프로 계산한다(도착 순서가 곧 코스 순서라는 세션 계약 전제).
    val stampedIds = remember(stampedCount) { course.places.take(stampedCount).map { it.id }.toSet() }
    val bookDef = remember(lastArrival?.id) { pickBookDef(seed, lastArrival?.id) }
    val stampTotal = bookDef?.placeIds?.size ?: course.places.size

    // ── 스텝0 판정: 스탬프가 0개이고 코스를 시작하지 않았으면(로컬 플래그) 코스 선택을 먼저 본다.
    val showCourseSelect = stampedCount == 0 && !CourseStartState.courseStarted

    // 06문서 고칠 것 3번: 루트에서 safe drawing 인셋(상단 주행 정보·하단 GNB)을 피한다 —
    // 스텝0(코스 선택) 분기와 본문 분기 모두 이 안쪽에 그려진다. 기존 내부 구조(배경·스크롤
    // modifier 순서)는 그대로 두고 루트에만 인셋 패딩을 얹는 최소 변경이다.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // 가로·세로 분기(04문서 공통): 좁은 창(세로)은 위아래로 쌓고, 넓은 창(가로)은 좌우 배치.
        val isWide = maxWidth >= 600.dp

        if (showCourseSelect) {
            // ── 스텝0: 코스 선택(시안 02). 출발 버튼 → 스텝1(스탬프 카드)로 넘어간다.
            CourseSelectScreen(
                container = container,
                stampedCount = stampedCount,
                onCourseStarted = {
                    CourseStartState.startCourse()
                    // 새 코스 시작 — 퀴즈 진행도 처음부터(QuizStateHolder.reset 계약).
                    QuizStateHolder.reset()
                },
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.aroColors.backgroundPrimary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // 상단: 코스 제목 + 데모 모드 꼬리표
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.title,
                        modifier = Modifier.weight(1f),
                        style = AroTextStyles.titleSmall,
                        color = MaterialTheme.aroColors.textPrimary,
                        maxLines = 1,
                    )
                    if (isDemoMode) SmallTag(stringResource(R.string.parked_demo_mode_tag))
                }
                Spacer(Modifier.height(8.dp))
                // 하단 스텝 내비게이션: ①~④ + 스텝5(오늘의 기록). 완주 시 자동으로 스텝5가 선택된다.
                ParkedPageNavigationBar(
                    labels = listOf(
                        stringResource(R.string.parked_step_stamp),
                        stringResource(R.string.parked_step_quiz),
                        stringResource(R.string.parked_step_thennow),
                        stringResource(R.string.parked_step_book),
                        stringResource(R.string.parked_step_record),
                    ),
                    selected = if (CourseStartState.showRecord) {
                        RECORD_STEP_INDEX
                    } else {
                        QuizStateHolder.stepIndex.coerceIn(0, 3)
                    },
                    onSelect = { index ->
                        if (index == RECORD_STEP_INDEX) {
                            CourseStartState.showRecord = true
                        } else {
                            CourseStartState.showRecord = false
                            QuizStateHolder.stepIndex = index
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))

                if (CourseStartState.showRecord) {
                    // ── 스텝5: 오늘의 기록(시안 09). 기존 완주 요소(배지·거리)도 이 화면으로 이동 정리했다.
                    RecordContent(
                        course = course,
                        seed = seed,
                        bookDef = bookDef,
                        stampedIds = stampedIds,
                        stampedCount = stampedCount,
                        stampTotal = stampTotal,
                        partySize = partySizeNow,
                        tripKm = tripKm,
                        isCompleted = isCompleted,
                        // B3: 가족 누적 거리(0 이하·없음은 null — 줄 숨김).
                        familyTotalKmLabel = familyTotalKm?.let { formatFamilyTotalTripKm(it) },
                        // B1: 이어받을 사람 질문 카드(가족 배지 다음 1회) — 정책 판정값을 그대로
                        // 전달한다. PIN 없음(heirPinGuideVisible)이면 질문 대신 안내 카드로 전환
                        // (구성원 버튼은 세션 조회분만 — PIN 없으면 빈 목록이라 버튼이 생기지 않는다).
                        heirCardVisible = heirShouldAsk || heirPinGuideVisible,
                        heirCardCandidates = heirCandidates,
                        heirCardBusy = heirBusy,
                        heirCardDesignatedName = heirDesignatedName,
                        heirCardOwnerGuide = heirOwnerGuide || heirPinGuideVisible,
                        heirCardFailed = heirPickFailed,
                        heirCardOnPick = { pickHeir(it) },
                        heirCardOnDefer = { HeirQuestionMemory.defer(course.id) },
                        modifier = Modifier.weight(1f),
                        onBackHome = {
                            // 처음으로: 세션 리셋(스탬프·방문 기록·거리·인원 초기화) 후 스텝0(코스 선택) 복귀.
                            session.resetCourse()
                            QuizStateHolder.reset()
                            CourseStartState.reset()
                        },
                    )
                } else {
                    // 본문: 세로 스텝. 스크롤은 스텝 안에서만 최소 사용.
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (QuizStateHolder.stepIndex.coerceIn(0, 3)) {
                            0 -> StampStep(lastArrival, currentTarget, stampedCount, stampTotal)
                            1 -> QuizStep(lastArrival, quizzes)
                            2 -> ThenNowStep(
                                container = container,
                                demo = seed.thenNowDemo,
                                place = lastArrival,
                                isWide = isWide,
                                partySizeNow = partySizeNow,
                                voiceLetter = voiceLetter,
                                voiceLetterPlayer = voiceLetterPlayer,
                                // E2 서버 과거 기록(2026-11-11) — 세션 없음·조회 실패면 null 로
                                // 예시(시드) 폴백(기존 동작). 서버 값이면 '예시' 태그 없이 그려진다.
                                serverPast = serverPast,
                                // T9-3 서버 실데이터(구성원 자리 이름표) — 세션 없음·실패면 null 로 숨김.
                                seatTags = seatTags,
                                seatAuthError = seatAuthError,
                                // B2·B4: then-now 부가 상태 — 조회 실패·필드 없음이면 null 로
                                // 첫 기록 문구·접기 버튼 없이 기존 동작을 유지한다(가짜 금지).
                                thenNowState = thenNowState,
                                // B4: 가족 prefs 접힘(partyLineFolded)까지 함께 본다 — 어느 경로로
                                // 접혀도 이 장소에서 문장·버튼이 다시 나오지 않는다.
                                comparisonFolded = isPersonComparisonFolded(thenNowState) || partyLineFolded,
                                showFoldButton = shouldShowFoldButton(thenNowState) && !partyLineFolded,
                                onFoldComparison = { foldComparison() },
                            )
                            else -> BookStep(
                                seed = seed,
                                place = lastArrival,
                                stampedIds = stampedIds,
                                isWide = isWide,
                                // '오늘의 기록 보기' 버튼 — 스텝5로 이동한다.
                                onOpenRecord = { CourseStartState.openRecord() },
                            )
                        }
                    }
                    // 하단 버튼: 다음 목표 안내. 실제 주행 재개는 차량 앱 밖 내비 역할이라 여기선 안내만 한다.
                    val target = currentTarget
                    if (target != null) {
                        Spacer(Modifier.height(12.dp))
                        LargePrimaryButton(text = stringResource(R.string.parked_next_place, target.name)) {
                            QuizStateHolder.stepIndex = 0 // 스텝1(스탬프 카드)로 돌아가 다음 목표를 안내한다.
                        }
                    }
                }
            }
        }
    }
    // onExitToDriving: 주행/정차 전환은 ModeRouter(DrivingState 수집 → REORDER_TO_FRONT)가
    // 담당하므로 이 콜백은 현재 미사용이다(ParkedActivity 에서 {} 로 전달).
}

/** 스텝5 탭 인덱스(①~④ 뒤의 오늘의 기록). 스텝0 은 코스 선택 화면이라 이 내비에 없다. */
private const val RECORD_STEP_INDEX = 4

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import app.arosaegim.data.ApiClient
import app.arosaegim.data.OwnerRequiredException
import app.arosaegim.data.SeedContent
import app.arosaegim.domain.Course
import app.arosaegim.domain.HeirCandidate
import app.arosaegim.domain.HeirQuestionInputs
import app.arosaegim.domain.HeirQuestionPolicy
import app.arosaegim.domain.HeirStatus
import app.arosaegim.domain.Place
import app.arosaegim.domain.QuizItem
import app.arosaegim.domain.StampBookDef
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
import java.util.Calendar
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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
    // T6-2: 좌석 점유 → 오늘 인원 / T12 보조: 오늘 함께 달린 거리(병렬 작업인 TripSession 이
    // TripSignalProvider 를 받아 노출하는 StateFlow — 고정 API: partySize, tripKm).
    val partySizeNow by session.partySize.collectAsState()
    val tripKm by session.tripKm.collectAsState()

    // 데모 모드 꼬리표: 수동 주행 상태 구현(폴백)이면 '데모 모드'(04문서 상태별 처리).
    val isDemoMode = remember { container.drivingState is ManualDrivingState }

    // T9-2: 이 장소에 남긴 예시 음성 편지 1건. seed.voiceLetters 는 병렬 작업 중인
    // SeedContent 확장 필드(고정 API) — 파싱 전이면 빈 목록으로 볼 수도 있어 null 허용.
    val voiceLetter = remember(seed.voiceLetters, lastArrival?.id) {
        seed.voiceLetters.firstOrNull { it.placeId == lastArrival?.id }
    }
    // VoiceLetterPlayer 어댑터(병렬 작업인 AppContainer 확장 필드 — 고정 API).
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

/** 스텝1: 스탬프 획득 카드. 도착 장소·스탬프 아이콘 자리·진행(스탬프 N/3). 도착 전엔 시작 안내. */
@Composable
private fun StampStep(lastArrival: Place?, currentTarget: Place?, stampedCount: Int, stampTotal: Int) {
    SectionCard {
        if (lastArrival == null) {
            // 빈 상태: 아직 도착 기록이 없다 — 코스 시작 안내 + 첫 목표.
            Text(
                text = stringResource(R.string.parked_course_start),
                style = AroTextStyles.titleMedium,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
            val target = currentTarget
            if (target != null) {
                Text(
                    text = stringResource(R.string.parked_first_target, target.name),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textSecondary,
                    maxLines = 3,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.parked_stamp_done, lastArrival.name),
                style = AroTextStyles.titleMedium,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 2,
            )
            StampSlots(stampedCount, stampTotal)
            Text(
                text = stringResource(R.string.parked_stamp_progress, stampedCount, stampTotal),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textSecondary,
            )
        }
    }
}

/**
 * 스텝2: 퀴즈. 문제 1개씩, 보기 choices 큰 버튼, 채점 후 해설 1~2줄 + "다음".
 * 진행(1/2) 표시. reviewed=false 면 '초안' 꼬리표. 점수·타이머 없음(04문서 화면 3).
 * 진행 상태는 QuizStateHolder(싱글톤)에 보존되어 주행 복귀 시 이어서 보인다.
 */
@Composable
private fun QuizStep(place: Place?, quizzes: List<QuizItem>) {
    if (place == null || quizzes.isEmpty()) {
        SectionCard {
            Text(
                text = stringResource(R.string.parked_quiz_empty),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
            )
        }
        return
    }
    val index = QuizStateHolder.quizIndex.coerceIn(0, quizzes.size - 1)
    val item = quizzes[index]
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.parked_quiz_progress, index + 1, quizzes.size),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textSecondary,
            )
            Spacer(Modifier.width(8.dp))
            if (!item.reviewed) SmallTag(stringResource(R.string.parked_quiz_draft_tag))
        }
        // 질문(Title)
        Text(
            text = item.q,
            style = AroTextStyles.titleMedium,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 3,
        )
        // 보기 큰 버튼(높이 76dp 이상). 미응답일 때만 누를 수 있고, 누르면 즉시 채점한다.
        item.choices.forEachIndexed { i, choice ->
            QuizChoiceButton(
                text = choice,
                enabled = QuizStateHolder.answered == null,
                correct = QuizStateHolder.answered != null && i == item.answer,
                wrong = QuizStateHolder.answered == false && QuizStateHolder.chosen == i,
                onClick = {
                    QuizStateHolder.chosen = i
                    QuizStateHolder.answered = (i == item.answer) // 채점만. 점수·타이머 없음.
                },
            )
        }
        if (QuizStateHolder.answered != null) {
            val correct = QuizStateHolder.answered == true
            Text(
                text = if (correct) {
                    stringResource(R.string.parked_quiz_correct)
                } else {
                    stringResource(R.string.parked_quiz_wrong, item.choices[item.answer.coerceIn(item.choices.indices)])
                },
                style = AroTextStyles.bodySmall,
                color = if (correct) MaterialTheme.aroColors.informativeActive else MaterialTheme.aroColors.textSecondary,
            )
            // 해설 1~2줄(04문서 화면 3 — maxLines=2 로 강제).
            Text(
                text = item.explain,
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 2,
            )
            LargePrimaryButton(text = stringResource(R.string.parked_quiz_next)) {
                if (QuizStateHolder.quizIndex + 1 < quizzes.size) {
                    QuizStateHolder.quizIndex += 1 // 다음 문제
                    QuizStateHolder.answered = null
                    QuizStateHolder.chosen = null
                } else {
                    QuizStateHolder.stepIndex = 2 // 마지막 문항 후: 그때와 지금 스텝으로
                }
            }
        }
    }
}

/** 요약 타일 한 칸의 값·라벨(시안 09의 "4명 / 함께 온 인원" 구성). */
private data class RecordTile(val value: String, val label: String)

/**
 * 스텝5: 오늘의 기록(00문서 12절 T6 — 시안 09). 요약 타일 4개(스탬프 N/N · 함께 온 인원 ·
 * 함께 달린 거리 · 방문 장소 수) + "오늘의 기록이 저장됐어요." + [처음으로] 버튼.
 * 기존 완주 화면의 요소도 이 화면으로 이동 정리했다: 코스 완주 배지(StampBookCard)·
 * 거리(타일로)·혜택 미리보기(offers_demo). 타일은 "안 읽히면 숨김" 원칙 — partySize·tripKm
 * 신호가 없으면 해당 타일 자체를 그리지 않는다(04문서 화면 4).
 *
 * B1(2026-11-11): 완주 시 가족 배지 다음에 이어받을 사람 질문 카드가 1회 나타난다.
 * 질문 여부는 HeirQuestionPolicy 판정(ParkedScreen) — 정하지 않아도 배지·완주 기록은 유지된다.
 * B3(2026-11-11): metrics.family_total_trip_km 를 "우리 가족이 함께 달린 거리 N km"로 표시.
 * 누적값 없음·0 이면 줄을 숨긴다(FamilyTotalKmLine).
 *
 * @param isCompleted 완주 여부. true 면 '코스 완주!' 제목과 배지(스탬프북 카드)를 함께 보인다.
 * @param familyTotalKmLabel 가족 누적 거리 표시 문자열("1,240"). null = 없음/0 → 줄 숨김.
 * @param heirCard*  이어받을 사람 질문 카드 값(ParkedScreen 이 정책 판정·서버 호출을 담당).
 * @param onBackHome  [처음으로] 버튼 — 세션 resetCourse 후 스텝0(코스 선택)으로 돌아간다.
 */
@Composable
private fun RecordContent(
    course: Course,
    seed: SeedContent,
    bookDef: StampBookDef?,
    stampedIds: Set<String>,
    stampedCount: Int,
    stampTotal: Int,
    partySize: Int?,
    tripKm: Double?,
    isCompleted: Boolean,
    familyTotalKmLabel: String?,
    heirCardVisible: Boolean,
    heirCardCandidates: List<HeirCandidate>,
    heirCardBusy: Boolean,
    heirCardDesignatedName: String?,
    heirCardOwnerGuide: Boolean,
    heirCardFailed: Boolean,
    heirCardOnPick: (HeirCandidate) -> Unit,
    heirCardOnDefer: () -> Unit,
    modifier: Modifier = Modifier,
    onBackHome: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 코스 제목 + 방문 순서 한 줄(시안 09: 제목 아래 "박물관 → 대릉원 → 첨성대" 구성).
        Text(
            text = course.title,
            style = AroTextStyles.titleSmall,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = course.places.joinToString(" → ") { it.name },
            style = AroTextStyles.bodySmall,
            color = MaterialTheme.aroColors.textSecondary,
            maxLines = 2,
        )
        if (isCompleted) {
            // 코스 완주(기존 완주 화면 요소 이동) — 배지는 아래 스탬프북 카드가 함께 보여 준다.
            Text(
                text = stringResource(R.string.parked_course_complete),
                style = AroTextStyles.headline,
                color = MaterialTheme.aroColors.stampPoint,
            )
            StampBookCard(bookDef, stampedIds)
            // B1: 가족 배지 다음 — 이어받을 사람 질문 카드(1회). 거절해도 배지는 그대로다.
            HeirQuestionCard(
                visible = heirCardVisible,
                candidates = heirCardCandidates,
                busy = heirCardBusy,
                designatedName = heirCardDesignatedName,
                ownerGuide = heirCardOwnerGuide,
                failed = heirCardFailed,
                onPick = heirCardOnPick,
                onDefer = heirCardOnDefer,
            )
        }
        Text(
            text = stringResource(R.string.parked_record_saved),
            style = AroTextStyles.bodyMedium,
            color = MaterialTheme.aroColors.textPrimary,
        )
        // B3: 우리 가족이 함께 달린 거리 누적(0·없음이면 줄 숨김).
        FamilyTotalKmLine(totalKmLabel = familyTotalKmLabel)

        // 요약 타일 4개(시안 09): 값이 없는 타일(인원·거리)은 통째로 숨긴다.
        val tiles = buildList {
            add(
                RecordTile(
                    value = stringResource(R.string.parked_record_stamps_value, stampedCount, stampTotal),
                    label = stringResource(R.string.parked_record_tile_stamps),
                ),
            )
            if (partySize != null) {
                add(
                    RecordTile(
                        value = stringResource(R.string.parked_record_party_value, partySize),
                        label = stringResource(R.string.parked_record_tile_party),
                    ),
                )
            }
            if (tripKm != null) {
                add(
                    RecordTile(
                        value = stringResource(R.string.parked_record_distance_value, tripKm),
                        label = stringResource(R.string.parked_record_tile_distance),
                    ),
                )
            }
            add(
                RecordTile(
                    value = stringResource(R.string.parked_record_places_value, stampedCount),
                    label = stringResource(R.string.parked_record_tile_places),
                ),
            )
        }
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTiles.forEach { tile ->
                    RecordTileBox(tile, Modifier.weight(1f))
                }
                // 홀수 개일 때 빈 칸을 채워 타일 폭이 흐트러지지 않게 한다.
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // 혜택 미리보기(기존 완주 화면 요소 이동): 시드 offers_demo 전부 simulated=true 이므로
        // '예시' 표기와 함께만 나열한다(00문서 ParkedActivity 완주 절).
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.parked_offers_section),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SmallTag(stringResource(R.string.parked_demo_simulation_tag))
            }
            seed.offersDemo.take(2).forEach { offer ->
                SectionCard {
                    Text(offer.title, style = AroTextStyles.bodyMedium, color = MaterialTheme.aroColors.textPrimary, maxLines = 2)
                    Text(
                        text = offer.condition,
                        style = AroTextStyles.label,
                        color = MaterialTheme.aroColors.textTertiary,
                    )
                }
            }
        }

        // [처음으로] — 코스 세션을 초기화하고 스텝0(코스 선택)으로 돌아간다.
        LargePrimaryButton(text = stringResource(R.string.parked_record_back_home)) {
            onBackHome()
        }
    }
}

/** 스텝5 요약 타일 한 칸(시안 09의 값 크게·라벨 아래 구성). 배경은 보조 배경 토큰. */
@Composable
private fun RecordTileBox(tile: RecordTile, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.aroColors.backgroundSecondary,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = tile.value,
                style = AroTextStyles.titleMedium,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = tile.label,
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

// ── T9-3 서버 members 파싱(kotlinx.serialization — 로컬 DTO, 화면에 잠깐 쓰고 저장하지 않는다) ──

/**
 * members JSON 배열 항목 중 이름표에 필요한 필드만 받는 로컬 DTO.
 * 서버 항목 형태: {"id":1,"nickname":"...","generation":"...","role":"...","seat_role":"driver|
 * navigator|explorer"|null,"is_minor":false,...} — 이름표에 없는 필드는 모두 무시한다.
 */
@Serializable
private data class MemberNameDto(
    val nickname: String? = null,
    @SerialName("seat_role") val seatRole: String? = null,
)

/** members 응답 파싱 전용 Json(모르는 필드 무시 — 서버 필드 추가에 흔들리지 않게). */
private val membersJson = Json { ignoreUnknownKeys = true }

/** 화면 표시 순서 고정: 운전석 → 조수석 길잡이 → 뒷좌석 탐험가. */
private val SEAT_ROLE_ORDER = listOf("driver", "navigator", "explorer")

/**
 * getMembers 원본 JSON 문자열을 seat_role → nickname Map 으로 바꾼다.
 *  - 배열([ {...}, ... ]) 또는 {"members":[ ... ]} 감싸기 둘 다 흡수한다(서버 응답 형태 방어).
 *  - seat_role 이 없거나 닉네임이 빈 구성원은 건너뛴다. 하나도 없으면 빈 Map(→ 이름표 숨김).
 *  - 파싱 실패는 조용히 빈 Map — 이름표가 숨겨질 뿐 화면이 망가지지 않는다.
 */
internal fun parseSeatTags(body: String): Map<String, String> {
    val byRole = decodeMembers(body)
        .filter { !it.seatRole.isNullOrBlank() && !it.nickname.isNullOrBlank() }
        .associate { it.seatRole!! to it.nickname!! }
    return SEAT_ROLE_ORDER.mapNotNull { role -> byRole[role]?.let { role to it } }.toMap()
}

/** 배열 응답 1차 시도, {"members":[...]} 감싼 객체 2차 시도. 실패 시 빈 목록. */
private fun decodeMembers(body: String): List<MemberNameDto> {
    runCatching { return membersJson.decodeFromString<List<MemberNameDto>>(body) }
    return runCatching {
        val array = membersJson.parseToJsonElement(body).jsonObject["members"]?.jsonArray
            ?: return emptyList()
        array.map { membersJson.decodeFromJsonElement<MemberNameDto>(it) }
    }.getOrDefault(emptyList())
}

/**
 * 401/403(코드·PIN 불일치) 판정. ApiClient 기존 관례(postVisit)처럼 실패를
 * IOException("getMembers HTTP 401") 형태의 예외 메시지로 전달한다는 전제로 상태 코드만 본다.
 * 방어: 메시지에 코드가 없으면 '아니오'로 흘러 일반 실패(이름표 숨김 + lastError 1줄) 처리만 된다.
 */
private fun isAuthFailure(t: Throwable?): Boolean {
    val message = t?.message ?: return false
    // 숫자 일부 우연 일치(예: 1401)를 막기 위해 코드 앞뒤가 숫자가 아닐 때만 인정한다.
    return Regex("(^|[^0-9])(401|403)([^0-9]|$)").containsMatchIn(message)
}

// ── E2 then-now 서버 응답 파싱(2026-11-11, kotlinx.serialization 로컬 DTO — 화면에 잠깐 쓰고 저장하지 않는다) ──

/**
 * ⚠ 병렬 의존 지점(E2, 2026-11-11) — 이 함수가 파일에서 유일하게 신규 ApiClient API 를 부른다.
 *
 * 계약: GET /v1/places/{id}/then-now 의 원본 JSON 문자열(Result<String>). 병렬 담당이 추가한
 * 실제 이름은 [ApiClient.getPlaceThenNow](placeId, familyCode, pin)다 — 계약 초안의 이름
 * `getThenNow` 는 기존 B2 의 `getThenNow(familyCode, pin, placeId)`(/v1/families/me/then-now/)
 * 와 같은 이름·비슷한 인자라 오버로드 모호성으로 ui 호출이 깨지므로, 담당이 getPlaceThenNow 로
 * 바꿔 착지했다. 호출은 이 헬퍼 한 곳에만 둔다(이름이 다시 바뀌면 여기 한 줄만 고치면 된다).
 *  - 임시 폴백: 신규 메서드가 없는 환경에서는 `api.getThenNow(familyCode, pin, placeId)`(기존
 *    B2 별칭 경로)로 바꿔도 화면은 같게 동작한다 — 서버가 두 경로(/v1/places/{id}/then-now 와
 *    /v1/families/me/then-now/{place_id})에서 같은 응답 본문을 돌려주기 때문이다
 *    (backend routers/places.py `_then_now_response` 공용).
 */
private suspend fun fetchThenNowBody(
    api: ApiClient,
    placeId: String,
    familyCode: String?,
    pin: String?,
): Result<String> = api.getPlaceThenNow(placeId, familyCode, pin)

/** then-now 응답 파싱 전용 Json(모르는 필드 무시 — 서버 필드 추가에 흔들리지 않게). */
private val thenNowJson = Json { ignoreUnknownKeys = true }

/** 사진 속 이름표 표시 상한(명) — 서버 MAX_MARKERS·웹 안내 '최대 10명'과 통일(02문서 13절). */
private const val MARKER_NAME_LIMIT = 10

/**
 * then-now 응답의 'past' 블록 중 화면에 필요한 필드만 받는 로컬 DTO.
 * 서버 형태: {"kind":"memory"|"visit", "year":1994, "years_ago":32, "note":"…",
 * "photo_url":"/uploads/…", "vehicle_label":"…", "party_size":4, "markers":[…]}.
 * markers(B5, 2026-11-12): 가족이 웹 '그때 기록하기'에서 사진 위를 직접 눌러 붙인 이름표다 —
 * 탑승자 자동 식별 금지('누가 탔는지' 표시 금지, 04문서 화면 4)와 다른 것으로, 가족이 직접
 * 적은 이름만 표시 대상이다. 차량 화면에서는 이름만 글자로 보여 주고 점(x·y 좌표)은 그리지
 * 않는다(차량에서 입력·수정 없음 — 07문서 B5).
 */
@Serializable
private data class ThenNowPastDto(
    val kind: String? = null,
    val year: Int? = null,
    @SerialName("years_ago") val yearsAgo: Int? = null,
    val note: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("vehicle_label") val vehicleLabel: String? = null,
    @SerialName("party_size") val partySize: Int? = null,
    val markers: List<ThenNowMarkerDto> = emptyList(), // 옛 응답에 필드가 없으면 빈 목록(기본값).
)

/**
 * past.markers 한 항목의 로컬 DTO — 서버 형태: {"x":0.3, "y":0.4, "name":"할아버지"}.
 * x·y는 사진 크기에 대한 비율(0~1)이지만 차량 화면에는 점을 그리지 않으므로 받아만 둔다(표시 미사용).
 * name 을 null 허용으로 둔 것은 방어다 — 이름 없는 항목이 섞여도 응답 전체 파싱이 깨지지 않게.
 */
@Serializable
private data class ThenNowMarkerDto(
    val x: Float? = null,
    val y: Float? = null,
    val name: String? = null,
)

/** then-now 응답의 'now' 블록 — 이번 방문 기록. 방문 기록이 전무하면 서버가 now 도 null 로 보낸다. */
@Serializable
private data class ThenNowNowDto(
    @SerialName("visited_at") val visitedAt: String? = null,
    @SerialName("vehicle_label") val vehicleLabel: String? = null,
    @SerialName("party_size") val partySize: Int? = null,
    @SerialName("trip_km") val tripKm: Double? = null,
)

/** then-now 응답의 'heritage' 블록 — 시드의 domain.HeritageInfo 와 이름이 겹치지 않게 별도 명명. */
@Serializable
private data class ThenNowHeritageDto(
    val name: String? = null,
    val year: Int? = null,
    val label: String? = null,
)

/** then-now 응답 전체. 필드가 빠지거나 형태가 달라도 null 기본값으로 흡수한다(방어 — 단일 객체 응답). */
@Serializable
private data class ThenNowResponseDto(
    @SerialName("place_id") val placeId: String? = null,
    val past: ThenNowPastDto? = null,
    val now: ThenNowNowDto? = null,
    val heritage: ThenNowHeritageDto? = null,
    @SerialName("visit_ordinal") val visitOrdinal: Int? = null,
    @SerialName("past_kind") val pastKind: String? = null, // 방어: kind 가 최상위 past_kind 로 오는 형태도 흡수.
)

/**
 * getThenNow 원본 JSON 을 화면 표시값(ServerThenNowInfo — ThenNowExtras.kt)으로 바꾼다.
 *  - 파싱 실패는 null — 호출부가 예시(시드) 폴백으로 조용히 돌아간다.
 *  - past 는 ThenNowResolver 와 같은 방어를 적용한다: 연도가 없거나 0 이하, 현재 연도보다 큰
 *    '미래 과거'는 과거 기록으로 인정하지 않고 past=null(→ 첫 방문 문구)로 둔다.
 *  - now.party_size·now.trip_km 는 있을 때만 화면에 나온다(04문서 화면 4 — 없으면 숨김).
 *  - B5(2026-11-12): past.markers 는 이름만 순서 그대로 뽑아 markerNames 로 넘긴다. 빈 이름은
 *    건너뛰고, 상한 10명(02문서 — 서버 MAX_MARKERS·웹 안내와 통일)까지만 받는다. 하나도 없으면
 *    빈 목록 — 사진 아래 '함께한 사람' 줄은 스스로 숨는다(ThenMarkerNamesLine 계약).
 */
internal fun parseThenNowServerData(body: String): ServerThenNowInfo? {
    val dto = runCatching { thenNowJson.decodeFromString<ThenNowResponseDto>(body) }.getOrNull()
        ?: return null
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val past = dto.past?.let { p ->
        val year = p.year
        if (year == null || year <= 0 || year > thisYear) {
            null // 비정상·미래 연도 — 과거 기록으로 인정하지 않는다(ThenNowResolver 규칙과 동일).
        } else {
            ThenNowServerPast(
                year = year,
                yearsAgo = p.yearsAgo ?: (thisYear - year), // years_ago 서버값 우선, 없으면 직접 계산.
                note = p.note?.takeIf { it.isNotBlank() },
                photoUrl = p.photoUrl?.takeIf { it.isNotBlank() },
                partySize = p.partySize,
                vehicleLabel = p.vehicleLabel?.takeIf { it.isNotBlank() },
                kind = p.kind ?: dto.pastKind, // kind 는 past 안, past_kind 는 최상위 — 둘 다 흡수(방어).
                markerNames = p.markers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }.take(MARKER_NAME_LIMIT),
            )
        }
    }
    return ServerThenNowInfo(
        past = past,
        nowPartySize = dto.now?.partySize,
        nowTripKm = dto.now?.tripKm,
        visitOrdinal = dto.visitOrdinal,
    )
}

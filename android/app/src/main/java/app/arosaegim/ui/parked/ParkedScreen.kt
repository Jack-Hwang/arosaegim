package app.arosaegim.ui.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.arosaegim.AppContainer
import app.arosaegim.R
import app.arosaegim.data.SeedContent
import app.arosaegim.domain.Course
import app.arosaegim.domain.Place
import app.arosaegim.domain.QuizItem
import app.arosaegim.domain.StampBookDef
import app.arosaegim.fallback.ManualDrivingState
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.aroColors
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

    // TripSession 공개 API 는 '찍힌 개수'만 주므로, 코스 방문 순서(= place_ids 순서)의
    // 앞 N개를 찍힌 스탬프로 계산한다(도착 순서가 곧 코스 순서라는 세션 계약 전제).
    val stampedIds = remember(stampedCount) { course.places.take(stampedCount).map { it.id }.toSet() }
    val bookDef = remember(lastArrival?.id) { pickBookDef(seed, lastArrival?.id) }
    val stampTotal = bookDef?.placeIds?.size ?: course.places.size

    // ── 스텝0 판정: 스탬프가 0개이고 코스를 시작하지 않았으면(로컬 플래그) 코스 선택을 먼저 본다.
    val showCourseSelect = stampedCount == 0 && !CourseStartState.courseStarted

    BoxWithConstraints(Modifier.fillMaxSize()) {
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
                                // T9-3 서버 실데이터(구성원 자리 이름표) — 세션 없음·실패면 null 로 숨김.
                                seatTags = seatTags,
                                seatAuthError = seatAuthError,
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
 * @param isCompleted 완주 여부. true 면 '코스 완주!' 제목과 배지(스탬프북 카드)를 함께 보인다.
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
        }
        Text(
            text = stringResource(R.string.parked_record_saved),
            style = AroTextStyles.bodyMedium,
            color = MaterialTheme.aroColors.textPrimary,
        )

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

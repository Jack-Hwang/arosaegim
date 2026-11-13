package app.arosaegim.ui.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.MenuButton
import app.arosaegim.ui.common.PageNavigationBar
import app.arosaegim.ui.common.aroColors

/*
 * 홈 화면(F10 T3, 2026-11-14) — 세션이 있고 정차 중일 때 앱이 처음 보여 주는 화면(AC09).
 *
 * 보이는 규칙(순수 판정은 [HomeGate], 화면 상태는 [HomeModeState]·[HomeGateState] 홀더):
 *  - 세션 + 정차 + 도착 아님 → 홈이 첫 화면이다(콜드·재진입 모두 — AC09).
 *  - 도착 이벤트로 정차 화면이 올라올 때는 홈이 가로채지 않고 기존대로 스탬프 카드(스텝1)가
 *    먼저다(AC20 — 마지막 도착 변화를 [HomeGateState] 가 기록한다).
 *  - 주행(D)이 되면 홈은 닫히고 주행 화면이 뜬다(ModeRouter 무변경 — ParkedScreen 안의
 *    '어떤 내용을 보여 줄까' 규칙만 더한다).
 *
 * 내용은 HomeContent.resolve 결과를 그린다(시안 38·39 배치 — 큰 카드·큰 버튼).
 * 값이 없는 줄은 그 줄만 숨기고 오류 문구는 만들지 않는다(AC23).
 * 안전 규칙: 터치 영역 76dp 이상, 색·글자는 토큰만, 애니메이션 없음.
 */

/** 홈 표시 판정 입력(순수 — JVM 단위 시험 대상). */
data class HomeGateInputs(
    val hasSession: Boolean,
    val driving: Boolean,
    /** 마지막 도착이 아직 이번 보임 구간에서 확인되지 않았다(도착 직후). */
    val arrivalUnseen: Boolean,
    /** 사용자의 홈 선택(null = 선택 없음 — 진입 자동 판정). */
    val homeSelected: Boolean?,
)

object HomeGate {

    /**
     * 정차 화면에서 홈을 보여 줄지 판정한다(순수 함수).
     *  - 세션 없음·주행 → 홈 아님(시작 화면 규칙·주행 최소 정보 원칙과 같은 방향).
     *  - 도착 직후 → 홈 아님 — 스탬프 카드(스텝1)가 먼저다(AC20).
     *  - 사용자 선택이 있으면 그대로 따른다(메뉴·홈 버튼의 결과).
     *  - 그 외(정차 + 세션 + 도착 아님) → 홈이 첫 화면이다(AC09).
     */
    fun shouldShowHome(inputs: HomeGateInputs): Boolean = when {
        !inputs.hasSession -> false
        inputs.driving -> false
        inputs.arrivalUnseen -> false
        inputs.homeSelected != null -> inputs.homeSelected
        else -> true
    }
}

/**
 * 홈 표시 모드 홀더 — QuizStateHolder 와 같은 방식의 프로세스 수명 object(F10 T3).
 * null = 사용자 선택 없음(진입마다 [HomeGate] 가 자동 판정), true = 홈, false = 정차 본문.
 */
object HomeModeState {

    var showingHome by mutableStateOf<Boolean?>(null)

    /** 홈을 선택한다 — 메뉴 '홈' 항목 처리용. */
    fun selectHome() {
        showingHome = true
    }

    /** 정차 본문(스텝·코스 선택)을 선택한다 — 항목·버튼 처리용. */
    fun selectBody() {
        showingHome = false
    }

    /** 주행 전환 — 홈을 닫는다(주행 화면이 뜬다). */
    fun closeForDriving() {
        showingHome = false
    }

    /** 화면 진입(앞으로 옴)마다 선택을 지운다 — 다음 판정은 HomeGate 자동 판정으로 돌아간다. */
    fun clearSelection() {
        showingHome = null
    }
}

/**
 * 도착 장면 보호 상태 홀더(F10 AC20) — 화면이 앞으로 올 때마다 마지막 도착을 확인해
 * '도착 직후'인지를 기록한다. 콜드 시작에서 저장에서 복원된 도착은 도착 이벤트가 아니므로
 * 보호하지 않는다(첫 진입은 항상 자동 판정 — AC09). 진입은 ParkedScreen 의 화면 수명
 * 관찰(ON_RESUME)과 첫 구성 1회에서 [onEntry] 로 기록하고, 화면이 가려질 때(ON_PAUSE)
 * [onHidden] 이 '다음 진입은 새 열림' 표시를 남긴다.
 */
object HomeGateState {

    private var initialized = false
    private var hiddenSinceEntry = false

    /** 마지막으로 화면에서 확인한 도착 장소 id — 다음 진입 때의 '새 도착' 판정 기준. */
    var lastEntryArrivalId: String? = null
        private set

    /** 이번 보임 구간이 도착 직후인지(홈이 스탬프 카드를 가로채지 않게 하는 근거). */
    var arrivalUnseen by mutableStateOf(false)
        private set

    /** 화면이 가려질 때(주행 전환·백그라운드) — 다음 진입은 새 '열림'이다. */
    fun onHidden() {
        hiddenSinceEntry = true
    }

    /**
     * 화면이 앞으로 올 때 — 같은 보임 구간의 재호출(진입 직후 ON_RESUME 중복 등)은
     * 판정을 다시 바꾸지 않는다. 가려졌다가 돌아왔을 때만 도착 직후 여부를 다시 계산한다.
     */
    fun onEntry(currentArrivalId: String?) {
        when {
            !initialized -> {
                // 첫 진입(콜드·복원) — 저장에서 읽은 도착은 도착 이벤트가 아니므로 보호하지 않는다.
                arrivalUnseen = false
            }
            hiddenSinceEntry -> {
                arrivalUnseen = currentArrivalId != null && currentArrivalId != lastEntryArrivalId
            }
        }
        lastEntryArrivalId = currentArrivalId
        initialized = true
        hiddenSinceEntry = false
    }
}

/**
 * HomeScreen — 정차 화면의 홈(F10 T3). [model] 은 HomeContent.resolve 결과다.
 * 값은 모두 모델에서 읽고, 없는 줄은 그 줄만 숨긴다(오류 문구 금지 — AC23).
 */
@Composable
fun HomeScreen(
    model: HomeModel,
    showMenuButton: Boolean,
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit = {},
    onContinue: () -> Unit = {},
    onOtherCourses: () -> Unit = {},
    onOpenThenNow: () -> Unit = {},
    onPickCourse: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.aroColors.backgroundPrimary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 머리글(Page Navigation — AC01) + [☰ 메뉴](F10 T2 — 정차 화면과 같은 자리).
        PageNavigationBar(
            courseName = null,
            trailingAction = if (showMenuButton) {
                {
                    MenuButton(
                        label = stringResource(R.string.menu_label),
                        onClick = onOpenMenu,
                    )
                }
            } else {
                null
            },
        )
        Spacer(Modifier.height(4.dp))
        // 화면 제목(시안 38 상단 — "홈" 한 글자).
        Text(
            text = stringResource(R.string.home_header),
            style = AroTextStyles.titleMedium,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))

        if (model.firstRecord) {
            // ③ 첫 기록 모드 — 지난 방문 기록도 진행 중 코스도 없을 때(AC11).
            SectionCard {
                Text(
                    text = stringResource(R.string.home_first_title),
                    style = AroTextStyles.titleMedium,
                    color = MaterialTheme.aroColors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.home_first_sub),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textSecondary,
                    maxLines = 2,
                )
                LargePrimaryButton(text = stringResource(R.string.home_first_button)) {
                    onPickCourse()
                }
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ① 이어서 하기 카드(AC10) — 진행 중 코스가 있을 때만 온다.
                model.resume?.let { resume ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.home_resume_label),
                                style = AroTextStyles.titleSmall,
                                color = MaterialTheme.aroColors.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            SmallTag(stringResource(R.string.home_resume_course_tag))
                        }
                        Text(
                            text = resume.courseName,
                            style = AroTextStyles.titleMedium,
                            color = MaterialTheme.aroColors.textPrimary,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.home_stamp_format, resume.stampCount, resume.stampTotal),
                            style = AroTextStyles.bodySmall,
                            color = MaterialTheme.aroColors.textPrimary,
                            maxLines = 1,
                        )
                        // 다음 목표가 없으면 이 줄만 숨긴다.
                        resume.nextPlaceName?.let { next ->
                            Text(
                                text = stringResource(R.string.home_next_place, next),
                                style = AroTextStyles.bodySmall,
                                color = MaterialTheme.aroColors.textPrimary,
                                maxLines = 1,
                            )
                        }
                        // 거리 한 줄 — TripSession 이 준 값(포맷분)만. 없으면 숨긴다(가짜 금지).
                        resume.distanceText?.let { distance ->
                            Text(
                                text = stringResource(R.string.home_distance_format, distance),
                                style = AroTextStyles.bodySmall,
                                color = MaterialTheme.aroColors.textSecondary,
                                maxLines = 1,
                            )
                        }
                        LargePrimaryButton(text = stringResource(R.string.home_resume_depart)) {
                            onContinue()
                        }
                        LargeSecondaryButton(text = stringResource(R.string.home_resume_other)) {
                            onOtherCourses()
                        }
                    }
                }
                // ② 지난 방문 카드 — 마지막 방문 기록이 있을 때만 온다.
                model.pastVisit?.let { past ->
                    SectionCard {
                        Text(
                            text = stringResource(R.string.home_past_label),
                            style = AroTextStyles.titleSmall,
                            color = MaterialTheme.aroColors.textPrimary,
                            maxLines = 1,
                        )
                        Text(
                            text = past.placeName,
                            style = AroTextStyles.bodyMedium,
                            color = MaterialTheme.aroColors.textPrimary,
                            maxLines = 2,
                        )
                        LargeSecondaryButton(text = stringResource(R.string.home_past_button)) {
                            onOpenThenNow()
                        }
                    }
                }
            }
        }
    }
}

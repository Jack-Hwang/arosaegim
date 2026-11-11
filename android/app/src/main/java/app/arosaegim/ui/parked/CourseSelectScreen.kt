package app.arosaegim.ui.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.arosaegim.AppContainer
import app.arosaegim.R
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.PageNavigationBar
import app.arosaegim.ui.common.aroColors

/*
 * 스텝0: 코스 선택 화면 (00문서 12절 T6 — 시안 02, 2026-11-11 신규).
 *
 * 시안 02 구성(픽셀 복사 금지 — 위계만 따른다):
 *   PageNavigationBar(Back 없음) → Tabs(Box: "코스" / "내 기록") → 코스 카드(제목·요약·
 *   장소 3개 미리보기·스탬프 진행 N/3) → 주 버튼 1(이 코스로 출발) + 보조 버튼 1(코스 소개
 *   더 보기 — 접기/펼치기, 애니메이션 없이 visible 토글) → 출발 안내 꼬리 한 줄.
 *
 * ParkedScreen 이 스탬프 0개 + 코스 미시작(로컬 플래그 CourseStartState)일 때 이 화면을
 * 먼저 보여 주고, 출발 버튼 → onCourseStarted 콜백 → Parked 스텝1(스탬프 카드)로 넘어간다.
 * 시드 코스는 ParkedScreen 과 같은 방식으로 container.seedRepository.load(context) 를 읽는다.
 *
 * 안전 규칙(04문서): 애니메이션 없음, 본문 문단 3줄 이내, 색은 MaterialTheme.aroColors 토큰만.
 */
@Composable
fun CourseSelectScreen(
    container: AppContainer,
    stampedCount: Int,
    onCourseStarted: () -> Unit,
) {
    val context = LocalContext.current
    // 시드 파일 1회 읽기 — ParkedScreen 이 이미 로드하는 방식과 동일하다(SeedRepository 계약:
    // 실패 시 빈 코스로 복구). 펼치기(코스 소개)에서 장소 3곳의 이름·주소를 함께 보인다.
    val seed = remember { container.seedRepository.load(context) }
    val course = seed.course

    // 탭 선택("코스"=0 / "내 기록"=1). 내 기록 탭은 이번 판 범위 밖이라 '준비 중' 자리표시만 둔다.
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // 보조 버튼 "코스 소개 더 보기" 접기/펼치기 상태. 애니메이션 없이 visible 만 토글한다.
    var introExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.aroColors.backgroundPrimary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 상단 헤더: Back 없음(정차 화면 안의 첫 단계라 뒤로 갈 곳이 없다).
        PageNavigationBar(courseName = null)
        Spacer(Modifier.height(8.dp))

        // Tabs(Box): "코스" / "내 기록" 2탭. ParkedPageNavigationBar 를 재사용한다(동일 규격).
        ParkedPageNavigationBar(
            labels = listOf(
                stringResource(R.string.course_select_tab_courses),
                stringResource(R.string.course_select_tab_records),
            ),
            selected = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedTab == 1) {
                // "내 기록" 탭 — 이번 판 범위 밖. 플레이스홀더만 표시한다.
                SectionCard {
                    Text(
                        text = stringResource(R.string.course_select_records_placeholder),
                        style = AroTextStyles.bodyMedium,
                        color = MaterialTheme.aroColors.textPrimary,
                    )
                }
            } else if (course.places.isEmpty()) {
                // 시드 로드 실패(빈 코스) 복구 화면 — 출발 버튼을 둘 수 없다.
                SectionCard {
                    Text(
                        text = stringResource(R.string.course_select_empty),
                        style = AroTextStyles.bodyMedium,
                        color = MaterialTheme.aroColors.textPrimary,
                    )
                }
            } else {
                // 코스 카드(시안 02): 제목 · 요약 · 장소 3개 미리보기 · 스탬프 진행 N/3.
                SectionCard {
                    Text(
                        text = course.title,
                        style = AroTextStyles.titleMedium,
                        color = MaterialTheme.aroColors.textPrimary,
                        maxLines = 1,
                    )
                    // 요약: 코스 장소 이름 잇기(시안 02 "국립경주박물관 · 대릉원 · 첨성대" 위치).
                    // 도메인 Course 에 summary 필드가 없어(SeedRepository 가 시드 summary 를 버린다)
                    // 장소 이름으로 요약 한 줄을 만든다 — 3줄 이내 강제.
                    Text(
                        text = course.places.take(3).joinToString(" · ") { it.name },
                        style = AroTextStyles.bodySmall,
                        color = MaterialTheme.aroColors.textSecondary,
                        maxLines = 2,
                    )
                    // 장소 3개 미리보기: 스탬프 칸 모양 원 3개(1·2·3).
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(course.places.size.coerceAtMost(3)) { i ->
                            CourseSlotPreview(index = i + 1)
                        }
                    }
                    // 코스 규모 + 지난 방문 기록(시안 02 "3곳 · 약 반나절 · 지난 방문 기록 있음" 위치).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.course_select_places_line, course.places.size),
                            style = AroTextStyles.label,
                            color = MaterialTheme.aroColors.textSecondary,
                        )
                        if (stampedCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            SmallTag(stringResource(R.string.course_select_history_line))
                        }
                    }
                    // 스탬프 진행 N/3(지난 세션에서 찍힌 개수 — 세션 시작 전엔 0/3).
                    Text(
                        text = stringResource(R.string.parked_stamp_progress, stampedCount, course.places.size),
                        style = AroTextStyles.bodySmall,
                        color = MaterialTheme.aroColors.textPrimary,
                    )
                }

                // 보조 버튼: 코스 소개 더 보기(접기/펼치기 — 애니메이션 없이 visible 토글).
                LargeSecondaryButton(
                    text = stringResource(
                        if (introExpanded) R.string.course_select_fold else R.string.course_select_more,
                    ),
                ) { introExpanded = !introExpanded }
                if (introExpanded) {
                    // 펼친 코스 소개: 장소별 이름·주소 목록. 주행 중 표시용이 아니므로 정차 화면 한도 내.
                    SectionCard {
                        course.places.take(3).forEach { place ->
                            Text(
                                text = place.name,
                                style = AroTextStyles.bodySmall,
                                color = MaterialTheme.aroColors.textPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = place.address,
                                style = AroTextStyles.label,
                                color = MaterialTheme.aroColors.textTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // 주 버튼: 이 코스로 출발 → onCourseStarted 콜백(ParkedScreen 이 스텝1로 넘긴다).
                LargePrimaryButton(text = stringResource(R.string.course_select_start)) {
                    onCourseStarted()
                }
                // 출발 안내 꼬리 한 줄(시안 02 하단 문구 위치).
                Text(
                    text = stringResource(R.string.course_select_footnote),
                    style = AroTextStyles.label,
                    color = MaterialTheme.aroColors.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 코스 카드의 장소 미리보기 칸(원 1·2·3 — 시안 02의 점선 스탬프 칸 구성을 단색 테두리 원으로 단순화). */
@Composable
private fun CourseSlotPreview(index: Int) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.aroColors.textQuaternary, RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$index",
            style = AroTextStyles.label.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.aroColors.textSecondary,
        )
    }
}

package app.arosaegim.ui.parked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arosaegim.R
import app.arosaegim.domain.HeirCandidate
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.aroColors

/*
 * 가족 기록 신규 카드 모음(B1~B3, 2026-11-11). ThenNowExtras.kt·ParkedComponents.kt 가
 * ~300줄 규칙을 넘기지 않게 별도 파일로 둔다.
 *
 * 공통 원칙(계약): 새 차량 신호 없음 · 주행 중 표시 없음(정차 화면에만 놓인다 — ParkedActivity 는
 * 주행 중 시스템이 가린다) · 값 없으면 숨김(가짜 금지) · 저장은 서버 가족 기록에(로컬 영속 금지).
 * 색은 MaterialTheme.aroColors 토큰만, 애니메이션 없음(04문서).
 */

/**
 * B1: '이어받을 사람 정하기' 질문 카드(시안 19) — 코스 3/3 완주 직후 정차 화면에서
 * 가족 배지 다음에 1회만 보인다. 질문 여부 판정은 [app.arosaegim.domain.HeirQuestionPolicy] 가
 * 하고(이미 정해짐·나중에 정하기 누름·주행 중에는 질문하지 않는다), 카드는 표시와 선택뿐이다.
 *
 * - 등록 가족 구성원 버튼(GET /v1/families/me/members)을 누르면 [onPick] → POST heir.
 * - 403(owner 아님 — 차량 세션에는 owner 권한이 없다)이면 버튼은 숨기지 않고 [ownerGuide] 안내
 *   한 줄('스마트폰의 가족 관리에서 정해 주세요')을 보인다(계약).
 * - PIN 없음(차량은 PIN 저장 금지 — 00문서 10.6)일 때도 같은 안내로 전환한다(계약). 이때는
 *   구성원 목록을 못 읽어 버튼이 만들어지지 않으며, [shouldShowHeirPinGuide](HeirPinGuide.kt)
 *   판정이 [ownerGuide]=true 와 [visible] 을 함께 켠다(ParkedScreen).
 * - '나중에 정하기' → [onDefer] (세션 메모리에 기록 — 이 코스에서 재문 금지).
 * - [designatedName] 이 있으면 지정 완료 상태 한 줄(버튼 대신)을 보인다.
 * - 문구에 '법적 상속이 아니라 가족이 직접 정하는 약속' 한 줄을 반드시 포함한다(계약).
 *
 * @param visible        정책 판정 결과(계산값). false 면 카드 자체를 그리지 않는다(재문 금지 포함).
 * @param candidates     등록 가족 구성원(닉네임 버튼). 빈 목록이면 호출부가 애초에 질문하지 않는다.
 * @param busy           POST 진행 중 — 중복 제출을 막기 위해 버튼을 비활성화한다.
 * @param designatedName 지정 완료 구성원 이름(null = 아직 미지정).
 * @param ownerGuide     안내 한 줄 표시 여부(403 owner 아님·PIN 없음 공용 문구). 버튼은 그대로 둔다.
 * @param failed         POST 실패(403 외) 안내 표시 여부. 재시도 가능 — 버튼은 그대로 둔다.
 */
@Composable
internal fun HeirQuestionCard(
    visible: Boolean,
    candidates: List<HeirCandidate>,
    busy: Boolean,
    designatedName: String?,
    ownerGuide: Boolean,
    failed: Boolean,
    onPick: (HeirCandidate) -> Unit,
    onDefer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible && designatedName == null) return // 질문 대상 아님 + 지정 완료 아님 = 그리지 않는다.
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.heir_question_title),
                style = AroTextStyles.titleSmall,
                color = MaterialTheme.aroColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            SmallTag(stringResource(R.string.parked_demo_simulation_tag))
        }
        // 법적 상속 아님 명시(계약) — 질문·지정 완료 상태 모두에서 함께 보인다.
        Text(
            text = stringResource(R.string.heir_question_note),
            style = AroTextStyles.label,
            color = MaterialTheme.aroColors.textTertiary,
            maxLines = 1,
        )
        if (designatedName != null) {
            // 지정 완료 — 정하지 않아도 배지·완주 기록은 그대로 유지된다(계약). 이 한 줄만 더 보인다.
            Text(
                text = stringResource(R.string.heir_question_designated, designatedName),
                style = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.aroColors.stampPoint,
                maxLines = 1,
            )
            return@SectionCard
        }
        // 등록 가족 구성원 버튼 — 서버 실데이터(가족 기록)만 쓴다. 차량 글자 입력은 만들지 않는다.
        candidates.forEach { candidate ->
            Button(
                onClick = { onPick(candidate) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(candidate.nickname, fontSize = 20.sp, maxLines = 1)
            }
        }
        if (ownerGuide) {
            // 403(owner 아님)·PIN 없음 공용 안내: 버튼은 숨기지 않고 안내 한 줄만 추가한다(계약).
            Text(
                text = stringResource(R.string.heir_question_owner_guide),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 2,
            )
        }
        if (failed) {
            Text(
                text = stringResource(R.string.heir_question_failed),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textTertiary,
                maxLines = 1,
            )
        }
        TextButton(onClick = onDefer, enabled = !busy) {
            Text(
                text = stringResource(R.string.heir_question_defer),
                fontSize = 18.sp,
                color = MaterialTheme.aroColors.textSecondary,
            )
        }
    }
}

/**
 * B2: 첫 기록 문구(visit_ordinal == 1) — '그때' 칸이 빈 상태일 때 서버가 첫 방문임을 확인한
 * 경우에만 빈 칸 대신 보인다. 서버 필드 없음·조회 실패면 이 컴포저블을 호출하지 않는다
 * (추측으로 만들지 않고 기존 빈 칸/숨김을 유지하는 것은 호출부 책임 — ThenNowStep 참조).
 *
 * @param todayPartyCount 오늘 함께 온 인원(좌석 점유 — 읽히면 '오늘은 N명이 함께 왔어요'까지만
 *                        표시하고 그때 인원 비교는 하지 않는다. null 이면 이 줄도 숨긴다).
 */
@Composable
internal fun FirstRecordContent(todayPartyCount: Int?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.thennow_first_record_title),
            style = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 2,
        )
        Text(
            text = stringResource(R.string.thennow_first_record_sub),
            style = AroTextStyles.bodySmall,
            color = MaterialTheme.aroColors.textSecondary,
            maxLines = 2,
        )
        if (todayPartyCount != null) {
            Text(
                text = stringResource(R.string.thennow_first_record_today_line, todayPartyCount),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/**
 * B3: 함께 달린 거리 누적 한 줄 — metrics.family_total_trip_km.
 * "우리 가족이 함께 달린 거리 N km". 누적값이 없거나(조회 실패·필드 없음) 0 이하면 호출부가
 * 이 줄 자체를 그리지 않는다(숨김). N 은 [app.arosaegim.domain.formatFamilyTotalTripKm] 로
 * 소수점 정리·천 단위 콤마("1,240") 처리된 문자열이다.
 */
@Composable
internal fun FamilyTotalKmLine(totalKmLabel: String?, modifier: Modifier = Modifier) {
    if (totalKmLabel.isNullOrBlank()) return // 누적값 없음/0 = 줄 숨김(가짜 값 금지).
    Text(
        text = stringResource(R.string.record_family_total_km, totalKmLabel),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        style = AroTextStyles.bodyMedium,
        color = MaterialTheme.aroColors.textPrimary,
        maxLines = 1,
    )
}

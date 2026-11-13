package app.arosaegim.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.PageNavigationBar
import app.arosaegim.ui.common.aroColors

/*
 * 가족 전환 확인 화면(13번 문서 T4 설정·가족 전환 — 시안 43, 2026-11-14 신규).
 *
 * 시안 43 구성(위계만 따른다): 제목(family_switch_title) → '지워져요' 목록 5줄(가족 코드·PIN·
 * 이 차에 남은 방문 기록·전송 대기 기록·진행 중이던 코스와 퀴즈) → '그대로 남아요' 목록 3줄
 * (서버의 가족 기록·스마트폰에서 올린 사진·포인트) → [로그아웃하고 바꾸기](주 버튼)·[취소]
 * → 안내 두 줄(family_switch_note_park·family_switch_note_lock).
 *
 * 경계 규약: 이 화면은 SignOutFlow 를 직접 호출하지 않는다 — 세션·저장소·Activity 를 전혀
 * 참조하지 않고, [로그아웃하고 바꾸기]는 onConfirm 콜백으로만 답한다. 실제 SignOutFlow 실행과
 * 시작 화면(가족 코드 입력) 열기는 이 화면을 여는 쪽(통합)이 한다. [취소]는 onCancel 로
 * 이전 화면(설정)으로 돌아간다.
 *
 * PIN 규칙(00문서 10.6): 목록의 'PIN' 줄은 지워질 항목 이름일 뿐이다 — 이 화면은 PIN 값을
 * 파라미터로 받지도, 화면에 보이지도, 어디에 남기지도 않는다.
 *
 * 안전 규칙(04문서): 애니메이션 없음 · 색은 MaterialTheme.aroColors 토큰만 · 본문 26sp 하한
 * (AroTextStyles.bodySmall) · 터치 영역 76dp 이상 · 로그 없음.
 */

/**
 * 가족 전환 확인 화면.
 *
 * @param onConfirm [로그아웃하고 바꾸기] 눌림 — SignOutFlow 실행·시작 화면 열기는 통합 몫.
 * @param onCancel  [취소] 눌림 — 이전 화면(설정)으로 돌아간다.
 * @param modifier  바깥 여백 등 화면 크기 제어용.
 */
@Composable
fun FamilySwitchConfirmScreen(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.aroColors.backgroundPrimary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PageNavigationBar(courseName = null)
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.family_switch_title),
                style = AroTextStyles.titleMedium,
                color = MaterialTheme.aroColors.textPrimary,
            )

            // '지워져요' — 이 차에서 가족을 바꾸면 사라지는 것 5가지(시안 43 순서 그대로).
            SettingsSectionCard {
                Text(
                    text = stringResource(R.string.family_switch_lose_title),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                )
                FamilySwitchItem(text = stringResource(R.string.family_switch_lose_code))
                FamilySwitchItem(text = stringResource(R.string.family_switch_lose_pin))
                FamilySwitchItem(text = stringResource(R.string.family_switch_lose_visits))
                FamilySwitchItem(text = stringResource(R.string.family_switch_lose_queue))
                FamilySwitchItem(text = stringResource(R.string.family_switch_lose_progress))
            }

            // '그대로 남아요' — 서버·스마트폰에 남는 것 3가지(시안 43 순서 그대로).
            SettingsSectionCard {
                Text(
                    text = stringResource(R.string.family_switch_keep_title),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                )
                FamilySwitchItem(text = stringResource(R.string.family_switch_keep_server))
                FamilySwitchItem(text = stringResource(R.string.family_switch_keep_photos))
                FamilySwitchItem(text = stringResource(R.string.family_switch_keep_points))
            }

            LargePrimaryButton(text = stringResource(R.string.family_switch_confirm)) {
                onConfirm()
            }
            SettingsSecondaryButton(text = stringResource(R.string.family_switch_cancel)) {
                onCancel()
            }

            Text(
                text = stringResource(R.string.family_switch_note_park),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textTertiary,
            )
            Text(
                text = stringResource(R.string.family_switch_note_lock),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textTertiary,
            )
        }
    }
}

/** 목록 한 줄 — 불릿 글리프 + 문구(시안 43의 두 목록 공용). */
@Composable
private fun FamilySwitchItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "·",
            style = AroTextStyles.titleMedium,
            color = MaterialTheme.aroColors.textTertiary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = AroTextStyles.bodySmall,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 1,
        )
    }
}

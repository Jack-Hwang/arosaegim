package app.arosaegim.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.PageNavigationBar
import app.arosaegim.ui.common.aroColors
import app.arosaegim.ui.parked.PartyLinePrefs

/*
 * 설정 화면(13번 문서 T4 설정·가족 전환 — 시안 42, 2026-11-14 신규).
 *
 * 시안 42 구성(위계만 따른다): 상단 헤더(settings_header — PageNavigationBar 재사용) 아래
 * 세 절만 둔다. 항목을 더 늘리지 않는다.
 *   ① 가족 — 접속 중 가족(코드는 FamilyCodeMask 로 가리고 menu_family_tag 꼬리표를 붙인다)
 *      + [가족 전환] + [이 차에서 로그아웃].
 *   ② 표시 — settings_display_desc + 스위치 행(settings_display_label). 값은 기존 PartyPrefs
 *      계약(GET·PUT /v1/families/me/prefs 의 party_line_all_hidden)을 그대로 따른다: 서버 값이
 *      있으면 그대로 보이고, 서버에 닿지 않으면 기존처럼 기기 안 임시값으로 판정한다(기존 동작
 *      유지 — ParkedScreen B4 의 폴백 규약). GET·PUT 호출은 이 화면을 여는 쪽(통합)이 하고
 *      data/ 파일은 수정하지 않는다 — 화면에는 결과만 상태 파라미터로 주입된다.
 *   ③ 개인정보 안내 — settings_privacy_keep·settings_privacy_drop 두 줄.
 *
 * 콜백 규약(통합 배선용 — 이 화면은 Activity·다른 화면·세션 흐름을 전혀 참조하지 않는다):
 *  - onFamilySwitch       [가족 전환] → 통합이 가족 전환 확인 화면(FamilySwitchConfirmScreen)을 연다.
 *  - onSignOut            [이 차에서 로그아웃] → SignOutFlow 실행·시작 화면 열기는 통합 몫.
 *  - onTogglePartyLine    스위치 토글 결과('문장 보임' 여부) → 통합이 putPrefs(전체 교체:
 *      SettingsState.keptHiddenPlaceIds + 새 allHidden 반대값)를 보내고, 서버 실패 시 기존처럼
 *      기기 안 임시값을 갱신해 다시 그린다.
 *
 * 안전 규칙(04문서): 애니메이션 없음 · 색은 MaterialTheme.aroColors 토큰만 · 본문 26sp 하한
 * (AroTextStyles.bodySmall) · 터치 영역 76dp 이상 · 로그 없음 · 가족 코드는 마스킹 값으로만 표시.
 */

/**
 * 설정 화면.
 *
 * @param connectedFamilyCode  세션 가족 코드 원문(FamilySessionStore.familyCode 값). 화면 안에서
 *                             마스킹 표시에만 쓰고 저장·전송·로그는 없다. null·빈 값(둘러보기)이면
 *                             코드 줄을 숨긴다(SettingsState.familyCodeRowVisible).
 * @param partyPrefs           GET /v1/families/me/prefs 성공분(ui.parked.parsePartyLinePrefs).
 *                             null = 서버 값 없음(세션 없음·조회 실패) → 기기 임시값 폴백.
 * @param devicePartyAllHidden 기기 안 임시값(PartyFoldLocalStore.readAllHidden — 통합이 읽어 넘긴다).
 *                             서버 값이 없을 때만 스위치 판정에 쓰인다.
 * @param onFamilySwitch       [가족 전환] 눌림.
 * @param onSignOut            [이 차에서 로그아웃] 눌림.
 * @param onTogglePartyLine    스위치 토글 결과(다음 '문장 보임' 값) 전달.
 * @param modifier             바깥 여백 등 화면 크기 제어용.
 * @param onBack               헤더 Back 동작(선택). null 이면 Back 을 보이지 않는다 — 메뉴 닫기
 *                             등은 통합이 연결한다.
 */
@Composable
fun SettingsScreen(
    connectedFamilyCode: String?,
    partyPrefs: PartyLinePrefs?,
    devicePartyAllHidden: Boolean,
    onFamilySwitch: () -> Unit,
    onSignOut: () -> Unit,
    onTogglePartyLine: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.aroColors.backgroundPrimary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PageNavigationBar(
            courseName = stringResource(R.string.settings_header),
            onBack = onBack,
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── ① 가족(시안 42) — 코드 줄은 접속 중일 때만, 값은 마스킹으로만 보인다. ──
            SettingsSectionCard {
                Text(
                    text = stringResource(R.string.settings_family_title),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                )
                if (SettingsState.familyCodeRowVisible(connectedFamilyCode)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.menu_family_code,
                                FamilyCodeMask.mask(connectedFamilyCode),
                            ),
                            style = AroTextStyles.bodySmall,
                            color = MaterialTheme.aroColors.textPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        SettingsTag(text = stringResource(R.string.menu_family_tag))
                    }
                }
                SettingsSecondaryButton(text = stringResource(R.string.settings_family_switch)) {
                    onFamilySwitch()
                }
                SettingsSecondaryButton(text = stringResource(R.string.settings_family_signout)) {
                    onSignOut()
                }
            }

            // ── ② 표시(시안 42) — 스위치 값 판정은 SettingsState(서버 값 우선 · 임시값 폴백). ──
            val partyLineVisible =
                SettingsState.partyLineVisible(partyPrefs?.allHidden, devicePartyAllHidden)
            SettingsSectionCard {
                Text(
                    text = stringResource(R.string.settings_display_title),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.settings_display_desc),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textSecondary,
                )
                // 스위치 행 — 행 전체가 하나의 터치 대상(76dp 이상)이라 접촉 면적이 넉넉하다.
                // Switch 자체의 클릭은 받지 않는다(onCheckedChange = null) — 이중 대상을 만들지 않는다.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.aroColors.surfaceLow)
                        .toggleable(
                            value = partyLineVisible,
                            role = Role.Switch,
                            // 다음 값은 순수 로직에서 판정한다 — 화면은 결과만 콜백으로 넘긴다.
                            onValueChange = {
                                onTogglePartyLine(SettingsState.nextPartyLineVisible(partyLineVisible))
                            },
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_display_label),
                        style = AroTextStyles.bodySmall,
                        color = MaterialTheme.aroColors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = partyLineVisible, onCheckedChange = null)
                }
            }

            // ── ③ 개인정보 안내(시안 42) — 저장하는 것 / 저장하지 않는 것 두 줄. ──
            SettingsSectionCard {
                Text(
                    text = stringResource(R.string.settings_privacy_title),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.settings_privacy_keep),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.settings_privacy_drop),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textPrimary,
                )
            }
        }
    }
}

/** 설정 화면 섹션 카드 — 정차 화면 카드와 같은 규격(표면·모서리·여백). */
@Composable
internal fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.aroColors.surfaceBase,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** 설정 화면 꼬리표("이 차에서 접속 중") — 정차 화면 꼬리표와 같은 규격. */
@Composable
internal fun SettingsTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.aroColors.surfaceLow,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = AroTextStyles.label,
            color = MaterialTheme.aroColors.textTertiary,
        )
    }
}

/** 설정 화면 보조 버튼(높이 76dp 이상 — 04문서 접근성 하한). 이 화면에는 주요 버튼이 없다. */
@Composable
internal fun SettingsSecondaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.aroColors.buttonBasic)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AroTextStyles.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 1,
        )
    }
}

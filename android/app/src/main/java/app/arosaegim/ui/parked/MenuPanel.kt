package app.arosaegim.ui.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.aroColors

/*
 * MenuPanel — 정차 화면 오른쪽에서 열리는 메뉴 패널(F10 T2, 2026-11-14).
 *
 * 열림·닫힘은 즉시 전환한다(애니메이션 금지 — 04문서). 폭은 호출부가 정한다
 * (창 폭의 27%, 최소 240dp — MenuPolicy.panelWidthDp). 내용 순서(시안 40 위계):
 *   맨 위 닫기 버튼 → 가족 블록(이름·마스킹 코드·접속 꼬리표) → 구분선 → 항목 4개
 *   → 하단 안내 한 줄.
 * 현재 보고 있는 내용에 해당하는 항목의 배경은 surfaceBase 토큰으로 표시한다
 * (그림자 대신 — 색은 토큰만 쓴다). 항목을 누르면 콜백이 부르고, 패널 닫기는
 * 호출부(ParkedScreen)가 MenuPanelState 로 한다 — 항목 처리와 닫힘을 한 곳에 묶기 위함.
 *
 * 안전 규칙: 터치 영역 76dp 이상, 본문 글자는 26sp 하한(AroTextStyles), 애니메이션 없음.
 */
@Composable
fun MenuPanel(
    panelWidth: Dp,
    familyCodeMasked: String?,
    selected: MenuPanelItem?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onItem: (MenuPanelItem) -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(panelWidth),
        color = MaterialTheme.aroColors.backgroundPopup,
        // 오른쪽 가장자리는 화면 끝에 붙고, 왼쪽 모서리만 둥글게 한다.
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 맨 위 닫기 버튼 ──
            MenuPanelRow(
                label = stringResource(R.string.menu_close),
                leading = {
                    Text(
                        text = "✕",
                        style = AroTextStyles.bodyMedium,
                        color = MaterialTheme.aroColors.textSecondary,
                    )
                },
                onClick = onClose,
            )

            // ── 가족 블록: 이름 · 코드(가림) · 접속 꼬리표 ──
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.menu_family_title),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                    maxLines = 1,
                )
                // 코드가 없으면(둘러보기) 이 줄만 숨긴다 — 가짜 코드를 만들지 않는다.
                if (familyCodeMasked != null) {
                    Text(
                        text = stringResource(R.string.menu_family_code, familyCodeMasked),
                        style = AroTextStyles.bodySmall,
                        color = MaterialTheme.aroColors.textSecondary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = stringResource(R.string.menu_family_tag),
                    style = AroTextStyles.label,
                    color = MaterialTheme.aroColors.textTertiary,
                    maxLines = 1,
                )
            }

            // ── 구분선 — 토큰 배경색 1dp(새 색 없음) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.aroColors.backgroundSecondary),
            )

            // ── 항목 4개(AC08 — 홈·코스 선택·그때와 지금·설정) ──
            MenuPanelRow(
                label = stringResource(R.string.menu_item_home),
                isSelected = selected == MenuPanelItem.HOME,
            ) {
                onItem(MenuPanelItem.HOME)
            }
            MenuPanelRow(
                label = stringResource(R.string.menu_item_course),
                isSelected = selected == MenuPanelItem.COURSE,
            ) {
                onItem(MenuPanelItem.COURSE)
            }
            MenuPanelRow(
                label = stringResource(R.string.menu_item_thennow),
                isSelected = selected == MenuPanelItem.THENNOW,
            ) {
                onItem(MenuPanelItem.THENNOW)
            }
            MenuPanelRow(
                label = stringResource(R.string.menu_item_settings),
                isSelected = selected == MenuPanelItem.SETTINGS,
            ) {
                onItem(MenuPanelItem.SETTINGS)
            }

            // ── 하단 안내 한 줄(시안 하단 문구) ──
            Text(
                text = stringResource(R.string.menu_footer),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textTertiary,
            )
        }
    }
}

/**
 * 패널 한 행(닫기·항목 공용). 터치 영역은 76dp 이상이고, 선택 행의 배경은
 * surfaceBase 토큰이다(그림자 대신 — 토큰만 쓴다). [leading] 은 닫기 행의 글리프 자리다.
 */
@Composable
private fun MenuPanelRow(
    label: String,
    isSelected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(if (isSelected) MaterialTheme.aroColors.surfaceBase else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = if (leading != null) {
                    AroTextStyles.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    AroTextStyles.bodyMedium
                },
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

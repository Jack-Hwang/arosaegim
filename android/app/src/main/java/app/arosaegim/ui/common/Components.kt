package app.arosaegim.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 아로새김 공통 컴포저블 (04_UI_GUIDE.md 6~7절 기준으로 재작성 — 2026-11-10).
 *
 * 안전 규칙(주행 중 사용성, 04문서 2절):
 *  - 버튼 최소 높이는 76dp(04문서 7절 [설계]: 시안 기준 버튼 높이 76 이상).
 *    시선 이동과 오터치를 줄이기 위해 접촉 면적을 넓게 유지한다.
 *  - 본문 기본 글자 크기는 24sp(AroTextStyles.bodySmall 이 본문 하한).
 *  - 파라미터 텍스트는 maxLines=3 으로 강제 절단한다.
 *    주행 중 긴 텍스트 표시는 금지 — 긴 내용은 정차 화면에서만 노출한다.
 *  - 애니메이션 없음. 색은 MaterialTheme.aroColors 토큰으로만 쓴다(16진수 직접 사용 금지).
 *
 * ⚠ D3 확인 과제(04문서 8절): 버튼 최소 크기·모서리는 가이드 Basic button 이미지로
 *   실측 후 이 파일의 상수를 수정할 것. 현재 76dp/모서리 16dp 는 시안 기준 임시값이다.
 */

/** 버튼 최소 높이 — 04문서 7절 [설계] (D3 실측 전 임시값). */
private val MinButtonHeight = 76.dp

/** 카드·버튼 모서리 반경 — D3 실측 전 임시값. */
private val CornerRadius = 16.dp

/**
 * 큰 주요 버튼(높이 ≥ 76dp, 화면 폭 채움). CTA 전용 — 한 화면에 주요 버튼은 1개를 원칙으로 한다.
 * 주행 중 오조작을 막기 위해 접촉 면적을 넓게 유지한다.
 */
@Composable
fun LargePrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // onClick 을 마지막에 둔다 — 트레일링 람다 호출 문법(`Button(...) { ... }`)을 지원하기 위함.
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(CornerRadius),
        modifier = modifier
            .fillMaxWidth()
            .height(MinButtonHeight),
    ) {
        Text(
            text = text,
            style = AroTextStyles.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

/** 본문 텍스트(기본 24sp). 한두 줄 안에 읽히는 짧은 문구 전용. */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 24,
) {
    Text(
        text = text,
        modifier = modifier,
        style = AroTextStyles.bodySmall.copy(
            fontSize = fontSizeSp.sp,
            color = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/** 안전 텍스트 — maxLines=3 강제. 주행 중 긴 텍스트 금지 규칙을 UI 수준에서 강제하는 헬퍼. */
@Composable
fun SafeShortText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = 3,
        style = AroTextStyles.bodySmall.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/**
 * PageNavigationBar — 앱 상단 헤더 (04문서 6절 Page Navigation).
 *
 * 구성: [아이콘] 아로새김 · 코스 이름 ···(선택적 우측 액션).
 *  - 하위 화면에서는 onBack 을 넘겨 Back(뒤로) 동작을 노출하는 Depth 형태로 쓴다.
 *  - 코스 이름이 null 이면 앱 이름만 표시한다.
 *  - 아이콘 자리는 스탬프 인주색 점(stampPoint) 표시로 대신한다. 앱의 유일한 포인트 색.
 *
 * 안전 규칙: 터치 영역은 76dp 이상, 애니메이션 없음.
 *
 * @param courseName 현재 코스 이름(없으면 null)
 * @param onBack 뒤로 동작(하위 화면에서만 제공. null 이면 Back 숨김)
 * @param trailingAction 우측 끝에 붙이는 선택적 액션(설정 등)
 */
@Composable
fun PageNavigationBar(
    courseName: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailingAction: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinButtonHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            NavGlyphButton(
                glyph = "◀",
                description = "뒤로",
                onClick = onBack,
            )
            Spacer(Modifier.width(8.dp))
        }
        // 앱 아이콘 자리 — 스탬프 인주색 점(과한 색 사용 금지 원칙상 인주 하나만 쓴다).
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(MaterialTheme.aroColors.stampPoint, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "아로새김", // TODO(문자열): res/values/strings.xml 로 분리(04문서 2절 다국어)
            style = AroTextStyles.titleMedium.copy(
                color = MaterialTheme.aroColors.textPrimary,
            ),
        )
        if (!courseName.isNullOrBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "·",
                style = AroTextStyles.titleMedium.copy(
                    color = MaterialTheme.aroColors.textTertiary,
                ),
            )
            Spacer(Modifier.width(12.dp))
            // 코스 이름은 한 줄로 잘라낸다 — 상단 헤더에서 두 줄 넘김은 금지(시선 분산 방지).
            Text(
                text = courseName,
                maxLines = 1,
                style = AroTextStyles.titleSmall.copy(
                    color = MaterialTheme.aroColors.textSecondary,
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (trailingAction != null) {
            trailingAction()
        }
    }
}

/**
 * DemoModeTag — "데모 모드" 꼬리표 (04문서 7절 상태별 처리).
 *
 * 데모 모드(수동 전환)일 때는 화면에 항상 표시해야 한다. 실제 주행과 혼동하지 않도록
 * 하는 안전 장치이므로, 데모 화면이라면 이 태그를 반드시 붙인다.
 */
@Composable
fun DemoModeTag(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.aroColors.textTertiary,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "데모 모드", // TODO(문자열): res/values/strings.xml 로 분리(04문서 2절)
            style = AroTextStyles.label.copy(
                color = MaterialTheme.aroColors.textSecondary,
            ),
        )
    }
}

/** 헤더용 글리프 버튼(Back 등). 아이콘 리소스 의존 없이 텍스트 글리프로 대체한다(D3 재확인 대상). */
@Composable
private fun NavGlyphButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(MinButtonHeight)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = AroTextStyles.titleSmall.copy(
                color = MaterialTheme.aroColors.textPrimary,
            ),
        )
    }
}

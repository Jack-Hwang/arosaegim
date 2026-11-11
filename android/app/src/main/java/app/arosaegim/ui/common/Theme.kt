package app.arosaegim.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/*
 * 아로새김 공통 테마 (04_UI_GUIDE.md 4절 기준으로 재작성 — 2026-11-10).
 *
 * 이전(00_BRIEF 5절)의 "다크 고대비 고정" 지시는 04문서로 대체되었다:
 *  - Pleos 기본은 밝은 테마이며 주간/야간 모드를 모두 지원해야 한다.
 *  - 시스템(에뮬레이터) 라이트/다크 설정은 따르지 않는다. 호출부가 isDark 로
 *    명시적으로 고른다. 이유: 차량 환경에서는 화면 테마가 시스템 설정과 별개로
 *    결정되므로, 앱이 예측 가능한 고정 색상으로 그리는 것이 안전하다.
 *  - ⚠ D5 확인 과제(04문서 8절): 에뮬레이터 테마 전환 후 두 테마의
 *    텍스트·아이콘 대비 4.5:1 을 캡처로 실측할 것. 아래 주석의 대비비는 계산값이다.
 *
 * 야간 전환 상태(00문서 12절 T6 — 시안 10 '세로창·야간'):
 *  - 전환 값은 [ThemeState].isDark(object 홀더 — ThemeState.kt)가 가진다.
 *  - 권장 호출 패턴(각 Activity setContent 안에서):
 *        ArosaegimTheme(isDark = ThemeState.isDark) { … }
 *    이렇게 쓰면 ThemeState.isDark 가 바뀔 때 그 Activity 의 전체 토큰이 즉시
 *    다크로 다시 그려진다(Compose 상태 구독).
 *  - ⚠ 현재 호출부(DiagnosticsActivity·ParkedActivity·DrivingActivity·StartActivity)는
 *    별도 통합 작업 소유라 이번 판에 고정 값(false 주간)을 유지한다. 따라서 진단 화면의
 *    "테마(개발)" 토글이 ThemeState.isDark 만 바꿔도 실제 토큰 적용은 호출부가 위 패턴으로
 *    바뀐 뒤, 또는 그 전에는 다음 앱 재시작 때부터다(D5 실측 절차에 이 제한을 기록할 것).
 *
 * 안전 규칙: 동적 컬러(Material You)와 애니메이션을 사용하지 않는다.
 */

/* ------------------------------------------------------------------
 * 매핑 전용 잉크 색 — 토큰 위에 얹히는 대비용 텍스트/스크림.
 * (인주 위 흰 글자 ≈5.7:1, 인주 위 다크 글자 ≈4.8:1)
 * ------------------------------------------------------------------ */
private val InkOnColorDark = Color(0xFF101014)
private val InkOnColorLight = Color(0xFFFFFFFF)
private val ScrimBlack = Color(0xFF000000)

/* ------------------------------------------------------------------
 * 레거시 색 val — 기존 호출부 호환용.
 * 이제 DarkAroColors 토큰을 가리킨다. 새 코드는 MaterialTheme.aroColors 를 쓸 것.
 * ------------------------------------------------------------------ */
@Deprecated("AroColors 사용 권장 — MaterialTheme.aroColors.backgroundPrimary", ReplaceWith("MaterialTheme.aroColors.backgroundPrimary"))
val ArosaeBackground: Color = DarkAroColors.backgroundPrimary
@Deprecated("AroColors 사용 권장 — MaterialTheme.aroColors.textPrimary", ReplaceWith("MaterialTheme.aroColors.textPrimary"))
val ArosaeOnBackground: Color = DarkAroColors.textPrimary
@Deprecated("AroColors 사용 권장 — MaterialTheme.aroColors.stampPoint", ReplaceWith("MaterialTheme.aroColors.stampPoint"))
val ArosaePrimary: Color = DarkAroColors.stampPoint
@Deprecated("AroColors 사용 권장 — 색 토큰 onPrimary 를 쓸 것", ReplaceWith("MaterialTheme.colorScheme.onPrimary"))
val ArosaeOnPrimary: Color = InkOnColorDark
@Deprecated("AroColors 사용 권장 — MaterialTheme.aroColors.surfaceBase", ReplaceWith("MaterialTheme.aroColors.surfaceBase"))
val ArosaeSurface: Color = DarkAroColors.surfaceBase
@Deprecated("AroColors 사용 권장 — MaterialTheme.aroColors.textPrimary", ReplaceWith("MaterialTheme.aroColors.textPrimary"))
val ArosaeOnSurface: Color = DarkAroColors.textPrimary
@Deprecated("AroColors 사용 권장 — MaterialTheme.aroColors.informativeNegative", ReplaceWith("MaterialTheme.aroColors.informativeNegative"))
val ArosaeError: Color = DarkAroColors.informativeNegative

/* ------------------------------------------------------------------
 * AroColors → Material3 ColorScheme 매핑.
 * 기존 호출부(DiagnosticsScreen 등)가 colorScheme.primary/error/onSurface 를
 * 참조하므로 이 표준 이름들이 계속 유효해야 한다.
 * ------------------------------------------------------------------ */
private fun aroColorScheme(c: AroColors, isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = c.stampPoint,
        onPrimary = InkOnColorDark, // 인주 위 다크 텍스트 ≈ 4.8:1
        primaryContainer = c.surfaceAccent,
        onPrimaryContainer = c.textPrimary,
        secondary = c.buttonFilled,
        onSecondary = InkOnColorDark,
        tertiary = c.informativePositive,
        onTertiary = InkOnColorDark,
        background = c.backgroundPrimary,
        onBackground = c.textPrimary,
        surface = c.surfaceBase,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceLow,
        onSurfaceVariant = c.textSecondary,
        outline = c.textQuaternary,
        outlineVariant = c.surfaceHigh,
        error = c.informativeNegative,
        onError = InkOnColorDark,
        errorContainer = c.surfaceAccent,
        onErrorContainer = c.informativeNegative,
        inverseSurface = c.buttonFilled,
        inverseOnSurface = InkOnColorDark,
        inversePrimary = LightAroColors.stampPoint,
        scrim = ScrimBlack,
    )
} else {
    lightColorScheme(
        primary = c.stampPoint,
        onPrimary = InkOnColorLight, // 인주 위 흰 텍스트 ≈ 5.7:1
        primaryContainer = c.surfaceAccent,
        onPrimaryContainer = c.textPrimary,
        secondary = c.buttonFilled,
        onSecondary = InkOnColorLight,
        tertiary = c.informativePositive,
        onTertiary = InkOnColorLight,
        background = c.backgroundPrimary,
        onBackground = c.textPrimary,
        surface = c.surfaceBase,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceLow,
        onSurfaceVariant = c.textSecondary,
        outline = c.textQuaternary,
        outlineVariant = c.surfaceHigh,
        error = c.informativeNegative,
        onError = InkOnColorLight,
        errorContainer = c.surfaceAccent,
        onErrorContainer = c.informativeNegative,
        inverseSurface = c.buttonFilled,
        inverseOnSurface = InkOnColorLight,
        inversePrimary = DarkAroColors.stampPoint,
        scrim = ScrimBlack,
    )
}

/**
 * 아로새김 앱 전역 테마.
 *
 * @param isDark 야간 테마 사용 여부. 기본 false(주간). 시스템 설정을 따르지 않고
 *               호출부(주행·정차·진단 Activity)가 명시적으로 고른다.
 */
@Composable
fun ArosaegimTheme(
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val aro = if (isDark) DarkAroColors else LightAroColors
    CompositionLocalProvider(LocalAroColors provides aro) {
        MaterialTheme(
            colorScheme = aroColorScheme(aro, isDark),
            content = content,
        )
    }
}

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
 * 이전(요구 문서 00의 5절)의 "다크 고대비 고정" 지시는 04문서로 대체되었다:
 *  - Pleos 기본은 밝은 테마이며 주간/야간 모드를 모두 지원해야 한다.
 *  - 시스템(에뮬레이터) 라이트/다크 설정은 따르지 않는다. 호출부가 isDark 로
 *    명시적으로 고른다. 이유: 차량 환경에서는 화면 테마가 시스템 설정과 별개로
 *    결정되므로, 앱이 예측 가능한 고정 색상으로 그리는 것이 안전하다.
 *  - ⚠ D5 확인 과제(04문서 8절): 에뮬레이터 테마 전환 후 두 테마의
 *    텍스트·아이콘 대비 4.5:1 을 캡처로 실측할 것. 아래 주석의 대비비는 계산값이다.
 *
 * 야간 전환 상태(00문서 12절 T6 — 시안 10 '세로창·야간'; 2026-11-12 09문서 6번 정정 반영):
 *  - 전환 값은 [ThemeState].isDark(object 홀더 — ThemeState.kt)가 가진다.
 *  - 현재 호출부(DiagnosticsActivity·ParkedActivity·DrivingActivity·StartActivity)는 네 곳 모두
 *        ArosaegimTheme(isDark = ThemeState.isDark) { … }
 *    로 이 상태를 구독한다. 그래서 진단 화면의 "테마(개발)" 토글이 isDark 를 바꾸면 해당
 *    Activity 의 전체 토큰이 앱 재시작 없이 즉시 다크/주간으로 다시 그려진다(Compose 상태 구독).
 *  - 자동 전환 정책(09문서 6번·기록 Q12 확정): ThemeState.autoByClock = true 면
 *    ThemeState.refreshByClock(LocalTime) 이 시간대(밤 19:00~아침 07:00)로 isDark 를 갱신하고
 *    수동 토글보다 우선한다. 호출부가 refreshByClock 을 주기적으로 불러야 즉시 반영된다 —
 *    계약·연결 위치는 ThemeState.kt 주석 3~4번을 따른다.
 *
 * 안전 규칙: 동적 컬러(Material You)와 애니메이션을 사용하지 않는다.
 */

/* ------------------------------------------------------------------
 * 매핑 전용 잉크 색 — 토큰 위에 얹히는 대비용 텍스트/스크림.
 * (라이트 인주 #B3401C 위 흰 글자 ≈5.7:1, 다크 인주 #DF7451 위 다크 글자 ≈6.1:1 —
 *  다크 쪽은 2026-11-12 stampPoint #D8552B→#DF7451 조정으로 4.8:1에서 올라갔다)
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
        onPrimary = InkOnColorDark, // 다크 인주(#DF7451) 위 다크 텍스트 ≈ 6.1:1 (2026-11-12 stampPoint 조정 반영)
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

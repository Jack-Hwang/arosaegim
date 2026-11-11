package app.arosaegim.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/*
 * 아로새김 색 토큰 전달 장치 (04_UI_GUIDE.md 4절).
 *
 *  - LocalAroColors 로 토큰을 내려보내고, 화면 코드에서는
 *    MaterialTheme.aroColors.stampPoint 처럼 토큰 이름으로만 접근한다.
 *  - 기본값은 주간 테마(LightAroColors). 실제 값은 ArosaegimTheme(isDark)가 채워 넣는다.
 *  - 안전 규칙: 화면에서 16진수 색을 직접 쓰지 않게 하는 것이 목적이다.
 */

val LocalAroColors = staticCompositionLocalOf { LightAroColors }

/** 화면 코드에서 색 토큰에 접근하는 표준 경로. */
val MaterialTheme.aroColors: AroColors
    @Composable get() = LocalAroColors.current

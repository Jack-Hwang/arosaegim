package app.arosaegim.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * 야간 테마 전환 상태 (00문서 12절 T6 — 시안 10 '세로창·야간' 대응, 2026-11-11).
 *
 * QuizStateHolder 와 같은 방식의 object 홀더다: 프로세스 수명 동안 살아 있어 Activity
 * 재생성 사이에 값이 보존된다. "일반 var + 알림용 mutableStateOf" 설계 노트도 같다 —
 * mutableStateOf 위임은 이 값을 읽는 화면이 다시 그리도록 하는 알림 장치일 뿐이다.
 *
 * 적용 범위와 제한(중요 — D5 실측 절차에 그대로 기록할 것):
 *  1. 테마 선택은 시스템 설정을 따르지 않고 호출부가 ArosaegimTheme(isDark = …) 로
 *     명시한다(Theme.kt 규약). 따라서 ThemeState 만 바꾸면 화면 토큰은 즉시 바뀌지 않는다.
 *     즉시 재구성이 필요하면 Activity setContent 가
 *         ArosaegimTheme(isDark = ThemeState.isDark) { … }
 *     처럼 ThemeState 를 구독해야 한다(권장 패턴 — Theme.kt 주석 참조).
 *  2. 호출부 네 곳(DiagnosticsActivity·ParkedActivity·DrivingActivity·StartActivity)은
 *     이 작업의 소유가 아니다(별도 통합 작업). 그래서 이번 판에서는 Diagnostics 진단 화면의
 *     "테마(개발)" 토글이 ThemeState 만 바꾸고, 실제 다크 토큰 적용은
 *     "다음 앱 재시작부터" 이뤄진다(호출부가 위 권장 패턴으로 바뀐 뒤 기준).
 *     - 예외: ThemeState 를 직접 읽는 컴포저블(DiagnosticsScreen 의 토글 표시 줄)은
 *       Compose 상태 알림으로 자기 자신은 즉시 다시 그린다. 테마 토큰(aroColors)은
 *       Activity 가 고정 값을 넘기므로 여전히 이전 테마로 그린다.
 *  3. 기본값은 false(주간) — 기존 ParkedActivity 주석("정차 화면은 주간 테마 기본")과 같다.
 *  4. 값은 프로세스 수명이다. 앱 재시작하면 주간으로 돌아온다(영속 저장 금지 — 차량
 *     화면 테마는 이번 판에서 개발자 확인용 D5 도구다).
 */
object ThemeState {

    /** 야간(다크) 토큰 사용 여부. false = 주간(라이트). 진단 화면 토글이 바꾼다. */
    var isDark by mutableStateOf(false)
}

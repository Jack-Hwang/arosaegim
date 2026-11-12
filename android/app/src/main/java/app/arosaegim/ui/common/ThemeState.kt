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
 * 적용 범위와 제한(2026-11-12 — 09문서 6번 정정 반영):
 *  1. 테마 선택은 시스템 설정을 따르지 않고 호출부가 ArosaegimTheme(isDark = …) 로
 *     명시한다(Theme.kt 규약). 네 호출부(DiagnosticsActivity·ParkedActivity·
 *     DrivingActivity·StartActivity)는 모두
 *         ArosaegimTheme(isDark = ThemeState.isDark) { … }
 *     로 이 상태를 구독하므로, isDark 가 바뀌면 그 화면의 토큰은 앱 재시작 없이 즉시
 *     다크/주간으로 다시 그려진다(Compose 상태 알림).
 *  2. 수동 전환(개발자 확인용)은 진단 화면의 "테마(개발)" 토글이 isDark 를 직접 바꾼다.
 *  3. 자동 전환(2026-11-12 추가 — 09문서 6번·기록 Q12 정책 확정):
 *     autoByClock = true 면 [refreshByClock] 이 시간대(밤 19:00~아침 07:00)로 isDark 를
 *     갱신한다. 이때는 수동 토글 값보다 시간 기준이 우선한다 — 토글을 눌러 바꿔도 다음
 *     refreshByClock 호출이 밤/아침 판정으로 되돌린다(계약: autoByClock 우선). 수동 확인을
 *     하려면 autoByClock 을 끄고 토글을 쓴다.
 *  4. autoByClock 은 어디서도 자동으로 켜지 않는다(기본 false — 수동 토글이 기본 동작).
 *     화면이 실시간으로 따라가려면 호출부가 주기적으로 refreshByClock 을 불러야 한다:
 *     - 연결 위치는 진단 화면의 테마 토글 처리(화면이 다시 보일 때·주기 폴링)와 각
 *       Activity 의 onResume 등이다. ui/driving 등 화면 코드는 이 파일의 소유 밖이라
 *       여기서 직접 연결하지 않는다 — 호출부 담당 작업이 아래 예시처럼 붙인다.
 *           ThemeState.refreshByClock(java.time.LocalTime.now())
 *     호출이 없어도 기존 동작(autoByClock 끔 — 수동 토글)은 그대로다(가짜 자동 전환 금지).
 *  5. 기본값은 isDark = false(주간) — 기존 ParkedActivity 주석("정차 화면은 주간 테마 기본")과 같다.
 *  6. 값은 프로세스 수명이다. 앱 재시작하면 주간으로 돌아온다(영속 저장 금지 — 차량
 *     화면 테마는 이번 판에서 개발자 확인용 D5 도구다).
 */
object ThemeState {

    /** 야간(다크) 토큰 사용 여부. false = 주간(라이트). 진단 화면 토글 또는 refreshByClock 이 바꾼다. */
    var isDark by mutableStateOf(false)

    /**
     * 시간대 기반 자동 전환 사용 여부. true 면 [refreshByClock] 의 시간 판정이
     * [isDark] 를 덮어쓴다(수동 토글보다 우선 — 위 3번 계약). 기본 false.
     */
    var autoByClock by mutableStateOf(false)

    /**
     * 시간대 기준으로 [isDark] 를 갱신한다. 야간 = 19:00:00 이상 또는 07:00:00 미만
     * (19:00 정각은 밤, 07:00 정각은 아침). autoByClock 이 false 면 아무것도 하지 않는다.
     *
     * java.time.LocalTime 은 JVM(호스트 진단 도구)·Android 공통이라 썼다(minSdk 28 —
     * API 26+ 기본 제공이라 desugaring 불필요).
     * 즉시 반영을 위해 호출부가 화면 복귀·주기 폴링 때 불러 줘야 한다(위 4번).
     */
    fun refreshByClock(now: java.time.LocalTime) {
        if (!autoByClock) return
        isDark = now.hour >= 19 || now.hour < 7
    }
}

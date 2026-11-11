package app.arosaegim.ui.parked

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import app.arosaegim.ArosaegimApp
import app.arosaegim.ui.common.ArosaegimTheme
import app.arosaegim.ui.common.ThemeState

/*
 * 정차 화면(ParkedActivity).
 *
 * 주행 시작 시 자동 가림 안내(시스템 동작):
 *  - 이 Activity 는 매니페스트에 distractionOptimized(DO) 를 선언하지 않았다(00문서/04문서).
 *    따라서 주행(DRIVING) 상태로 바뀌면 시스템의 UX 제한이 이 화면을 자동으로 가린다.
 *    화면이 가려져도 앱은 살아 있고, ModeRouter 가 PARKED 복귀 시
 *    FLAG_ACTIVITY_REORDER_TO_FRONT 로 이 Activity 를 다시 앞으로 가져온다.
 *  - 그때 시스템이 Activity 인스턴스를 파괴·재생성할 수 있으므로, 퀴즈 진행 상태는
 *    Activity 나 Compose state 에 두지 않고 [QuizStateHolder] 싱글톤에 보존한다.
 *    정차 복귀 시 ParkedScreen 이 홀더 값을 읽어 이어서 보여 준다(00문서 5절).
 *
 * Back 종료 금지 규칙: Back 키는 finish() 대신 moveTaskToBack(true) 로 태스크를
 * 백그라운드로 보존한다(CCP 로터리 규칙, 01문서 §6). 차량 환경에서 태스크 파괴는
 * 재진입 비용과 SDK 재연결 부담이 크기 때문이다.
 */
class ParkedActivity : ComponentActivity() {

    private val backToBackground = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // finish() 금지 — 태스크를 보존하고 런처로 돌아간다.
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back 키 → moveTaskToBack 규칙 등록
        onBackPressedDispatcher.addCallback(this, backToBackground)

        val app = application as ArosaegimApp
        setContent {
            // 정차 화면은 주간 테마 기본(isDark = false — ArosaegimTheme 기본값).
            ArosaegimTheme(isDark = ThemeState.isDark) {
                ParkedScreen(container = app.container, onExitToDriving = {})
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // T9-2: 정차 화면이 가려지는 순간(주행 전환 — DO 미선언이라 시스템이 가림 / 백그라운드 전환)
        // 음성 편지 재생을 즉시 중단한다. 주행을 시작하면 '재생이 멈추고 버튼이 사라진다'(00문서 11절)
        // 는 동작에서 '멈춤'의 구현 지점이다. 버튼은 Parked 화면이 가려지며 자연히 사라진다.
        runCatching {
            (application as ArosaegimApp).container.voiceLetterPlayer.stop()
        }
    }
}

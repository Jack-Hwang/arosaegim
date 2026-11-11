package app.arosaegim.ui.driving

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import app.arosaegim.ArosaegimApp
import app.arosaegim.ui.common.ArosaegimTheme
import app.arosaegim.ui.common.ThemeState

/*
 * 주행 화면(DrivingActivity) — T5.
 *
 *  - 주행 중 사용을 허용하기 위해 매니페스트에 distractionOptimized=true 를 선언했다.
 *  - 이 액티비티의 Back 키는 finish() 하지 않고 moveTaskToBack(true) 로
 *    앱을 뒤로 보낸다.
 *    이유: 차량 앱에서 finish()는 태스크를 파괴해 재진입 비용(초기화, SDK 재연결)이
 *    크고, 주행 중 화면 전환 실패 시 안전 문제가 될 수 있다. 태스크를 유지한 채
 *    백그라운드로 전환하는 것이 차량 플랫폼 표준 패턴이다.
 *
 * 세션 수명 규칙(T5): TripSession.start()/stop() 을 이 Activity 생명주기에 묶지 않는다.
 * 세션은 ArosaegimApp.onCreate 에서 1회 시작되어 Application 스코프로 산다 —
 * 백그라운드로 내려갔다 복귀해도 도착 감시·스탬프 진행이 유지된다.
 * 전체 해제는 앱 종료 시 AppContainer.closeAll() 이 담당한다.
 */
class DrivingActivity : ComponentActivity() {

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

        val container = (application as ArosaegimApp).container
        setContent {
            // 주행은 주간 테마 기본값(isDark=false). 04문서 8절 D5 대비 실측은 Theme 주석 참조.
            ArosaegimTheme(isDark = ThemeState.isDark) {
                DrivingScreen(
                    session = container.tripSession,
                    narrator = container.narrator,
                    latestLocation = container.fusedArrival.latestLocation,
                )
            }
        }
    }
}

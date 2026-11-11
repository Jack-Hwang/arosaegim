package app.arosaegim.ui.session

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import app.arosaegim.data.FamilySessionStore
import app.arosaegim.ui.common.ArosaegimTheme
import app.arosaegim.ui.common.ThemeState

/*
 * 시작 화면(T9-1) — 가족 코드 접속 또는 '가족 없이 둘러보기'(00문서 T9-1·10.6).
 *
 * - 릴리스 흐름: launcher 는 DiagnosticsActivity 로 유지하되, 릴리스 빌드에서는
 *   DiagnosticsActivity 가 이 화면으로 리다이렉트한다(가족 세션부터 시작 — 주석은 매니페스트 참조).
 * - FamilySessionStore 는 AppContainer 밖에서 직접 만든다. AppContainer 는 다른
 *   작업과 병렬 수정 중이라 건드리지 않기 위함이며, SharedPreferences 를 공유하므로
 *   인스턴스 위치와 무관하게 저장 상태는 일치한다(FamilySessionStore 주석 참조).
 * - 차량 화면에 가족 코드를 크게 띄우지 않는다(00문서 10.6) — 코드는 입력 시에만 다룬다.
 * - Back 종료 금지(CCP 로터리 규칙, 01문서 §6): finish() 대신 moveTaskToBack(true).
 */
class StartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back 키 → moveTaskToBack 규칙 등록(진단·정차 화면과 동일).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            },
        )

        // AppContainer 수정 없이 필요한 것만 직접 조립(세션 저장소 1개).
        val store = FamilySessionStore(applicationContext)

        setContent {
            // 시작 화면은 주간 테마 고정(ArosaegimTheme 기본값 isDark = false).
            ArosaegimTheme(isDark = ThemeState.isDark) {
                StartScreen(store = store)
            }
        }
    }
}

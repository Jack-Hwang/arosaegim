package app.arosaegim.ui.driving

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import app.arosaegim.ArosaegimApp
import app.arosaegim.BuildConfig
import app.arosaegim.ui.common.ArosaegimTheme
import app.arosaegim.ui.common.ThemeState
import app.arosaegim.ui.session.StartActivity

/**
 * 진단(스모크 테스트) 화면 — 개발 중 런처.
 *
 * - 릴리스 빌드에서는 즉시 StartActivity 로 넘기고 finish 한다(진단 화면 숨김).
 *   릴리스 흐름은 가족 세션(시작 화면, T9-1)부터 시작한다(00문서 T9-1).
 *   launcher 는 개발 루프 보존을 위해 DiagnosticsActivity 로 유지한다(매니페스트 주석).
 * - 디버그 빌드 흐름은 유지: launcher → 진단 화면에서 수동 제어·스모크 테스트.
 * - Back 키는 finish() 금지(CCP 로터리 규칙): moveTaskToBack(true) 로 태스크를 보존한다.
 */
class DiagnosticsActivity : ComponentActivity() {

    private lateinit var viewModel: DiagnosticsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.DEBUG) {
            // 릴리스: 진단 화면 비노출 — 시작 화면(가족 세션, T9-1)을 최상단으로 당겨 온 뒤
            // 이 화면만 종료한다. 주행/정차 전환은 ModeRouter 가 이어서 처리한다.
            startActivity(
                Intent(this, StartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
            finish()
            return
        }

        val app = application as ArosaegimApp
        viewModel = DiagnosticsViewModel(
            container = app.container,
            modeRouter = app.modeRouter,
            appContext = applicationContext,
        )

        // Back 종료 금지: finish 대신 태스크를 백그라운드로 보낸다(재진입 시 SDK 상태 보존).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            },
        )

        setContent {
            ArosaegimTheme(isDark = ThemeState.isDark) {
                DiagnosticsScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 진단 화면 복귀 시 차량 세션(T9-1) 표시를 새로 고친다 — 가족 코드 입력 화면
        // (StartActivity)을 다녀와도 세션 상태가 갱신되도록 한다.
        if (::viewModel.isInitialized) {
            viewModel.refreshSessionState()
        }
    }
}

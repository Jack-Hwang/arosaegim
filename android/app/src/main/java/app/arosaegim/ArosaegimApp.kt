package app.arosaegim

import android.app.Application
import app.arosaegim.ui.ModeRouter

/**
 * 앱 진입점. Application.onCreate(메인 스레드)에서 AppContainer 를 조립하고
 * ModeRouter 로 주행/정차 화면 전환을 묶는다.
 *
 * Back 종료 금지 규칙: 모든 화면은 Back 시 finish() 대신 moveTaskToBack(true) 로
 * 태스크를 보존한다(CCP 로터리 규칙, 01문서 §6). 실제 처리는 각 Activity 가 담당.
 */
class ArosaegimApp : Application() {

    lateinit var container: AppContainer
        private set
    lateinit var modeRouter: ModeRouter
        private set

    override fun onCreate() {
        super.onCreate()
        // AppContainer 생성은 메인 스레드 규약(UxRestrictions start, SDK 초기화)을 지키기 위해
        // 여기서 수행한다. 콜드 스타트 교착 수정(2026-11-11): SDK 초기화 콜백에 대한 runBlocking
        // 대기는 더 이상 없다 — onCreate 는 SDK 응답과 무관하게 즉시 끝나고, 늦은 초기화 성공은
        // 일방향 업그레이드(SwitchableRouteService·SwitchableNarrator)가 뒤따라 처리한다.
        container = AppContainer(this)
        modeRouter = ModeRouter(
            this,
            // Q28(2026-11-13): 세션(가족 코드·둘러보기)이 없으면 정차 화면 자동 전환을 보류해
            // 시작 화면(가족 코드 입력)을 유지한다. 람다는 route 시점에 평가되므로 초기화
            // 순서와 무관하다.
            sessionExists = { container.familySessionStore.hasSession() },
        )
        // PARKED/DRIVING 상태 변화 → 해당 화면을 REORDER_TO_FRONT 로 최상단화(앱 수명 동안 1회 묶음).
        modeRouter.bind(container.drivingState)
        // 여행 세션(T5)은 Application 수명으로 1회 시작한다. Activity 가 백그라운드로
        // 내려가도 세션(도착 감시·스탬프)은 유지되어 복귀 시 진행 상태가 그대로다.
        container.tripSession.start()
    }
}

package app.arosaegim.ui.parked

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.arosaegim.data.FamilySessionStore

/*
 * 정차 화면용 차량 세션 저장소 홀더(T9-1·T9-3, 2026-11-11).
 *
 * StartActivity 와 DiagnosticsViewModel 이 FamilySessionStore 를 AppContainer 밖에서
 * 직접 만드는 기존 패턴을 그대로 따른다(AppContainer 는 병렬 수정 중이라 건드리지 않기 위함 —
 * 각 파일 주석 참조). SharedPreferences("family_session") 를 공유하므로 인스턴스가 여러 개여도
 * 저장 상태(가족 코드·게스트 플래그)는 서로 일치한다(FamilySessionStore 주석의 인스턴스화 규약).
 *
 * PIN 규칙(00문서 10.6): PIN 은 이 저장소의 메모리 전용 API(setPin/pinOrNull)로만 다뤄지고
 * 어디에도 저장되지 않는다. 프로세스가 끝나면 PIN 은 사라지고, 정차 화면·진단 화면은
 * pinOrNull() 이 null 이면 서버 조회를 하지 않고 해당 값을 숨긴다(00문서 13절: 없으면 숨김).
 */
@Composable
internal fun rememberFamilyStore(context: Context): FamilySessionStore =
    remember { FamilySessionStore(context.applicationContext) }

/**
 * '나중에 정하기' 세션 메모리(B1, 2026-11-11) — QuizStateHolder·CourseStartState 와 같은
 * object 홀더다(프로세스 수명, Compose mutableStateOf 로 화면 갱신만 담당).
 *
 * 규약(계약):
 *  - 이 코스(코스 id 단위)에서 '나중에 정하기'를 누르면 그 코스에서는 다시 질문하지 않는다.
 *  - **세션 메모리만** — SharedPreferences·파일 어디에도 저장하지 않는다(영속 금지).
 *    프로세스가 끝나면 사라지고, 재방문 시 다시 물어도 된다.
 *  - 로그아웃 시 [clearAll] 로 즉시 소멸시킨다(DiagnosticsViewModel.signOutFromCar 가 호출 —
 *    '로그아웃 시 소멸' 계약).
 */
object HeirQuestionMemory {

    /** '나중에 정하기'를 누른 코스 id 집합. 세션(프로세스) 메모리에만 존재한다. */
    var deferredCourseIds by mutableStateOf(emptySet<String>())
        private set

    /** 이 코스에서 '나중에 정하기'를 눌렀는지. */
    fun isDeferred(courseId: String): Boolean = courseId in deferredCourseIds

    /** '나중에 정하기' 처리 — 이 코스에서는 더 이상 질문하지 않는다. */
    fun defer(courseId: String) {
        deferredCourseIds = deferredCourseIds + courseId
    }

    /** 세션 메모리 전체 소멸(로그아웃). */
    fun clearAll() {
        deferredCourseIds = emptySet()
    }
}

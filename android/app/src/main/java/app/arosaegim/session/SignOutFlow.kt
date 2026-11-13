package app.arosaegim.session

import app.arosaegim.data.SignOutCleanup
import app.arosaegim.data.VisitQueue
import app.arosaegim.ui.parked.CourseStartState
import app.arosaegim.ui.parked.HeirQuestionMemory
import app.arosaegim.ui.parked.QuizStateHolder
import java.io.File

/**
 * 가족 세션(코드·PIN) 정리 단계의 최소 추출(T1, 2026-11-14).
 *
 * [app.arosaegim.data.FamilySessionStore] 은 SharedPreferences(Context) 의존이라 JVM 단위
 * 테스트에서 만들 수 없다. 기존 규약(FamilyCodeValidator·SessionCredentials 를 같은 파일에서
 * 분리한 것과 같은 취지)을 따라, 로그아웃 흐름이 정말로 필요로 하는 두 동작만 이 인터페이스로
 * 좁혀 두면 시험(SignOutFlowTest)은 임시 가짜로 실제 정리를 검증할 수 있다.
 *
 * 프로덕션 조립(AppContainer.signOutFlow)은 FamilySessionStore 로 위임한다 — 동작 변화는 없다.
 * 가족 코드·PIN 의 해제 규약(00문서 10.6 — PIN 은 메모리 전용, 로그아웃 = 열람 권한 상실)은
 * FamilySessionStore.signOut 본체가 그대로 담당한다.
 *
 * ⚠ 이 인터페이스는 '가족 세션' 한 단계만 다룬다. 세션 컴포넌트 전체에 마커를 두고 목록을
 * 순회하는 방식(SessionScoped)은 쓰지 않았다 — 정리 단계의 순서와 목록은 SignOutFlow.run 의
 * 본문에 고정하고, 시험(SignOutFlowTest)이 그 목록을 고정해 검증한다.
 */
interface FamilySessionCleaner {

    /** 세션 존재 여부(가족 코드 접속 또는 둘러보기 모드) — 결과 줄의 '없음' 판정용. */
    fun hasSession(): Boolean

    /** 가족 세션 해제 — FamilySessionStore.signOut 과 같은 동작(코드·세션 PIN·거절 상태 해제). */
    fun signOut()
}

/**
 * '이 차에서 로그아웃' 정리 일원화(T1, 2026-11-14).
 *
 * 기존 DiagnosticsViewModel.signOutFromCar 는 가족 코드·PIN·visits.json·visit_queue.json·
 * HeirQuestionMemory 만 지웠다. 같은 프로세스에 남는 것(TripSession 의 스탬프·현재 목표·
 * 마지막 도착·주행거리·인원, CourseStartState 의 코스 시작·기록 열람 상태, QuizStateHolder 의
 * 퀴즈 진행, VisitQueue 의 메모리 상태)은 로그아웃 뒤 다른 가족 코드로 접속하면 앞 가족의
 * 진행이 화면에 남는 원인이었다. 이 클래스가 모든 정리를 **고정된 순서** 한 번에 묶는다:
 *
 *  ① 가족 세션(코드·PIN) 해제 — [FamilySessionCleaner.signOut]. 서버의 가족 기록은 건드리지
 *     않는다(가족 코드로 다른 차에서 이어 보기 위함).
 *  ② 로컬 파일 삭제 — [SignOutCleanup.deleteFiles](visits.json·visit_queue.json). 부분 실패는
 *     deleteFiles 자체가 계속 진행하고 파일별 결과 줄로 기록한다.
 *  ③ '나중에 정하기' 세션 메모리 — [HeirQuestionMemory.clearAll](기존 규약 유지).
 *  ④ 여행 세션 — [TripSession.stop] → [TripSession.resetCourse]. stop 을 먼저 해 수집·감시가
 *     멈춘 상태에서 resetCourse 가 진행·스탬프·거리·인원·로컬 방문 기록(visitStore.clear)을
 *     초기화한다. stop 이 먼저라 resetCourse 끝의 재시작(wasStarted)은 걸리지 않는다 —
 *     로그아웃 상태로는 감시를 다시 걸지 않는다(여행은 새 세션에서 다시 시작한다).
 *     resetCourse 가 로컬 방문 기록(visitStore.clear)도 지우므로 ②와 겹쳐도 무해하다.
 *  ⑤ 퀴즈 진행 — [QuizStateHolder.reset].
 *  ⑥ 코스 시작·기록 열람 상태 — [CourseStartState.reset].
 *  ⑦ 전송 큐의 메모리 상태 — [VisitQueue.clearMemoryState](대기 항목·거절 기록·PIN 보관·건수).
 *     파일은 ②가 이미 지웠다 — 이 단계는 파일을 건드리지 않는다.
 *
 * 부분 실패 규약(SignOutCleanup.deleteFiles 와 같은 취지): 한 단계가 예외로 실패해도 나머지
 * 단계를 계속 진행하고, 실패 사실을 결과 줄에 남긴다("…: 실패" 형식 — deleteFiles 의
 * "삭제됨/없음/삭제 실패" 출력과 같은 규약으로 통일). run() 은 멱등이다 — 이미 정리된 상태에서
 * 다시 불려도 예외 없이 "없음/초기화됨" 줄만 다시 남긴다.
 *
 * 결과 줄은 진단 화면(DiagnosticsViewModel.sessionResult)에 그대로 표시된다. 코드·PIN 값은
 * 어느 줄에도 담지 않는다(로그·화면 노출 금지, 00문서 10.6).
 *
 * @param familyStore ① 단계. FamilySessionStore 위임 조립은 AppContainer.signOutFlow 가 한다.
 * @param filesDir    ② 단계가 지울 파일들이 있는 앱 전용 디렉터리.
 * @param tripSession ④ 단계. 앱 공용 세션 인스턴스를 그대로 받는다.
 * @param visitQueue  ⑦ 단계. 앱 공용 큐 인스턴스를 그대로 받는다.
 */
class SignOutFlow(
    private val familyStore: FamilySessionCleaner,
    private val filesDir: File,
    private val tripSession: TripSession,
    private val visitQueue: VisitQueue,
) {

    /**
     * 정리 전체를 순서대로 실행하고 단계별 결과 한 줄 목록을 돌려준다.
     * 실패가 있어도 흐름을 멈추지 않는다(부분 실패가 전체 정리를 막지 않게).
     */
    fun run(): List<String> {
        val lines = mutableListOf<String>()

        // ① 가족 세션(코드·PIN) 해제. 서버의 가족 기록은 이 흐름이 건드리지 않는다.
        //    hasSession 판정이 예외로 실패하면 '세션이 있었다'로 본다 — 해제 대상을
        //    과소 기록하는 것(없음으로 위장)보다 안전한 쪽이다.
        val hadSession = runCatching { familyStore.hasSession() }.getOrDefault(true)
        lines += step(
            "가족 세션(코드·PIN)",
            done = "해제됨",
            fail = "해제 실패",
            absent = "없음(해제할 세션 없음)",
            absentWhen = !hadSession,
        ) { familyStore.signOut() }

        // ② 로컬 파일(방문 기록·전송 대기 큐) 삭제. 부분 실패는 deleteFiles 내부에서
        //    파일별 줄로 기록되고 나머지 파일 삭제를 계속한다. 목록 생성 자체가
        //    예외로 흘러가는 경우(비정상 환경)에만 대체 줄 하나로 흡수한다.
        lines += runCatching { SignOutCleanup.deleteFiles(filesDir) }.getOrElse {
            listOf("로컬 파일(visits.json·visit_queue.json): 삭제 실패")
        }

        // ③ '나중에 정하기' 세션 메모리(B1) — 로그아웃 시 소멸 계약(세션 메모리만, 영속 금지).
        lines += step("이어받기 안내 메모리(나중에 정하기)") { HeirQuestionMemory.clearAll() }

        // ④ 여행 세션 — stop(수집·감시 중단) 먼저, 그 뒤 resetCourse(진행·스탬프·거리·인원·
        //    로컬 방문 기록 초기화). stop 이 먼저라 resetCourse 끝의 감시 재시작은 걸리지 않는다.
        lines += step("여행 세션(스탬프·진행·거리·인원)") {
            tripSession.stop()
            tripSession.resetCourse()
        }

        // ⑤ 퀴즈 진행(프로세스 수명 홀더).
        lines += step("퀴즈 진행") { QuizStateHolder.reset() }

        // ⑥ 코스 시작·기록 열람 상태(프로세스 수명 홀더).
        lines += step("코스 시작 상태") { CourseStartState.reset() }

        // ⑦ 전송 큐의 메모리 상태(대기 항목·거절 기록·PIN 보관·건수). 파일은 ②가 담당했다.
        lines += step("전송 큐 메모리(대기 건수·PIN 보관)") { visitQueue.clearMemoryState() }

        return lines
    }

    /**
     * 한 단계를 실행하고 결과 줄 하나를 만든다. 예외는 잡아 "실패" 줄로 바꾼다 —
     * 한 단계의 실패가 다음 단계를 막지 않는다(부분 실패 규약).
     */
    private inline fun step(
        name: String,
        done: String = "초기화됨",
        fail: String = "초기화 실패",
        absent: String? = null,
        absentWhen: Boolean = false,
        block: () -> Unit,
    ): String = try {
        block()
        if (absentWhen && absent != null) "$name: $absent" else "$name: $done"
    } catch (t: Throwable) {
        "$name: $fail"
    }
}

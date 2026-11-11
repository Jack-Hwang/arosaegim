package app.arosaegim.data

import java.io.File

/**
 * '이 차에서 로그아웃'(T9-1)의 로컬 삭제 규약(A8, 2026-11-11).
 *
 * 로그아웃 시 차량(앱 데이터)에 남으면 안 되는 것 세 가지를 한 곳에서 정의한다:
 *  1. 가족 코드 — FamilySessionStore.signOut() 이 SharedPreferences("family_session")에서 지운다.
 *  2. 세션 PIN — 같은 signOut() 이 메모리 세션 자격 증명(SessionCredentials)을 끊는다
 *     (PIN 은 어디에도 저장돼 있지 않으므로 '해제'가 전부다 — 00문서 10.6).
 *  3. 방문 기록·전송 대기 큐 파일 — 이 오브젝트가 filesDir 아래에서 직접 지운다.
 *
 * 파일 삭제 로직을 java.io.File 만 받는 순수 함수로 분리한 이유: DiagnosticsViewModel 안에
 * 있던 인라인 삭제 코드는 Context 종속이라 JVM 단위 테스트가 불가능했다. 이 오브젝트는
 * 임시 디렉터리로 실제 삭제를 검증할 수 있다(SignOutCleanupTest).
 * 기록 자체(서버 가족 기록)는 지우지 않는다 — 가족 코드로 다른 차에서 이어 보기 위함이다.
 */
object SignOutCleanup {

    /** 방문 기록(VisitStore) 로컬 파일명. VisitStore.FILE_NAME("visits.json")과 동일해야 한다. */
    const val VISITS_FILE_NAME = "visits.json"

    /** 전송 대기 큐(VisitQueue) 로컬 파일명. VisitQueue 가 filesDir/visit_queue.json 을 쓴다. */
    const val VISIT_QUEUE_FILE_NAME = "visit_queue.json"

    /** 로그아웃 시 지울 로컬 파일 목록(순서 = 진단 화면 결과 표시 순서). */
    val FILES_TO_DELETE = listOf(VISITS_FILE_NAME, VISIT_QUEUE_FILE_NAME)

    /**
     * [dir] 아래의 [names] 파일을 각각 삭제하고 파일별 결과 한 줄을 돌려준다.
     * 삭제 실패가 있어도 나머지 파일 삭제를 계속한다(부분 실패가 전체 정리를 막지 않게).
     *
     * @return "visits.json: 삭제됨" / "…: 없음(삭제할 파일 없음)" / "…: 삭제 실패" 형태의 줄 목록.
     */
    fun deleteFiles(dir: File, names: List<String> = FILES_TO_DELETE): List<String> =
        names.map { name ->
            val file = File(dir, name)
            "$name: " + when {
                !file.exists() -> "없음(삭제할 파일 없음)"
                file.isFile && file.delete() -> "삭제됨"
                else -> "삭제 실패"
            }
        }

    /** 모든 대상 파일이 실제로 사라졌는지 확인한다(로그아웃 완료 기준 검증용). */
    fun allCleared(dir: File, names: List<String> = FILES_TO_DELETE): Boolean =
        names.none { File(dir, it).exists() }
}

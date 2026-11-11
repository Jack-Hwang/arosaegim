package app.arosaegim.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * '이 차에서 로그아웃' 로컬 삭제 규약 단위 테스트(A8, 2026-11-11).
 *
 * DiagnosticsViewModel.signOutFromCar 가 쓰는 [SignOutCleanup] 은 Context 종속이 없으므로
 * 임시 디렉터리로 실제 삭제를 검증할 수 있다. 기존 인라인 구현은 Context 안에 묻혀 테스트가
 * 불가능했던 것이 이 분리로 JVM 테스트 대상이 됐다.
 *
 * 검증 포인트(00문서 T9-1 완료 기준):
 *  1. visits.json(방문 기록)·visit_queue.json(전송 대기 큐)이 실제로 지워진다.
 *  2. 파일이 없으면 '없음(삭제할 파일 없음)'으로 기록한다 — 오류가 아니다.
 *  3. 부분 실패가 나머지 파일 삭제를 막지 않는다.
 *  4. 삭제 후 allCleared 로 '앱 데이터에 남지 않는다' 완료 기준을 확인할 수 있다.
 * 가족 코드 삭제(SharedPreferences)와 PIN 해제(SessionCredentials)는 FamilySessionStore.signOut 이
 * 담당하며, PIN 해제 로직은 FamilySessionStoreTest 의 `clear는 코드와 pin을 모두 지운다` 가 검증한다.
 */
class SignOutCleanupTest {

    @Test
    fun `로그아웃 대상 파일 두 개를 실제로 지운다`() {
        val dir = Files.createTempDirectory("arosaegim-signout").toFile()
        try {
            java.io.File(dir, "visits.json").writeText("[]")
            java.io.File(dir, "visit_queue.json").writeText("[]")
            assertTrue(java.io.File(dir, "visits.json").exists())
            assertTrue(java.io.File(dir, "visit_queue.json").exists())

            val lines = SignOutCleanup.deleteFiles(dir)

            assertEquals(
                listOf("visits.json: 삭제됨", "visit_queue.json: 삭제됨"),
                lines,
            )
            assertFalse(java.io.File(dir, "visits.json").exists())
            assertFalse(java.io.File(dir, "visit_queue.json").exists())
            assertTrue(SignOutCleanup.allCleared(dir)) // 완료 기준: 로컬 파일이 하나도 남지 않는다.
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `대상 파일이 없으면 오류가 아니라 없음으로 기록한다`() {
        val dir = Files.createTempDirectory("arosaegim-signout-empty").toFile()
        try {
            val lines = SignOutCleanup.deleteFiles(dir)
            assertEquals(
                listOf(
                    "visits.json: 없음(삭제할 파일 없음)",
                    "visit_queue.json: 없음(삭제할 파일 없음)",
                ),
                lines,
            )
            assertTrue(SignOutCleanup.allCleared(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `한 파일 삭제 실패가 나머지 삭제를 막지 않는다`() {
        val dir = Files.createTempDirectory("arosaegim-signout-partial").toFile()
        try {
            val queue = java.io.File(dir, "visit_queue.json")
            queue.writeText("[]")
            // 삭제 불가능한 '디렉터리'를 visits.json 자리에 만든다 — File.delete 가 false 를 반환한다.
            java.io.File(dir, "visits.json").mkdir()

            val lines = SignOutCleanup.deleteFiles(dir)

            assertEquals("visits.json: 삭제 실패", lines[0])
            assertEquals("visit_queue.json: 삭제됨", lines[1])
            assertFalse(queue.exists()) // 나머지 파일은 정리된다.
            assertFalse(SignOutCleanup.allCleared(dir)) // 실패가 있으면 완료 기준도 거짓으로 답한다.
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `대상 파일 목록은 방문 기록과 큐 두 개다`() {
        // 로그아웃이 지워야 하는 로컬 파일의 계약 — VisitStore·VisitQueue 와 이름이 일치해야 한다.
        assertEquals(listOf("visits.json", "visit_queue.json"), SignOutCleanup.FILES_TO_DELETE)
    }
}

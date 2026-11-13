package app.arosaegim.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/*
 * ApiClient 의 HTTP 상태 코드 → 예외 타입 매핑 계약 시험(P2, 2026-11-14).
 *
 * 루프백 ServerSocket 에 최소 HTTP 응답기 하나를 띄워 실제 소켓 응답으로 검증한다
 * (안드로이드 단위 시험 클래스패스에는 내장 HTTP 서버가 없어 ServerSocket 을 쓴다).
 * 계약: 401·403·429 는 AuthException(PIN 재입력 신호 — VisitQueue 의 거절 잠금이 이 타입에
 * 걸려 같은 조합의 자동 재전송을 멈춘다), 그 외 4xx·5xx 는 IOException(일반 실패)이다.
 * 429(PIN 잠금)가 IOException 이면 큐가 멈추지 않고 잠금 10분 동안 같은 요청을 되풀이해
 * 서버 실패 횟수를 올린다 — 이 시험이 그 회귀를 잡는다.
 */
class ApiClientHttpContractTest {

    /** 요청 하나에 상태 코드 한 개로만 답는 최소 서버(몸체 없음 — 앱은 에러 몸체를 읽지 않는다). */
    private fun serverReturning(status: Int): ServerSocket {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val worker = Thread {
            try {
                server.accept().use { conn -> respond(conn, status) }
            } catch (_: Throwable) {
                // 시험 종료 시의 소켓 닫힘은 흡수한다.
            }
        }
        worker.isDaemon = true
        worker.start()
        return server
    }

    private fun respond(conn: Socket, status: Int) {
        // 요청 줄·헤더를 소비한다(빈 줄에서 멈춘다 — 몸체는 읽지 않아도 답이 나간다).
        // 입력 스트림은 닫지 않는다 — 닫으면 소켓이 먼저 끊겨 응답이 나가지 않는다.
        val reader = conn.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val head = "HTTP/1.1 $status LOCKED\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        conn.getOutputStream().write(head.toByteArray(Charsets.ISO_8859_1))
        conn.getOutputStream().flush()
    }

    private fun baseUrlOf(server: ServerSocket): String = "http://127.0.0.1:${server.localPort}"

    private fun payload() = VisitPayload(
        placeId = "gj-museum",
        courseId = "course-gyeongju",
        visitedAtIso = "2026-11-14T09:00:00Z",
    )

    @Test
    fun `429 응답은 AuthException 으로 답한다 — PIN 잠금에서 재전송을 멈추는 경로다`() = runBlocking {
        val server = serverReturning(429)
        try {
            val result = ApiClient(baseUrlOf(server)).postVisit(
                payload(), familyCode = "EXAMPLE8", pin = "0000",
            )
            assertTrue(
                "429(PIN 잠금)는 AuthException 이어야 한다 — 실제: ${result.exceptionOrNull()?.javaClass?.simpleName}",
                result.exceptionOrNull() is AuthException,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `404 응답은 AuthException 이 아니라 일반 실패로 흡수된다`() = runBlocking {
        val server = serverReturning(404)
        try {
            val result = ApiClient(baseUrlOf(server)).postVisit(
                payload(), familyCode = "EXAMPLE8", pin = "0000",
            )
            val failure = result.exceptionOrNull()
            assertTrue("404 는 IOException 이어야 한다", failure is IOException)
            assertFalse("404 는 AuthException 이 아니어야 한다", failure is AuthException)
        } finally {
            server.close()
        }
    }
}

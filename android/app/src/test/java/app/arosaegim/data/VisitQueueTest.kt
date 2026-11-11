package app.arosaegim.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** 실측 서버 응답(2026-11-11) — visit_id 는 정수, then_now 등 추가 키가 함께 온다. */
private const val REAL_SERVER_BODY =
    "{\"visit_id\": 3, \"stamp_no\": 2, \"party_size\": null, \"trip_km\": null, " +
        "\"then_now\": {\"place_id\":\"gj-museum\",\"past\":{},\"now\":{},\"heritage\":{}}}"

/**
 * VisitQueue 단위 테스트(T8) — 오프라인 큐의 멱등 적재·전송·보존 규칙을 검증한다.
 *
 * 제약 준수:
 *  - Android Context 를 쓰지 않는다. VisitStore 와 같은 방식으로 파일 경로를 직접 지정하는
 *    생성자(임시 디렉터리의 visit_queue.json)를 쓴다.
 *  - 네트워크를 치지 않는다. ApiClient 를 open class 로 만든 이유 그대로, postVisit 만
 *    가로채는 하위 클래스(FakeApiClient)로 성공/실패를 제어한다.
 *  - kotlinx-coroutines-test 미사용. runBlocking + 실제 Dispatchers.IO 로 flush 를 돌린다.
 *
 * E1 재정합(2026-11-11): 성공 응답은 '실제 서버가 돌려준 JSON 문자열'을 그대로 해석해 답한다
 * (사용자 지시) — visit_id(정수)·stamp_no 파싱과 then_now 무시가 전송 성공 판정의 기준이다.
 */
class VisitQueueTest {

    /* ---------------- 테스트용 가짜 ---------------- */

    /** postVisit 만 가로채는 가짜 ApiClient. 호출 순서·실린 값(payload·헤더)을 기록한다. */
    private class FakeApiClient : ApiClient(baseUrl = "http://localhost:0") {
        val received = mutableListOf<VisitPayload>()
        val receivedFamilyCodes = mutableListOf<String?>()
        val receivedPins = mutableListOf<String?>()

        /** 이 순서(1부터 셈)의 호출을 실패시킨다. 0 = 실패 없음. */
        var failAt = 0

        /** 모든 호출을 실패시킨다(서버 없음 상태 재현). */
        var failAlways = false

        private val attempts = java.util.concurrent.atomic.AtomicInteger()

        override suspend fun postVisit(
            v: VisitPayload,
            familyCode: String?,
            pin: String?,
        ): Result<VisitResponse> {
            // received 는 '서버에 실제 도달한 전송'만 기록한다 — 실패한 시도는 기록하지 않는다.
            // 호출 순서(order)는 시도 기준 1부터 센다.
            val order = attempts.incrementAndGet()
            return if (failAlways || order == failAt) {
                Result.failure(IOException("테스트: 전송 실패($order)"))
            } else {
                received.add(v)
                receivedFamilyCodes.add(familyCode)
                receivedPins.add(pin)
                // 실제 서버 응답 문자열을 그대로 해석한다(와이어 계약 — ignoreUnknownKeys).
                Result.success(ApiClient.wireJson.decodeFromString<VisitResponse>(REAL_SERVER_BODY))
            }
        }
    }

    /* ---------------- 픽스처 ---------------- */

    /** 분 단위가 서로 다른 시각을 만들기 위한 기준 시각(아무 고정 값). */
    private val baseMs = 1_700_000_000_000L

    private fun visit(placeId: String, minute: Long = 0): LocalVisit = LocalVisit(
        placeId = placeId,
        courseId = "course-test",
        visitedAtEpochMs = baseMs + minute * 60_000L,
        source = "fused",
    )

    /** Context 없이 임시 파일로 큐를 만든다(NIO — deprecated createTempFile 회피). */
    private fun newQueue(api: FakeApiClient): VisitQueue = VisitQueue(newTempFile(), api)

    /** PIN 공급자를 함께 주입하는 큐(E1 — flush 가 X-Family-Pin 을 구성하는 경로 검증용). */
    private fun newQueue(api: FakeApiClient, pinProvider: () -> String?): VisitQueue =
        VisitQueue(newTempFile(), api, pinProvider = pinProvider)

    private fun newTempFile(): File =
        File(Files.createTempDirectory("arosaegim-queue-test").toFile(), "visit_queue.json")

    /* ---------------- 케이스 ---------------- */

    @Test
    fun `1_같은 멱등키의 중복 enqueue는 무시된다`() {
        val api = FakeApiClient()
        val queue = newQueue(api)

        val same = visit("p1", minute = 0)
        queue.enqueue(same, familyCode = null)
        queue.enqueue(same, familyCode = null) // 완전히 같은 방문 — 무시되어야 한다
        assertEquals("동일 방문 재적재는 1건만", 1, queue.pendingCount.value)

        // 같은 장소라도 방문 시각(분)이 다르면 별도 건이다 — 멱등 키에 visitedAt(분)이 들어가기 때문.
        queue.enqueue(visit("p1", minute = 1), familyCode = null)
        assertEquals("다른 시각의 방문은 별도 건", 2, queue.pendingCount.value)
    }

    @Test
    fun `2_flush 성공시 큐가 비우고 전송 건수를 돌려준다`() = runBlocking {
        val api = FakeApiClient()
        val queue = newQueue(api)
        queue.enqueue(visit("p1"), familyCode = null)

        val result = queue.flush()

        assertTrue("전송 성공", result.isSuccess)
        assertEquals("성공 건수 1", 1, result.getOrNull())
        assertEquals("큐가 비었다", 0, queue.pendingCount.value)
        assertEquals("서버에 1건 도달", 1, api.received.size)
        assertEquals("장소 id가 실렸다", "p1", api.received[0].placeId)
        assertEquals("코스 id가 실렸다", "course-test", api.received[0].courseId)
        assertTrue(
            "방문 시각은 ISO-8601(UTC)로 실린다",
            api.received[0].visitedAtIso.endsWith("Z"),
        )
    }

    @Test
    fun `3_flush 실패시 항목이 큐에 유지된다`() = runBlocking {
        val api = FakeApiClient()
        api.failAlways = true // 서버 없음 상태 — 첫 항목에서 실패하고 끝나야 한다.
        val queue = newQueue(api)
        queue.enqueue(visit("p1"), familyCode = null)
        queue.enqueue(visit("p2"), familyCode = null)

        val result = queue.flush()

        assertTrue("전송 실패", result.isFailure)
        assertNotNull("실패 사유가 있다", result.exceptionOrNull())
        assertEquals("두 항목 모두 유지", 2, queue.pendingCount.value)
        assertEquals("첫 항목에서 중단 — 서버 도달은 0", 0, api.received.size)
    }

    @Test
    fun `4_pendingCount가 적재와 전송을 정확히 반영한다`() = runBlocking {
        val api = FakeApiClient()
        val queue = newQueue(api)
        assertEquals("빈 큐는 0", 0, queue.pendingCount.value)

        queue.enqueue(visit("p1"), familyCode = null)
        assertEquals("1건 적재", 1, queue.pendingCount.value)
        queue.enqueue(visit("p2"), familyCode = null)
        assertEquals("2건 적재", 2, queue.pendingCount.value)
        queue.enqueue(visit("p1"), familyCode = null) // 중복 — 무시
        assertEquals("중복 적재는 수를 늘리지 않는다", 2, queue.pendingCount.value)

        assertEquals("전체 전송 성공", 2, queue.flush().getOrNull())
        assertEquals("전송 후 0", 0, queue.pendingCount.value)

        queue.clear()
        assertEquals("clear 후 0", 0, queue.pendingCount.value)
    }

    @Test
    fun `5_flush는 FIFO 순서를 지키고 부분 실패 후 이어서 재전송한다`() = runBlocking {
        val api = FakeApiClient()
        api.failAt = 2 // 두 번째 호출만 실패 — 1건 전송 후 중단해야 한다.
        val queue = newQueue(api)
        queue.enqueue(visit("p1", minute = 0), familyCode = null)
        queue.enqueue(visit("p2", minute = 1), familyCode = null)
        queue.enqueue(visit("p3", minute = 2), familyCode = null)

        val first = queue.flush()
        assertTrue("두 번째에서 실패", first.isFailure)
        assertEquals("앞 1건은 이미 전송돼 큐에서 제거", 2, queue.pendingCount.value)
        assertEquals("전송 순서는 enqueue 순서(FIFO)", listOf("p1"), api.received.map { it.placeId })

        api.failAt = 0 // 서버 복구 — 남은 항목이 같은 순서로 이어 전송되는지 본다.
        val second = queue.flush()
        assertTrue("재전송 성공", second.isSuccess)
        assertEquals("남은 2건 전송", 2, second.getOrNull())
        assertEquals("순서가 유실되지 않는다", listOf("p1", "p2", "p3"), api.received.map { it.placeId })
        assertEquals("재전송 후 큐는 비었다", 0, queue.pendingCount.value)
    }

    @Test
    fun `6_pin 공급자의 세션 PIN 이 X-Family-Pin 값으로 전달된다`() = runBlocking {
        val api = FakeApiClient()
        val queue = newQueue(api, pinProvider = { "230444" }) // 세션 메모리 PIN 흉내(00문서 10.6).
        queue.enqueue(visit("p1"), familyCode = "8XTPUG8N")

        val result = queue.flush()

        assertTrue("코드·PIN 이 모두 있으면 전송 성공(E1 실측 계약)", result.isSuccess)
        assertEquals("가족 코드가 실렸다", "8XTPUG8N", api.receivedFamilyCodes[0])
        assertEquals("공급자의 PIN 이 전송된다", "230444", api.receivedPins[0])
    }

    @Test
    fun `7_enqueue 로 받은 PIN 은 파일에 저장되지 않고 전송에만 쓰인다`() = runBlocking {
        val api = FakeApiClient()
        api.failAlways = true // 서버 없음 — 첫 flush 는 실패하고 항목이 큐에 남는다.
        val file = newTempFile()
        val queue = VisitQueue(file, api, pinProvider = { null }) // 공급자는 PIN 을 모른다.
        queue.enqueue(visit("p1"), familyCode = null, courseRunId = "run-1", pin = "230444")

        // 00문서 10.6: PIN 은 파일(visit_queue.json)에 절대 쓰이지 않는다. 코스 주행
        // 식별자(course_run_id)는 파일에 저장된다(서버 몸체용 로컬 상태라 유지해야 한다).
        val fileText = if (file.exists()) file.readText() else ""
        assertTrue("큐 파일에는 PIN 이 없다", !fileText.contains("230444"))
        assertTrue("큐 파일에는 course_run_id 가 저장된다", fileText.contains("run-1"))

        api.failAlways = false // 서버 복구 — 메모리에만 묶어 둔 PIN 으로 재전송이 성공한다.
        val result = queue.flush()
        assertTrue("재전송 성공", result.isSuccess)
        assertEquals("enqueue 시 받은 PIN 이 전송에 쓰인다", "230444", api.receivedPins[0])
        assertEquals("코스 주행 식별자가 몸체에 실린다", "run-1", api.received[0].courseRunId)
    }

    @Test
    fun `8_실측 서버 응답 JSON 을 파싱해 visit_id와 stamp_no 로 성공 판정한다`() {
        // 사용자 지시: FakeApiClient 가 실제 서버 응답 문자열을 그대로 해석한다(위 가짜 참조).
        // 여기서는 그 문자열이 VisitResponse 계약(정수 visit_id·stamp_no)으로 읽히는지 직접 확인.
        val parsed = ApiClient.wireJson.decodeFromString<VisitResponse>(REAL_SERVER_BODY)
        assertEquals("visit_id 는 정수 3", 3, parsed.visitId)
        assertEquals("stamp_no 는 2", 2, parsed.stampNo)
    }

    @Test
    fun `9_실측 응답에는 then_now 등 추가 키가 있어도 flush 가 성공한다`() = runBlocking {
        val api = FakeApiClient() // 성공 응답은 항상 REAL_SERVER_BODY(then_now 포함)로 답한다.
        val queue = newQueue(api)
        queue.enqueue(visit("p1"), familyCode = null)

        val result = queue.flush()

        assertTrue("추가 키는 무시되고 visit_id·stamp_no 로 성공 판정", result.isSuccess)
        assertEquals("전송 1건", 1, result.getOrNull())
        assertEquals("큐가 비었다", 0, queue.pendingCount.value)
    }
}

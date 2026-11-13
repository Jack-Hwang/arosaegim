package app.arosaegim.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 전송 대기 1건 — LocalVisit + 전송 시 필요한 가족 코드·코스 주행 식별자.
 * filesDir/visit_queue.json 에 JSON 배열로 유지되며 배열 순서가 곧 전송 순서다(FIFO).
 * PIN 은 이 구조에 없다 — 00문서 10.6(PIN 저장 금지)에 따라 메모리에서만 흐른다(클래스 주석 참조).
 */
@Serializable
private data class QueuedVisit(
    val placeId: String,
    val courseId: String,
    val visitedAtEpochMs: Long,
    val source: String,
    val vehicleLabel: String? = null,
    val familyCode: String? = null,
    val courseRunId: String? = null,
)

/**
 * PIN 거절(2026-11-13 — 방문 전송 경로의 안전장치 전용 실패). 서버가 가족 코드·PIN 이 모두
 * 실린 전송을 거부했거나(401·403 — [AuthException]), 서버가 거부한 조합의 자동 재전송을
 * [VisitQueue] 가 막았을 때 flush 의 실패 타입으로 돌아온다. 호출부(TripSession)는 이 타입을
 * 보고 화면 한 줄에 'PIN 재입력' 안내를 고른다. 메시지에 가족 코드·PIN 은 담지 않는다.
 */
class PinRejectedException(
    message: String = "PIN이 거부됐습니다(재입력 필요)",
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 오프라인 전송 큐(T8) — 서버에 아직 못 보낸 방문 기록을 로컬 파일에 보존한다.
 *
 * 설계 의도(02문서 4절: "전송 실패 시 로컬 큐, 다음 기회 재전송"):
 *  - offline-first. 서버 없는 환경(기본 상황)에서도 앱의 모든 기능이 동작해야 하므로,
 *    enqueue 는 실패해도 예외를 던지지 않고, flush 실패도 Result.failure 로 조용히 보고한다.
 *  - 멱등 키 = placeId + courseId + visitedAt(분 단위) + familyCode. 같은 방문을 실수로
 *    여러 번 enqueue 해도 큐에 한 건만 남는다(서버에도 중복 스탬프가 생기지 않게).
 *  - flush 는 FIFO 순서로 전송하고 성공한 항목만 제거한다. 하나라도 실패하면 거기서 멈추고
 *    남은 항목은 그대로 둔다 — 순서 보장(뒤 항목이 앞으로 새지 않음)이 핵심이다.
 *  - 좌표는 큐에도 저장하지 않는다(VisitStore 규약과 동일 — 위치는 도착 판정에만 쓰고 버린다).
 *  - 이 파일에는 Log 호출이 없다. 좌표·가족코드·메모를 로그로 남기지 않는 규칙 준수.
 *
 * open class 인 이유: TripSessionTest 가 enqueue/flush 만 기록하는 하위 클래스로
 * 대체할 수 있게 한다(VisitStore 의 open 규약과 같은 취지).
 *
 * E1 재정합(2026-11-11 서버 실측): 서버가 X-Family-Pin 헤더를 요구하므로 flush 가 PIN 을
 * 실어 보낸다. PIN 은 [pinProvider] 로 '전송 순간'에만 읽고, 큐 파일(visit_queue.json)에는
 * 절대 쓰지 않는다(00문서 10.6 PIN 저장 금지). enqueue 로 건네진 PIN 도 마찬가지로 메모리
 * 맵([pendingPins])에만 잠깐 묶어 둔다 — 프로세스가 끝나면 사라지고, 그때는 pinProvider
 * (세션에 다시 입력된 PIN)가 대신 답한다.
 *
 * 전송 시점·재시도 예산(E④ 확인, 2026-11-13):
 *  - flush 의 유일한 전송 트리거는 TripSession 도착 처리의 enqueue 직후 1회 호출이다
 *    (주기 폴링·타이머 없음 — 서버 없는 환경에서 큐가 공회전하지 않는다. 설계 요건 충족 확인).
 *  - 전송 실패 시 첫 실패 항목에서 즉시 중단하고 남은 항목을 큐에 유지한다. 재시도는
 *    다음 enqueue(다음 도착)가 flush 를 다시 부를 때 일어나며, 재시도 예산은 곧
 *    '전송 트리거 횟수'(= enqueue 횟수)다 — 백그라운드 폴링으로 소모되지 않는다.
 *  - 이 절은 확인 결과의 문서화만이다. 오프라인 우선 계약 보호를 위해 로직은 변경하지 않았다.
 *
 * PIN 오입력 반복 방지(E⑤, 2026-11-13): 서버가 가족 코드·PIN 이 모두 실린 전송을 거부하면
 * (401·403 → [AuthException] — 서버 계약상 'PIN이 틀렸다'의 신호이며 실패 1회를 기록한다),
 *  1. 거부된 (가족 코드, PIN) 조합을 메모리에 기록하고([rejectedPair] — 파일에 쓰지 않는다),
 *  2. 메모리에 묶어 둔 같은 값의 PIN([pendingPins])을 치워 재입력된 새 값이 흐르게 하고,
 *  3. 세션 자격 증명에도 거절을 표시한다([onAuthRejected] — 기본값은 FamilySessionStore 의
 *     프로세스 전역 표시. 현재 세션 PIN 이 거부된 값과 같을 때만 — 사용자가 이미 새 PIN 을
 *     넣었으면 새 값은 거르지 않는다).
 * 이후 flush 는 그 조합을 다시 보내지 않는다 — 같은 코드에 PIN 이 없으면(재입력 전) 전송을
 * 생략하고([PinRejectedException] 으로 큐를 보존한 채 실패로 답한다), 다른 PIN 이 들어오면
 * 재개한다. 이 안전장치 없이는 도착마다 같은 틀린 PIN 이 재전송돼 서버 실패 횟수(5회)를
 * 채우고 가족 전체가 10분 잠긴다. 큐 파일은 절대 지우지 않는다(재입력 후 재전송 계약 유지).
 *
 * @param file        visit_queue.json 파일(테스트는 임시 파일을 직접 지정한다).
 * @param api         전송 담당 클라이언트. flush 가 유일한 호출자다.
 * @param pinProvider 전송 시점의 세션 PIN 공급자(예: { sessionStore.pinOrNull() }).
 *                    null·빈값이면 X-Family-Pin 없이 보낸다(서버 401 — 큐는 보존된다).
 * @param onAuthRejected 서버 거부 시 거절을 표시하는 콜백(거부된 PIN 값 인자). 기본값은
 *                    FamilySessionStore.rejectSessionPin — 프로세스 전역 세션 자격 증명에
 *                    표시한다(큐는 저장소 인스턴스를 갖지 않으므로 companion 경유).
 */
open class VisitQueue(
    private val file: File,
    private val api: ApiClient,
    private val pinProvider: () -> String? = { null },
    private val onAuthRejected: (rejectedPin: String) -> Unit = { rejectedPin ->
        FamilySessionStore.rejectSessionPin(rejectedPin)
    },
) {

    /** 앱에서 쓰는 표준 생성자 — filesDir/visit_queue.json 을 가리킨다. */
    constructor(context: Context, api: ApiClient, pinProvider: () -> String? = { null }) : this(
        File(context.applicationContext.filesDir, FILE_NAME),
        api,
        pinProvider,
    )

    /** 읽기-수정-쓰기 경쟁 방지용 잠금(TripSession 콜백과 flush 코루틴이 같이 접근한다). */
    private val lock = Any()

    /**
     * enqueue 로 건네진 PIN 의 메모리 전용 보관(멱등 키 → PIN). 파일에는 절대 쓰지 않는다
     * (00문서 10.6). flush 가 성공해 큐에서 빠지거나 clear 되면 함께 지운다.
     */
    private val pendingPins = mutableMapOf<String, String>()

    /**
     * 서버가 거부한 (가족 코드, PIN) 조합의 마지막 기록(E⑤). 메모리 전용 — 파일에 쓰지 않는다.
     * null = 거부 기록 없음. 같은 코드의 항목은 이 조합으로 다시 보내지 않는다(클래스 주석 E⑤).
     */
    @Volatile
    private var rejectedPair: Pair<String, String>? = null

    private val _pending = MutableStateFlow(0)

    /** 미전송 건수. 화면·진단이 '아직 안 보냄' 상태를 볼 때 쓴다. */
    open val pendingCount: StateFlow<Int> = _pending.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true // 사람이 열어 확인 가능하게(발주자가 코드·파일을 직접 설명한다).
    }

    // 모든 프로퍼티가 준비된 뒤(위 초기화 순서 보장) 파일 사실로 미전송 건수를 맞춘다.
    init {
        refreshPendingCount()
    }

    /**
     * 방문 기록 1건을 큐에 적재한다. 멱등 키가 같은 항목이 이미 있으면 조용히 무시한다.
     * 실패해도 예외를 던지지 않는다(기록 실패가 주행 흐름을 막지 않게).
     *
     * @param familyCode  가족 코드(T9-1 연동 전까지는 null 로 들어온다).
     * @param courseRunId 이 코스 주행(start 1회)의 식별자 — 서버 몸체 course_run_id 로 실린다.
     *                    세션 재시작마다 새 값이므로, 재시작 앞뒤의 주행 거리는 서버에서
     *                    서로 다른 주행으로 분리 합산된다.
     * @param pin         이 방문 시점의 세션 PIN. 메모리에만 묶어 둔다(파일 저장 금지 —
     *                    00문서 10.6). 없으면 flush 때 [pinProvider] 가 대신 답한다.
     */
    open fun enqueue(
        visit: LocalVisit,
        familyCode: String?,
        courseRunId: String? = null,
        pin: String? = null,
    ) {
        synchronized(lock) {
            try {
                val current = readAllInternal()
                val item = visit.toQueued(familyCode, courseRunId)
                val key = item.idempotencyKey()
                // 중복 적재 방지 — 같은 방문(같은 분 단위 시각)은 한 번만 쌓인다.
                if (current.any { it.idempotencyKey() == key }) return
                // PIN 은 메모리에만(파일 반영 전에 기록 — 실패해도 provider 가 보완한다).
                pin?.takeIf { it.isNotBlank() }?.let { pendingPins[key] = it }
                file.writeText(json.encodeToString(current + item))
                _pending.value = current.size + 1
            } catch (t: Throwable) {
                // 파일 실패(IOException 등)는 흡수한다. 다음 flush 기회에 다시 시도 가능.
            }
        }
    }

    /**
     * 큐의 앞에서부터 순서대로 전송한다. 성공한 항목만 큐에서 제거하고, 하나라도 실패하면
     * 중단해 나머지를 유지한다(재전송 대기). 서버가 없으면 첫 항목에서 실패로 끝난다.
     * 서버가 거부한 (가족 코드, PIN) 조합은 다시 보내지 않는다(E⑤ — 클래스 주석 참조).
     *
     * @return 성공 시 전송한 건수, 실패 시 Result.failure(전송된 건수는 유실되지 않는다 —
     *         이미 큐에서 제거됐으므로 pendingCount 가 사실을 유지한다). 거절 관련 실패는
     *         [PinRejectedException], 그 외는 원래 예외다.
     */
    open suspend fun flush(): Result<Int> = withContext(Dispatchers.IO) {
        // 전송 루프 전에 스냅샷을 읽는다. 루프 중 enqueue 되는 항목은 다음 flush 가 맡는다.
        val snapshot = synchronized(lock) {
            try {
                readAllInternal()
            } catch (t: Throwable) {
                emptyList() // 깨진 파일은 빈 큐로 복구(크래시 금지).
            }
        }
        if (snapshot.isEmpty()) {
            // 파일이 외부(T9-1 로그아웃 화면이 파일을 직접 삭제)에서 지워졌을 수 있다.
            // 카운트를 파일 사실로 다시 맞춰 화면 표시가 틀리지 않게 한다.
            refreshPendingCount()
            return@withContext Result.success(0)
        }

        var sent = 0
        for (item in snapshot) {
            // PIN 결정: enqueue 시 받은 값(메모리 맵)이 우선, 없으면 공급자에서 '지금' 읽는다.
            // 전송 순간에 다시 읽는 이유 — 도착 때 PIN 이 없었다가 재입력돼 있을 수 있기 때문이다.
            val key = item.idempotencyKey()
            val pin = synchronized(lock) { pendingPins[key] } ?: pinProvider()

            // E⑤ 안전장치: 서버가 거부한 조합은 다시 보내지 않는다. 같은 가족 코드의 항목이
            // PIN 없이(재입력 전) 오거나 거부된 값 그대로 오면 전송 자체를 생략한다 — 서버의
            // 실패 횟수를 올리지 않고, 큐는 그대로 보존한다. 다른 PIN 이 들어왔으면(재입력)
            // 이 분기에 걸리지 않아 재개된다.
            val latch = synchronized(lock) { rejectedPair }
            if (latch != null && latch.first == item.familyCode &&
                (pin.isNullOrBlank() || pin == latch.second)
            ) {
                return@withContext Result.failure(PinRejectedException("PIN 재입력 대기 중"))
            }

            val result = try {
                api.postVisit(item.toPayload(), item.familyCode, pin)
            } catch (t: Throwable) {
                // postVisit 이 예외를 던져도 큐는 살아남아야 한다(AuthException 포함 — 재입력 후 재전송).
                Result.failure(t)
            }
            if (result.isFailure) {
                val failure = result.exceptionOrNull() ?: IOException("전송 실패")
                // 가족 코드·PIN 이 모두 실린 전송만 'PIN 거부'로 본다 — 둘 중 하나가 빠진 401 은
                // 서버가 PIN 을 검증하기 전의 거절(코드·PIN 헤더 누락)이라 실패 횟수와 무관하다.
                val rejectedCode = item.familyCode?.takeIf { it.isNotBlank() }
                val rejectedPin = pin?.takeIf { it.isNotBlank() }
                if (failure is AuthException && rejectedCode != null && rejectedPin != null) {
                    // 서버가 (가족 코드, PIN) 조합을 거부했다(실패 1회 기록 — 반복하면 잠금).
                    // 같은 조합의 자동 재전송을 멈춘다: 조합 기록 → 메모리 PIN 치우기 → 세션
                    // 거절 표시(현재 세션 PIN 이 이 값일 때만). 큐는 유지된다(재입력 후 재전송).
                    synchronized(lock) {
                        rejectedPair = Pair(rejectedCode, rejectedPin)
                        pendingPins.entries.removeAll { it.value == rejectedPin }
                    }
                    if (rejectedPin == pinProvider()) {
                        runCatching { onAuthRejected(rejectedPin) } // 콜백 실패도 흡수한다(offline-first).
                    }
                    return@withContext Result.failure(PinRejectedException(cause = failure))
                }
                // 하나라도 실패하면 중단 — 이 항목부터 끝까지 큐에 유지(순서 보장).
                return@withContext Result.failure(failure)
            }
            sent++
            removeByKey(key)
            // E⑤: 전송이 성공했으면 거부 기록은 더 필요 없다(조합이 서버에 통과했다).
            synchronized(lock) { rejectedPair = null }
        }
        Result.success(sent)
    }

    /** 큐 전체 삭제 — '이 차에서 로그아웃'(T9-1)용. 방문 기록(VisitStore)과 별개로 운영한다. */
    open fun clear() {
        synchronized(lock) {
            try {
                if (file.exists()) file.delete()
            } catch (t: Throwable) {
                // 삭제 실패도 흡수한다. pendingCount 는 0 으로 본다(다음 flush 가 정리).
            }
            pendingPins.clear() // 메모리 PIN 도 함께 버린다(로그아웃 = 열람 권한 상실).
            rejectedPair = null // E⑤: 거부 기록도 함께 버린다(새 세션은 새 조합이다).
            _pending.value = 0
        }
    }

    /** 멱등 키가 같은 항목 1건을 큐에서 제거한다(flush 성공 경로 전용). */
    private fun removeByKey(key: String) {
        synchronized(lock) {
            try {
                val current = readAllInternal()
                val updated = current.filterNot { it.idempotencyKey() == key }
                if (updated.size != current.size) {
                    file.writeText(json.encodeToString(updated))
                }
                pendingPins.remove(key) // 전송 성공 — 메모리 PIN 도 같이 치운다.
                _pending.value = updated.size
            } catch (t: Throwable) {
                // 제거 실패 시 항목이 큐에 남는다 — 서버 중복보다 기록 유실이 나쁘다.
            }
        }
    }

    /** 파일을 다시 읽어 pendingCount 를 현재 사실로 맞춘다(생성자 초기화용). */
    private fun refreshPendingCount() {
        synchronized(lock) {
            _pending.value = try {
                readAllInternal().size
            } catch (t: Throwable) {
                0
            }
        }
    }

    /** 실제 파일 읽기/파싱. lock 안에서만 부른다. 파일이 없거나 깨졌으면 빈 목록. */
    private fun readAllInternal(): List<QueuedVisit> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(text)
    }

    /** LocalVisit → 큐 항목. 가족 코드·주행 식별자는 전송 시점까지 여기서만 보관한다. */
    private fun LocalVisit.toQueued(familyCode: String?, courseRunId: String?) = QueuedVisit(
        placeId = placeId,
        courseId = courseId,
        visitedAtEpochMs = visitedAtEpochMs,
        source = source,
        vehicleLabel = vehicleLabel,
        familyCode = familyCode,
        courseRunId = courseRunId,
    )

    /**
     * 큐 항목 → 서버 몸체(E1 계약). 시각은 ISO-8601(UTC) 문자열로 변환한다.
     * java.time 은 minSdk 28 에서 표준 제공된다(desugaring 불필요).
     * source 는 서버에 extra=forbid 로 거부되는 필드라 몸체에서 뺐다(VisitPayload 주석 참조).
     * party_size·trip_km 은 아직 연동 전 — null 로 두면 몸체에서 생략된다(가짜 값 금지).
     */
    private fun QueuedVisit.toPayload() = VisitPayload(
        placeId = placeId,
        courseId = courseId,
        visitedAtIso = Instant.ofEpochMilli(visitedAtEpochMs).toString(),
        vehicleLabel = vehicleLabel,
        courseRunId = courseRunId,
    )

    /**
     * 멱등 키: placeId + courseId + 방문 시각(분 단위 내림) + familyCode.
     * 같은 스탬프는 언제 enqueue 해도 같은 키가 나온다(중복 적재·재전송 방지의 기준).
     */
    private fun QueuedVisit.idempotencyKey(): String = listOf(
        placeId,
        courseId,
        (visitedAtEpochMs / MILLIS_PER_MINUTE).toString(),
        familyCode ?: "",
    ).joinToString("|")

    private companion object {
        const val FILE_NAME = "visit_queue.json"
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

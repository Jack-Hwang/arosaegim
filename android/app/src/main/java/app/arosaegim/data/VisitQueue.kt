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
 * @param file        visit_queue.json 파일(테스트는 임시 파일을 직접 지정한다).
 * @param api         전송 담당 클라이언트. flush 가 유일한 호출자다.
 * @param pinProvider 전송 시점의 세션 PIN 공급자(예: { sessionStore.pinOrNull() }).
 *                    null·빈값이면 X-Family-Pin 없이 보낸다(서버 401 — 큐는 보존된다).
 */
open class VisitQueue(
    private val file: File,
    private val api: ApiClient,
    private val pinProvider: () -> String? = { null },
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
     *
     * @return 성공 시 전송한 건수, 실패 시 Result.failure(전송된 건수는 유실되지 않는다 —
     *         이미 큐에서 제거됐으므로 pendingCount 가 사실을 유지한다).
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
            val result = try {
                api.postVisit(item.toPayload(), item.familyCode, pin)
            } catch (t: Throwable) {
                // postVisit 이 예외를 던져도 큐는 살아남아야 한다(AuthException 포함 — 재입력 후 재전송).
                Result.failure(t)
            }
            if (result.isFailure) {
                // 하나라도 실패하면 중단 — 이 항목부터 끝까지 큐에 유지(순서 보장).
                return@withContext Result.failure(
                    result.exceptionOrNull() ?: IOException("전송 실패"),
                )
            }
            sent++
            removeByKey(key)
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

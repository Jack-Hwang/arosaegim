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
 * 전송 대기 1건 — LocalVisit + 전송 시 필요한 가족 코드.
 * filesDir/visit_queue.json 에 JSON 배열로 유지되며 배열 순서가 곧 전송 순서다(FIFO).
 */
@Serializable
private data class QueuedVisit(
    val placeId: String,
    val courseId: String,
    val visitedAtEpochMs: Long,
    val source: String,
    val vehicleLabel: String? = null,
    val familyCode: String? = null,
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
 * @param api   전송 담당 클라이언트. flush 가 유일한 호출자다.
 */
open class VisitQueue {

    /** visit_queue.json 이 놓이는 파일. 앱 전용 filesDir 아래다(기본 생성자). */
    private val file: File

    /** 전송 담당 클라이언트. */
    private val api: ApiClient

    /** 앱에서 쓰는 표준 생성자 — filesDir/visit_queue.json 을 가리킨다. */
    constructor(context: Context, api: ApiClient) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
        this.api = api
        refreshPendingCount()
    }

    /** 파일을 직접 지정하는 생성자 — 단위 테스트가 Context 없이 임시 파일로 대체할 때 쓴다. */
    constructor(file: File, api: ApiClient) {
        this.file = file
        this.api = api
        refreshPendingCount()
    }

    /** 읽기-수정-쓰기 경쟁 방지용 잠금(TripSession 콜백과 flush 코루틴이 같이 접근한다). */
    private val lock = Any()

    private val _pending = MutableStateFlow(0)

    /** 미전송 건수. 화면·진단이 '아직 안 보냄' 상태를 볼 때 쓴다. */
    open val pendingCount: StateFlow<Int> = _pending.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true // 사람이 열어 확인 가능하게(발주자가 코드·파일을 직접 설명한다).
    }

    /**
     * 방문 기록 1건을 큐에 적재한다. 멱등 키가 같은 항목이 이미 있으면 조용히 무시한다.
     * 실패해도 예외를 던지지 않는다(기록 실패가 주행 흐름을 막지 않게).
     *
     * @param familyCode 가족 코드(T9-1 연동 전까지는 null 로 들어온다).
     */
    open fun enqueue(visit: LocalVisit, familyCode: String?) {
        synchronized(lock) {
            try {
                val current = readAllInternal()
                val item = visit.toQueued(familyCode)
                val key = item.idempotencyKey()
                // 중복 적재 방지 — 같은 방문(같은 분 단위 시각)은 한 번만 쌓인다.
                if (current.any { it.idempotencyKey() == key }) return
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
            val result = try {
                api.postVisit(item.toPayload(), item.familyCode)
            } catch (t: Throwable) {
                // postVisit 이 예외를 던져도 큐는 살아남아야 한다.
                Result.failure(t)
            }
            if (result.isFailure) {
                // 하나라도 실패하면 중단 — 이 항목부터 끝까지 큐에 유지(순서 보장).
                return@withContext Result.failure(
                    result.exceptionOrNull() ?: IOException("전송 실패"),
                )
            }
            sent++
            removeByKey(item.idempotencyKey())
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

    /** LocalVisit → 큐 항목. 가족 코드는 전송 시점까지 여기서만 보관한다. */
    private fun LocalVisit.toQueued(familyCode: String?) = QueuedVisit(
        placeId = placeId,
        courseId = courseId,
        visitedAtEpochMs = visitedAtEpochMs,
        source = source,
        vehicleLabel = vehicleLabel,
        familyCode = familyCode,
    )

    /**
     * 큐 항목 → 서버 몸체. 시각은 ISO-8601(UTC) 문자열로 변환한다.
     * java.time 은 minSdk 28 에서 표준 제공된다(desugaring 불필요).
     */
    private fun QueuedVisit.toPayload() = VisitPayload(
        placeId = placeId,
        courseId = courseId,
        visitedAtIso = Instant.ofEpochMilli(visitedAtEpochMs).toString(),
        source = source,
        vehicleLabel = vehicleLabel,
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

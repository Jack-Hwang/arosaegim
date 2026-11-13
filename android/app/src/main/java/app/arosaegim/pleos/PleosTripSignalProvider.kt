package app.arosaegim.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import android.content.Context
import app.arosaegim.ports.TripSignalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * TripSignalProvider 의 Pleos 구현 — T6-2(Seat 점유 인원 수)·T5-1(Odometer 주행거리).
 *
 * AAR( Vehicle 2.3.2 )을 javap 로 확정한 시그니처만 쓴다(DEV_LOG F38·F39. 추측 금지 규칙):
 *  - seat.checkSeatOccupancyCapabilities(onComplete:(List<SeatArea>), onFailed)
 *  - seat.getCurrentSeatOccupancy(SeatArea, onComplete:(Boolean), onFailed)
 *  - SeatArea = SeatArea(SeatValue.ROW_1_LEFT … ROW_3_*) 가변인자
 *    (공식 샘플 VehicleApiExplorer.kt 의 `SeatArea(enumValue<SeatValue>(selected))` 와 일치)
 *  - odo.getCurrentDistanceDriven(onComplete:(Float), onFailed)
 *
 * 규칙:
 *  - partySize 는 '점유(true) 좌석 수 합계'만 돌려준다. 좌석별·사람별 정보는 만들지 않는다
 *    (00문서 11-2: 좌석 신호는 세대 간 인원 비교에만 쓴다).
 *  - 모든 단계를 runCatching 으로 감싸고, 실패 시 null 로 답한다 — 가짜 값 금지(T5-1).
 *  - SDK 콜백 대기는 메인 스레드 전제(기존 Vehicle 어댑터 규약) + 3초 타임아웃.
 *  - 좌석 응답률이 50% 미만이면 partySize 는 null 이다 — 일부만 읽힌 합산이 '1명' 같은
 *    잘못된 표시로 이어가지 않게 한다(Q27·F73). 집계 규칙은 countOccupied 순수 함수가 맡는다.
 */
class PleosTripSignalProvider : TripSignalProvider {

    private val vehicle: Vehicle
    private var initialized = false

    /** 마지막 초기화·조회 오류(진단 표시용 — 화면 표시로만 쓰고 저장하지 않는다). */
    var lastError: String? = null
        private set

    /**
     * 마지막 좌석 조회의 성공/실패 개수 한 줄(진단 화면 표시 전용 — Q27).
     * 예: "좌석 조회: 요청 9 / 성공 5 / 실패 4". 개수만 담고 좌석 값·식별자는 담지 않으며,
     * 저장·전송하지 않는다(lastError 와 같은 표시 전용 규약).
     */
    var lastSeatDiagnostics: String? = null
        private set

    /** 이미 초기화된 Vehicle 을 주입받는 생성자(AppContainer 가 같은 규약으로 재사용할 때). */
    constructor(vehicle: Vehicle, alreadyInitialized: Boolean) {
        this.vehicle = vehicle
        this.initialized = alreadyInitialized
    }

    /** Vehicle 을 직접 만들고 초기화한다(메인 스레드 전제 — AppContainer 생성 규약 참조). */
    constructor(context: Context) {
        vehicle = Vehicle(context.applicationContext)
        initialized = try {
            vehicle.initialize()
            true
        } catch (t: Throwable) {
            lastError = "Vehicle.initialize 실패: ${t.javaClass.simpleName}"
            false
        }
    }

    override fun toString(): String = "PleosTripSignalProvider(initialized=$initialized)"

    /** 초기화 성공 여부 — AppContainer 가 구현 선택(Pleos/폴백) 판정에 쓴다. */
    fun isInitialized(): Boolean = initialized

    /**
     * 좌석 점유로 '오늘 함께 온 인원 수'(T6-2). 읽기 실패·타임아웃은 null.
     *
     * 합산 방식(Q27 견고화): capability 로 받은 영역 목록에서 좌석(SeatValue)을 모아, 그 목록에
     * 담긴 좌석만 getCurrentSeatOccupancy 로 병렬 조회한다. capability 가 일부 좌석만 줘도
     * 받은 목록 기준으로만 집계한다 — 미제공 좌석을 추측해 세지 않는다(가짜 값 금지).
     * 좌석별 실패(null)는 '답 없음'으로 제외하되, 응답률이 50% 미만이면 불안정 판정으로
     * null 을 돌려준다(countOccupied 참조 — Q27·F73 '오늘은 1명' 오표시 방지).
     */
    override suspend fun partySize(): Int? {
        if (!initialized) return null
        val capabilitySeats = runCatching { checkSeatCapabilitySeats() }.getOrNull() ?: return null
        // capability 가 빈 목록이면 '조회 가능한 좌석 없음'으로 본다(실패와 같은 취급).
        val candidates = capabilitySeats.takeIf { it.isNotEmpty() } ?: return null
        if (candidates.size > MAX_SEATS) return null // 비정상적으로 넓은 목록은 보수적으로 거부.

        // 좌석별 조회는 병렬로 보낸다(순차면 9좌석 × 3초 최악 27초 — 세션 시작을 늦춘다).
        val results = runCatching {
            coroutineScope {
                candidates.map { seat -> async { askSeatOccupancy(seat) } }.awaitAll()
            }
        }.getOrNull() ?: return null

        // 진단 표시용 한 줄. 개수만 센다 — 좌석 값·식별자는 담지 않고 저장·전송하지도 않는다.
        val answeredCount = results.count { it != null }
        lastSeatDiagnostics =
            "좌석 조회: 요청 ${results.size} / 성공 $answeredCount / 실패 ${results.size - answeredCount}"

        // 키 = 요청 좌석 전부, 값 = 좌석별 답(null = 실패·타임아웃). 판정 규칙은 순수 함수가 맡는다.
        val answers = candidates.zip(results).toMap()
        return countOccupied(answers)?.coerceAtMost(MAX_SEATS)
    }

    /**
     * 현재 주행거리계 값(km 가정, T5-1). 읽기 실패·타임아웃은 null.
     * ★ 단위 미확인(U13 실측 과제, 01문서 10절) — km 로 가정한다. 실측 후 단위가 밝혀지면
     *   여기서 변환하거나 표기를 고친다. 값은 '총 주행거리' 취급이며 세션이 시작~완주 차이를 낸다.
     */
    override suspend fun odometerKm(): Double? {
        if (!initialized) return null
        val km = runCatching {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(CALL_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            vehicle.getOdometer().getCurrentDistanceDriven(
                                onComplete = { value -> if (cont.isActive) cont.resume(value) },
                                onFailed = { e ->
                                    lastError = "Odometer.onFailed: ${e.javaClass.simpleName}"
                                    if (cont.isActive) cont.resume(Float.NaN)
                                },
                            )
                        }.onFailure { t ->
                            lastError = "Odometer 호출 실패: ${t.javaClass.simpleName}"
                            if (cont.isActive) cont.resume(Float.NaN)
                        }
                    }
                }
            }
        }.getOrNull() ?: return null
        if (km.isNaN()) return null // onFailed·호출 실패는 NaN 마커로 돌려 null 로 정규화한다.
        return km.toDouble()
    }

    /**
     * capability 조회 → '좌석별 단일 영역' 목록만 남긴 SeatValue 집합.
     * 영역 하나에 여러 좌석이 묶여 와도 좌석 단위로 풀어 센다(SeatArea 는 SeatValue 가변인자).
     */
    private suspend fun checkSeatCapabilitySeats(): Set<SeatValue> =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(CALL_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        vehicle.getSeat().checkSeatOccupancyCapabilities(
                            onComplete = { areas ->
                                // SeatArea.values(SeatValue 목록)를 좌석 단위로 풀어 집합으로 만든다.
                                val seats = runCatching {
                                    areas.flatMap { it.values.toList() }.toSet()
                                }.getOrDefault(CANONICAL_SEATS.toSet())
                                if (cont.isActive) cont.resume(seats)
                            },
                            onFailed = { e ->
                                lastError = "Seat capability onFailed: ${e.javaClass.simpleName}"
                                if (cont.isActive) cont.resume(emptySet())
                            },
                        )
                    }.onFailure { t ->
                        lastError = "Seat capability 호출 실패: ${t.javaClass.simpleName}"
                        if (cont.isActive) cont.resume(emptySet())
                    }
                }
            } ?: emptySet() // 타임아웃 → 조회 실패와 같은 취급(partySize 는 null 이 된다).
        }

    /** 한 좌석의 점유 여부. 실패·타임아웃은 null(합산에서 제외). */
    private suspend fun askSeatOccupancy(seat: SeatValue): Boolean? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(CALL_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        vehicle.getSeat().getCurrentSeatOccupancy(
                            SeatArea(seat),
                            onComplete = { occupied -> if (cont.isActive) cont.resume(occupied) },
                            onFailed = { if (cont.isActive) cont.resume(null) },
                        )
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
        }

    /**
     * 종료 시 일괄 해제. 규칙(01문서): SDK 콜백(onComplete/onFailed)이 끝나기 전에 release 금지 —
     * 호출자(AppContainer.closeAll)는 tripSession.stop() 으로 조회 코루틴을 먼저 끝낸 뒤 부른다.
     */
    fun release() {
        if (!initialized) return
        initialized = false
        runCatching { vehicle.release() }
            .onFailure { lastError = "Vehicle.release 실패: ${it.javaClass.simpleName}" }
    }

    companion object {
        /** SDK 콜백 대기 한도(포트당). AppContainer 의 INIT_WAIT_MS 와 같은 근거(데모 안정성 우선). */
        const val CALL_TIMEOUT_MS = 3_000L

        /** ROW_1~3 × 좌/중/우 최대 9좌석(00문서 SeatValue 상수 목록 기준). */
        const val MAX_SEATS = 9

        /** capability 가 영역별 values 를 주지 않을 때의 보수적 전체 좌석 후보. */
        private val CANONICAL_SEATS = listOf(
            SeatValue.ROW_1_LEFT, SeatValue.ROW_1_CENTER, SeatValue.ROW_1_RIGHT,
            SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT,
            SeatValue.ROW_3_LEFT, SeatValue.ROW_3_CENTER, SeatValue.ROW_3_RIGHT,
        )
    }
}

/**
 * 좌석 점유 집계 순수 함수(Q27 견고화 — JVM 단위 테스트 대상).
 *
 * [answers]의 키는 조회를 요청한 좌석 전부(capability 가 준 목록), 값은 좌석별 답이다.
 * true = 점유, false = 빈 좌석, null = 조회 실패·타임아웃. 판정 규칙:
 *  - 답(non-null)이 하나도 없으면 null — 기존 계약 유지(성공 좌석 0 = 판정 불가).
 *  - 답한 좌석 비율이 [MIN_RESPONSE_RATIO] 미만이면 null — 일부만 읽힌 합산을 '1명' 같은
 *    잘못된 표시로 이어가지 않게 한다(Q27·F73: 9석 요청 2석 응답 사례의 방어선).
 *  - 그 외에는 답한 좌석 중 true(점유)만 합산한다. 키에 없는 좌석을 추측해 세지 않는다.
 *
 * 키 타입에 의존하지 않는다 — SeatValue 같은 SDK 클래스 로딩 없이 JVM 테스트가 가능하다.
 */
fun <T> countOccupied(answers: Map<T, Boolean?>): Int? {
    if (answers.isEmpty()) return null // 요청 좌석이 아예 없으면 판정할 수 없다.
    val answeredCount = answers.count { it.value != null }
    if (answeredCount == 0) return null // 실패 좌석뿐 — 답이 없으므로 판정 불가(기존 계약).
    if (answeredCount < answers.size * MIN_RESPONSE_RATIO) return null // 응답률 미달 — 불안정.
    return answers.count { it.value == true }
}

/**
 * 좌석 응답 안정 판정 하한(50%). 요청 좌석의 과반이 답하지 않으면 합산을 거부한다.
 * Q27 원인 후보(9석 중 일부만 응답해 운전석 등 일부만 합산돼 '1명' 표시)에 대한 방어선이며,
 * 정확히 50%(예: 2석 중 1석)는 통과로 본다. 0.5 는 부동소수점에서 정확히 표현되므로
 * 경계 비교(answeredCount < size * 0.5)에 반올림 오차가 없다.
 */
const val MIN_RESPONSE_RATIO = 0.5

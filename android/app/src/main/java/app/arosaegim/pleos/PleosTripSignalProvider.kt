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
 */
class PleosTripSignalProvider : TripSignalProvider {

    private val vehicle: Vehicle
    private var initialized = false

    /** 마지막 초기화·조회 오류(진단 표시용 — 화면 표시로만 쓰고 저장하지 않는다). */
    var lastError: String? = null
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
     * 합산 방식: capability 로 받은 영역 목록에서 좌석(SeatValue)을 모아, ROW_1~3 각 좌석에
     * getCurrentSeatOccupancy 를 병렬 조회한다. 성공한 좌석 중 true 개수만 합산하고,
     * 실패한 좌석은 제외한다. 성공한 좌석이 하나도 없으면 null — 일부만 읽혀도 성공분만 믿는다.
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

        // 성공(Boolean 답)한 좌석만 세고, true(점유)만 합산한다. 전부 실패면 null.
        val answered = results.filterNotNull()
        if (answered.isEmpty()) return null
        return answered.count { it }.coerceAtMost(MAX_SEATS)
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

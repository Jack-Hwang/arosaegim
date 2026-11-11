package app.arosaegim.ports

import app.arosaegim.domain.DrivingState
import app.arosaegim.domain.Place
import app.arosaegim.domain.VehicleInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DrivingStateProvider { val state: StateFlow<DrivingState>; fun start(); fun stop() }
interface RouteService { suspend fun startRoute(to: Place): Result<Unit>; fun cancel(); val events: Flow<RouteEvent> }
sealed interface RouteEvent { object Started: RouteEvent; object Ready: RouteEvent
                              data class Arrived(val placeId: String?): RouteEvent; data class Error(val code: String): RouteEvent }
interface ArrivalDetector { fun watch(target: Place); fun stop(); val arrivals: Flow<ArrivalEvent> }
data class ArrivalEvent(val placeId: String, val source: String /* "fused" | "navi" | "manual" */)
interface Narrator { val isAvailable: Boolean; suspend fun speak(text: String): Result<Unit>; fun stop() }
interface VehicleInfoProvider { suspend fun read(): VehicleInfo? }

/**
 * 차량 신호 조회 포트(00문서 4절) — T6-2(좌석 점유 인원 수)·T5-1(주행거리).
 *
 * - Pleos 구현: Seat·Odometer 도메인 조회. 대체 구현: 수동 입력·null.
 * - 안 읽힌 신호는 null 로 답한다. 가짜 값을 채우지 않는다(00문서 11-1 T5-1 '가짜 값 금지').
 * - partySize 는 개인 식별이 없는 '점유 좌석 수 합계'만 반환한다(좌석별·사람별 정보 저장 금지).
 */
interface TripSignalProvider { suspend fun partySize(): Int?; suspend fun odometerKm(): Double? }

/**
 * 예시 음성 편지 재생 포트(T9-2, 00문서 4절). 정차(PARKED) 중에만 호출된다는 계약이지만
 * 포트가 이를 강제할 수는 없다 — 호출부(UI/세션)가 DrivingState 로 게이트한다.
 * 실제 녹음 기능은 만들지 않는다(로드맵). 이번 범위는 시드의 예시 편지 1건 재생뿐이다.
 */
interface VoiceLetterPlayer {
    /** 예시 음성 파일(assets)이 준비됐는지. false 면 UI 는 텍스트 편지를 보여준다. */
    val isAvailable: Boolean
    suspend fun play(letterId: String): Result<Unit>
    fun stop()
}

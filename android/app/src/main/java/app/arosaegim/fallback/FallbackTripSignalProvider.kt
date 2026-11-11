package app.arosaegim.fallback

import app.arosaegim.ports.TripSignalProvider

/**
 * TripSignalProvider 의 대체 구현(00문서 4절: 대체 = 수동 입력 · null).
 *
 * - partySize: 시동 걸기 전 사람이 센 인원을 입력할 수 있다([partySizeOverride]).
 *   null 이면 '읽지 못함' 그대로 — 화면은 인원 문장을 숨긴다(T6-2: 안 읽히면 숨긴다).
 * - odometerKm: 대체 구현에서는 주행거리를 만들지 않는다. 항상 null(가짜 값 금지 — T5-1).
 *   대체 구현이 쓰이는 동안이라는 사실은 AppContainer.adapterNotes 와 화면 꼬리표로
 *   표시한다(00문서 11-2 '대체 구현의 표시').
 */
class FallbackTripSignalProvider(
    /** 수동 입력 인원(예: 시드 then_now_demo.party_size 가족 입력값). 없으면 null. */
    private val partySizeOverride: Int? = null,
) : TripSignalProvider {

    override suspend fun partySize(): Int? = partySizeOverride

    override suspend fun odometerKm(): Double? = null

    override fun toString(): String =
        "FallbackTripSignalProvider(partySizeOverride=$partySizeOverride)"
}

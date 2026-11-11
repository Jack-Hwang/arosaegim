package app.arosaegim.fallback

import app.arosaegim.domain.Place
import app.arosaegim.ports.ArrivalDetector
import app.arosaegim.ports.ArrivalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 수동 도착 감지(데모용) — 진단 화면의 '수동 도착' 버튼이 호출한다.
 * 근접 알림/위치 권한 없이도 도착 이후 흐름(스탬프·해설)을 검증할 수 있게 한다.
 * source 는 "manual" 로 발행한다.
 */
class ManualArrivalDetector : ArrivalDetector {

    private val _arrivals = MutableSharedFlow<ArrivalEvent>(extraBufferCapacity = 16)
    override val arrivals: Flow<ArrivalEvent> = _arrivals.asSharedFlow()

    /** 지금 목표 장소에 '도착'한 것으로 처리한다. */
    fun arriveNow(placeId: String) {
        _arrivals.tryEmit(ArrivalEvent(placeId, "manual"))
    }

    override fun watch(target: Place) {
        // 수동 감지는 watch 대상이 필요 없다(버튼이 placeId 를 직접 전달).
    }

    override fun stop() {
        // 해제할 자원이 없다.
    }
}

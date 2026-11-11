package app.arosaegim.fallback

import app.arosaegim.domain.Place
import app.arosaegim.ports.RouteEvent
import app.arosaegim.ports.RouteService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 내비 없는 환경(NaviHelper 초기화 실패, 에뮬레이터 내비 미탑재)용 가짜 경로 서비스.
 * startRoute → 200ms 후 Started → Ready 이벤트를 발행해 화면 전환 흐름을 검증한다.
 * 화면 목업 수준 — T7에서 실연동(NaviHelper/서버)으로 교체.
 */
class FakeRouteService : RouteService {

    private val _events = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 16)
    override val events: Flow<RouteEvent> = _events.asSharedFlow()

    override suspend fun startRoute(to: Place): Result<Unit> {
        // 목적지 정보는 이벤트에 반영하지 않는다(로그에 좌표를 남기지 않는 규칙).
        delay(200)
        _events.tryEmit(RouteEvent.Started)
        _events.tryEmit(RouteEvent.Ready)
        return Result.success(Unit)
    }

    override fun cancel() {
        // 목업이므로 취소할 진행 상태가 없다.
    }
}

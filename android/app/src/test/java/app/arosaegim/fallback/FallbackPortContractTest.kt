package app.arosaegim.fallback

import app.arosaegim.domain.DrivingState
import app.arosaegim.domain.Place
import app.arosaegim.domain.VehicleInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * 차량 포트 5종의 대체 구현 계약 시험(P2 추가 ④, 2026-11-14).
 *
 * 포트 계약은 '같은 입력 → 같은 형태의 출력, 실패하면 null·빈 값·failure'(ADR-014)다.
 * Pleos 구현은 차량 SDK(Context·콜백)를 직접 감싸 JVM 단위 시험이 불가하므로, 이 파일은
 * JVM에서 검증 가능한 대체 구현 쪽 계약을 포트별로 고정한다:
 *   주행 상태(ManualDrivingState)·도착 감지(ManualArrivalDetector)·차량 정보(ProfileVehicleInfo)
 *   ·차량 신호 조회(FallbackTripSignalProvider)·음성 편지 재생(FallbackVoiceLetterPlayer).
 * 경로 안내·안내 음성의 전환 계약은 SwitchableRouteServiceTest·SwitchableNarratorTest가 담당한다.
 * Pleos 구현 쪽은 에뮬레이터 실측(U1~U16)이 계약의 나머지 절반을 보증한다.
 */
class FallbackPortContractTest {

    private fun place(id: String = "gj-museum") = Place(
        id = id, name = "국립경주박물관", address = "경북 경주시 일정로 186",
        lat = 35.828983, lon = 129.228361, arriveRadiusM = 120,
        narration = "테스트용 해설", quiz = emptyList(),
    )

    // ── 주행 상태: 수동 구현 ──

    @Test
    fun `수동 주행 상태는 PARKED로 시작하고 stop하면 PARKED로 돌아온다`() {
        val provider = ManualDrivingState()
        assertEquals(DrivingState.PARKED, provider.state.value)
        provider.set(DrivingState.DRIVING)
        assertEquals(DrivingState.DRIVING, provider.state.value)
        provider.stop()
        assertEquals(DrivingState.PARKED, provider.state.value)
    }

    @Test
    fun `수동 주행 상태의 start는 대체 구현에서 아무 자원도 얻지 않는다`() {
        val provider = ManualDrivingState()
        provider.start()
        assertEquals(DrivingState.PARKED, provider.state.value)
    }

    // ── 도착 감지: 수동 구현 ──

    @Test
    fun `수동 도착 감지는 source manual로 도착 이벤트를 발행한다`() = runBlocking {
        val detector = ManualArrivalDetector()
        detector.watch(place())
        // SharedFlow는 구독자가 없을 때의 발행을 버리므로, 구독을 먼저 걸고 한 번 넘긴 뒤 발행한다.
        val firstArrival = async { detector.arrivals.first { it.placeId == "gj-museum" } }
        yield()
        detector.arriveNow("gj-museum")
        val event = withTimeout(5_000) { firstArrival.await() }
        assertEquals("manual", event.source)
    }

    @Test
    fun `수동 도착 감지는 watch와 stop을 여러 번 불러도 안전하다`() {
        val detector = ManualArrivalDetector()
        detector.watch(place())
        detector.watch(place("gj-daereungwon"))
        detector.stop()
        detector.stop()
    }

    // ── 차량 정보: 수동 프로필 ──

    @Test
    fun `차량 정보는 모델이 없으면 null로 답한다 — 가짜 값을 만들지 않는다`() = runBlocking {
        assertNull(ProfileVehicleInfo(model = null, modelYear = null).read())
    }

    @Test
    fun `차량 정보는 프로필 값을 그대로 답한다`() = runBlocking {
        val info = ProfileVehicleInfo(model = "현대 쏘나타", modelYear = 1996).read()
        assertEquals(VehicleInfo(model = "현대 쏘나타", modelYear = 1996), info)
    }

    // ── 차량 신호 조회: 대체 구현 ──

    @Test
    fun `대체 차량 신호는 인원 입력이 없으면 null로 답한다`() = runBlocking {
        val provider = FallbackTripSignalProvider()
        assertNull(provider.partySize())
    }

    @Test
    fun `대체 차량 신호는 수동 인원을 그대로 답하고 주행거리는 항상 null이다`() = runBlocking {
        val provider = FallbackTripSignalProvider(partySizeOverride = 4)
        assertEquals(4, provider.partySize())
        assertNull(provider.odometerKm())
    }

    // ── 음성 편지 재생: 대체 구현 ──

    @Test
    fun `대체 음성 편지 재생은 항상 failure로 답한다 — 가짜 재생을 만들지 않는다`() = runBlocking {
        val player = FallbackVoiceLetterPlayer()
        assertFalse(player.isAvailable)
        val result = player.play("demo-letter")
        assertTrue(result.isFailure)
    }

    @Test
    fun `대체 음성 편지 재생의 stop은 재생 중이 아니어도 안전하다`() {
        FallbackVoiceLetterPlayer().stop()
    }
}

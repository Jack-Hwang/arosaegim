package app.arosaegim.ui.driving

import app.arosaegim.AppContainer
import app.arosaegim.domain.DrivingState
import app.arosaegim.domain.Place
import app.arosaegim.fallback.ManualDrivingState
import app.arosaegim.ports.Narrator
import app.arosaegim.ports.RouteEvent
import app.arosaegim.ui.ModeRouter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 스모크 테스트 1건의 화면 상태(진단 화면이 그대로 렌더링한다). */
data class SmokeResult(
    val id: String,
    val label: String,
    val status: Status = Status.IDLE,
    val detail: List<String> = emptyList(),
    val updatedAt: String = "",
) {
    enum class Status { IDLE, RUNNING, PASS, FAIL }
}

/** 1회형 테스트의 결과. 관찰형(U3/U7/U10)은 취소될 때까지 collect 한다. */
data class Outcome(val passed: Boolean)

/**
 * U1~U10 스모크 테스트 실행 로직(T0 AAR 검증 결과를 실측으로 확인하는 절차).
 * 규칙: SDK 를 직접 호출하지 않고 pleos/fallback 어댑터와 포트만 호출한다.
 */
object SmokeTests {

    /** geo fix 는 "경도 위도" 순서다(1문서/샘플 README) — 순서 착각이 흔한 원인. */
    const val GEO_FIX_GUIDE = "위치 주입: adb emu geo fix <경도> <위도> — 경도를 먼저 쓴다"

    /** U1: requestRoute 를 poiId="" 로 호출(U1 핵심 관찰점)하고 5초간 이벤트를 관찰한다. */
    suspend fun runU1(container: AppContainer, place: Place, onDetail: (String) -> Unit): Outcome =
        coroutineScope {
            onDetail("목표: ${place.name} — poiId=\"\", poiSubId=\"\", poiProvider=\"\" 로 호출(빈 값 허용 여부가 핵심 관찰점)")
            val received = mutableListOf<String>()
            // 호출 전에 수집을 먼저 시작한다(SharedFlow replay=0 — 이후 발행분만 수신).
            val collector = launch {
                container.naviRoute.events
                    .take(2)
                    .collect { event ->
                        received += event.toString()
                        onDetail("이벤트 수신: $event")
                    }
            }
            val result = container.naviRoute.startRoute(place)
            onDetail(
                if (result.isSuccess) {
                    "startRoute 호출: 예외 없음(주의 — 탐색 완료가 아니라 호출 성공만 의미)"
                } else {
                    "startRoute 호출 실패: ${result.exceptionOrNull()?.javaClass?.simpleName}: " +
                        "${result.exceptionOrNull()?.message ?: ""}"
                },
            )
            val timedOut = withTimeoutOrNull(5_000L) { collector.join() } == null
            if (timedOut) {
                collector.cancel()
                onDetail("5초 내 이벤트 ${received.size}건 수신 후 타임아웃")
            }
            val passed = result.isSuccess && received.none { it.contains("Error") }
            onDetail(
                if (passed) "판정: PASS(호출 성공, Error 미수신 — 내비 화면에 경로가 떴는지 육안 병기)"
                else "판정: FAIL",
            )
            Outcome(passed)
        }

    /** U2: NaviHelper 초기화 상태/오류 표시. */
    fun runU2(container: AppContainer, onDetail: (String) -> Unit): Outcome {
        onDetail("initState: ${container.naviRoute.initState.value}")
        onDetail("initError: ${container.naviRoute.initError.value ?: "없음"}")
        onDetail("lastStatus: ${container.naviRoute.lastStatus.value ?: "수신 이력 없음"}")
        return Outcome(container.naviRoute.initError.value == null)
    }

    /**
     * U3(관찰형): 근접 알림 등록 후 ENTER/EXIT 전이를 취소될 때까지 수신한다.
     * A3(2026-11-11): 관찰은 [FusedArrivalDetector.startObservation] 전용 수명주기를 쓴다 —
     * 관찰 중단(stopObservation)은 여정 감시(TripSession 이 watch 한 근접 알림)를 건드리지
     * 않아 관찰을 꺼도 다음 장소 도착 판정이 계속 된다. 관찰은 도착 이벤트를 발행하지 않고,
     * 여정 감시(모드·transitions 흐름)의 상태를 함께 표시해 두 판정이 분리됐음을 보여 준다.
     */
    suspend fun runU3(container: AppContainer, place: Place, onDetail: (String) -> Unit) {
        onDetail("진단 관찰 등록(여정 감시와 분리): ${place.name} 반경 ${place.arriveRadiusM}m")
        onDetail(GEO_FIX_GUIDE)
        val result = container.fusedArrival.startObservation(place)
        onDetail(
            "관찰 등록: $result / 관찰 모드: ${container.fusedArrival.observationMode.value} — " +
                "여정 감시 모드(${container.fusedArrival.mode.value})는 그대로 유지",
        )
        // coroutineScope 로 관찰 모드/전이 두 스트림을 병렬 수집하고, 취소 시 finally 에서 정리한다.
        coroutineScope {
            launch {
                container.fusedArrival.observationMode.collect { onDetail("관찰 모드 → $it") }
            }
            try {
                container.fusedArrival.observationTransitions.collect { transition ->
                    onDetail("관찰 transition 수신: $transition (도착 판정은 여정 감시가 담당)")
                }
            } finally {
                container.fusedArrival.stopObservation()
                onDetail(
                    "관찰 중단 — 여정 감시(${container.fusedArrival.mode.value})는 유지, " +
                        "다음 장소 도착 판정 계속",
                )
            }
        }
    }

    /** U4: FusedLocation isSupported 결과 표시. */
    fun runU4(container: AppContainer, onDetail: (String) -> Unit): Outcome {
        val supported = container.fusedArrival.supported
        onDetail("FusedLocation.isSupported = $supported")
        onDetail("mode: ${container.fusedArrival.mode.value} / lastError: ${container.fusedArrival.lastError.value ?: "없음"}")
        return Outcome(supported)
    }

    /** U5: VIN capability AccessType + 기어 상태 조회(값 미읽기 원칙 유지). */
    suspend fun runU5(container: AppContainer, onDetail: (String) -> Unit): Outcome {
        onDetail("Odometer 생략(이번 스코프)")
        val access = askOrNull(3_000L) { onResult ->
            container.pleosVehicle.checkVinCapabilityOnly(onResult)
        } ?: "타임아웃"
        onDetail("VIN capability AccessType = $access (capability 확인만 — VIN 값은 읽지 않음)")
        val gear = askOrNull(3_000L) { onResult ->
            container.pleosVehicle.readGearStateOnly(onResult)
        } ?: "타임아웃"
        onDetail("기어 상태 = $gear (표시 전용·저장 금지 — U5 미검증)")
        val passed = access in setOf("NONE", "READ", "WRITE", "READ_WRITE") && !gear.startsWith("FAILED")
        return Outcome(passed)
    }

    /** U6: 선택된 Narrator 로 speak("테스트") — 소요 시간 기록, Pleos 미선택 시 Pleos 직접 재시도 비교. */
    suspend fun runU6(container: AppContainer, onDetail: (String) -> Unit): Outcome {
        val selected = container.narrator
        var passed = speakAndReport(selected, "테스트", "선택 Narrator(${selected.javaClass.simpleName})", onDetail)
        if (selected !== container.pleosTts) {
            passed = speakAndReport(container.pleosTts, "테스트", "PleosTtsNarrator 재시도", onDetail) || passed
        } else {
            onDetail("PleosTtsNarrator 가 선택됨 — 폴백 재시도 생략")
        }
        return Outcome(passed)
    }

    /** U7(관찰형): 주행 상태 변화 이력을 취소될 때까지 기록한다. */
    suspend fun runU7(container: AppContainer, onDetail: (String) -> Unit) {
        val provider = container.drivingState
        if (provider is ManualDrivingState) {
            onDetail("provider=ManualDrivingState → 수동 모드. ② 섹션 토글로 전환")
        } else {
            onDetail("provider=${provider.javaClass.simpleName} (U7 미검증)")
            onDetail("Virtual Console 좌측 기어 패널에서 Park → Drive 전환")
        }
        provider.state.collect { state -> onDetail("state 변화 → $state") }
    }

    /** U8: 에뮬레이터 → 호스트 서버 health 확인(10.0.2.2 는 에뮬레이터에서 호스트 루프백). */
    suspend fun runU8(onDetail: (String) -> Unit): Outcome = withContext(Dispatchers.IO) {
        onDetail("GET http://10.0.2.2:8000/health (접속/읽기 3초 타임아웃)")
        val connection = (URL(HEALTH_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            val body = runCatching {
                connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            }.getOrDefault("")
            onDetail("응답 코드: $code")
            onDetail("본문(앞 100자): ${body.take(100)}") // health 응답은 개인정보가 아니다
            Outcome(code in 200..299)
        } catch (t: Throwable) {
            onDetail("실패: ${t.javaClass.simpleName}: ${t.message ?: ""} — 호스트 서버 미기동 가능(U8)")
            Outcome(false)
        } finally {
            connection.disconnect()
        }
    }

    /** U9: DrivingActivity ↔ ParkedActivity 3회 전환(REORDER_TO_FRONT). */
    suspend fun runU9(modeRouter: ModeRouter, onDetail: (String) -> Unit): Outcome {
        onDetail("DrivingActivity ↔ ParkedActivity 3회 전환(FLAG_ACTIVITY_REORDER_TO_FRONT)")
        repeat(3) { index ->
            modeRouter.forceBringTo(DrivingState.DRIVING)
            delay(700)
            modeRouter.forceBringTo(DrivingState.PARKED)
            delay(700)
            onDetail("전환 ${index + 1}/3 완료")
        }
        return Outcome(true)
    }

    /** U10(관찰형): 도착 이벤트 관찰 — 에뮬레이터 내비가 geo fix 를 안 따라가므로 미발생이 예상값. */
    suspend fun runU10(container: AppContainer, onDetail: (String) -> Unit) {
        onDetail("U1 실행 후 사용. geo fix 로 목적지 접근 시 onDestinationArrived 발생 여부 관찰")
        onDetail(GEO_FIX_GUIDE)
        onDetail("예상: [REPORTED] 에뮬레이터 내비는 위치를 안 따라가므로 미발생 — 미발생이면 PASS 처리")
        container.naviRoute.events.collect { event ->
            onDetail("이벤트 수신: $event")
            if (event is RouteEvent.Arrived) {
                onDetail("Arrived 수신(placeId=null — DestinationArrivedInfo 에 placeId 없음, AAR 확인)")
            }
        }
    }

    // --- 내부 도구 ---

    private suspend fun speakAndReport(
        narrator: Narrator,
        text: String,
        tag: String,
        onDetail: (String) -> Unit,
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        val result = narrator.speak(text)
        val elapsed = System.currentTimeMillis() - startedAt
        val verdict = if (result.isSuccess) "성공" else {
            "실패: ${result.exceptionOrNull()?.message ?: result.exceptionOrNull()?.javaClass?.simpleName}"
        }
        onDetail("$tag → $verdict (${elapsed}ms)")
        return result.isSuccess
    }

    private suspend fun askOrNull(timeoutMs: Long, ask: ((String) -> Unit) -> Unit): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                ask { answer ->
                    // 타임아웃 취소 후 늦게 도착한 SDK 콜백은 조용히 무시한다(resume 계열은 이미 취소된
                    // continuation 에 resume 하면 IllegalStateException).
                    cont.resume(answer) { /* 취소로 값 폐기 — 무시 */ }
                }
            }
        }

    private const val HEALTH_URL = "http://10.0.2.2:8000/health"
}

package app.arosaegim

import app.arosaegim.ports.Narrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 안내 음성 일방향 업그레이드 단위 테스트(콜드 스타트 교착 수정, 2026-11-11).
 *
 * 배경: 예전 selectNarrator 는 runBlocking 으로 Pleos TTS onReady 를 기다렸고 콜드 스타트에서
 * 메인 스레드를 파킹시켰다(dropbox data_app_anr). 수정 후는 블로킹 없이 Android TTS(대체)로
 * 즉시 시작하고 onReady 지연 확인 시 Pleos 로 일방향 전환한다 — 이 래퍼의 전환·위임 규약을 검증한다.
 *
 * 검증 포인트:
 *  1. onReady 전 speak 는 Android TTS(대체)로 전달된다 — 앱은 즉시 기동한다(블로킹 없음).
 *  2. onReady(readyState=true) 후 speak 는 Pleos(real)로 전달된다.
 *  3. onReady 가 끝내 오지 않으면 대체로 머문다('구현: 대체' 표기 유지 — 거짓 표기 금지).
 *  4. 일방향 보장 — readyState 가 false 로 되돌아가도 전환은 취소되지 않는다.
 *  5. isAvailable 은 활성 위임자의 실제 준비 상태를 그대로 답한다(가짜 가용 표기 금지).
 *  6. activeName·activeImplName 은 활성 위임자의 단순 클래스명을 그대로 노출한다(내부 식별용 계약).
 *     가짜를 넣으면 가짜 클래스명이 나오는 것이 올바른 동작이다 — 진단 화면 '구현:' 줄은
 *     AppContainer adapterNotes 와 이 활성명을 합쳐 거짓 표기 없이 보여준다.
 */
class SwitchableNarratorTest {

    /**
     * speak 호출을 기록하는 가짜 Narrator 공통 몸체(AndroidTtsNarrator·PleosTtsNarrator 자리).
     *
     * 활성명 계약: SwitchableNarrator 는 활성 위임자의 javaClass.simpleName 을 그대로 노출한다.
     * 따라서 기대값도 가짜의 simpleName 에서 읽어 온다 — 실제 구현명("AndroidTtsNarrator" 등)을
     * 기대했던 옛 기대값이 4건 실패의 원인이었다(가짜 클래스명 노출은 버그가 아니라 계약).
     *
     * 대체·Pleos 자리를 서로 다른 하위 클래스로 만들어 simpleName 이 갈라지게 둔다 — 같은
     * 클래스 하나로 두면 전환 전후 문자열이 같아 "활성명이 전환을 따라 바뀌는지" 검사가 공허하다.
     */
    private open class RecordingNarrator(val label: String, available: Boolean = true) : Narrator {
        val spoken = mutableListOf<String>()
        var stopCount = 0
        private var avail = available
        override val isAvailable: Boolean get() = avail
        override suspend fun speak(text: String): Result<Unit> {
            spoken.add(text)
            return Result.success(Unit)
        }
        override fun stop() { stopCount++ }
        fun setAvailable(value: Boolean) { avail = value }
    }

    /** 대체(Android TTS) 자리용 가짜 — simpleName: "FakeAndroidTts". */
    private class FakeAndroidTts(available: Boolean = true) : RecordingNarrator("AndroidTtsNarrator", available)

    /** Pleos 자리용 가짜 — simpleName: "FakePleosTts". */
    private class FakePleosTts : RecordingNarrator("PleosTtsNarrator")

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `onReady 전에는 대체(Android TTS 자리)로 말한다 - 블로킹 없이 즉시 기동`() = runBlocking {
        val fallback = FakeAndroidTts()
        val real = FakePleosTts()
        val ready = MutableStateFlow(false)
        val narrator = SwitchableNarrator(fallback, real, ready.asStateFlow(), testScope)
        yield()

        runCatching { narrator.speak("안녕") }.let { assertTrue(it.isSuccess) }
        narrator.stop()

        assertEquals(listOf("안녕"), fallback.spoken)
        assertEquals(0, real.spoken.size)
        assertEquals(1, fallback.stopCount)
        assertFalse(narrator.isSwitched)
        assertEquals(fallback.javaClass.simpleName, narrator.activeName.value)
    }

    @Test
    fun `onReady 지연 확인 후에는 Pleos로 말한다`() = runBlocking {
        val fallback = FakeAndroidTts()
        val real = FakePleosTts()
        val ready = MutableStateFlow(false)
        var upgradedCallback = 0
        val narrator = SwitchableNarrator(fallback, real, ready.asStateFlow(), testScope) {
            upgradedCallback++
        }
        yield()
        assertEquals(0, upgradedCallback) // 아직 대기 중 — 콜드 스타트에서도 앱은 계속 동작한다.

        ready.value = true // SDK 콜백이 늦게 온 상황 재현.
        yield()

        assertTrue(narrator.isSwitched)
        assertEquals(1, upgradedCallback)
        // 활성명이 real 의 클래스명("FakePleosTts")으로 바뀌었는지 — 두 가짜 클래스가 달라
        // 전환 전("FakeAndroidTts")과 문자열이 갈리므로 이 검사는 실질적이다.
        assertEquals(real.javaClass.simpleName, narrator.activeName.value)
        assertEquals(real.javaClass.simpleName, narrator.activeImplName())

        runCatching { narrator.speak("도착했어요") }.let { assertTrue(it.isSuccess) }
        assertEquals(listOf("도착했어요"), real.spoken)
        assertEquals(0, fallback.spoken.size) // 전환 뒤 대체는 다시 불리지 않는다.
    }

    @Test
    fun `onReady가 끝내 오지 않으면 대체로 머문다`() = runBlocking {
        val fallback = FakeAndroidTts()
        val real = FakePleosTts()
        val ready = MutableStateFlow(false)
        val narrator = SwitchableNarrator(fallback, real, ready.asStateFlow(), testScope)
        yield()

        runCatching { narrator.speak("아직") }.let { assertTrue(it.isSuccess) }
        assertEquals(listOf("아직"), fallback.spoken)
        assertFalse(narrator.isSwitched) // '구현: 대체' 표기가 솔직하게 유지된다.
        assertEquals(fallback.javaClass.simpleName, narrator.activeName.value)
    }

    @Test
    fun `전환은 일방향 - ready가 false로 되돌아가도 Pleos를 유지한다`() = runBlocking {
        val fallback = FakeAndroidTts()
        val real = FakePleosTts()
        val ready = MutableStateFlow(true) // 이미 준비됨 — 동기 확인 케이스.
        val narrator = SwitchableNarrator(fallback, real, ready.asStateFlow(), testScope)
        yield()

        assertTrue(narrator.isSwitched)
        ready.value = false // 서비스가 줄었다고 해도 되돌리지 않는다(일방향).
        yield()

        assertTrue(narrator.isSwitched)
        assertEquals(real.javaClass.simpleName, narrator.activeName.value)
    }

    @Test
    fun `isAvailable은 활성 위임자의 실제 준비 상태를 답한다 - 거짓 표기 금지`() = runBlocking {
        val fallback = FakeAndroidTts(available = false) // 초기화 미완료.
        val real = FakePleosTts()
        val ready = MutableStateFlow(false)
        val narrator = SwitchableNarrator(fallback, real, ready.asStateFlow(), testScope)
        yield()

        assertFalse(narrator.isAvailable) // 대체가 아직 못 쓰면 못 쓴다고 답한다.
        fallback.setAvailable(true)
        assertTrue(narrator.isAvailable) // 전환 없이도 활성 위임자 상태를 그대로 반영한다.
    }
}

package app.arosaegim

import app.arosaegim.ports.Narrator
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * 안내 음성(Narrator) 일방향 업그레이드 래퍼 — 콜드 스타트 교착 수정(2026-11-11, 조치 8 구조 일반화).
 *
 * 배경(P0): 기존 selectNarrator 는 runBlocking 으로 Pleos TTS onReady 를 최대 3초 기다렸다.
 * 에뮬레이터(차량) 콜드 스타트에서 SDK 콜백이 오지 않으면 이 대기가 메인 스레드를 파킹시켜
 * 시스템이 "failed to complete startup"으로 앱을 강제 종료했다(dropbox data_app_anr 3건 이상).
 *
 * 수정: Application.onCreate 는 즉시 Android 기본 TTS(대체)로 통과시키고, Pleos onReady 가
 * 늦게라도 수신되면 Pleos 구현으로 **일방향** 갈아끼운다. 되돌림(Pleos → Android TTS)은 없다.
 * onReady 가 끝내 오지 않으면 대체 구현으로 머무른다 — '구현: 대체' 표기는 솔직하게 유지된다
 * (거짓 표기 금지). A1③ 계약: Pleos TTS 합성은 onReady 를 확인한 뒤에만 fallback 으로 쓴다.
 *
 * 규약:
 *  - 대기·폴링 없음. readyState 관찰은 주입받은 [scope] 에서 블로킹 없이 돈다.
 *  - 스레드 안전: SDK 콜백 스레드에서 readyState 가 바뀌어도 AtomicReference CAS 로 최대 1회만
 *    전환한다. speak 중간 전환 시 진행 중 재생은 기존 구현이 끝내고, 새 speak 는 새 구현이 받는다.
 *  - JVM 테스트 가능: Narrator 포트와 StateFlow 에만 의존한다(SwitchableNarratorTest).
 */
class SwitchableNarrator(
    fallback: Narrator,
    private val real: Narrator,
    readyState: StateFlow<Boolean>,
    scope: CoroutineScope,
    private val onUpgraded: () -> Unit = {},
) : Narrator {

    /** 일방향 전환의 기준점. CAS 는 항상 이 초기 위임자에서만 시작한다. */
    private val initial: Narrator = fallback

    private val active = AtomicReference(initial)

    /** 현재 활성 위임자의 단순 클래스명(진단 화면 표시용). */
    private val _activeName = MutableStateFlow(initial.javaClass.simpleName)
    val activeName: StateFlow<String> = _activeName.asStateFlow()

    /** 전환 완료 여부(진단 표시용). true 이면 다시 false 로는 절대 돌아가지 않는다. */
    private val _switched = MutableStateFlow(false)
    val switched: StateFlow<Boolean> = _switched.asStateFlow()

    /** 재전환이 이미 일어났는지(동기 확인용). */
    val isSwitched: Boolean get() = _switched.value

    init {
        scope.launch {
            // onReady 가 오지 않아도 영원히 기다리기만 한다 — 블로킹 없음, 앱 기동을 막지 않는다.
            val ready = readyState.firstOrNull { it } == true
            if (ready && active.compareAndSet(initial, real)) {
                _activeName.value = real.javaClass.simpleName
                _switched.value = true
                onUpgraded()
            }
        }
    }

    /** onReady 확인 여부를 활성 위임자가 직접 답한다(가짜 가용 표기 금지). */
    override val isAvailable: Boolean
        get() = active.get().isAvailable

    override suspend fun speak(text: String): Result<Unit> = active.get().speak(text)

    override fun stop() = active.get().stop()

    /** 진단 표시용: 현재 활성 구현의 단순 클래스명 즉시 조회. */
    fun activeImplName(): String = active.get().javaClass.simpleName
}

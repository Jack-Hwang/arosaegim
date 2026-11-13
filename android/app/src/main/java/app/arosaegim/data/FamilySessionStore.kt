package app.arosaegim.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * 아로새김 차량 세션 저장소(T9-1) — 00문서 10.6, 02문서 3-2·9절 기준.
 */

/**
 * 가족 코드 검증 — 순수 Kotlin(Context 없음).
 *
 * FamilySessionStore 본체는 SharedPreferences 를 쓰므로 JVM 단위 테스트가 불가능하다.
 * 검증 로직만 이 오브젝트로 분리해 app/src/test 에서 테스트한다(FamilySessionStoreTest).
 *
 * 규칙(00문서 T9-1·02문서 3-2): 가족 코드는 영숫자. VIN·실명 같은 개인 식별
 * 정보를 코드로 쓰지 않는다 — 코드는 '가족을 식별하는 용도'일 뿐이다.
 *
 * [F58 실측] 서버가 발급하는 코드는 8자리(예: A37K9Q2M)다. 웹 초창기 가정(6자리)과의
 * 호환을 위해 6~8자리를 모두 허용한다.
 */
object FamilyCodeValidator {

    /** 6~8자리 영숫자(대소문자 허용). 공백·특수문자·한글은 모두 거부한다. */
    private val CODE_PATTERN = Regex("^[A-Za-z0-9]{6,8}$")

    /** 6~8자리 영숫자면 true. 앞뒤 공백도 허용하지 않는다(입력 UI 에서 잘라내지 않는다). */
    fun validate(code: String): Boolean = CODE_PATTERN.matches(code)
}

/**
 * 세션 자격 증명(휘발성 전용) — 가족 코드·PIN 의 '메모리에 있는 현재 값'만 다룬다(T9-3·T12).
 *
 * Context 가 없는 순수 Kotlin 이므로 JVM 단위 테스트가 가능하다(FamilySessionStoreTest —
 * 설정/해제/부분 갱신). 파일·SharedPreferences 저장은 이 클래스의 관심사가 아니며 절대
 * 일어나지 않는다. 특히 PIN 은 00문서 10.6("가족 코드는 식별용, 열람·수정에는 PIN 추가 /
 * PIN 저장 금지")에 따라 프로세스 수명의 메모리에만 존재한다 — 앱을 다시 켜면 사라지고
 * 재입력이 필요하다(휘발성 설계의 의도).
 *
 * 값 규칙: 공백만 있는 값은 '없음'과 같다(null 로 취급). 앞뒤 공백은 잘라 보관한다.
 *
 * 거절 상태(2026-11-13): 서버가 'PIN이 틀렸다'(401 invalid_pin)고 답한 PIN 을 세션 메모리에
 * 남겨 두면, 도착마다 전송 큐(VisitQueue)·정차 화면 조회가 같은 틀린 PIN 을 반복 보내 서버의
 * 실패 횟수(PIN_LOCK_ATTEMPTS=5)를 올리고 가족 전체를 10분 잠근다(PIN_LOCK_MINUTES=10).
 * [markRejected] 로 거절을 표시하면 PIN 값도 메모리에서 즉시 지워 [pinOrNull] 이 null 이 된다
 * — ApiClient 헤더 구성·VisitQueue flush·정차 화면 조회가 전부 재전송을 멈춘다. 거절 상태는
 * PIN 해제([clearPin]·[clear])와 새 PIN 입력([setPin])에서 풀린다 — 사용자가 다시 입력하면
 * 재개다. 거절 표시도 메모리 전용이며 저장·로그 경로가 없다(00문서 10.6 동일 규약).
 */
class SessionCredentials(familyCode: String? = null, pin: String? = null) {

    @Volatile private var code: String? = familyCode?.trim()?.takeIf { it.isNotEmpty() }

    /** PIN 은 세션 메모리에만 존재한다 — 직렬화·저장·로그 경로가 이 클래스에는 없다. */
    @Volatile private var pin: String? = pin?.trim()?.takeIf { it.isNotEmpty() }

    /** 서버가 거부한 PIN 을 보관 중인가. PIN 이 없으면(입력 전·해제) 거절은 의미가 없다. */
    @Volatile private var rejected = false

    fun codeOrNull(): String? = code

    fun pinOrNull(): String? = pin

    fun setCode(newCode: String) {
        code = newCode.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * PIN 설정(덮어쓴다). 저장 계층이 아니므로 프로세스가 끝나면 사라진다.
     * 새 PIN 입력은 거절 상태를 해제한다 — 사용자가 다시 입력했으면 재개다.
     */
    @Synchronized
    fun setPin(newPin: String) {
        pin = newPin.trim().takeIf { it.isNotEmpty() }
        rejected = false
    }

    /** PIN 만 해제한다 — 가족 코드는 유지한다(부분 갱신). 거절 상태도 함께 풀린다. */
    @Synchronized
    fun clearPin() {
        pin = null
        rejected = false
    }

    /** 코드·PIN 을 모두 해제한다(로그아웃). 거절 상태도 함께 풀린다. */
    @Synchronized
    fun clear() {
        code = null
        pin = null
        rejected = false
    }

    /**
     * 서버가 거부한 PIN 을 거절 표시한다. PIN 값은 메모리에서도 즉시 지운다(다시 보낼
     * 이유가 없다). [rejectedPin] 을 넘기면 '지금 세션에 있는 PIN 이 그 값일 때만' 표시한다 —
     * 네트워크 응답(거절)이 사용자의 새 입력보다 늦게 도착했을 때 새 PIN 을 함부로 지우지
     * 않게 하는 안전장치다. PIN 이 없거나(입력 전·해제) 새 값이 이미 들어왔으면 아무 일도
     * 하지 않는다. 값 비교만 하고 저장·로그는 하지 않는다(00문서 10.6).
     */
    @Synchronized
    fun markRejected(rejectedPin: String? = null) {
        if (pin == null) return
        if (rejectedPin != null && pin != rejectedPin) return // 이미 새 PIN — 새 값은 거르지 않는다.
        pin = null // 값을 먼저 치운다 — [pinOrNull] 은 한 번의 읽기로 항상 일관된다.
        rejected = true
    }

    /** 서버가 거부한 PIN 을 보관 중인가([setPin]·[clearPin]·[clear] 로 해제된다). */
    fun isRejected(): Boolean = rejected
}

/**
 * 차량 세션 저장소(T9-1).
 *
 * 설계(00문서 10.6, 02문서 3-2·9절):
 *  - 기록의 주인은 '가족'이다. 같은 가족 코드로 다른 차·렌터카에서 접속하면 같은
 *    기록에 이어진다. 따라서 차량에는 개인 기록을 오래 두지 않는다 — '이 차에서
 *    로그아웃'이 코드·캐시·큐를 지운다(DiagnosticsViewModel.signOutFromCar 참조).
 *  - ⚠ PIN 은 절대 저장하지 않는다(00문서 10.6: 코드는 식별용, 열람·수정에는 PIN 추가).
 *    T9-3(구성원 이름표)·T12(가족 지표)부터 앱이 서버의 PIN 필요 API 를 쓰므로, PIN 은
 *    프로세스 수명의 세션 메모리([SessionCredentials])에만 보관한다 — SharedPreferences
 *    ·파일·로그 어디에도 남지 않는다. 앱 재시작 시 사라지므로 재입력이 필요하다(의도된 동작).
 *    [setPin] 은 StartActivity 의 PIN 입력에서, [pinOrNull] 은 ApiClient 헤더 구성에서 쓴다.
 *    [signOut] 은 PIN 도 함께 끊어 '이 차에서 로그아웃 = 열람 권한 상실'을 만든다.
 *  - guestMode('가족 없이 둘러보기')는 데모/개인 모드 — 기록은 이 차에만 저장된다.
 *
 * 인스턴스화 규약: 이 클래스는 AppContainer 밖에서 직접 만든다. AppContainer 는
 * 다른 작업과 동시에 수정 중이라 건드리지 않기 위함이며, SharedPreferences 를 공유하므로
 * 인스턴스가 여러 개여도 저장 상태는 서로 일치한다(메모리 StateFlow 만 인스턴스별이다).
 */
class FamilySessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _familyCode = MutableStateFlow(prefs.getString(KEY_CODE, null))

    /** 현재 가족 코드(없으면 null). PIN 은 SharedPreferences 에 절대 존재하지 않는다. */
    val familyCode: StateFlow<String?> = _familyCode.asStateFlow()

    /** 둘러보기 모드 여부(가족 코드 없이 이 차만으로 사용 중). */
    val isGuestMode: Boolean
        get() = prefs.getBoolean(KEY_GUEST, false)

    /**
     * 세션 존재 여부 — 가족 코드 접속 또는 둘러보기 모드 중 하나라도 있으면 true.
     * ModeRouter 의 정차 화면 자동 전환 게이트가 쓴다(Q28, 2026-11-13): 둘 다 아니면
     * 시작 화면(가족 코드 입력)을 최상단에 유지해야 한다.
     */
    fun hasSession(): Boolean = _familyCode.value != null || isGuestMode

    /**
     * 가족 코드로 접속. 6자리 영숫자가 아니면 IllegalArgumentException 을 던진다.
     * 서버 검증은 이번 범위에서 생략했다 — Sync 담당 큐 flush 가 실질 검증이다(T9-2 이후).
     * 저장 전 대문자로 정규화해 코드 표기 흔들림을 줄인다.
     */
    fun signIn(code: String) {
        require(FamilyCodeValidator.validate(code)) {
            "가족 코드는 6자리 영숫자여야 합니다"
        }
        val normalized = code.uppercase()
        prefs.edit()
            .putString(KEY_CODE, normalized)
            .remove(KEY_GUEST) // 코드로 접속하면 둘러보기 모드는 해제된다.
            .apply()
        _familyCode.value = normalized
    }

    /**
     * '이 차에서 로그아웃' — 가족 코드 삭제와 함께 세션 자격 증명(PIN·세션 내 코드)을 전부
     * 해제한다(A8, 2026-11-11: clearPin() → clear() 로 강화 — 코드·PIN 모두 메모리에서 지워
     * '로그아웃 = 열람 권한 완전 상실'을 보장한다. 00문서 10.6: PIN 은 열람·수정 권한이므로
     * 코드가 사라지면 권한도 끊긴다). 방문 기록(visits.json)·전송 대기 큐(visit_queue.json)
     * 파일 삭제는 [SignOutCleanup] 이 수행한다(DiagnosticsViewModel.signOutFromCar 경유).
     */
    fun signOut() {
        prefs.edit().remove(KEY_CODE).apply()
        _familyCode.value = null
        sessionCredentials.clear()
    }

    /** '가족 없이 둘러보기' — 데모/개인 모드로 전환. 기록은 이 차에만 저장된다. */
    fun enterGuestMode() {
        prefs.edit().putBoolean(KEY_GUEST, true).apply()
    }

    /**
     * PIN 을 세션 메모리에 보관한다(StartActivity 의 PIN 입력에서 호출).
     * 저장 계층을 거치지 않는다 — SharedPreferences·파일·로그 어디에도 남지 않는다(00문서 10.6).
     */
    fun setPin(pin: String) {
        sessionCredentials.setPin(pin)
    }

    /** 세션 PIN 해제(재입력 유도). */
    fun clearPin() {
        sessionCredentials.clearPin()
    }

    /** 현재 세션 PIN(없으면 null) — ApiClient 헤더(X-Family-Pin) 구성용. 재시작 후에는 null.
     *  서버가 거부한 PIN 이 거절 표시 중이면(2026-11-13) null — 헤더·큐·화면 조회가 재전송을
     *  멈춘다. */
    fun pinOrNull(): String? = sessionCredentials.pinOrNull()

    /**
     * 세션 PIN 거절 표시(2026-11-13 — 방문 전송 경로의 안전장치). 서버가 'PIN이 틀렸다'
     * (401·403 → [AuthException])고 답했을 때 호출한다. 세션 PIN 이 메모리에서 지워져
     * [pinOrNull] 이 null 이 되고, 거절 상태는 [setPin]·[clearPin]·[signOut] 에서 풀린다.
     * [rejectedPin] 은 거부된 PIN 값(선택) — 지금 세션 PIN 이 그 값일 때만 표시한다.
     */
    fun markPinRejected(rejectedPin: String? = null) {
        sessionCredentials.markRejected(rejectedPin)
    }

    /** 세션 PIN 거절 여부(2026-11-13). 거절 표시 중이면 true — [setPin] 으로 해제된다. */
    fun isPinRejected(): Boolean = sessionCredentials.isRejected()

    companion object {

        /**
         * 프로세스 전역 세션 PIN 거절 표시(2026-11-13). [VisitQueue] 의 거절 콜백 기본값이
         * 쓴다 — 큐는 세션 저장소 인스턴스를 갖지 않으므로 companion 경유로 표시한다(세션
         * PIN 이 companion 보관이라 인스턴스가 달라도 같은 상태를 보는 기존 규약과 같다).
         * 메모리 전용 — 저장·로그 경로가 없다(00문서 10.6).
         */
        fun rejectSessionPin(rejectedPin: String? = null) {
            sessionCredentials.markRejected(rejectedPin)
        }

        private const val PREFS_NAME = "family_session"
        private const val KEY_CODE = "family_code"
        private const val KEY_GUEST = "guest_mode"

        /**
         * 휘발성 세션 PIN 의 보관 장소(companion — 프로세스 수명의 메모리).
         * companion 에 둔 이유: 이 저장소는 화면마다 인스턴스를 따로 만든다(StartActivity,
         * DiagnosticsViewModel). SharedPreferences 는 프로세스 전역이라 인스턴스가 달라도
         * 상태가 일치하는데, PIN 도 같은 취지로 '이 앱 프로세스의 세션 PIN' 하나로 본다 —
         * setPin 하는 인스턴스와 pinOrNull 읽는 인스턴스가 달라도 같은 값을 본다(헤더 구성이
         * 어긋나 T9-3·T12 호출이 조용히 실패하는 것을 막는다). 프로세스가 죽으면 사라진다.
         */
        private val sessionCredentials = SessionCredentials()
    }
}

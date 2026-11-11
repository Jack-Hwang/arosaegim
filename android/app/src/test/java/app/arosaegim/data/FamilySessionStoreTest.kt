package app.arosaegim.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * 가족 코드 검증 + 세션 자격 증명(PIN) 단위 테스트(T9-1·T9-3/T12).
 *
 * FamilySessionStore 본체는 SharedPreferences(Context) 의존이라 JVM 단위 테스트가
 * 불가능하다. 따라서 검증 로직은 FamilyCodeValidator 로, PIN 보관 로직은
 * SessionCredentials 로 각각 분리했고, 이 파일은 그 순수 부분만 테스트한다.
 * 저장·삭제 동작(signIn/signOut/setPin)은 진단 화면 ⑤ 차량 세션 섹션의
 * '파일 목록으로 확인' 절차로 검증한다(00문서 T9-1 완료 기준).
 */
class FamilySessionStoreTest {

    // --- 유효한 코드 ---

    @Test
    fun `여섯 자리 영숫자는 유효하다`() {
        assertTrue(FamilyCodeValidator.validate("A1B2C3"))
        assertTrue(FamilyCodeValidator.validate("abc123"))
        assertTrue(FamilyCodeValidator.validate("ZZ99X7"))
    }

    @Test
    fun `숫자만 여섯 자리도 유효하다`() {
        assertTrue(FamilyCodeValidator.validate("000000"))
        assertTrue(FamilyCodeValidator.validate("123456"))
    }

    // --- 빈값 ---

    @Test
    fun `빈 값은 무효하다`() {
        assertFalse(FamilyCodeValidator.validate(""))
    }

    // --- 자릿수 경계 ---

    @Test
    fun `다섯 자리는 무효하다`() {
        assertFalse(FamilyCodeValidator.validate("A1B2C"))
    }

    @Test
    fun `일곱 자리 영숫자도 유효하다 — 서버가 8자리 코드를 발급한다(F58)`() {
        assertTrue(FamilyCodeValidator.validate("A1B2C3D"))
        assertTrue(FamilyCodeValidator.validate("8XTPUG8N"))
    }

    @Test
    fun `아홉 자리는 무효하다`() {
        assertFalse(FamilyCodeValidator.validate("A1B2C3D4E"))
    }

    // --- 허용되지 않는 문자 ---

    @Test
    fun `특수문자가 섞이면 무효하다`() {
        assertFalse(FamilyCodeValidator.validate("A1B2C-"))
        assertFalse(FamilyCodeValidator.validate("A1!C3D"))
    }

    @Test
    fun `한글이 섞이면 무효하다`() {
        assertFalse(FamilyCodeValidator.validate("가나다1"))
    }

    @Test
    fun `공백이 섞이면 무효하다`() {
        assertFalse(FamilyCodeValidator.validate("A1B2C3 "))
        assertFalse(FamilyCodeValidator.validate(" A1B2C3"))
        assertFalse(FamilyCodeValidator.validate("A1 C3D"))
    }

    // ---------------- SessionCredentials(휘발성 세션 자격 증명) ----------------
    //
    // FamilySessionStore 의 PIN 보관은 SharedPreferences(Context) 없이는 검증할 수 없어서,
    // 그 순수 로직만 SessionCredentials 로 추출해 여기서 테스트한다(T9-3 구성원 이름표·
    // T12 가족 지표의 가족 인증용). PIN 은 저장 금지(00문서 10.6) — 이 객체의 메모리에만
    // 존재하며, 테스트는 '설정 → 읽기 → 해제 → 부분 갱신'의 수명 주기를 검증한다.

    @Test
    fun `pin을 설정하면 pinOrNull로 같은 값이 나온다`() {
        val credentials = SessionCredentials(familyCode = "A1B2C3")
        credentials.setPin("230444")
        assertEquals("230444", credentials.pinOrNull())
        assertEquals("PIN 설정은 가족 코드에 영향 없다", "A1B2C3", credentials.codeOrNull())
    }

    @Test
    fun `pin을 해제하면 null이 되고 코드는 유지된다`() {
        val credentials = SessionCredentials(familyCode = "A1B2C3", pin = "230444")
        credentials.clearPin()
        assertNull(credentials.pinOrNull())
        assertEquals("PIN 해제는 부분 갱신 — 코드는 남는다", "A1B2C3", credentials.codeOrNull())
    }

    @Test
    fun `pin을 다시 설정하면 새 값으로 덮어쓴다`() {
        val credentials = SessionCredentials()
        credentials.setPin("111111")
        credentials.setPin("222222")
        assertEquals("이전 PIN 이 메모리에 남지 않는다", "222222", credentials.pinOrNull())
    }

    @Test
    fun `clear는 코드와 pin을 모두 지운다`() {
        val credentials = SessionCredentials(familyCode = "A1B2C3", pin = "230444")
        credentials.clear()
        assertNull(credentials.codeOrNull())
        assertNull(credentials.pinOrNull())
    }

    @Test
    fun `공백뿐인 pin은 없는 것과 같다`() {
        val credentials = SessionCredentials()
        credentials.setPin("   ")
        assertNull("공백 PIN 을 보관하지 않는다", credentials.pinOrNull())
    }

    @Test
    fun `pin 앞뒤 공백은 잘라 보관한다`() {
        val credentials = SessionCredentials(pin = " 230444 ")
        assertEquals("230444", credentials.pinOrNull())
    }

    @Test
    fun `코드와 pin을 각각 따로 갱신할 수 있다`() {
        val credentials = SessionCredentials()
        credentials.setCode("ZZ99X7")
        assertNull("PIN 은 아직 없다", credentials.pinOrNull())
        credentials.setPin("230444")
        assertEquals("ZZ99X7", credentials.codeOrNull())
        assertEquals("230444", credentials.pinOrNull())
    }
}

package app.arosaegim.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * 가족 코드 마스킹 단위 테스트(13번 문서 T4 설정 — JVM, 2026-11-14).
 *
 * FamilyCodeMask.mask 의 계약을 검증한다: 앞 2자리·뒤 2자리만 남기고 가운데는 불릿(U+2022),
 * 길이 4 이하는 전부 불릿, null·빈 값(공백만 있는 값 포함)은 빈 문자열. 순수 함수라 JVM 에서
 * 바로 검증하며, 예시 코드는 문서에 공개된 가짜 형식(8자리 영숫자)만 쓴다.
 */
class FamilyCodeMaskTest {

    @Test
    fun `여덟 자리 코드는 앞 두 자리와 뒤 두 자리만 남긴다`() {
        // 서버 발급 8자리(예시 가짜 값) — 가운데 네 칸이 불릿으로 채워진다.
        assertEquals("A3••••2M", FamilyCodeMask.mask("A37K9Q2M"))
    }

    @Test
    fun `여섯 자리 코드도 앞 두 자리와 뒤 두 자리만 남긴다`() {
        assertEquals("ZZ••X7", FamilyCodeMask.mask("ZZ99X7"))
        // 다섯 자리부터 앞·뒤 두 자리가 겹치지 않는다 — 가운데 한 칸만 불릿.
        assertEquals("AB•DE", FamilyCodeMask.mask("ABCDE"))
    }

    @Test
    fun `네 자리 이하는 전부 가린다`() {
        assertEquals("••••", FamilyCodeMask.mask("ABCD"))
        assertEquals("•••", FamilyCodeMask.mask("ABC"))
        assertEquals("•", FamilyCodeMask.mask("A"))
    }

    @Test
    fun `빈 값은 빈 문자열이다`() {
        assertEquals("", FamilyCodeMask.mask(""))
        assertEquals("공백만 있는 값도 없음과 같다", "", FamilyCodeMask.mask("   "))
    }

    @Test
    fun `null은 빈 문자열이다`() {
        assertEquals("", FamilyCodeMask.mask(null))
    }
}

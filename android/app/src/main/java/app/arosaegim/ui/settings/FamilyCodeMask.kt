package app.arosaegim.ui.settings

/*
 * 가족 코드 화면 표시 가리기(13번 문서 T4 설정·가족 전환 — 시안 42, 2026-11-14 신규).
 *
 * 규칙: 가족 코드는 화면에서만 마스킹해 표시하고 원문을 그대로 노출하지 않는다.
 * 이 함수는 순수 Kotlin 이다 — Context·저장·네트워크·로그가 전혀 없어 app/src/test(JVM)에서
 * 직접 검증한다(FamilyCodeMaskTest). 입력으로 받은 코드 원문은 어디에도 남지 않는다
 * (반환값은 화면 표시용 문자열뿐 — 호출부가 이 값으로만 그린다).
 *
 * 형식(서버 발급은 8자리 — FamilySessionStore 의 6~8자리 호환 유지):
 *   앞 2자리·뒤 2자리만 남기고 가운데는 불릿(U+2022)으로 채운다.
 *     8자 "A37K9Q2M" → "A3••••2M"
 *     6자 "ZZ99X7"   → "ZZ••X7"
 *     5자 "ABCDE"    → "AB•DE"   (앞·뒤 두 자리가 겹치지 않는 최소 길이)
 *   길이 4 이하는 앞·뒤를 구별해 남길 수 없으므로 전부 불릿("ABCD" → "••••").
 *   null·빈 값은 빈 문자열 — 공백만 있는 값도 '없음'과 같게 본다(SessionCredentials 규약과 동일).
 *   호출부는 빈 문자열이 나오면 코드 줄 자체를 숨긴다(SettingsState.familyCodeRowVisible).
 */
object FamilyCodeMask {

    /** 가운데를 채우는 불릿 문자(U+2022). */
    const val BULLET: String = "•"

    /**
     * 가족 코드를 화면 표시용으로 가린다.
     *
     * @param code 세션 가족 코드 원문(null·빈 값 허용 — 둘러보기 모드 등 코드가 없는 상태).
     * @return 마스킹된 표시 문자열. 가릴 값이 없으면 빈 문자열.
     */
    fun mask(code: String?): String {
        if (code.isNullOrBlank()) return ""
        val length = code.length
        if (length <= 4) return BULLET.repeat(length)
        return code.take(2) + BULLET.repeat(length - 4) + code.takeLast(2)
    }
}

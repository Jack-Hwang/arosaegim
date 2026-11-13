package app.arosaegim.ui.parked

import app.arosaegim.data.AuthException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정차 화면 GET 조회 경로의 가족 인증 실패 판정 단위 테스트(2026-11-14, F102).
 *
 * 계약(ApiClient.kt getJson — 2026-11-14 소스 재확인): 401(PIN 누락·오류)·403(가족 인증)·
 * 429(PIN 오류 잠금)는 AuthException("가족 인증 실패(HTTP 코드)")으로 답하고, 그 외(404 모르는
 * 코드·장소, 5xx, 네트워크 오류)는 일반 실패(IOException)다. ParkedScreen 과
 * DiagnosticsViewModel 은 이 판정으로 FamilySessionStore.markPinRejected 를 부르는지
 * 갈린다 — 거절 대상을 틀리면 틀린 PIN 이 세션 메모리에 남아 조회 때마다 서버 실패 횟수를
 * 올린다(5회 → 가족 전체 10분 잠금).
 *
 * isAuthFailure 는 메시지 기반 상태 코드 판정이라 순수 함수다(Context 없음 — JVM 시험 가능).
 * 화면(Compose) 직접 시험은 이 저장소의 JVM 테스트 구성(junit4, Robolectric 없음)으로 불가하다.
 */
class ParkedAuthFailureTest {

    // ── 거절 대상(→ markPinRejected 를 부른다) ──

    @Test
    fun `401 은 거절 대상이다`() {
        assertTrue(isAuthFailure(AuthException("가족 인증 실패(HTTP 401)")))
    }

    @Test
    fun `403 은 거절 대상이다`() {
        assertTrue(isAuthFailure(AuthException("가족 인증 실패(HTTP 403)")))
    }

    @Test
    fun `429_PIN_잠금도 거절 대상이다`() {
        assertTrue(isAuthFailure(AuthException("가족 인증 실패(HTTP 429)")))
    }

    @Test
    fun `기존 postVisit 관례 메시지도 거절 대상이다`() {
        assertTrue(isAuthFailure(AuthException("getMembers HTTP 401")))
    }

    // ── 거절 대상 아님(일반 실패 — 기능 숨김·lastError 기록만) ──

    @Test
    fun `404 는 거절 대상이 아니다`() {
        assertFalse(isAuthFailure(IOException("GET /v1/families/me/members HTTP 404")))
    }

    @Test
    fun `5xx 는 거절 대상이 아니다`() {
        assertFalse(isAuthFailure(IOException("GET /v1/families/me/metrics HTTP 503")))
    }

    @Test
    fun `코드가 없는 네트워크 오류는 거절 대상이 아니다`() {
        assertFalse(isAuthFailure(IOException("connect timed out")))
    }

    @Test
    fun `null 예외는 거절 대상이 아니다`() {
        assertFalse(isAuthFailure(null))
    }

    @Test
    fun `코드 앞뒤로 붙은 숫자는 우연 일치로 치지 않는다`() {
        assertFalse(isAuthFailure(IOException("HTTP 1401")))
        assertFalse(isAuthFailure(IOException("HTTP 4012")))
    }
}

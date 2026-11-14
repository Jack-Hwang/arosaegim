package app.arosaegim.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Gleo 음성 function calling 수신 리시버 — 서비스(GleoActionService)와 같은 계약을
 * [AgentCallHandler.handle] 하나로 받는다.
 *
 * 존재 이유(T6 실측, 2026-11-14): Android 12+ 백그라운드 시작 제한 때문에 adb shell(uid 2000)의
 * startService 는 "Background start not allowed" 로 막힌다(실제 Gleo 호출자는 시스템 권한이라
 * 서비스 경로 자체는 이 제한 밖이다). adb 검증은 이 리시버(am broadcast)로 열어 둔다.
 * 수신 경로가 원문 미확인이라 서비스·리시버 둘 다 같은 handle 계약을 받는다 — 판정·부가 동작·
 * 응답 방송의 차이는 없다.
 *
 * goAsync 불필요: handle 이 코루틴·파일 I/O 없이 동기 즉시 완료이므로 onReceive 안에서
 * 판정과 응답 방송이 모두 끝난다. null 이면 조용히 종료한다(응답 방송 없음 — 기존 계약).
 */
class GleoActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val result = AgentCallHandler.handle(context, intent) ?: return
        AgentCallHandler.sendResponse(context, result)
    }
}

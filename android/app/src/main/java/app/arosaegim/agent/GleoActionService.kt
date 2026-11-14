package app.arosaegim.agent

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Gleo 음성 function calling 수신 서비스(T2) — 하나의 Service 로 3개 도구를 모두 받는다
 * (단순함 우선). 실제 음성 경로용 수신 점이다.
 *
 * 판정·스냅샷·부가 동작·응답 방송은 전부 [AgentCallHandler]로 옮겨졌고, 리시버
 * (GleoActionReceiver — adb 검증용)와 같은 계약을 같은 handle 하나로 받는다. 이 클래스는
 * 수신 점과 수명(startId 종료)만 맡는다:
 *  1. handle 이 null(intent 없음·extra 없음 — 원문 미확인(T0)이라 extra 키 'functionName'
 *     가정)이면 응답 방송 없이 조용히 stopSelfResult 로 끝난다.
 *  2. 결과가 있으면 응답 방송 후 stopSelfResult 로 끝난다.
 *
 * 동기 규약(7초 게이트 대응): 코루틴·Thread.sleep·네트워크·파일 I/O 가 전혀 없다 — handle 이
 * 전부 값 읽기와 sendBroadcast 만으로 한 번에 끝난다.
 *
 * T6 실측(2026-11-14): Android 12+ 백그라운드 시작 제한으로 adb shell(uid 2000)의 서비스
 * 시작은 막힌다(callStart=false 반복·Pending Starts 누적). 실제 Gleo 호출자는 시스템 권한이라
 * 이 서비스 경로는 유지하고, adb 검증은 같은 계약의 GleoActionReceiver(am broadcast)로 한다.
 */
class GleoActionService : Service() {

    /** 이 서비스는 startService 호출마다 한 번 일하고 끝난다 — 바인드 제공 없음. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent null 이면 handle 이 null 을 반환해 같은 무음 종료가 된다(기존 계약 유지).
        AgentCallHandler.handle(this, intent)?.let { AgentCallHandler.sendResponse(this, it) }
        stopSelfResult(startId)
        return START_NOT_STICKY
    }
}

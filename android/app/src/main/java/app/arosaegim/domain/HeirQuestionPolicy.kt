package app.arosaegim.domain

/**
 * '이어받을 사람 지금 정하기' 질문 정책(B1, 시안 19 — 2026-11-11).
 *
 * 질문 조건(계약 — 모두 만족해야 물어본다):
 *  - 코스 3/3 완주 직후([courseCompleted])이고,
 *  - 주행 상태가 아니며([driving]=false — 주행 중에는 질문하지 않는다)이고,
 *  - 이 코스에서 이미 '나중에 정하기'를 누르지 않았고([deferred]=false — 세션 메모리만,
 *    로그아웃 시 소멸. 재방문 시 다시 물어도 된다)이고,
 *  - 서버 heir 조회에 성공해 미지정임이 확인됐고([heirStatus]=Known(null) — 이미 정해져 있으면
 *    다시 묻지 않는다)이고,
 *  - 등록 가족 구성원 목록(GET members)이 읽혀 있어([membersAvailable]=true) 버튼을 만들 수 있다.
 *
 * 서버 응답이 없거나(heir Unknown) 필드가 없으면 추측으로 질문하지 않는다(기능 숨김 —
 * '필드가 응답에 없으면 기능 숨김(fallback 안전)' 계약). 정하지 않아도 배지·완주 기록은 유지된다.
 *
 * 순수 Kotlin — JVM 단위 테스트 대상(HeirQuestionTest).
 */
data class HeirQuestionInputs(
    val courseCompleted: Boolean,
    val driving: Boolean,
    val deferred: Boolean,
    val heirStatus: HeirStatus,
    val membersAvailable: Boolean,
)

object HeirQuestionPolicy {

    /** 위 조건이 모두 만족될 때만 true — 질문 카드를 보여 준다. */
    fun shouldAsk(inputs: HeirQuestionInputs): Boolean {
        if (!inputs.courseCompleted) return false
        if (inputs.driving) return false
        if (inputs.deferred) return false
        val status = inputs.heirStatus
        if (status !is HeirStatus.Known) return false // 모르면 묻지 않는다(추측 금지).
        if (status.memberId != null) return false // 이미 정해져 있으면 다시 묻지 않는다.
        if (!inputs.membersAvailable) return false // 구성원 버튼을 만들 수 없으면 묻지 않는다.
        return true
    }
}

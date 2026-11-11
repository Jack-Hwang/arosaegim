package app.arosaegim.domain

/** 스탬프 등록 성공 결과. stampNo는 이 세션(코스)에서 1부터 1씩 증가한다. */
data class StampResult(val placeId: String, val stampNo: Int)

/**
 * 스탬프 엔진 — 장소당 1회 획득, 재진입 방지, 코스 순서 규칙을 적용한다.
 *
 * 도착 판정 경계 규칙: "거리 == 반경 이내(<=)면 도착"이다.
 * 이 거리/반경 판정은 Haversine 호출자(도착 감지 레이어)가 수행하고,
 * 엔진은 판정을 통과한 placeId와 현재 목표 placeId만 전달받는다.
 * (단위 테스트에서 호출자 역할을 모킹하여 이 계약을 검증한다.)
 */
class StampEngine {

    /** 획득 완료 placeId 집합 (내부 LinkedHashSet의 읽기 전용 뷰). */
    private val stamped = LinkedHashSet<String>()
    val stampedPlaceIds: Set<String>
        get() = stamped

    /**
     * 도착 처리. currentTargetPlaceId(코스 순서상 지금 목표)와 일치하고 아직 미획득이면
     * 스탬프를 등록하고 [StampResult]를 반환한다. 그 외의 모든 경우는 null을 반환한다.
     *
     * - currentTargetPlaceId가 null(코스 완주)이면 항상 null.
     * - placeId가 현재 목표와 다르면(순서 위반) null.
     * - placeId가 이미 획득된 장소면(재진입 중복) null — 장소당 1회만 획득.
     */
    fun registerArrival(placeId: String, currentTargetPlaceId: String?): StampResult? {
        // 완주 후 남는 도착 신호는 무시한다.
        if (currentTargetPlaceId == null) return null
        // 코스 순서 규칙: 지금 목표가 아닌 장소 도착은 무시한다.
        if (placeId != currentTargetPlaceId) return null
        // 재진입 중복 방지: 이미 획득한 장소는 다시 획득하지 않는다.
        if (placeId in stamped) return null

        stamped.add(placeId)
        // stampNo는 1부터 증가: 획득 순번 = 현재까지 획득 수.
        return StampResult(placeId = placeId, stampNo = stamped.size)
    }

    /** 상태 초기화. 모든 획득 기록을 지워 다시 획득 가능하게 한다. */
    fun reset() {
        stamped.clear()
    }
}

package app.arosaegim.domain

/**
 * 코스 진행 상태. 스탬프 획득 기록과 다음 목표 장소 계산을 담당한다.
 * 순수 도메인 로직 — Android 프레임워크 의존 없음.
 */
class CourseProgress(val course: Course) {

    /** 코스 장소를 id로 빠르게 조회하기 위한 인덱스. */
    private val placeById: Map<String, Place> = course.places.associateBy { it.id }

    /** 획득한 placeId 집합. LinkedHashSet으로 찍은 순서를 보존한다. */
    private val stampedIds = LinkedHashSet<String>()

    /** 스탬프를 찍은 장소들, 찍은 순서대로. */
    val completedPlaces: List<Place>
        get() = stampedIds.mapNotNull { placeById[it] }

    /** 다음 목표 장소. 코스 순서상 아직 못 찍은 첫 장소. 코스를 완주하면 null. */
    val currentTarget: Place?
        get() = course.places.firstOrNull { it.id !in stampedIds }

    /** 코스 완주 여부. 남은 목표가 없으면(빈 코스 포함) true. */
    val isCompleted: Boolean
        get() = currentTarget == null

    /**
     * 스탬프를 찍는다.
     * - 코스에 없는 placeId는 무시한다.
     * - 이미 찍은 장소의 중복 요청도 무시한다(1회만 기록).
     */
    fun markStamped(placeId: String) {
        if (!placeById.containsKey(placeId)) return // 코스 외 장소 무시
        stampedIds.add(placeId)                    // Set이라 중복 자동 무시
    }
}

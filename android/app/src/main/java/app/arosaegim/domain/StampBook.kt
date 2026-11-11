package app.arosaegim.domain

/**
 * 스탬프북 정의 — 코스·권역 묶음 단위의 틀(시드 stamp_books 항목에 대응).
 *
 * @property placeIds 스탬프를 찍을 장소 id 목록. 이 정의에 없는 장소는 스탬프로 인정하지 않는다.
 * @property badge     스탬프북을 전부 완성했을 때 얻는 배지 이름.
 */
data class StampBookDef(
    val id: String,
    val title: String,
    val region: String,
    val country: String,
    val placeIds: List<String>,
    val badge: String,
)

/**
 * 스탬프북 진행 상태. 정의([StampBookDef]) 하나에 상태 하나가 대응한다.
 *
 * 규칙(03문서 5-1):
 *  - 정의에 없는 placeId 로 addStamp 해도 무시한다(아무 변화 없음).
 *  - 같은 placeId 로 중복 addStamp 해도 무시한다(진행률 변화 없음).
 *  - 진행률은 '스탬프 개수 ÷ 정의 장소 수 × 100'을 내림(정수 나눗셈)한다. 예) 1/3 → 33.
 *  - 정의의 모든 장소를 찍으면 완성이며, earnedBadge 가 배지 이름을 돌려준다.
 *  - 정의 장소가 0개면 완성으로 보지 않는다(빈 정의로 배지를 주는 사고 방지).
 *
 * 순수 Kotlin 이며 Android 프레임워크 의존이 없다. 한 인스턴스는 한 사용자의 한 스탬프북 상태만 담는다.
 */
class StampBook(private val def: StampBookDef) {

    // LinkedHashSet: 중복 스탬프를 집합으로 거르면서, 찍은 순서는 유지한다(UI 표시용).
    private val stamped = LinkedHashSet<String>()

    /**
     * 지금까지 스탬프를 찍은 장소 id 집합(읽기 전용 복사본).
     * 내부 상태를 직접 노출하지 않아 외부에서 임의로 수정할 수 없다.
     */
    val stampedPlaceIds: Set<String> get() = stamped.toSet()

    /** 정의에서 스탬프를 찍어야 할 장소 총 개수. */
    val totalPlaces: Int get() = def.placeIds.size

    /** 진행률 0~100(내림). 정의 장소가 0개면 0. 예) 1/3 → 33, 2/3 → 66, 3/3 → 100. */
    val progressPercent: Int
        get() {
            val total = def.placeIds.size
            if (total <= 0) return 0
            return stamped.size * 100 / total // 정수 나눗셈 = 내림
        }

    /** 정의의 모든 장소를 찍었는가. 빈 정의는 완성으로 보지 않는다. */
    val isCompleted: Boolean
        get() {
            val total = def.placeIds.size
            return total > 0 && stamped.size >= total
        }

    /**
     * 스탬프를 찍는다.
     * - 정의([StampBookDef.placeIds])에 없는 placeId 는 무시한다.
     * - 이미 찍은 placeId 는 중복으로 보고 무시한다.
     * 둘 다 성공 여부를 따지지 않고 조용히 무시하는 것이 이 클래스의 계약이다(크래시/예외 없음).
     */
    fun addStamp(placeId: String) {
        if (placeId in def.placeIds) {
            stamped.add(placeId) // Set 이므로 중복 add 는 자동으로 무시된다
        }
    }

    /** 완성 시 정의의 배지 이름, 미완성이면 null. */
    val earnedBadge: String? get() = if (isCompleted) def.badge else null
}

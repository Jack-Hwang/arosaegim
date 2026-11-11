package app.arosaegim.domain

/**
 * 과거 기록 한 건(서버·시드 원문 보관용).
 *
 * @property label 연도 표시용 라벨(예: "1990년대 중반"). 화면 표시는 이 label 을 우선하고,
 *                 없으면 '현재 연도 − yearsAgo'로 계산한다(00문서 10.4).
 */
data class PastRecord(
    val year: Int?,
    val note: String?,
    val photo: String?,
    val vehicleLabel: String?,
    val label: String? = null,
)

/**
 * '옛날과 지금' 비교 정보 해석기.
 * 순수 도메인 로직 — Android 프레임워크 의존 없음.
 */
object ThenNowResolver {

    /**
     * 과거 기록(단일)을 [ThenNow]로 해석한다 — 기존 호출부 호환용.
     *
     * - 과거 기록이 없으면(pastYear == null) null을 반환한다 → '첫 방문' 표시용.
     * - pastYear가 0 이하인 비정상 값이면 null을 반환한다.
     * - nowYear < pastYear 인 '미래 과거'(데이터 오류)도 null로 방어한다.
     * - 정상이면 yearsAgo = nowYear - pastYear 로 계산해 반환한다.
     *   (pastNote/pastPhoto/pastVehicle/nowVehicle는 전달된 그대로 보존한다.)
     *
     * label 규칙(00문서 10.4): [label] 을 그대로 [ThenNow.label] 로 옮긴다. 생략(null)이면
     * [ThenNow.label] 은 null 이 되고, 화면은 '현재 연도 − yearsAgo'로 연도를 표시한다.
     */
    fun resolve(
        pastYear: Int?,
        pastNote: String?,
        pastPhoto: String?,
        pastVehicle: String?,
        nowVehicle: String?,
        nowYear: Int,
        label: String? = null,
    ): ThenNow? {
        val year = pastYear ?: return null // 과거 기록 없음 → 첫 방문
        if (year <= 0) return null         // 비정상 연도 방어
        if (nowYear < year) return null    // 미래 과거(데이터 오류) 방어

        return ThenNow(
            pastYear = year,
            yearsAgo = nowYear - year,
            pastNote = pastNote,
            pastPhoto = pastPhoto,
            pastVehicle = pastVehicle,
            nowVehicle = nowVehicle,
            label = label,
        )
    }

    /**
     * 과거 기록 후보가 여러 개일 때의 해석.
     *
     * 선택 규칙(00문서 10.4 / 03문서 5-1):
     *  - 후보에서 제외: year 가 null 인 기록, year <= 0 인 비정상 기록, nowYear < year 인 '미래 과거'.
     *  - 살아남은 후보가 없으면 null(= '첫 방문' 취급).
     *  - 여러 후보가 살아남으면 **year 가 가장 작은(가장 오래된) 기록**을 고른다.
     *    같은 연도가 여러 개면 목록에서 먼저 나온 것을 유지한다.
     *  - label 은 선택된 기록의 label 값 그대로 [ThenNow.label] 에 담는다.
     */
    fun resolveFromRecords(
        past: List<PastRecord>,
        nowVehicle: String?,
        nowYear: Int,
    ): ThenNow? {
        // 1) 유효 후보만 남긴다(null 연도·0 이하 연도·미래 과거 제외).
        val candidates = past.filter { record ->
            val year = record.year ?: return@filter false
            year > 0 && nowYear >= year
        }
        if (candidates.isEmpty()) return null

        // 2) 가장 오래된(year 최소) 기록 선택. minByOrNull 은 목록 순서에서 먼저 나온 동률을 유지한다.
        val oldest = candidates.minByOrNull { it.year ?: Int.MAX_VALUE } ?: return null
        val year = oldest.year ?: return null // 필터로 이미 보장되지만 컴파일러가 모른다 — 방어.

        return ThenNow(
            pastYear = year,
            yearsAgo = nowYear - year,
            pastNote = oldest.note,
            pastPhoto = oldest.photo,
            pastVehicle = oldest.vehicleLabel,
            nowVehicle = nowVehicle,
            label = oldest.label,
        )
    }
}

package app.arosaegim.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 두 위경도 좌표 사이의 구면 거리를 미터 단위로 계산한다 (Haversine 공식).
 * 순수 도메인 로직 — Android 프레임워크 의존 없음.
 */
object Haversine {

    /** 지구 평균 반경 (미터). WGS84 타원체 근사 평균값 6,371km. */
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 도(degree) → 라디안 변환 상수. */
    private const val DEG_TO_RAD = Math.PI / 180.0

    /**
     * 위도/경도(도 단위) 2점 사이의 대원거리(great-circle distance)를 미터로 반환한다.
     *
     * 경계 판정 계약: "거리 == 반경 이내(<=)면 도착"으로 판정하는 것은
     * 이 함수를 호출하는 쪽(도착 감지 호출자)의 책임이며, 본 객체는 거리 값만 제공한다.
     */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLon = (lon2 - lon1) * DEG_TO_RAD

        val lat1Rad = lat1 * DEG_TO_RAD
        val lat2Rad = lat2 * DEG_TO_RAD

        // Haversine 공식: a = sin²(Δφ/2) + cosφ1·cosφ2·sin²(Δλ/2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2) * sin(dLon / 2)

        // c = 2·atan2(√a, √(1−a)) — a의 부동소수 오차로 1을 넘지 않도록 atan2 사용
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return EARTH_RADIUS_M * c
    }
}

package app.arosaegim.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VisitPayload 와이어 계약 테스트(E1 재발 방지 — 2026-11-11 서버 실측).
 *
 * 배경: 앱이 placeId·courseId·visitedAtIso 같은 캐멀케이스 키와 source 를 보내 서버가
 * 400(extra=forbid)으로 거부한 실측이 있다. 이 테스트는 '전송 몸체의 키는 서버 계약대로
 * 스네이크 케이스다'와 'null 필드는 몸체에서 빠진다'를 직렬화 결과로 단언해, 계약 어긋남이
 * 다시 생기면 전송 코드가 아닌 이 테스트가 먼저 잡아내게 한다.
 *
 * 직렬화는 ApiClient.wireJson(전송용 설정과 동일한 인스턴스)으로 수행한다 — 설정이 따로
 * 놀라 계약이 깨지는 일도 함께 막는다.
 */
class VisitPayloadContractTest {

    /** 모든 선택 필드를 채운 몸체 — 키 이름 계약 검증용. */
    private val full = VisitPayload(
        placeId = "gj-museum",
        courseId = "course-gyeongju",
        visitedAtIso = "2026-11-11T09:30:00Z",
        vehicleLabel = "가상 차량(수동 프로필)",
        partySize = 4,
        tripKm = 42.7,
        courseRunId = "run-uuid-1",
    )

    @Test
    fun `필드 이름은 서버 계약대로 스네이크 케이스다`() {
        val text = ApiClient.wireJson.encodeToString(full)

        // 서버가 받는 키(실측 계약) — 전부 있어야 한다.
        listOf(
            "place_id",
            "course_id",
            "visited_at",
            "vehicle_label",
            "party_size",
            "trip_km",
            "course_run_id",
        ).forEach { key ->
            assertTrue("전송 몸체에 \"$key\" 키가 있다", text.contains("\"$key\""))
        }

        // 캐멀케이스 키는 extra=forbid 로 400의 원인이 되므로 하나도 없어야 한다.
        listOf(
            "placeId",
            "courseId",
            "visitedAtIso",
            "vehicleLabel",
            "partySize",
            "tripKm",
            "courseRunId",
        ).forEach { key ->
            assertFalse("캐멀케이스 키 \"$key\" 는 몸체에 없다", text.contains("\"$key\""))
        }
    }

    @Test
    fun `서버 계약에 없는 source 필드는 전송 몸체에서 빠진다`() {
        val text = ApiClient.wireJson.encodeToString(full)

        // E1 실측: 도착 출처(source)는 서버가 모르는 필드다 — 400 방지를 위해 보내지 않는다.
        assertFalse("source 키는 몸체에 없다", text.contains("\"source\""))
    }

    @Test
    fun `null 필드는 전송 몸체에서 생략된다`() {
        // 필수 값만 채운 몸체 — 선택 필드(vehicle_label·party_size·trip_km·course_run_id)는
        // 전부 null 이고, wireJson(explicitNulls=false·encodeDefaults=false)이 이를 빼먹는다.
        val minimal = VisitPayload(
            placeId = "gj-museum",
            courseId = "course-gyeongju",
            visitedAtIso = "2026-11-11T09:30:00Z",
        )
        val text = ApiClient.wireJson.encodeToString(minimal)

        assertFalse("몸체에 null 리터럴이 없다", text.contains("null"))
        assertFalse("vehicle_label 은 생략된다", text.contains("vehicle_label"))
        assertFalse("party_size 는 생략된다", text.contains("party_size"))
        assertFalse("trip_km 은 생략된다", text.contains("trip_km"))
        assertFalse("course_run_id 는 생략된다", text.contains("course_run_id"))

        // 필수 필드는 그대로 실린다.
        assertTrue(text.contains("\"place_id\":\"gj-museum\""))
        assertTrue(text.contains("\"course_id\":\"course-gyeongju\""))
        assertTrue(text.contains("\"visited_at\":\"2026-11-11T09:30:00Z\""))
    }

    @Test
    fun `응답 계약 - 실측 응답의 visit_id와 stamp_no 는 정수로 읽힌다`() {
        // 서버 실측 응답(2026-11-11): visit_id 는 정수, then_now 등 추가 키가 함께 온다.
        val body = "{\"visit_id\": 3, \"stamp_no\": 2, \"party_size\": null, " +
            "\"trip_km\": null, \"then_now\": {\"place_id\":\"gj-museum\"}}"
        val parsed = ApiClient.wireJson.decodeFromString<VisitResponse>(body)

        assertEquals("visit_id(정수) 파싱", 3, parsed.visitId)
        assertEquals("stamp_no 파싱", 2, parsed.stampNo)
    }
}

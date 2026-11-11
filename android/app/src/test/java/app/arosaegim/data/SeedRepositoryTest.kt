package app.arosaegim.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * SeedRepository 단위 테스트(JVM, JUnit4).
 *
 * kotlinx.serialization 만 사용(org.json 은 JVM 스텁이라 금지).
 * 실제 시드 파일 검증은 src/main/assets/seed/seed.json 이 있을 때만 수행하고,
 * 없으면 Assume 으로 스킵한다(다른 소유 파일에 의존하지 않음).
 * 2026-11-10 지시서 기준: stamp_books / partners_demo / then_now_demo.label 과
 * 도메인 Place·QuizItem 의 검수 플래그(coordVerified·narrationReviewed·reviewed)를 함께 검증한다.
 */
class SeedRepositoryTest {

    private val repository = SeedRepository()

    @Test
    fun realSeedFile_parsesToExpectedContent() {
        val seedFile = File("src/main/assets/seed/seed.json")
        assumeTrue("시드 에셋이 없으면 이 테스트는 스킵한다", seedFile.exists())

        val content = repository.parse(seedFile.readText(Charsets.UTF_8))

        // 코스 식별자
        assertEquals("gyeongju-silla-01", content.course.id)

        // 장소 3개, place_ids 순서(museum → 대릉원 → 첨성대) 그대로 코스에 채워짐
        assertEquals(3, content.places.size)
        assertEquals(
            listOf("gj-museum", "gj-daereungwon", "gj-cheomseongdae"),
            content.course.places.map { it.id },
        )

        // 각 장소 퀴즈 2개씩
        content.places.forEach { place ->
            assertEquals("장소 ${place.id} 의 퀴즈 개수", 2, place.quiz.size)
        }

        // 도메인 검수 플래그: 좌표·해설·퀴즈 전부 초안(false)이다
        content.places.forEach { place ->
            assertFalse("장소 ${place.id} 좌표는 미검증 초안", place.coordVerified)
            assertFalse("장소 ${place.id} 해설은 검수 전 초안", place.narrationReviewed)
            place.quiz.forEach { quiz ->
                assertFalse("장소 ${place.id} 퀴즈 '${quiz.q}' 는 검수 전 초안", quiz.reviewed)
            }
        }

        // 초안 퀴즈: 3장소 x 2퀴즈 = 6개, 키 형식 "placeId#0부터인덱스"
        assertEquals(6, content.draftQuizKeys.size)
        assertTrue("gj-museum#0" in content.draftQuizKeys)
        assertTrue("gj-cheomseongdae#1" in content.draftQuizKeys)

        // 좌표 미검증 장소: 3개 전부
        assertEquals(3, content.unverifiedCoordPlaceIds.size)
        assertTrue(
            content.unverifiedCoordPlaceIds.containsAll(
                listOf("gj-museum", "gj-daereungwon", "gj-cheomseongdae"),
            ),
        )

        // '그때 vs 지금' 데모: 박물관 장소, label 우선 표시 규칙용 라벨과 years_ago 32
        assertEquals("gj-museum", content.thenNowDemo?.placeId)
        assertEquals(32, content.thenNowDemo?.yearsAgo)
        assertEquals("연도 표시는 label 우선(00문서 10.4)", "1990년대 중반", content.thenNowDemo?.label)
        assertEquals("절대 연도는 발주자 확정값(1994)", 1994, content.thenNowDemo?.year)
        assertEquals("assets/seed/then_placeholder.png", content.thenNowDemo?.photoAsset)
        // T6-2 데이터: '그때 함께 간 인원' — 발주자 확정 5명(party_size)
        assertEquals("그때 인원은 발주자 확정값(5)", 5, content.thenNowDemo?.partySize)

        // T6-1 데이터: 유물의 시간(heritage) — 2026-11-11 시드 보강으로 3곳 모두 보유
        val heritageByPlace = content.places.associate { it.id to it.heritage }
        val museum = heritageByPlace["gj-museum"]
        val daereungwon = heritageByPlace["gj-daereungwon"]
        val cheomseongdae = heritageByPlace["gj-cheomseongdae"]
        assertNotNull("gj-museum heritage", museum)
        assertEquals("성덕대왕신종", museum?.name)
        assertEquals(771, museum?.year)
        assertEquals("1,250여 년", museum?.label)
        assertNotNull("gj-daereungwon heritage(신규 보강)", daereungwon)
        assertEquals("천마총", daereungwon?.name)
        assertEquals(650, daereungwon?.year)
        assertNotNull("gj-cheomseongdae heritage(신규 보강)", cheomseongdae)
        assertEquals("첨성대", cheomseongdae?.name)
        assertEquals(632, cheomseongdae?.year)
        // 초안 표기 규칙: heritage 가 있는 곳은 reviewed=false 유지(00문서 13절 — 학설 단정 금지)
        assertTrue("모든 heritage 는 초안(reviewed=false)이어야 한다",
            listOf(museum, daereungwon, cheomseongdae).all { it?.reviewed == false })
        // 검수 메모: heritage note 는 '공식 자료로 검수 필요' 안내를 담는다(02문서 7절 — 단정 금지).
        assertTrue("heritage note 는 검수 필요 안내를 담는다",
            listOf(museum, daereungwon, cheomseongdae).all { !it?.note.isNullOrBlank() })

        // T9-2 데이터: 예시 음성 편지 — 시드 단일 객체가 목록 1건으로 정규화된다
        assertEquals("voice_letter_demo 단일 객체 → 목록 1건", 1, content.voiceLetters.size)
        val letter = content.voiceLetters.first()
        assertEquals("letter-demo-1", letter.id)
        assertEquals("gj-museum", letter.placeId)
        assertEquals("지난 방문에 남긴 한마디", letter.sealedLabel)
        assertEquals("assets/seed/letter_demo.ogg", letter.audioAsset)
        assertTrue("예시 편지는 simulated=true", letter.simulated)

        // T9-3 데이터: 오늘의 자리 이름표 문구 3개
        assertEquals(
            listOf("운전석", "조수석 길잡이", "뒷좌석 탐험가"),
            content.seatRoles,
        )

        // 혜택 데모 2개
        assertEquals(2, content.offersDemo.size)

        // 스탬프북: 1개 — 경주 신라, 배지 '신라 이야기꾼', 장소 3개
        assertEquals(1, content.stampBooks.size)
        val stampBook = content.stampBooks.first()
        assertEquals("gyeongju-silla", stampBook.id)
        assertEquals("신라 이야기꾼", stampBook.badge)
        assertEquals(
            listOf("gj-museum", "gj-daereungwon", "gj-cheomseongdae"),
            stampBook.placeIds,
        )

        // 제휴처 데모: 4개(수정구 숙박 p-demo-stay 추가) — 전부 가상(simulated=true), 적립률 0.01 또는 0.02
        assertEquals(4, content.partnersDemo.size)
        content.partnersDemo.forEach { partner ->
            assertTrue("제휴처 ${partner.id} 는 가상(시뮬레이션)이어야 한다", partner.simulated)
            assertTrue(
                "제휴처 ${partner.id} 적립률은 0.01 또는 0.02여야 한다",
                partner.pointRate == 0.01 || partner.pointRate == 0.02,
            )
        }
    }

    @Test
    fun corruptJson_returnsEmptyContentWithoutCrash() {
        val content = repository.parse("{oops")

        // 크래시 없이 빈 코스(places 0개)로 복구 — 새 목록(stamp_books/partners_demo)도 빈 값
        assertEquals(0, content.places.size)
        assertEquals(0, content.course.places.size)
        assertNull(content.thenNowDemo)
        assertTrue(content.offersDemo.isEmpty())
        assertTrue(content.draftQuizKeys.isEmpty())
        assertTrue(content.unverifiedCoordPlaceIds.isEmpty())
        assertTrue(content.stampBooks.isEmpty())
        assertTrue(content.partnersDemo.isEmpty())
        assertTrue(content.voiceLetters.isEmpty())
        assertTrue(content.seatRoles.isEmpty())
    }

    @Test
    fun missingThenNowDemo_returnsNull() {
        val content = repository.parse(COURSE_WITHOUT_THEN_NOW)

        assertNull(content.thenNowDemo)
        assertEquals(1, content.places.size)
        // reviewed=false 퀴즈 1개만 초안으로 기록된다
        assertEquals(setOf("p1#0"), content.draftQuizKeys)
        assertTrue(content.unverifiedCoordPlaceIds.isEmpty())
        // 검증된(coord_verified=true) 장소라 도메인 플래그도 true로 심긴다
        assertTrue(content.places.first().coordVerified)
        assertTrue(content.places.first().narrationReviewed)
        assertFalse(content.places.first().quiz.first().reviewed)
        // stamp_books / partners_demo 가 없는 시드는 빈 목록으로 복귀한다(기본값 동작)
        assertTrue(content.stampBooks.isEmpty())
        assertTrue(content.partnersDemo.isEmpty())
    }

    @Test
    fun unknownFields_areIgnored() {
        // _comment 등 알 수 없는 필드는 ignoreUnknownKeys 로 무시되고 정상 파싱된다
        val content = repository.parse(COURSE_WITH_UNKNOWN_FIELDS)

        assertEquals("c2", content.course.id)
        assertEquals(0, content.places.size)
        assertNull(content.thenNowDemo)
        assertTrue(content.offersDemo.isEmpty())
    }

    @Test
    fun placesNotListedInPlaceIds_areSkippedFromCourse() {
        val content = repository.parse(COURSE_WITH_GHOST_PLACE)

        // places 목록 자체는 전부 유지되지만, place_ids 에 없는 장소는 코스에서 스킵된다
        assertEquals(2, content.places.size)
        assertEquals(listOf("p1"), content.course.places.map { it.id })
        // 스킵된 장소의 초안 퀴즈도 draftQuizKeys 에는 포함된다
        assertEquals(setOf("p1#0", "ghost#0"), content.draftQuizKeys)
    }

    @Test
    fun thenNowDemoWithoutLabel_labelIsNull() {
        // label 이 없는 구형 시드도 크래시 없이 파싱되고 label 은 null 이다(00문서 10.4 기본 동작)
        val content = repository.parse(COURSE_WITH_LABEL_LESS_THEN_NOW)

        assertEquals("p1", content.thenNowDemo?.placeId)
        assertEquals(35, content.thenNowDemo?.yearsAgo)
        assertNull("label 이 없으면 null — 화면은 '현재 연도 − years_ago'로 계산한다", content.thenNowDemo?.label)
    }

    @Test
    fun missingStampBookAndPartners_defaultToEmptyLists() {
        // stamp_books / partners_demo 가 전혀 없는(구형) 시드도 빈 목록으로 안전하게 파싱된다
        val content = repository.parse(COURSE_WITHOUT_THEN_NOW)

        assertTrue(content.stampBooks.isEmpty())
        assertTrue(content.partnersDemo.isEmpty())
    }

    private companion object {
        /** then_now_demo 가 없는 최소 시드. */
        private const val COURSE_WITHOUT_THEN_NOW = """
        {
          "version": 1,
          "course": {"id": "c1", "title": "테스트 코스", "place_ids": ["p1"]},
          "places": [
            {
              "id": "p1", "name": "장소", "address": "주소",
              "lat": 35.0, "lon": 129.0,
              "coord_verified": true, "coord_note": "검증됨",
              "arrive_radius_m": 100,
              "narration": "해설", "narration_reviewed": true,
              "quiz": [{"q": "문제", "choices": ["보기1", "보기2"], "answer": 1, "explain": "설명", "reviewed": false}]
            }
          ],
          "offers_demo": []
        }
        """

        /** _comment 등 알 수 없는 필드를 섞은 시드. */
        private const val COURSE_WITH_UNKNOWN_FIELDS = """
        {
          "_comment": "무시해야 하는 주석 필드",
          "version": 2,
          "course": {"id": "c2", "title": "주석 코스", "summary": "도메인 Course 에 없는 요약", "place_ids": []},
          "places": [],
          "then_now_demo": null,
          "offers_demo": [],
          "unknown_top_level": {"nested": true}
        }
        """

        /** place_ids 에 없는 장소(ghost)가 섞인 시드. */
        private const val COURSE_WITH_GHOST_PLACE = """
        {
          "version": 1,
          "course": {"id": "c3", "title": "스킵 코스", "place_ids": ["p1"]},
          "places": [
            {
              "id": "p1", "name": "장소", "address": "주소",
              "lat": 35.0, "lon": 129.0, "coord_verified": true,
              "arrive_radius_m": 100, "narration": "해설", "narration_reviewed": true,
              "quiz": [{"q": "문제", "choices": ["보기"], "answer": 0, "explain": "설명", "reviewed": false}]
            },
            {
              "id": "ghost", "name": "코스에 없는 장소", "address": "주소",
              "lat": 36.0, "lon": 130.0, "coord_verified": false,
              "arrive_radius_m": 100, "narration": "해설", "narration_reviewed": false,
              "quiz": [{"q": "문제", "choices": ["보기"], "answer": 0, "explain": "설명", "reviewed": false}]
            }
          ],
          "offers_demo": []
        }
        """

        /** label 이 없는 then_now_demo 를 가진 시드(구형 시드 호환 확인용). */
        private const val COURSE_WITH_LABEL_LESS_THEN_NOW = """
        {
          "version": 1,
          "course": {"id": "c4", "title": "구형 데모 코스", "place_ids": ["p1"]},
          "places": [
            {
              "id": "p1", "name": "장소", "address": "주소",
              "lat": 35.0, "lon": 129.0, "coord_verified": true,
              "arrive_radius_m": 100, "narration": "해설", "narration_reviewed": true,
              "quiz": []
            }
          ],
          "then_now_demo": {
            "place_id": "p1",
            "years_ago": 35,
            "note": "메모",
            "vehicle_label": "그때 차",
            "photo": "assets/seed/then_placeholder.png"
          },
          "offers_demo": []
        }
        """
    }
}

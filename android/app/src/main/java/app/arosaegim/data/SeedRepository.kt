package app.arosaegim.data

import android.content.Context
import app.arosaegim.domain.Course
import app.arosaegim.domain.HeritageInfo
import app.arosaegim.domain.Place
import app.arosaegim.domain.QuizItem
import app.arosaegim.domain.StampBookDef
import app.arosaegim.domain.VoiceLetter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * 시드 저장소: assets/seed/seed.json 을 읽어 [SeedContent] 로 바꿔 준다.
 *
 * 파싱은 kotlinx.serialization 사용(JVM 단위 테스트에서도 동작. org.json 은 스텁이므로 사용 금지).
 * 손상된 JSON 이나 읽기 실패 시에도 절대 크래시하지 않고 빈 코스(places 0개)를 반환한다.
 * stamp_books / partners_demo / then_now_demo.label 등 새 시드 필드도 같은 규칙으로 파싱한다.
 */
class SeedRepository {

    /**
     * 문자열 파서 — JVM 단위 테스트용.
     * 파싱 실패 시(손상 JSON) 크래시 대신 빈 코스(places 0개)인 [SeedContent] 를 반환한다.
     */
    fun parse(json: String): SeedContent {
        val dto = try {
            seedJson.decodeFromString(SeedFileDto.serializer(), json)
        } catch (e: SerializationException) {
            // 손상된 JSON: 빈 콘텐츠로 복구(크래시 금지).
            return emptyContent()
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization 이 잘못된 입력에 던질 수 있는 래퍼 예외도 빈 콘텐츠로 복구.
            return emptyContent()
        }
        return toContent(dto)
    }

    /**
     * assets/seed/seed.json 을 읽어 파싱한다.
     * 파일이 없거나 읽기 실패(IOException) 시 parse("") 결과와 동일한 빈 콘텐츠를 반환한다(크래시 금지).
     */
    fun load(context: Context): SeedContent {
        val jsonText = try {
            context.assets.open(SEED_ASSET_PATH).use { stream ->
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: IOException) {
            // assets 읽기 실패(파일 누락, 패키징 오류 등): 빈 콘텐츠로 복구.
            return parse("")
        }
        return parse(jsonText)
    }

    /**
     * 내부 DTO → 공개 타입 매핑.
     * 매핑 규칙:
     *  - Course.places 는 course.place_ids 순서대로 채운다. places 에 없는 id 는 스킵한다.
     *  - places 목록 자체는 시드 선언 순서를 그대로 유지한다.
     *  - 검수·좌표 플래그(coord_verified, narration_reviewed, quiz.reviewed)는 도메인
     *    Place/QuizItem 필드에 그대로 심는다 — 도메인 필드가 1차 출처(진실의 원천)다.
     *  - draftQuizKeys(reviewed=false 퀴즈 "placeId#인덱스")와 unverifiedCoordPlaceIds 는
     *    그 도메인 필드에서 다시 집계한 UI 편의 뷰(집합)다.
 *  - stamp_books → StampBookDef, partners_demo → PartnerDemo 로 그대로 옮긴다.
 *    (시드에 없으면 빈 목록 — 기존 시드와의 호환을 위한 기본값.)
 *  - places[].heritage → 도메인 Place.heritage(HeritageInfo). 시드에 없으면 null.
 *  - voice_letter_demo 단일 객체 → voiceLetters 목록 1건으로 정규화한다. 없으면 빈 목록.
 *  - seat_roles → seatRoles 목록. 없으면 빈 목록.
 *  - then_now_demo.label 은 ThenNowDemo.label 로 옮긴다(연도 표시는 label 우선 — 00문서 10.4).
 *  - then_now_demo.party_size 는 ThenNowDemo.partySize 로 옮긴다('그때 인원' — 00문서 11-1 T6-2).
     *  - course.summary 는 도메인 Course 에 필드가 없어 시드 값을 버린다.
     *  - offers_demo 의 simulated 는 OfferDemo 에 담지 않는다(시드는 전부 simulated=true).
     */
    private fun toContent(dto: SeedFileDto): SeedContent {
        val courseDto = dto.course ?: return emptyContent()
        val places = dto.places.map { it.toDomain() }
        val placesById = places.associateBy { it.id }

        val course = Course(
            id = courseDto.id,
            title = courseDto.title,
            places = courseDto.placeIds.mapNotNull { placesById[it] },
        )

        // UI 편의 뷰: 도메인 필드(1차 출처)에서 집계한다.
        val draftQuizKeys = buildSet {
            places.forEach { place ->
                place.quiz.forEachIndexed { quizIndex, quizItem ->
                    if (!quizItem.reviewed) {
                        add("${place.id}#$quizIndex")
                    }
                }
            }
        }
        val unverifiedCoordPlaceIds = places.filter { !it.coordVerified }.map { it.id }.toSet()

        return SeedContent(
            course = course,
            places = places,
            thenNowDemo = dto.thenNowDemo?.toDemo(),
            offersDemo = dto.offers.map { it.toDemo() },
            draftQuizKeys = draftQuizKeys,
            unverifiedCoordPlaceIds = unverifiedCoordPlaceIds,
            stampBooks = dto.stampBooks.map { it.toDef() },
            partnersDemo = dto.partnersDemo.map { it.toDemo() },
            // voice_letter_demo 는 시드에서 단일 객체다 — 목록 1건으로 정규화해 담는다(00문서 4절).
            voiceLetters = dto.voiceLetterDemo?.let { listOf(it.toVoiceLetter()) } ?: emptyList(),
            seatRoles = dto.seatRoles,
        )
    }

    /** 파싱 실패·읽기 실패 시 돌려주는 빈 콘텐츠(코스 places 0개, 새 목록도 전부 빈 값). */
    private fun emptyContent(): SeedContent = SeedContent(
        course = Course(id = "", title = "", places = emptyList()),
        places = emptyList(),
        thenNowDemo = null,
        offersDemo = emptyList(),
        draftQuizKeys = emptySet(),
        unverifiedCoordPlaceIds = emptySet(),
        stampBooks = emptyList(),
        partnersDemo = emptyList(),
        voiceLetters = emptyList(),
        seatRoles = emptyList(),
    )

    companion object {
        /** assets 루트 기준 시드 파일 경로. */
        const val SEED_ASSET_PATH = "seed/seed.json"

        /** _comment 등 시드에 섞인 비표준 필드를 무시하기 위한 설정. */
        private val seedJson = Json { ignoreUnknownKeys = true }
    }
}

// ---------------------------------------------------------------------------
// 내부 DTO: seed.json 구조를 그대로 반영(kotlinx.serialization).
// 필드에 기본값을 두어 일부 항목이 빠진 시드도 크래시 없이 파싱된다.
// ---------------------------------------------------------------------------

/** seed.json 최상위 구조. */
@Serializable
internal data class SeedFileDto(
    val version: Int = 0,
    val course: CourseDto? = null,
    val places: List<PlaceDto> = emptyList(),
    @SerialName("then_now_demo") val thenNowDemo: ThenNowDemoDto? = null,
    @SerialName("offers_demo") val offers: List<OfferDto> = emptyList(),
    @SerialName("stamp_books") val stampBooks: List<StampBookDto> = emptyList(),
    @SerialName("partners_demo") val partnersDemo: List<PartnerDto> = emptyList(),
    // 예시 음성 편지는 단일 객체(00문서 11-1 T9-2: 예시 1건). 매핑에서 목록 1건으로 정규화한다.
    @SerialName("voice_letter_demo") val voiceLetterDemo: VoiceLetterDemoDto? = null,
    // 오늘의 자리 이름표 문구(T9-3 데이터). 차량 화면 표시는 웹 연동 후이지만 시드 값은 읽어 둔다.
    @SerialName("seat_roles") val seatRoles: List<String> = emptyList(),
)

@Serializable
internal data class CourseDto(
    val id: String = "",
    val title: String = "",
    // summary 는 도메인 Course 에 없어 파싱만 하고 버린다.
    val summary: String = "",
    @SerialName("place_ids") val placeIds: List<String> = emptyList(),
)

@Serializable
internal data class PlaceDto(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    @SerialName("coord_verified") val coordVerified: Boolean = false,
    @SerialName("coord_note") val coordNote: String = "",
    @SerialName("arrive_radius_m") val arriveRadiusM: Int = 0,
    val narration: String = "",
    @SerialName("narration_reviewed") val narrationReviewed: Boolean = false,
    val quiz: List<QuizDto> = emptyList(),
    // 유물의 시간(T6-1). 시드에 heritage 가 없는 장소는 null 이다(gj-museum 만 있다).
    val heritage: HeritageDto? = null,
) {
    fun toDomain(): Place = Place(
        id = id,
        name = name,
        address = address,
        lat = lat,
        lon = lon,
        arriveRadiusM = arriveRadiusM,
        narration = narration,
        quiz = quiz.map { quizDto ->
            QuizItem(
                q = quizDto.q, choices = quizDto.choices, answer = quizDto.answer,
                explain = quizDto.explain, reviewed = quizDto.reviewed,
            )
        },
        coordVerified = coordVerified,
        narrationReviewed = narrationReviewed,
        heritage = heritage?.toDomain(),
    )
}

/** 시드 places[].heritage 항목 → 도메인 HeritageInfo(00문서 then-now heritage 스펙 기준). */
@Serializable
internal data class HeritageDto(
    val name: String = "",
    val year: Int = 0,
    val label: String? = null,
    val reviewed: Boolean = false,
    val note: String? = null,
) {
    fun toDomain(): HeritageInfo = HeritageInfo(
        name = name, year = year, label = label, reviewed = reviewed, note = note,
    )
}

/** 퀴즈 문항. reviewed 는 도메인 QuizItem.reviewed 로 옮겨 1차 출처가 된다. */
@Serializable
internal data class QuizDto(
    val q: String = "",
    val choices: List<String> = emptyList(),
    val answer: Int = 0,
    val explain: String = "",
    val reviewed: Boolean = false,
)

@Serializable
internal data class ThenNowDemoDto(
    @SerialName("place_id") val placeId: String = "",
    val year: Int? = null,               // 절대 연도(2026-11-10 발주자 확정: 1994)
    @SerialName("years_ago") val yearsAgo: Int = 0,
    val note: String = "",
    @SerialName("vehicle_label") val vehicleLabel: String = "",
    val photo: String = "",
    // 연도 표시용 라벨(예: "1990년대 중반"). 시드에 없으면 null(00문서 10.4).
    val label: String? = null,
    // '그때 함께 간 인원'(2026-11-10 발주자 확정 5명). 시드에 없으면 null(T6-2 비교용).
    @SerialName("party_size") val partySize: Int? = null,
) {
    fun toDemo(): ThenNowDemo = ThenNowDemo(
        placeId = placeId,
        year = year,
        yearsAgo = yearsAgo,
        note = note,
        vehicleLabel = vehicleLabel,
        photoAsset = photo,
        label = label,
        partySize = partySize,
    )
}

/** 시드 voice_letter_demo 단일 객체 → 도메인 VoiceLetter(T9-2 예시 편지 1건). */
@Serializable
internal data class VoiceLetterDemoDto(
    val id: String = "",
    @SerialName("place_id") val placeId: String = "",
    @SerialName("sealed_label") val sealedLabel: String = "",
    val text: String = "",
    val audio: String? = null,
    val simulated: Boolean = true, // 시드에 값이 빠져도 '예시'로 안전하게 취급한다.
) {
    fun toVoiceLetter(): VoiceLetter = VoiceLetter(
        id = id,
        placeId = placeId,
        sealedLabel = sealedLabel,
        text = text,
        audioAsset = audio,
        simulated = simulated,
    )
}

@Serializable
internal data class OfferDto(
    val id: String = "",
    val title: String = "",
    val condition: String = "",
    // simulated 는 데모 표식이므로 OfferDemo 에 담지 않는다.
    val simulated: Boolean = false,
) {
    fun toDemo(): OfferDemo = OfferDemo(id = id, title = title, condition = condition)
}

/** 시드 stamp_books 항목 → 도메인 StampBookDef. */
@Serializable
internal data class StampBookDto(
    val id: String = "",
    val title: String = "",
    val region: String = "",
    val country: String = "",
    @SerialName("place_ids") val placeIds: List<String> = emptyList(),
    val badge: String = "",
) {
    fun toDef(): StampBookDef = StampBookDef(
        id = id, title = title, region = region, country = country,
        placeIds = placeIds, badge = badge,
    )
}

/** 시드 partners_demo 항목 → 데모용 PartnerDemo. 전부 가상이므로 실제 적립·결제에 쓰지 않는다. */
@Serializable
internal data class PartnerDto(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    @SerialName("near_place_id") val nearPlaceId: String = "",
    @SerialName("point_rate") val pointRate: Double = 0.0,
    // simulated 는 데모 표식. 시드에 빠져도 안전하게 '가상'으로 보도록 기본 true.
    val simulated: Boolean = true,
) {
    fun toDemo(): PartnerDemo = PartnerDemo(
        id = id, name = name, kind = kind,
        nearPlaceId = nearPlaceId, pointRate = pointRate, simulated = simulated,
    )
}

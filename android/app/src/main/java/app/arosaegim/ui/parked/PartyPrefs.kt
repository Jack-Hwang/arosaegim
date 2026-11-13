package app.arosaegim.ui.parked

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/*
 * B4: 인원 비교 문장('오늘은 N명이 함께 왔어요. 그때는 M명…') 접어 두기의 조회·판정·폴백
 * (00문서 16절·요구 문서 07의 2절 B4 — 2026-11-12).
 *
 * 저장 위치(계약): 서버 가족 기록 —
 *   GET  /v1/families/me/prefs → {"party_line_hidden_places": [...], "party_line_all_hidden": false}
 *   PUT  /v1/families/me/prefs  같은 몸체(전체 교체) — PIN 헤더(ApiClient.getPrefs·putPrefs).
 * 백엔드 prefs 가 늦거나 호출이 실패하면 [PartyFoldLocalStore] 의 로컬 SharedPreferences 로
 * 방어한다(아래 주석 — 서버 연동 예정). 조회가 성공하면 서버 값이 언제나 이긴다.
 *
 * 파서·판정은 순수 Kotlin 이라 JVM 단위 테스트가 가능하다(PartyPrefsTest — domain
 * FamilyApiParsers 와 같은 규칙). SharedPreferences 를 만지는 [PartyFoldLocalStore] 는
 * 테스트에서 부르지 않는다(android 프레임워크 의존).
 */

/** prefs 응답 파싱 전용 Json(모르는 키 무시 — 서버 필드 추가에 흔들리지 않게). */
private val partyPrefsJson = Json { ignoreUnknownKeys = true }

/**
 * 인원 비교 문장 접기 설정(가족 단위 — 서버 /v1/families/me/prefs 의 앱 표시값).
 *
 * @property hiddenPlaceIds 문장을 접어 둔 장소 id 집합. 그 장소에서만 문장이 다시 나오지 않는다.
 * @property allHidden     '모든 장소 끄기' 스위치(웹 가족 관리). true 면 어느 장소에서나 숨긴다.
 */
data class PartyLinePrefs(
    val hiddenPlaceIds: Set<String>,
    val allHidden: Boolean,
)

/**
 * GET /v1/families/me/prefs 응답 원문을 [PartyLinePrefs] 로 바꾼다(B4).
 *  - 필드가 빠져도 서버 계약상 초기값(빈 목록·false)이므로 그 값으로 채워 돌려준다
 *    (백엔드 preferences.py — '값이 없으면 초기값').
 *  - 객체가 아니거나 깨진 JSON 은 null — 호출부가 로컬 폴백으로 전환한다(추측 금지).
 */
fun parsePartyLinePrefs(body: String): PartyLinePrefs? = runCatching {
    val obj = partyPrefsJson.parseToJsonElement(body).jsonObject
    val places = (obj["party_line_hidden_places"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.filter { it.isNotBlank() }
        ?: emptyList()
    val allHidden = (obj["party_line_all_hidden"] as? JsonPrimitive)?.booleanOrNull ?: false
    PartyLinePrefs(hiddenPlaceIds = places.toSet(), allHidden = allHidden)
}.getOrNull()

/**
 * 이 장소의 인원 비교 문장을 숨겨야 하는지(B4 표시 판정). 접힘 목록에 placeId 가 있거나
 * '모든 장소 끄기'가 true 면 숨긴다. 설정이 없으면(미조회·실패) 기존 동작 — 숨기지 않는다.
 */
fun partyLineHiddenFor(placeId: String?, prefs: PartyLinePrefs?): Boolean {
    if (placeId == null || prefs == null) return false
    return prefs.allHidden || placeId in prefs.hiddenPlaceIds
}

/**
 * 접기 저장(PUT 은 전체 교체)에 쓸 장소 목록 — 현재 값에 이 장소를 합친다. 이미 접어 있으면
 * 그대로(멱등). prefs 가 null(미조회)이면 이 장소 하나만 — 그 장소의 접기가 사라지지 않게.
 */
fun partyPrefsPlacesWithPlace(prefs: PartyLinePrefs?, placeId: String): Set<String> =
    (prefs?.hiddenPlaceIds ?: emptySet()) + placeId

/**
 * ⚠ 방어 폴백(B4, 2026-11-12) — 서버 가족 기록이 원칙이다(공통 규칙: 저장은 차량이 아니라 서버).
 *
 * 백엔드의 prefs API(/v1/families/me/prefs)가 아직 착지하지 않았거나 호출이 실패했을 때만
 * 이 차의 SharedPreferences("family_party_prefs")에 접기를 남긴다. 서버 연동이 정착하면
 * 이 저장소는 '서버 실패 시에만' 읽히고, 서버 값이 있으면 언제나 무시된다. 로그아웃·차 교체에는
 * 이어지지 않는다(로컬 한계 — 주석으로 서버 연동 예정을 명시한다).
 *
 * 키 계약(요구 문서 지정): 접힌 장소 folded_party_<placeId>(Boolean), 모든 장소 끄기
 * all_party_folded(Boolean). 개인 식별값·좌표는 저장하지 않는다(00문서 2-2).
 */
internal object PartyFoldLocalStore {

    /** SharedPreferences 파일 이름 — B4 로컬 폴백 전용. */
    const val PREFS_NAME = "family_party_prefs"

    /** 접힌 장소 키 접두사 — 실제 키는 folded_party_<placeId>. */
    const val KEY_FOLDED_PREFIX = "folded_party_"

    /** '모든 장소 끄기' 폴백 키(웹 가족 관리 스위치의 로컬 사본 — 서버 실패 시에만 읽는다). */
    const val KEY_ALL_FOLDED = "all_party_folded"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 접어 둔 장소 id 전체 — folded_party_* 키를 훑어 placeId 부분만 모은다. 실패는 빈 집합
     * (폴백이 기능을 막지 않게 — DiagnosticsMetrics 와 같은 흡수 규칙).
     */
    fun readFoldedPlaceIds(context: Context): Set<String> = runCatching {
        prefs(context).all.keys
            .filter { it.startsWith(KEY_FOLDED_PREFIX) }
            .map { it.removePrefix(KEY_FOLDED_PREFIX) }
            .filter { it.isNotBlank() }
            .toSet()
    }.getOrDefault(emptySet())

    /** '모든 장소 끄기' 폴백값. 없으면 false(끄지 않음이 초기값 — 서버 계약과 같다). */
    fun readAllHidden(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_ALL_FOLDED, false) }.getOrDefault(false)

    /** 이 장소의 접기를 로컬에 남긴다(서버 저장 실패 시에만 호출한다). */
    fun writeFolded(context: Context, placeId: String) {
        if (placeId.isBlank()) return // 빈 placeId 는 읽는 쪽에서 걸러지므로 아예 남기지 않는다.
        runCatching {
            prefs(context).edit().putBoolean(KEY_FOLDED_PREFIX + placeId, true).apply()
        }
    }
}

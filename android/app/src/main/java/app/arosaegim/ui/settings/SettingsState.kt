package app.arosaegim.ui.settings

import app.arosaegim.ui.parked.PartyLinePrefs

/*
 * 설정 화면 순수 상태 로직(13번 문서 T4 설정·가족 전환 — 시안 42, 2026-11-14 신규).
 *
 * 화면(SettingsScreen)은 그리기와 결과 전달만 하고, 값 판정은 전부 이 오브젝트가 한다.
 * Context·저장·네트워크·로그가 없는 순수 Kotlin 이라 app/src/test(JVM)에서 검증한다
 * (SettingsStateTest — domain 파서 테스트와 같은 규칙).
 *
 * 서버 계약(표시 스위치 — 기존 PartyPrefs API 재사용, 2026-11-12 B4와 같은 저장 위치):
 *   GET  /v1/families/me/prefs → {"party_line_hidden_places": [...], "party_line_all_hidden": false}
 *   PUT  /v1/families/me/prefs  같은 몸체(전체 교체 — ApiClient.putPrefs).
 * 서버 값이 언제나 이기고, 서버에 닿지 않으면(세션 없음·조회 실패 — null) 기존 동작대로
 * 기기 안 임시값(PartyFoldLocalStore.readAllHidden)으로 판정한다. GET·PUT 호출과 임시값 읽기는
 * 이 화면을 여는 쪽(통합)이 하고, 화면에는 결과만 상태 파라미터로 주입된다 — data/ 파일은
 * 수정하지 않는다.
 */
object SettingsState {

    /**
     * [표시] 스위치에 보일 값 판정. 스위치 라벨은 '인원 비교 문장 보기'(settings_display_label)라
     * '문장이 보임'이 true 다 — 서버의 party_line_all_hidden(전부 숨김)의 반대다.
     *
     * @param serverAllHidden  서버 party_line_all_hidden 값. null = 서버 값 없음(세션 없음·조회 실패).
     * @param deviceAllHidden  기기 안 임시값(PartyFoldLocalStore.readAllHidden — 서버 실패 시에만 읽는다).
     * @return 스위치가 켜진 모양(문장 보임). 서버 값이 있으면 그 값의 반대, 없으면 임시값의 반대.
     */
    fun partyLineVisible(serverAllHidden: Boolean?, deviceAllHidden: Boolean): Boolean {
        val allHidden = serverAllHidden ?: deviceAllHidden
        return !allHidden
    }

    /**
     * 토글 후 다음 값 — '보임 ↔ 숨김' 반전. 화면은 이 값만 콜백으로 넘기고, 실제 저장
     * (PUT prefs 또는 기기 임시값 갱신)은 통합이 한다.
     */
    fun nextPartyLineVisible(currentVisible: Boolean): Boolean = !currentVisible

    /**
     * PUT /v1/families/me/prefs 는 '전체 교체'라 현재 값을 통째로 보내지 않으면 접어 둔 장소
     * 목록이 서버 초기값(빈 목록)으로 덮어써진다(ApiClient.putPrefs 계약). 토글 저장 때는
     * 스위치 값과 무관하게 이 목록을 그대로 유지해 보낸다. 순서는 서버가 보낸 순서를 보존한다
     * (parsePartyLinePrefs 가 LinkedHashSet 으로 만든다).
     *
     * @return PUT 몸체의 hidden_place_ids. prefs 가 null(미조회)이면 빈 목록.
     */
    fun keptHiddenPlaceIds(prefs: PartyLinePrefs?): List<String> =
        prefs?.hiddenPlaceIds?.toList().orEmpty()

    /**
     * [가족] 코드 줄 표시 조건 — 접속 중 가족 코드가 있을 때만 보인다(둘러보기 모드 등 코드가
     * 없는 상태에서는 줄 자체를 숨긴다). 표시 값은 원문이 아니라 마스킹(FamilyCodeMask.mask)이다.
     */
    fun familyCodeRowVisible(familyCode: String?): Boolean = !familyCode.isNullOrBlank()
}

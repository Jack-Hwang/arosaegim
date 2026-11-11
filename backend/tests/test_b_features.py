# 기획서 v10 B1~B5 신규 기능 테스트.
# B1 상속자 조회·지정(403/성공/재지정/404) / B2 설정(사람 비교 전체 접기)
# B3 장소 접기 / B4 visit_ordinal·family_total_trip_km(코스 1회 1합산) / B5 인물 이름표(markers).
import io
import json
import zipfile

from tests.conftest import make_jpeg

VISIT = {"place_id": "gj-museum", "course_id": "gyeongju-silla-01",
         "visited_at": "2026-11-10T10:00:00", "source": "manual"}


# ---------- B1: heir ----------

def test_heir_get_null_then_set(client, auth):
    """처음엔 상속자가 없다(member_id null). PUT으로 지정하면 조회에 id가 나온다."""
    assert client.get("/v1/families/me/heir", headers=auth).json() == {"member_id": None}
    client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth)  # 첫 구성원 = owner
    son = client.post("/v1/families/me/members", json={"nickname": "아들"}, headers=auth).json()
    r = client.put("/v1/families/me/heir", json={"member_id": son["id"]}, headers=auth)
    assert r.status_code == 200, r.text
    assert r.json() == {"member_id": son["id"]}  # 계약 응답은 member_id 하나
    assert client.get("/v1/families/me/heir", headers=auth).json() == {"member_id": son["id"]}

    # owner를 상속자로 지정하려는 시도는 400 already_owner (기존 규칙 유지)
    r = client.put("/v1/families/me/heir", json={"member_id": 1}, headers=auth)
    assert r.status_code == 400 and r.json()["error"]["code"] == "already_owner"


def test_heir_owner_auth_failure_is_403(client, family, auth):
    """owner 인증 실패(PIN 불일치)는 403 not_owner. PIN 헤더가 아예 없으면 401(미인증)."""
    son = client.post("/v1/families/me/members", json={"nickname": "아들"}, headers=auth).json()
    wrong = {"X-Family-Code": family["family_code"], "X-Family-Pin": "000000"}
    assert client.put("/v1/families/me/heir", json={"member_id": son["id"]}, headers=wrong).status_code == 403
    body = client.put("/v1/families/me/heir", json={"member_id": son["id"]}, headers=wrong).json()
    assert body["error"]["code"] == "not_owner"
    no_pin = {"X-Family-Code": family["family_code"]}
    r = client.put("/v1/families/me/heir", json={"member_id": son["id"]}, headers=no_pin)
    assert r.status_code == 401 and r.json()["error"]["code"] == "pin_required"
    # POST 경로도 같은 owner 인증 규칙을 따른다
    assert client.post("/v1/families/me/heir", json={"member_id": son["id"]}, headers=wrong).status_code == 403


def test_heir_reassign_to_another_member(client, auth):
    """재지정: 다른 구성원으로 바꾸면 이전 상속자는 member로 돌아가고 새 상속자만 heir다."""
    client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth)  # owner
    son = client.post("/v1/families/me/members", json={"nickname": "아들"}, headers=auth).json()
    daughter = client.post("/v1/families/me/members", json={"nickname": "딸"}, headers=auth).json()
    r1 = client.put("/v1/families/me/heir", json={"member_id": son["id"]}, headers=auth)
    assert r1.status_code == 200
    r2 = client.put("/v1/families/me/heir", json={"member_id": daughter["id"]}, headers=auth)
    assert r2.status_code == 200 and r2.json() == {"member_id": daughter["id"]}
    assert client.get("/v1/families/me/heir", headers=auth).json() == {"member_id": daughter["id"]}
    roles = {m["id"]: m["role"] for m in client.get("/v1/families/me/members", headers=auth).json()}
    assert roles[son["id"]] == "member" and roles[daughter["id"]] == "heir"


def test_heir_unknown_member_404(client, auth):
    client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth)  # owner 확보
    r = client.put("/v1/families/me/heir", json={"member_id": 99999}, headers=auth)
    assert r.status_code == 404 and r.json()["error"]["code"] == "member_not_found"


def test_heir_and_handover_without_members_is_400(client, auth):
    """감사 지적 S1: 구성원이 하나도 없는 가족에서 상속자 지정·관리 이전을
    요청하면 서버 오류(500)가 아니라 400 invalid_request 안내다.
    ('구성원을 먼저 등록해 주세요' — heir POST·PUT과 handover 네 경로 모두 동일 규칙)"""
    payload = {"member_id": 1}
    for method in ("put", "post"):
        r = getattr(client, method)("/v1/families/me/heir", json=payload, headers=auth)
        assert r.status_code == 400, r.text
        body = r.json()["error"]
        assert body["code"] == "invalid_request" and "구성원을 먼저 등록해 주세요" in body["message"]
    r = client.post("/v1/families/me/handover", json={"to_member_id": 1}, headers=auth)
    assert r.status_code == 400, r.text
    body = r.json()["error"]
    assert body["code"] == "invalid_request" and "구성원을 먼저 등록해 주세요" in body["message"]


# ---------- B2: settings ----------

def test_settings_fold_person_comparison_all(client, auth):
    """설정 저장(PUT) → then-now 응답의 fold_person_comparison_all이 그 값을 받는다.
    기본값은 false이고, 되돌리기도 가능하다."""
    client.post("/v1/visits", json=VISIT, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["fold_person_comparison_all"] is False

    r = client.put("/v1/families/me/settings", json={"fold_person_comparison_all": True}, headers=auth)
    assert r.status_code == 200 and r.json() == {"fold_person_comparison_all": True}
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["fold_person_comparison_all"] is True

    client.put("/v1/families/me/settings", json={"fold_person_comparison_all": False}, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["fold_person_comparison_all"] is False


def test_get_settings_returns_folded_places_too(client, auth):
    """GET /settings: 저장된 설정 + 현재 접힌 장소 목록을 함께 돌려준다.
    값이 없으면 false·빈 목록(정직한 기본값)."""
    r = client.get("/v1/families/me/settings", headers=auth)
    assert r.status_code == 200, r.text
    assert r.json() == {"fold_person_comparison_all": False, "folded_place_ids": []}

    client.put("/v1/families/me/settings", json={"fold_person_comparison_all": True}, headers=auth)
    client.post("/v1/families/me/folded-places", json={"place_id": "gj-museum"}, headers=auth)
    client.post("/v1/families/me/folded-places", json={"place_id": "gj-daereungwon"}, headers=auth)
    body = client.get("/v1/families/me/settings", headers=auth).json()
    assert body == {"fold_person_comparison_all": True,
                    "folded_place_ids": ["gj-museum", "gj-daereungwon"]}

    client.delete("/v1/families/me/folded-places/gj-museum", headers=auth)
    assert client.get("/v1/families/me/settings", headers=auth).json() == {
        "fold_person_comparison_all": True, "folded_place_ids": ["gj-daereungwon"]}


def test_settings_requires_pin(client, family):
    r = client.put("/v1/families/me/settings",
                   json={"fold_person_comparison_all": True},
                   headers={"X-Family-Code": family["family_code"]})
    assert r.status_code == 401 and r.json()["error"]["code"] == "pin_required"
    # GET도 같은 규칙 — 설정·접기 현황은 PIN이 있어야 본다
    r = client.get("/v1/families/me/settings",
                   headers={"X-Family-Code": family["family_code"]})
    assert r.status_code == 401 and r.json()["error"]["code"] == "pin_required"


# ---------- B3: folded places ----------

def test_folded_place_add_remove(client, auth):
    """접기 추가·해제. 응답은 항상 현재 접힌 장소 목록 전체다."""
    r = client.post("/v1/families/me/folded-places", json={"place_id": "gj-museum"}, headers=auth)
    assert r.status_code == 200 and r.json() == {"folded_place_ids": ["gj-museum"]}
    # 같은 장소를 다시 보내도 한 번만 남는다(멱등)
    r = client.post("/v1/families/me/folded-places", json={"place_id": "gj-museum"}, headers=auth)
    assert r.json() == {"folded_place_ids": ["gj-museum"]}
    client.post("/v1/families/me/folded-places", json={"place_id": "gj-daereungwon"}, headers=auth)

    r = client.delete("/v1/families/me/folded-places/gj-museum", headers=auth)
    assert r.status_code == 200 and r.json() == {"folded_place_ids": ["gj-daereungwon"]}
    # 접지 않은 장소를 풀어도 멱등하게 성공
    r = client.delete("/v1/families/me/folded-places/gj-museum", headers=auth)
    assert r.status_code == 200 and r.json() == {"folded_place_ids": ["gj-daereungwon"]}


def test_folded_unknown_place_404(client, auth):
    r = client.post("/v1/families/me/folded-places", json={"place_id": "nope"}, headers=auth)
    assert r.status_code == 404 and r.json()["error"]["code"] == "place_not_found"


def test_then_now_folded_flag(client, auth):
    """then-now 응답의 folded는 이 장소가 접혀 있는지를 그대로 반영한다."""
    client.post("/v1/visits", json=VISIT, headers=auth)
    assert client.get("/v1/places/gj-museum/then-now", headers=auth).json()["folded"] is False
    client.post("/v1/families/me/folded-places", json={"place_id": "gj-museum"}, headers=auth)
    assert client.get("/v1/places/gj-museum/then-now", headers=auth).json()["folded"] is True
    client.delete("/v1/families/me/folded-places/gj-museum", headers=auth)
    assert client.get("/v1/places/gj-museum/then-now", headers=auth).json()["folded"] is False


# ---------- B1~B3: then-now 확장 필드 ----------

def test_then_now_visit_ordinal_counts_per_place(client, auth):
    """visit_ordinal은 가족·장소 기준 방문 순번(1=첫 방문). past_visit_count는 이전 방문 수."""
    r1 = client.post("/v1/visits", json=VISIT, headers=auth)
    assert r1.json()["then_now"]["visit_ordinal"] == 1
    assert r1.json()["then_now"]["past_visit_count"] == 0

    r2 = client.post("/v1/visits", json={**VISIT, "visited_at": "2026-11-11T10:00:00"}, headers=auth)
    assert r2.json()["then_now"]["visit_ordinal"] == 2
    assert r2.json()["then_now"]["past_visit_count"] == 1

    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["visit_ordinal"] == 2 and body["past_visit_count"] == 1

    # 다른 장소는 별도 순번이다
    other = client.post(
        "/v1/visits", json={**VISIT, "place_id": "gj-daereungwon"}, headers=auth
    ).json()
    assert other["then_now"]["visit_ordinal"] == 1


def test_then_now_family_alias_path(client, auth):
    """계약 별칭 경로 GET /v1/families/me/then-now/{place_id}는
    GET /v1/places/{place_id}/then-now 와 같은 본문을 돌려준다."""
    client.post("/v1/visits", json=VISIT, headers=auth)
    a = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    b = client.get("/v1/families/me/then-now/gj-museum", headers=auth).json()
    assert a == b


def test_then_now_without_visits_is_honest(client, auth):
    """방문이 없어도 설정·접기 상태는 정직하게, 방문 순번은 null로 답한다."""
    client.put("/v1/families/me/settings", json={"fold_person_comparison_all": True}, headers=auth)
    client.post("/v1/families/me/folded-places", json={"place_id": "gj-museum"}, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["now"] is None and body["past"] is None
    assert body["visit_ordinal"] is None and body["past_visit_count"] == 0
    assert body["fold_person_comparison_all"] is True and body["folded"] is True


# ---------- B4: family_total_trip_km ----------

def test_metrics_total_trip_km_dedup_same_course_run(client, auth):
    """같은 코스 주행(같은 날)의 trip_km는 방문마다 중복 합산되지 않는다.
    같은 날 코스의 두 장소 방문이 각각 132.5km를 기록해도 합계는 132.5."""
    client.post("/v1/visits", json={**VISIT, "trip_km": 132.5}, headers=auth)
    client.post("/v1/visits", json={**VISIT, "place_id": "gj-daereungwon",
                                    "visited_at": "2026-11-10T11:00:00", "trip_km": 132.5},
                headers=auth)
    body = client.get("/v1/families/me/metrics", headers=auth).json()
    assert body["family_total_trip_km"] == 132.5

    # 다음 날 같은 코스를 다시 달리면(새 코스 주행) 그 거리는 더해진다.
    client.post("/v1/visits", json={**VISIT, "visited_at": "2026-11-11T10:00:00",
                                    "trip_km": 100.0}, headers=auth)
    body = client.get("/v1/families/me/metrics", headers=auth).json()
    assert body["family_total_trip_km"] == 232.5


def test_metrics_total_trip_km_respects_course_run_id(client, auth):
    """course_run_id를 보내면 서버 유도 규칙(같은 날=같은 주행) 대신 그 식별을 쓴다.
    같은 날이라도 다른 run이면 두 번의 주행으로 합산한다."""
    client.post("/v1/visits", json={**VISIT, "trip_km": 10.0, "course_run_id": "run-a"}, headers=auth)
    client.post("/v1/visits", json={**VISIT, "place_id": "gj-daereungwon",
                                    "visited_at": "2026-11-10T11:00:00",
                                    "trip_km": 10.0, "course_run_id": "run-a"}, headers=auth)
    client.post("/v1/visits", json={**VISIT, "place_id": "gj-cheomseongdae",
                                    "visited_at": "2026-11-10T12:00:00",
                                    "trip_km": 5.0, "course_run_id": "run-b"}, headers=auth)
    body = client.get("/v1/families/me/metrics", headers=auth).json()
    assert body["family_total_trip_km"] == 15.0


def test_metrics_total_trip_km_zero_without_trip_km(client, auth):
    """trip_km 기록이 없는 visit은 0 취급 — 총계도 0이다(값을 지어내지 않는다)."""
    client.post("/v1/visits", json=VISIT, headers=auth)
    body = client.get("/v1/families/me/metrics", headers=auth).json()
    assert body["family_total_trip_km"] == 0


# ---------- B5: markers ----------

MARKERS = [{"x": 0.25, "y": 0.5, "name": "아빠"}, {"x": 0.75, "y": 0.4, "name": "아들"}]


def _create_memory(client, auth, markers_json=None):
    data = {"place_id": "gj-museum", "year": "1994", "note": "그때 우리"}
    if markers_json is not None:
        data["markers"] = markers_json
    return client.post(
        "/v1/memories", data=data,
        files={"photo": ("m.jpg", make_jpeg(), "image/jpeg")}, headers=auth,
    )


def test_markers_saved_and_shown(client, auth):
    """생성 시 markers 저장 → 타임라인·then-now 과거(기억)에서 그대로 조회된다."""
    r = _create_memory(client, auth, json.dumps(MARKERS, ensure_ascii=False))
    assert r.status_code == 200, r.text

    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    mem = next(e for e in timeline if e["kind"] == "memory")
    assert mem["markers"] == MARKERS

    client.post("/v1/visits", json=VISIT, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["past"]["kind"] == "memory"
    assert body["past"]["markers"] == MARKERS


def test_markers_empty_by_default_and_replaceable(client, auth):
    """markers를 안 보내면 빈 목록. PUT으로 전체 교체(빈 배열이면 삭제)된다."""
    _create_memory(client, auth)
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    mem = next(e for e in timeline if e["kind"] == "memory")
    assert mem["markers"] == []

    r = client.put(f"/v1/families/me/memories/{mem['memory_id']}/markers",
                   json={"markers": [{"x": 0.1, "y": 0.2, "name": "엄마"}]}, headers=auth)
    assert r.status_code == 200
    assert r.json() == {"markers": [{"x": 0.1, "y": 0.2, "name": "엄마"}]}

    r = client.put(f"/v1/families/me/memories/{mem['memory_id']}/markers",
                   json={"markers": []}, headers=auth)
    assert r.status_code == 200 and r.json() == {"markers": []}
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    mem = next(e for e in timeline if e["kind"] == "memory")
    assert mem["markers"] == []


def test_markers_validation_and_ownership(client, auth):
    """좌표 범위 밖·빈 이름은 400 invalid_markers. 남의 기억 id는 404."""
    bad = _create_memory(client, auth, json.dumps([{"x": 1.5, "y": 0.2, "name": "아빠"}]))
    assert bad.status_code == 400 and bad.json()["error"]["code"] == "invalid_markers"
    bad2 = _create_memory(client, auth, "not-json")
    assert bad2.status_code == 400 and bad2.json()["error"]["code"] == "invalid_markers"
    bad3 = _create_memory(client, auth, json.dumps([{"x": 0.1, "y": 0.2, "name": ""}]))
    assert bad3.status_code == 400 and bad3.json()["error"]["code"] == "invalid_markers"

    r = client.put("/v1/families/me/memories/99999/markers",
                   json={"markers": MARKERS}, headers=auth)
    assert r.status_code == 404 and r.json()["error"]["code"] == "memory_not_found"


def test_markers_limit_is_10_on_update_and_create(client, auth):
    """감사 지적 S2: 이름표 상한은 등록·수정 경로 모두 10개(웹 안내 '최대 10명'과 통일).
    수정 경로(PUT)는 11개면 400 invalid_markers, 10개면 200이다.
    등록 경로(POST)도 11개를 400으로 거부한다."""
    _create_memory(client, auth)
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    mem = next(e for e in timeline if e["kind"] == "memory")
    url = f"/v1/families/me/memories/{mem['memory_id']}/markers"

    def _many(n: int) -> list:
        return [{"x": i / 100, "y": 0.5, "name": f"사람{i}"} for i in range(1, n + 1)]

    # 수정 경로: 11개는 초과 → 400 invalid_markers
    over = client.put(url, json={"markers": _many(11)}, headers=auth)
    assert over.status_code == 400, over.text
    err = over.json()["error"]
    assert err["code"] == "invalid_markers" and "최대 10명" in err["message"]
    # 수정 경로: 딱 10개는 허용 → 200
    ok = client.put(url, json={"markers": _many(10)}, headers=auth)
    assert ok.status_code == 200, ok.text
    assert len(ok.json()["markers"]) == 10

    # 등록 경로도 같은 상한(10)을 적용한다
    bad_create = _create_memory(client, auth, json.dumps(_many(11), ensure_ascii=False))
    assert bad_create.status_code == 400 and bad_create.json()["error"]["code"] == "invalid_markers"


def test_markers_removed_with_family_delete(client, auth):
    """가족 삭제 시 이름표도 함께 사라진다(새 가족·같은 memory id는 재사용되지 않으므로
    삭제 후 같은 가족 코드 조회가 404로 끝나는 것으로 흔적 소멸을 확인한다)."""
    _create_memory(client, auth, json.dumps(MARKERS, ensure_ascii=False))
    assert client.delete("/v1/families/me", headers=auth).status_code == 200
    assert client.get("/v1/families/me/timeline", headers=auth).status_code == 404


# ---------- export ----------

def test_export_contains_b1_b5_fields(client, auth):
    """내보내기 timeline.json: 방문에 visit_ordinal·trip_km, 상위에
    family_total_trip_km·heir, 기억에 markers."""
    owner = client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth).json()
    son = client.post("/v1/families/me/members", json={"nickname": "아들"}, headers=auth).json()
    client.put("/v1/families/me/heir", json={"member_id": son["id"]}, headers=auth)
    _ = owner  # owner는 첫 구성원

    _create_memory(client, auth, json.dumps(MARKERS, ensure_ascii=False))
    client.post("/v1/visits", json={**VISIT, "trip_km": 132.5}, headers=auth)

    r = client.get("/v1/families/me/export", headers=auth)
    assert r.status_code == 200
    zf = zipfile.ZipFile(io.BytesIO(r.content))
    doc = json.loads(zf.read("timeline.json").decode("utf-8"))
    assert doc["family_total_trip_km"] == 132.5
    assert doc["heir"]["member_id"] == son["id"] and doc["heir"]["nickname"] == "아들"

    visits = [e for e in doc["entries"] if e["kind"] == "visit"]
    memories = [e for e in doc["entries"] if e["kind"] == "memory"]
    assert visits[0]["visit_ordinal"] == 1 and visits[0]["trip_km"] == 132.5
    assert memories[0]["markers"] == MARKERS

    # 앨범 HTML에도 순번·거리·이름표가 나타난다
    html = zf.read("README.html").decode("utf-8")
    assert "132.5km" in html and "아빠" in html


def test_export_without_heir_or_trip_km_is_honest(client, auth):
    """상속자·거리가 없으면 null·0으로 정직하게 내보낸다."""
    client.post("/v1/visits", json=VISIT, headers=auth)
    r = client.get("/v1/families/me/export", headers=auth)
    assert r.status_code == 200
    doc = json.loads(zipfile.ZipFile(io.BytesIO(r.content)).read("timeline.json").decode("utf-8"))
    assert doc["heir"] is None and doc["family_total_trip_km"] == 0

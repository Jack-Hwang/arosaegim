# 기획서 v10 15절①(요구 문서 07의 2절 B4): 인원 비교 문장 접어 두기 가족 설정(prefs) 테스트.
# + 방문 순번(visit_ordinal)의 타임라인 반영과 family_total_trip_km 합계 재확인.
# B2(방문 순번)·B3(누적 거리) 자체 규칙은 test_b_features.py가 이미 검증하며,
# 이 파일은 계약 위치(then-now·타임라인·metrics·export)를 한 번에 훑는다.
import io
import json
import zipfile

VISIT = {"place_id": "gj-museum", "course_id": "gyeongju-silla-01",
         "visited_at": "2026-11-10T10:00:00", "source": "manual"}
INITIAL = {"party_line_hidden_places": [], "party_line_all_hidden": False}


# ---------- B4: prefs 초기값·조회 ----------

def test_prefs_initial_value_is_empty_and_false(client, auth):
    """저장한 적이 없으면 초기값: 빈 목록 + all_hidden=false(값을 지어내지 않는다)."""
    r = client.get("/v1/families/me/prefs", headers=auth)
    assert r.status_code == 200, r.text
    assert r.json() == INITIAL


# ---------- B4: prefs 저장(PUT=전체 교체) ----------

def test_prefs_put_then_get_roundtrip(client, auth):
    """PUT으로 장소 접기 목록·전체 끄기를 저장하면 GET에 그대로 나온다."""
    body = {"party_line_hidden_places": ["gj-museum", "gj-daereungwon"],
            "party_line_all_hidden": False}
    r = client.put("/v1/families/me/prefs", json=body, headers=auth)
    assert r.status_code == 200, r.text
    assert r.json() == body
    assert client.get("/v1/families/me/prefs", headers=auth).json() == body

    # 모든 장소 한 번에 끄기(15절 약속): 새 장소마다 같은 문장을 다시 접지 않게 하는 값.
    r = client.put("/v1/families/me/prefs",
                   json={"party_line_hidden_places": [], "party_line_all_hidden": True},
                   headers=auth)
    assert r.status_code == 200 and r.json()["party_line_all_hidden"] is True
    assert client.get("/v1/families/me/prefs", headers=auth).json() == {
        "party_line_hidden_places": [], "party_line_all_hidden": True}


def test_prefs_put_replaces_whole_body(client, auth):
    """PUT은 전체 교체다: 목록을 빼고 보내면 빈 목록으로 저장된다(부분 갱신 아님 — 계약 명시)."""
    client.put("/v1/families/me/prefs",
               json={"party_line_hidden_places": ["gj-museum"], "party_line_all_hidden": False},
               headers=auth)
    r = client.put("/v1/families/me/prefs", json={"party_line_all_hidden": True}, headers=auth)
    assert r.status_code == 200
    assert r.json() == {"party_line_hidden_places": [], "party_line_all_hidden": True}


def test_prefs_post_alias_same_as_put(client, auth):
    """PUT 미지원 환경을 위한 POST 별칭: PUT과 동일 동작·동일 응답(heir 경로와 같은 패턴)."""
    body = {"party_line_hidden_places": ["gj-cheomseongdae"], "party_line_all_hidden": True}
    r = client.post("/v1/families/me/prefs", json=body, headers=auth)
    assert r.status_code == 200 and r.json() == body
    assert client.get("/v1/families/me/prefs", headers=auth).json() == body


def test_prefs_dedupes_and_keeps_order(client, auth):
    """같은 장소 id를 다시 보내면 한 번만 남고(멱등), 처음 보낸 순서가 유지된다."""
    r = client.put("/v1/families/me/prefs",
                   json={"party_line_hidden_places": ["gj-museum", "gj-museum", "gj-daereungwon"],
                         "party_line_all_hidden": False}, headers=auth)
    assert r.status_code == 200
    assert r.json()["party_line_hidden_places"] == ["gj-museum", "gj-daereungwon"]


def test_prefs_rejects_bad_payload(client, auth):
    """스키마 밖 필드는 400 invalid_request(extra=forbid). 빈 place_id도 거부한다."""
    r = client.put("/v1/families/me/prefs",
                   json={**INITIAL, "fold_person_comparison_all": True}, headers=auth)
    assert r.status_code == 400 and r.json()["error"]["code"] == "invalid_request"
    r = client.put("/v1/families/me/prefs",
                   json={"party_line_hidden_places": ["  "], "party_line_all_hidden": False},
                   headers=auth)
    assert r.status_code == 400 and r.json()["error"]["code"] == "invalid_request"


# ---------- B4: prefs는 PIN이 필요하다 ----------

def test_prefs_requires_pin(client, family):
    """settings와 같은 규칙: 조회·저장 모두 PIN 없이는 401 pin_required, 틀리면 401 invalid_pin."""
    code = {"X-Family-Code": family["family_code"]}
    r = client.get("/v1/families/me/prefs", headers=code)
    assert r.status_code == 401 and r.json()["error"]["code"] == "pin_required"
    for method in ("put", "post"):
        r = getattr(client, method)("/v1/families/me/prefs", json=INITIAL, headers=code)
        assert r.status_code == 401, r.text
        assert r.json()["error"]["code"] == "pin_required"
    wrong = {"X-Family-Code": family["family_code"], "X-Family-Pin": "000000"}
    r = client.get("/v1/families/me/prefs", headers=wrong)
    assert r.status_code == 401 and r.json()["error"]["code"] == "invalid_pin"


def test_prefs_survives_code_rotation_and_family_delete(client, auth):
    """코드 재발급 시 prefs도 새 코드로 옮겨진다(데이터 유지). 가족 삭제 시에는 함께 사라진다."""
    client.put("/v1/families/me/prefs",
               json={"party_line_hidden_places": ["gj-museum"], "party_line_all_hidden": True},
               headers=auth)
    new = client.post("/v1/families/me/rotate-code", headers=auth).json()
    new_auth = {"X-Family-Code": new["family_code"], "X-Family-Pin": new["pin"]}
    assert client.get("/v1/families/me/prefs", headers=new_auth).json() == {
        "party_line_hidden_places": ["gj-museum"], "party_line_all_hidden": True}

    assert client.delete("/v1/families/me", headers=new_auth).status_code == 200
    assert client.get("/v1/families/me/prefs", headers=new_auth).status_code == 404


# ---------- B2 확인: visit_ordinal이 then-now·타임라인 양쪽에 나온다 ----------

def test_visit_ordinal_in_then_now_and_timeline(client, auth):
    """첫 방문 1 → 두 번째 방문 2. 방문 등록 응답(then_now.visit_ordinal)·
    then-now GET·타임라인 항목 모두 같은 순번을 말한다."""
    r1 = client.post("/v1/visits", json=VISIT, headers=auth)
    assert r1.json()["then_now"]["visit_ordinal"] == 1
    r2 = client.post("/v1/visits", json={**VISIT, "visited_at": "2026-11-11T10:00:00"}, headers=auth)
    assert r2.json()["then_now"]["visit_ordinal"] == 2

    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["visit_ordinal"] == 2  # then-now는 최신 방문 기준

    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    visits = [e for e in timeline if e["kind"] == "visit"]
    by_ordinal = {e["visit_ordinal"]: e["visited_at"][:10] for e in visits}
    assert by_ordinal == {1: "2026-11-10", 2: "2026-11-11"}  # 양쪽 항목에 순번이 있다


# ---------- B3 확인: family_total_trip_km 합계(중복 합산 금지) ----------

def test_family_total_trip_km_in_metrics_and_export(client, auth):
    """같은 코스 주행(같은 날)의 trip_km는 한 번만 합산하고, 다른 날 주행은 더한다.
    metrics와 내보내기 timeline.json의 값이 일치해야 한다."""
    client.post("/v1/visits", json={**VISIT, "trip_km": 132.5, "party_size": 4}, headers=auth)
    client.post("/v1/visits", json={**VISIT, "place_id": "gj-daereungwon",
                                    "visited_at": "2026-11-10T11:00:00",
                                    "trip_km": 132.5, "party_size": 3}, headers=auth)
    client.post("/v1/visits", json={**VISIT, "visited_at": "2026-11-12T10:00:00",
                                    "trip_km": 100.0}, headers=auth)

    body = client.get("/v1/families/me/metrics", headers=auth).json()
    assert body["family_total_trip_km"] == 232.5  # 132.5(11일 주행 1회) + 100.0(12일 주행)

    r = client.get("/v1/families/me/export", headers=auth)
    assert r.status_code == 200
    doc = json.loads(zipfile.ZipFile(io.BytesIO(r.content)).read("timeline.json").decode("utf-8"))
    assert doc["family_total_trip_km"] == 232.5

    visits = {e["place_id"]: e for e in doc["entries"] if e["kind"] == "visit"}
    # 내보내기 항목: visit_ordinal·trip_km·party_size가 모두 나온다(15절·10절 확인).
    assert visits["gj-museum"]["visit_ordinal"] == 1
    assert visits["gj-museum"]["trip_km"] == 132.5 and visits["gj-museum"]["party_size"] == 4
    assert visits["gj-daereungwon"]["visit_ordinal"] == 1  # 장소별 별개 순번
    assert visits["gj-daereungwon"]["party_size"] == 3

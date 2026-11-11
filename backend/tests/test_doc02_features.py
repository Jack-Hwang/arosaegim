# 02문서 10·11절 신규 기능 테스트.
# - POST /v1/visits: party_size·trip_km 수용
# - then-now 확장: past.party_size / now.party_size·trip_km / heritage
# - GET /v1/places/{id}/voice-letters: 시드 예시 메타데이터
# - GET /v1/families/me/metrics: 값 + 출처 필드
# - food_notes kind / members seat_role
from tests.conftest import make_jpeg, make_unique_png

VISIT = {"place_id": "gj-museum", "course_id": "gyeongju-silla-01",
         "visited_at": "2026-11-10T10:00:00", "source": "manual"}


def test_visit_accepts_party_size_and_trip_km(client, auth):
    """02문서 10절: 방문 요청의 party_size·trip_km(선택)를 저장하고 응답·then-now에 실어 보낸다."""
    r = client.post("/v1/visits", json={**VISIT, "party_size": 4, "trip_km": 132.5}, headers=auth)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["party_size"] == 4
    assert body["trip_km"] == 132.5
    assert body["then_now"]["now"]["party_size"] == 4
    assert body["then_now"]["now"]["trip_km"] == 132.5

    # 필드 생략은 null로 저장·응답된다.
    r2 = client.post("/v1/visits", json={**VISIT, "visited_at": "2026-11-11T10:00:00"}, headers=auth)
    assert r2.status_code == 200
    assert r2.json()["party_size"] is None


def test_then_now_heritage_and_party_size(client, auth):
    """then-now 응답 확장(02문서 10절):
    heritage는 장소 시드 값, past.party_size는 과거 기억의 값."""
    # 과거 기억(1994년, 함께 간 인원 5명)
    client.post(
        "/v1/memories",
        data={"place_id": "gj-museum", "year": "1994", "note": "아버지와", "party_size": "5"},
        files={"photo": ("m.jpg", make_jpeg(), "image/jpeg")},
        headers=auth,
    )
    client.post("/v1/visits", json={**VISIT, "party_size": 3, "trip_km": 100.0}, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["past"]["party_size"] == 5
    assert body["now"]["party_size"] == 3
    assert body["now"]["trip_km"] == 100.0
    h = body["heritage"]
    assert h is not None and h["name"] and isinstance(h["year"], int)  # 시드 heritage


def test_then_now_heritage_for_daereungwon_and_cheomseongdae(client, auth):
    """감사 지적 S3 보강: 대릉원·첨성대에도 시드 heritage가 있어 then-now 응답에
    {name, year, label} 요약이 실린다(국립경주박물관과 같은 구조).
    학설이 갈리는 표현은 단정하지 않게 쓰고 reviewed=false(초안)를 유지한다."""
    dae = {"place_id": "gj-daereungwon", "course_id": "gyeongju-silla-01",
           "visited_at": "2026-11-10T11:00:00", "source": "manual"}
    cheom = {**dae, "place_id": "gj-cheomseongdae", "visited_at": "2026-11-10T12:00:00"}

    client.post("/v1/visits", json=dae, headers=auth)
    client.post("/v1/visits", json=cheom, headers=auth)

    body = client.get("/v1/places/gj-daereungwon/then-now", headers=auth).json()
    h = body["heritage"]
    assert h == {"name": "천마총", "year": 650, "label": "1,300여 년"}

    body = client.get("/v1/places/gj-cheomseongdae/then-now", headers=auth).json()
    h = body["heritage"]
    assert h == {"name": "첨성대", "year": 632, "label": "1,350여 년"}

    # 장소 상세 응답에서도 같은 유산 정보가 note·reviewed=false(초안 표시 대상)로 나온다
    detail = client.get("/v1/places/gj-cheomseongdae", headers=auth).json()
    dh = detail["heritage"]
    assert dh["name"] == "첨성대" and dh["year"] == 632 and dh["reviewed"] is False
    assert "학설이 갈린다" in dh["note"]


def test_then_now_heritage_null_for_place_without_heritage():
    """유산 정보가 없는 장소는 heritage가 null이어야 한다(순수 함수 단위)."""
    from app.services import then_now as svc

    body = svc.resolve_then_now(
        memories=[], earlier_visits=[], current_year=2026,
        now_visit={"place_id": "x", "visited_at": "2026-11-10T10:00:00",
                   "vehicle_label": None, "party_size": 2, "trip_km": None},
        heritage=None,
    )
    assert body["heritage"] is None
    assert body["now"]["party_size"] == 2


def test_voice_letters_returns_seed_demo(client):
    """GET /v1/places/{id}/voice-letters (02문서 10절): 시드 예시 편지의
    {id, sealed_label, simulated, audio_url} 메타데이터만 돌려준다(업로드 API는 로드맵)."""
    r = client.get("/v1/places/gj-museum/voice-letters")
    assert r.status_code == 200, r.text
    letters = r.json()
    assert len(letters) == 1
    letter = letters[0]
    assert letter["id"] == "letter-demo-1"
    assert letter["sealed_label"]
    assert letter["simulated"] is True
    assert letter["audio_url"]  # 앱 내장 시드 에셋 경로

    # 예시가 없는 장소는 빈 목록, 없는 장소는 404.
    assert client.get("/v1/places/gj-daereungwon/voice-letters").json() == []
    assert client.get("/v1/places/nope/voice-letters").status_code == 404


def test_metrics_values_and_sources(client, auth):
    """GET /v1/families/me/metrics (02문서 10절): 각 지표가 값+출처 필드를 가진다.
    방문 2회(재방문 1쌍, 간격 1일), 영수증 인증 1건, 상속자 지정 → 값 검증."""
    client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth)
    son = client.post("/v1/families/me/members", json={"nickname": "아들"}, headers=auth).json()
    client.post("/v1/families/me/heir", json={"member_id": son["id"]}, headers=auth)

    client.post("/v1/visits", json={**VISIT}, headers=auth)
    client.post(
        "/v1/visits",
        json={**VISIT, "visited_at": "2026-11-11T10:00:00"},  # 같은 장소 재방문(간격 1일)
        headers=auth,
    )
    r = client.post(
        "/v1/receipts",
        data={"partner_id": "p-demo-museumshop", "amount_krw": "20000", "paid_at": "2026-11-10T12:00:00"},
        files={"photo": ("r.jpg", make_unique_png(), "image/jpeg")},
        headers=auth,
    )
    assert r.json()["status"] == "accepted"

    body = client.get("/v1/families/me/metrics", headers=auth).json()
    # 스탬프북: gj-museum만 방문 → 1권 중 0권 완성
    assert body["stamp_books"]["total"] == 1
    assert body["stamp_books"]["completed"] == 0
    assert body["stamp_books"]["rate_pct"] == 0
    assert body["stamp_books"]["source"]
    # 그때와 지금 열람 수: visits 수로 근사(주석 정의)
    assert body["then_now_views"]["count"] == 2
    assert body["then_now_views"]["approx"] is True
    # 재방문 간격: 1일
    assert body["revisit_gap_days"]["avg_days"] == 1.0
    assert body["revisit_gap_days"]["pairs"] == 1
    # 영수증 인증 수: accepted 1건
    assert body["receipts_accepted"]["count"] == 1
    # 물려주기 지정 여부
    assert body["heir"]["designated"] is True


def test_metrics_revisit_gap_null_when_no_repeat(client, auth):
    """재방문이 없으면 재방문 간격은 null이다."""
    client.post("/v1/visits", json=VISIT, headers=auth)
    body = client.get("/v1/families/me/metrics", headers=auth).json()
    assert body["revisit_gap_days"]["avg_days"] is None


def test_metrics_requires_pin(client, family):
    """metrics는 열람·수정 성격의 집계이므로 PIN이 필요하다."""
    r = client.get("/v1/families/me/metrics", headers={"X-Family-Code": family["family_code"]})
    assert r.status_code == 401
    assert r.json()["error"]["code"] == "pin_required"


def test_family_code_device_token_daily_limit(client, family):
    """가족 코드 생성 제한 (02문서 11절): 같은 X-Device-Token으로 하루 1회,
    2회째는 429 device_limit. 다른 토큰은 만들 수 있다."""
    ok = client.post("/v1/families", json={}, headers={"X-Device-Token": "device-A"})
    assert ok.status_code == 200, ok.text
    dup = client.post("/v1/families", json={}, headers={"X-Device-Token": "device-A"})
    assert dup.status_code == 429
    assert dup.json()["error"]["code"] == "device_limit"
    other = client.post("/v1/families", json={}, headers={"X-Device-Token": "device-B"})
    assert other.status_code == 200


def test_food_note_kind_explicit_and_inherited(client, auth):
    """food_notes kind (02문서 11절): 명시 지정 가능, 없으면 영수증 제휴처 kind 상속.
    잘못된 kind는 400 invalid_kind."""
    visit = client.post("/v1/visits", json=VISIT, headers=auth).json()
    client.post(
        "/v1/receipts",
        data={"partner_id": "p-demo-museumshop", "amount_krw": "10000", "paid_at": "2026-11-10T12:00:00"},
        files={"photo": ("r.jpg", make_unique_png(), "image/jpeg")},
        headers=auth,
    )
    # 명시 지정
    r1 = client.post(
        "/v1/families/me/food-notes",
        data={"visit_id": str(visit["visit_id"]), "name": "숙소", "kind": "lodging"},
        headers=auth,
    )
    assert r1.status_code == 200
    # 미지정 → 이 방문의 영수증 제휴처 kind(museum_shop) 상속
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    notes = timeline[0]["food_notes"]
    kinds = {n["name"]: n["kind"] for n in notes}
    assert kinds["숙소"] == "lodging"
    client.post(
        "/v1/families/me/food-notes",
        data={"visit_id": str(visit["visit_id"]), "name": "기념품"},
        headers=auth,
    )
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    kinds = {n["name"]: n["kind"] for n in timeline[0]["food_notes"]}
    assert kinds["기념품"] == "museum_shop"

    # 허용 목록 밖
    bad = client.post(
        "/v1/families/me/food-notes",
        data={"visit_id": str(visit["visit_id"]), "name": "x", "kind": "cafe"},
        headers=auth,
    )
    assert bad.status_code == 400
    assert bad.json()["error"]["code"] == "invalid_kind"


def test_member_seat_role(client, auth):
    """members.seat_role (02문서 10절): driver/navigator/explorer(선택)를 저장·응답한다.
    목록 밖 값은 400으로 거부된다."""
    m = client.post(
        "/v1/families/me/members",
        json={"nickname": "운전자", "seat_role": "driver"},
        headers=auth,
    ).json()
    assert m["seat_role"] == "driver"
    members = client.get("/v1/families/me/members", headers=auth).json()
    assert members[0]["seat_role"] == "driver"

    bad = client.post(
        "/v1/families/me/members",
        json={"nickname": "x", "seat_role": "captain"},
        headers=auth,
    )
    assert bad.status_code == 400

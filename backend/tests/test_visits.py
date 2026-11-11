# 방문 기록 API 테스트 — 멱등(같은 분 = 1건)과 스탬프 번호.
VISIT = {
    "place_id": "gj-museum",
    "course_id": "gyeongju-silla-01",
    "visited_at": "2026-11-10T14:30:00",
    "source": "manual",
}


def test_same_minute_is_idempotent(client, auth):
    """같은 분(초 무시)의 재요청은 같은 visit을 돌려준다 — 중복 스탬프 방지(02문서 6-2)."""
    r1 = client.post("/v1/visits", json=VISIT, headers=auth)
    assert r1.status_code == 200, r1.text
    r2 = client.post(
        "/v1/visits", json={**VISIT, "visited_at": "2026-11-10T14:30:59"}, headers=auth
    )
    assert r2.status_code == 200
    assert r2.json()["visit_id"] == r1.json()["visit_id"]
    assert r2.json()["stamp_no"] == 1

    # 타임라인에도 1건만 있다
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    assert len(timeline) == 1


def test_different_minute_creates_new_visit(client, auth):
    r1 = client.post("/v1/visits", json=VISIT, headers=auth)
    r2 = client.post(
        "/v1/visits", json={**VISIT, "visited_at": "2026-11-10T14:31:00"}, headers=auth
    )
    assert r2.json()["visit_id"] != r1.json()["visit_id"]
    assert r2.json()["stamp_no"] == 2  # 가족 스탬프는 순차 부여


def test_first_visit_then_now_has_no_past(client, auth):
    """과거 기록이 없으면 past는 null이다(02문서 6-2)."""
    r = client.post("/v1/visits", json=VISIT, headers=auth)
    body = r.json()
    assert body["then_now"]["past"] is None
    assert body["then_now"]["now"]["visited_at"].startswith("2026-11-10T14:30")


def test_invalid_source_rejected(client, auth):
    """source는 fused/navi/manual만 허용하고, 스키마 밖 필드는 400으로 거부한다.
    (설계 결정: FastAPI 기본 422 대신 모든 요청 검증 실패를 400 invalid_request로 통일)"""
    assert (
        client.post(
            "/v1/visits", json={**VISIT, "source": "gps"}, headers=auth
        ).status_code
        == 400
    )
    # 실시간 위치(lat/lon) 같은 스키마 밖 필드(extra="forbid")도 받지 않는다
    assert (
        client.post(
            "/v1/visits", json={**VISIT, "lat": 35.8, "lon": 129.2}, headers=auth
        ).status_code
        == 400
    )

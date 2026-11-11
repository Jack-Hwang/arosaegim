# '그때와 지금' 규칙 테스트 (02문서 6-2·8절).
# 과거 없음 / 기억 있음 / 기억 여러 개 → 가장 오래된 것 / 기억 없으면 이전 방문.
from tests.conftest import make_jpeg

NOW_VISIT = {
    "place_id": "gj-museum",
    "course_id": "gyeongju-silla-01",
    "visited_at": "2026-11-10T10:00:00",
    "source": "manual",
}


def _add_memory(client, auth, year: int, note="그때 기억"):
    r = client.post(
        "/v1/memories",
        data={"place_id": "gj-museum", "year": str(year), "note": note},
        files={"photo": ("upload.jpg", make_jpeg(), "image/jpeg")},
        headers=auth,
    )
    assert r.status_code == 200, r.text


def test_no_past_returns_null(client, auth):
    """과거 기록이 전혀 없으면 past=null."""
    client.post("/v1/visits", json=NOW_VISIT, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["past"] is None
    assert body["now"]["visited_at"].startswith("2026-11-10")


def test_memory_becomes_past_with_server_years_ago(client, auth):
    """기억이 있으면 past가 되고, years_ago는 서버가 계산한다(현재 연도 - 기억 연도)."""
    _add_memory(client, auth, 1994, "아버지 차로 처음 온 경주")
    client.post("/v1/visits", json=NOW_VISIT, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["past"]["kind"] == "memory"
    assert body["past"]["year"] == 1994
    assert body["past"]["years_ago"] == 2026 - 1994  # 서버 계산값
    assert "아버지 차로 처음 온 경주" in body["past"]["note"]
    assert body["past"]["photo_url"].startswith("/uploads/")


def test_multiple_memories_pick_oldest(client, auth):
    """기억이 여러 개면 가장 오래된 것을 고른다(02문서 8절)."""
    _add_memory(client, auth, 1996, "최근 쪽")
    _add_memory(client, auth, 1990, "가장 오래된 쪽")
    client.post("/v1/visits", json=NOW_VISIT, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["past"]["year"] == 1990
    assert body["past"]["years_ago"] == 2026 - 1990


def test_no_memory_falls_back_to_earlier_visit(client, auth):
    """기억이 없으면 이전 방문이 past가 된다. years_ago는 두 방문의 연도 차."""
    client.post(
        "/v1/visits", json={**NOW_VISIT, "visited_at": "1998-05-01T09:00:00"}, headers=auth
    )
    client.post("/v1/visits", json=NOW_VISIT, headers=auth)
    body = client.get("/v1/places/gj-museum/then-now", headers=auth).json()
    assert body["past"]["kind"] == "visit"
    assert body["past"]["year"] == 1998
    assert body["past"]["years_ago"] == 2026 - 1998

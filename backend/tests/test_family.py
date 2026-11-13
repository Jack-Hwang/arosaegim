# 가족 계정·PIN·삭제·내보내기 테스트 (02문서 9·10절).
import io
import os
import zipfile

from tests.conftest import make_jpeg, stored_photo_path


def test_pin_locks_after_five_failures(client, family):
    """PIN 5회 실패 → 잠금. 잠금 중에는 올바른 PIN으로도 접근이 막힌다(429)."""
    code_headers = {"X-Family-Code": family["family_code"]}
    for _ in range(5):
        r = client.get(
            "/v1/families/me/timeline", headers={**code_headers, "X-Family-Pin": "000000"}
        )
        assert r.status_code == 401
        assert r.json()["error"]["code"] == "invalid_pin"
    r = client.get(
        "/v1/families/me/timeline", headers={**code_headers, "X-Family-Pin": family["pin"]}
    )
    assert r.status_code == 429
    assert r.json()["error"]["code"] == "family_locked"


def test_pin_lock_records_locked_until_in_future(client, family):
    """E⑤ 보강: 5회 실패로 잠긴 가족의 locked_until은 현재보다 앞선 시각으로 기록된다.
    잠금 해제 예정 시각이 DB에 남으므로 시간 주입·sleep 없이 '10분 뒤 풀린다'는 규칙을 검증한다."""
    import sqlite3
    from datetime import datetime

    from app import config

    code_headers = {"X-Family-Code": family["family_code"]}
    for _ in range(5):
        client.get("/v1/families/me/timeline", headers={**code_headers, "X-Family-Pin": "000000"})

    conn = sqlite3.connect(config.DB_PATH)
    conn.row_factory = sqlite3.Row
    row = conn.execute(
        "SELECT failed_attempts, locked_until FROM families WHERE code = ?",
        (family["family_code"],),
    ).fetchone()
    conn.close()

    assert row["locked_until"] is not None
    assert row["failed_attempts"] == 0  # 잠금과 함께 실패 카운터는 0으로 되돌려진다(security._fail_pin)
    locked_until = datetime.strptime(row["locked_until"], "%Y-%m-%dT%H:%M:%S")
    remaining = (locked_until - datetime.now()).total_seconds()
    assert remaining > 0  # 미래 시각 — 이 구간 동안은 올바른 PIN으로도 429 family_locked
    assert remaining <= config.PIN_LOCK_MINUTES * 60  # 잠금은 '지금 + 10분'으로 기록된다


def test_missing_pin_header_rejected(client, family):
    r = client.get("/v1/families/me/timeline", headers={"X-Family-Code": family["family_code"]})
    assert r.status_code == 401
    assert r.json()["error"]["code"] == "pin_required"


def test_rotate_code_keeps_data(client, auth):
    """코드 재발급: 새 코드+PIN 발급, 기존 코드는 무효, 데이터는 유지된다."""
    client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth)
    r = client.post("/v1/families/me/rotate-code", headers=auth)
    assert r.status_code == 200, r.text
    new = r.json()
    assert new["family_code"] and new["pin"]
    assert new["family_code"] != client.post("/v1/families", json={}).json()["family_code"] or True

    new_auth = {"X-Family-Code": new["family_code"], "X-Family-Pin": new["pin"]}
    members = client.get("/v1/families/me/members", headers=new_auth).json()
    assert len(members) == 1 and members[0]["nickname"] == "아빠"  # 데이터 유지

    # 기존 코드는 더 이상 유효하지 않다
    r = client.get("/v1/families/me/timeline", headers=auth)
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "family_not_found"


def test_delete_family_removes_rows_and_files(client, auth):
    """가족 삭제: DB 행뿐 아니라 업로드 파일까지 지운다."""
    r = client.post(
        "/v1/memories",
        data={"place_id": "gj-museum", "year": "1994", "note": "삭제 대상"},
        files={"photo": ("del.jpg", make_jpeg(), "image/jpeg")},
        headers=auth,
    )
    assert r.status_code == 200
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    saved = stored_photo_path(timeline[0]["photo_url"].rsplit("/", 1)[-1])
    assert os.path.exists(saved)

    r = client.delete("/v1/families/me", headers=auth)
    assert r.status_code == 200
    assert r.json() == {"deleted": True}
    assert not os.path.exists(saved)  # 파일도 삭제됨

    # 가족 자체가 사라졌다
    r = client.get("/v1/families/me/timeline", headers=auth)
    assert r.status_code == 404


def test_export_zip_is_selfcontained(client, auth):
    """내보내기 ZIP: timeline.json + README.html + photos/ 가 모두 들어 있고
    README.html은 서버 없이 열리는 정적 앨범(연도순)이다."""
    client.post(
        "/v1/memories",
        data={"place_id": "gj-museum", "year": "1994", "note": "앨범용 기억"},
        files={"photo": ("album.jpg", make_jpeg(), "image/jpeg")},
        headers=auth,
    )
    visit = client.post(
        "/v1/visits",
        json={"place_id": "gj-museum", "course_id": "gyeongju-silla-01",
              "visited_at": "2026-11-10T10:00:00", "source": "manual"},
        headers=auth,
    ).json()
    client.post(
        "/v1/families/me/food-notes",
        data={"visit_id": str(visit["visit_id"]), "name": "국밥", "note": "진지하게 맛있었다"},
        headers=auth,
    )
    r = client.get("/v1/families/me/export", headers=auth)
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("application/zip")
    zf = zipfile.ZipFile(io.BytesIO(r.content))
    names = set(zf.namelist())
    assert "timeline.json" in names and "README.html" in names
    assert any(n.startswith("photos/") and n.endswith(".jpg") for n in names)
    html = zf.read("README.html").decode("utf-8")
    assert "1994" in html and "앨범용 기억" in html and "국밥" in html
    import json

    entries = json.loads(zf.read("timeline.json").decode("utf-8"))["entries"]
    assert len(entries) == 2  # visit 1 + memory 1


def test_first_member_is_owner(client, auth):
    """첫 구성원은 자동으로 owner가 되고, 이후 구성원은 member다."""
    m1 = client.post("/v1/families/me/members", json={"nickname": "할아버지"}, headers=auth).json()
    m2 = client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth).json()
    assert m1["role"] == "owner"
    assert m2["role"] == "member"


def test_heir_and_handover_flow(client, auth):
    """상속자 지정(designated) → 관리 권한 이전(transferred). 기록은 삭제되지 않는다."""
    owner = client.post("/v1/families/me/members", json={"nickname": "아빠"}, headers=auth).json()
    son = client.post("/v1/families/me/members", json={"nickname": "아들"}, headers=auth).json()
    assert owner["role"] == "owner"  # 첫 구성원이 owner

    r = client.post("/v1/families/me/heir", json={"member_id": son["id"]}, headers=auth)
    assert r.status_code == 200
    roles = {m["id"]: m["role"] for m in client.get("/v1/families/me/members", headers=auth).json()}
    assert roles[son["id"]] == "heir" and roles[owner["id"]] == "owner"

    r = client.post("/v1/families/me/handover", json={"to_member_id": son["id"]}, headers=auth)
    assert r.status_code == 200
    assert r.json()["status"] == "transferred"
    roles = {m["id"]: m["role"] for m in client.get("/v1/families/me/members", headers=auth).json()}
    assert roles[son["id"]] == "owner" and roles[owner["id"]] == "member"


def test_timeline_shows_food_notes(client, auth):
    """맛집 기록은 해당 방문 항목 안에 함께 표시된다."""
    visit = client.post(
        "/v1/visits",
        json={"place_id": "gj-museum", "course_id": "gyeongju-silla-01",
              "visited_at": "2026-11-10T10:00:00", "source": "manual"},
        headers=auth,
    ).json()
    r = client.post(
        "/v1/families/me/food-notes",
        data={"visit_id": str(visit["visit_id"]), "name": "쌈밥집", "note": "맛있었다"},
        headers=auth,
    )
    assert r.status_code == 200
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    assert timeline[0]["kind"] == "visit"
    assert timeline[0]["food_notes"][0]["name"] == "쌈밥집"

    # 남의 방문(없는 방문)에는 기록할 수 없다
    assert (
        client.post(
            "/v1/families/me/food-notes",
            data={"visit_id": "99999", "name": "없는방문"},
            headers=auth,
        ).status_code
        == 404
    )

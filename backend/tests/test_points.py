# 영수증 적립 규칙 테스트 (02문서 6-2·11절 — services/points.py).
# 사진은 필수(11절)이고, 같은 사진 재제출은 거부되므로 영수증마다 make_unique_png()를 쓴다.
# 정상(floor) / no_visit / duplicate / daily_limit / 경계 금액 + 순수 함수 단위 검증.
import sqlite3

from app import config
from tests.conftest import make_png, make_unique_png

VISIT_DAY = "2026-11-10"


def _visit_near(client, auth, place_id="gj-daereungwon", day=VISIT_DAY):
    r = client.post(
        "/v1/visits",
        json={"place_id": place_id, "course_id": "gyeongju-silla-01",
              "visited_at": f"{day}T10:00:00", "source": "manual"},
        headers=auth,
    )
    assert r.status_code == 200, r.text


def _receipt(client, auth, partner_id="p-demo-ssambap", amount=50000, day=VISIT_DAY):
    """영수증 제출. 사진 필수 — 매번 다른 사진을 자동으로 붙인다(재제출 차단 회피)."""
    return client.post(
        "/v1/receipts",
        data={"partner_id": partner_id, "amount_krw": str(amount), "paid_at": f"{day}T12:30:00"},
        files={"photo": ("r.jpg", make_unique_png(), "image/jpeg")},
        headers=auth,
    )


def test_accepted_points_are_floored(client, auth):
    """정상: 50,000원 × 0.02 = 1,000점. point_ledger까지 반영됐는지 확인.
    11절: 응답에 label "포인트 캐시백(예시)"가 붙는다."""
    _visit_near(client, auth)
    r = _receipt(client, auth, amount=50000)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["status"] == "accepted"
    assert body["points"] == 1000
    assert body["simulated"] is True  # 적립 응답에는 항상 simulated 플래그가 붙는다
    assert body["label"] == "포인트 캐시백(예시)"  # 현금 환급 문구 금지 표기

    points = client.get("/v1/families/me/points", headers=auth).json()
    assert points["total"] == 1000
    assert points["label"] == "포인트 캐시백(예시)"
    assert points["entries"][0]["points"] == 1000


def test_no_visit_same_day_rejected(client, auth):
    """식당: 그날(또는 다음 날) near_place_id 방문이 없으면 rejected(no_visit)."""
    _visit_near(client, auth, day="2026-11-08")
    body = _receipt(client, auth, day="2026-11-10").json()
    assert body["status"] == "rejected"
    assert body["reason"] == "no_visit"
    assert body["points"] == 0


def test_duplicate_receipt_rejected(client, auth):
    """같은 (제휴처+날짜+금액+사진해시) 재제출은 rejected(duplicate).
    같은 사진 바이트를 써야 receipt_key가 같아진다."""
    _visit_near(client, auth)
    photo = make_png()  # 고정 바이트 → 두 번 제출 시 같은 해시
    files = {"photo": ("r.jpg", photo, "image/jpeg")}
    r1 = client.post(
        "/v1/receipts",
        data={"partner_id": "p-demo-ssambap", "amount_krw": "30000", "paid_at": f"{VISIT_DAY}T12:30:00"},
        files=files, headers=auth,
    )
    assert r1.json()["status"] == "accepted"
    r2 = client.post(
        "/v1/receipts",
        data={"partner_id": "p-demo-ssambap", "amount_krw": "30000", "paid_at": f"{VISIT_DAY}T12:30:00"},
        files={"photo": ("r.jpg", photo, "image/jpeg")},
        headers=auth,
    )
    body = r2.json()
    assert body["status"] == "rejected"
    assert body["reason"] == "duplicate"


def test_daily_limit_after_five(client, auth):
    """하루 5건까지 accepted, 6건째는 rejected(daily_limit).
    같은 제휴처·같은 날 두 번째는 pending이 먼저 적용되므로(11절), 6건째는
    '그날 처음 보내는 제휴처'여야 한다 — 시드 제휴처는 4개라서 테스트가 임시 제휴처 2개를 시드한다."""
    conn = sqlite3.connect(config.DB_PATH)
    for pid, name, place in (
        ("p-demo-cafe", "(테스트) 시내 카페", "gj-museum"),
        ("p-demo-books", "(테스트) 골목 서점", "gj-museum"),
    ):
        conn.execute(
            "INSERT OR IGNORE INTO partners (id, name, kind, near_place_id, point_rate, simulated)"
            " VALUES (?,?, 'shop', ?, 0.01, 1)", (pid, name, place),
        )
    conn.commit()
    conn.close()

    # 하루에 세 장소를 방문한다(각 제휴처의 near_place_id 커버).
    for place, hour in (("gj-daereungwon", "09:00"), ("gj-museum", "10:00"), ("gj-cheomseongdae", "11:00")):
        client.post(
            "/v1/visits", json={"place_id": place, "course_id": "gyeongju-silla-01",
                                "visited_at": f"{VISIT_DAY}T{hour}:00", "source": "manual"},
            headers=auth,
        )
    for i, partner in enumerate(["p-demo-ssambap", "p-demo-bread", "p-demo-museumshop",
                                 "p-demo-cafe", "p-demo-books"]):
        r = _receipt(client, auth, partner_id=partner, amount=1000 * (i + 1))
        assert r.json()["status"] == "accepted", r.text

    # 6건째: 같은 날 처음 보내는 제휴처(p-demo-stay, 숙소) → 더 이상 적립하지 않는다.
    r6 = client.post(
        "/v1/receipts",
        data={"partner_id": "p-demo-stay", "amount_krw": "123456",
              "paid_at": f"{VISIT_DAY}T18:00:00",
              "stay_checkin": VISIT_DAY, "stay_checkout": "2026-11-12"},
        files={"photo": ("r.jpg", make_unique_png(), "image/jpeg")}, headers=auth,
    ).json()
    assert r6["status"] == "rejected"
    assert r6["reason"] == "daily_limit"


def test_amount_boundary(client, auth):
    """0 < amount ≤ 1,000,000 경계: 1,000,000은 통과, 그 밖은 invalid_amount.
    invalid_amount는 저장되지 않으므로 같은 제휴처 재제출이 pending이 되지 않는다."""
    _visit_near(client, auth, place_id="gj-museum")
    ok = _receipt(client, auth, partner_id="p-demo-museumshop", amount=1_000_000).json()
    assert ok["status"] == "accepted"
    assert ok["points"] == 20_000  # floor(1,000,000 × 0.02)

    too_big = _receipt(client, auth, partner_id="p-demo-museumshop", amount=1_000_001).json()
    assert too_big["reason"] == "invalid_amount"
    zero = _receipt(client, auth, partner_id="p-demo-museumshop", amount=0).json()
    assert zero["reason"] == "invalid_amount"
    negative = _receipt(client, auth, partner_id="p-demo-museumshop", amount=-100).json()
    assert negative["reason"] == "invalid_amount"


def test_wrong_partner_place_has_no_visit(client, auth):
    """방문한 장소와 다른 제휴처 근처는 no_visit 처리된다."""
    _visit_near(client, auth, place_id="gj-museum")  # 박물관만 방문
    body = _receipt(client, auth, partner_id="p-demo-ssambap").json()  # 대릉원 앞 제휴처
    assert body["reason"] == "no_visit"


def test_evaluate_receipt_pure_function():
    """순수 함수 단위 검증: 검사 순서(금액→키중복→사진중복→방문→대기→한도)와 floor 계산."""
    base = dict(amount_krw=50000, point_rate=0.02, has_visit_near_place=True,
                is_duplicate_key=False, is_duplicate_photo=False,
                is_second_same_partner_day=False, receipts_accepted_today=0)
    ok = evaluate_receipt(**base)
    assert ok == {"status": "accepted", "points": 1000, "reason": "ok"}
    # 금액이 먼저다
    assert evaluate_receipt(**{**base, "amount_krw": 0, "is_duplicate_key": True,
                               "receipts_accepted_today": 99})["reason"] == "invalid_amount"
    # 키 중복이 방문보다 먼저다
    assert evaluate_receipt(**{**base, "amount_krw": 1000, "has_visit_near_place": False,
                               "is_duplicate_key": True})["reason"] == "duplicate"
    # 사진 중복이 방문보다 먼저다 (02문서 11절)
    assert evaluate_receipt(**{**base, "amount_krw": 1000, "has_visit_near_place": False,
                               "is_duplicate_photo": True})["reason"] == "duplicate_photo"
    # 방문이 '같은 제휴처 같은 날 대기'보다 먼저다
    assert evaluate_receipt(**{**base, "amount_krw": 1000, "has_visit_near_place": False,
                               "is_second_same_partner_day": True})["reason"] == "no_visit"
    # 같은 제휴처·같은 날 두 번째는 pending, 적립 0 (02문서 11절)
    p = evaluate_receipt(**{**base, "amount_krw": 1000, "is_second_same_partner_day": True})
    assert p == {"status": "pending", "points": 0, "reason": "pending"}
    # 대기가 한도보다 먼저다
    assert evaluate_receipt(**{**base, "amount_krw": 1000, "is_second_same_partner_day": True,
                               "receipts_accepted_today": 5})["status"] == "pending"
    # 한도: 그날 accepted가 이미 5건이면 daily_limit
    assert evaluate_receipt(**{**base, "amount_krw": 1000, "receipts_accepted_today": 5})["reason"] == "daily_limit"
    # floor 확인: 9,999 × 0.015 = 149.985 → 149
    assert evaluate_receipt(**{**base, "amount_krw": 9999, "point_rate": 0.015})["points"] == 149


from app.services.points import evaluate_receipt, receipt_key, photo_hash_of  # noqa: E402


def test_receipt_key_uses_photo_hash():
    """receipt_key = 제휴처+날짜+금액+사진해시 (02문서 11절).
    사진 해시가 다르면 같은 제휴처·날짜·금액이라도 키가 달라진다."""
    k1 = receipt_key("FAM", "p-demo-ssambap", "2026-11-10", 30000, "hash-a")
    k2 = receipt_key("FAM", "p-demo-ssambap", "2026-11-10", 30000, "hash-b")
    k3 = receipt_key("FAM", "p-demo-ssambap", "2026-11-10", 30000, "hash-a")
    assert k1 != k2
    assert k1 == k3


def test_photo_hash_is_sha256_of_raw_bytes():
    """사진 해시는 원본 바이트 sha256 — 같은 파일을 다시 올리면 같은 해시."""
    b = make_png()
    import hashlib

    assert photo_hash_of(b) == photo_hash_of(make_png())  # make_png는 매번 같은 바이트
    assert photo_hash_of(b) == hashlib.sha256(b).hexdigest()

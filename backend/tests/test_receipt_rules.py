# 영수증 인정 규칙 테스트 (02문서 11절).
# - 식당: 스탬프 당일·다음 날 인정, 이틀 뒤 거부
# - 숙소: 숙박 기간 안 스탬프 인정, 기간 밖 거부, 3박 초과 400
# - 사진 없는 요청 400 / 같은 사진 재제출 거부 / 같은 제휴처·같은 날 두 번째 pending
from tests.conftest import make_png, make_unique_png

VISIT_DAY = "2026-11-10"


def _visit(client, auth, place_id="gj-daereungwon", day=VISIT_DAY):
    r = client.post(
        "/v1/visits",
        json={"place_id": place_id, "course_id": "gyeongju-silla-01",
              "visited_at": f"{day}T10:00:00", "source": "manual"},
        headers=auth,
    )
    assert r.status_code == 200, r.text


def _submit(client, auth, *, partner="p-demo-ssambap", amount=20000, paid_at="2026-11-10T12:30:00",
            checkin=None, checkout=None, photo=None):
    data = {"partner_id": partner, "amount_krw": str(amount), "paid_at": paid_at}
    if checkin:
        data["stay_checkin"] = checkin
    if checkout:
        data["stay_checkout"] = checkout
    files = {"photo": ("r.jpg", photo if photo is not None else make_unique_png(), "image/jpeg")}
    return client.post("/v1/receipts", data=data, files=files, headers=auth)


def test_restaurant_same_day_accepted(client, auth):
    """식당: 결제일 == 스탬프 날짜 → 인정."""
    _visit(client, auth)
    body = _submit(client, auth, paid_at=f"{VISIT_DAY}T19:00:00").json()
    assert body["status"] == "accepted"
    assert body["points"] == 400  # floor(20,000 × 0.02)


def test_restaurant_next_day_accepted(client, auth):
    """식당: 결제일 == 스탬프 다음 날 → 인정(02문서 11절)."""
    _visit(client, auth, day=VISIT_DAY)
    body = _submit(client, auth, paid_at="2026-11-11T12:00:00").json()
    assert body["status"] == "accepted"


def test_restaurant_two_days_later_rejected(client, auth):
    """식당: 결제일이 스탬프 이틀 뒤 → rejected(no_visit)."""
    _visit(client, auth, day="2026-11-10")
    body = _submit(client, auth, paid_at="2026-11-12T12:00:00").json()
    assert body["status"] == "rejected"
    assert body["reason"] == "no_visit"


def test_lodging_within_stay_accepted(client, auth):
    """숙소: stay_checkin ≤ 스탬프 날짜 ≤ stay_checkout → 인정.
    결제일은 체크아웃 날짜여도 숙박 기간 안 스탬프면 인정된다(숙박은 다음 날 영수증이 일반적)."""
    _visit(client, auth, day="2026-11-10")  # 스탬프
    body = _submit(
        client, auth, partner="p-demo-stay", amount=80000,
        paid_at="2026-11-12T10:00:00",  # 결제는 체크아웃일
        checkin="2026-11-09", checkout="2026-11-12",
    ).json()
    assert body["status"] == "accepted"
    assert body["points"] == 1600  # floor(80,000 × 0.02)


def test_lodging_outside_stay_rejected(client, auth):
    """숙소: 스탬프 날짜가 숙박 기간 밖 → rejected(no_visit)."""
    _visit(client, auth, day="2026-11-20")
    body = _submit(
        client, auth, partner="p-demo-stay",
        checkin="2026-11-09", checkout="2026-11-12",
    ).json()
    assert body["status"] == "rejected"
    assert body["reason"] == "no_visit"


def test_lodging_over_three_nights_rejected_400(client, auth):
    """숙소: 4박(stay_checkin~stay_checkout이 3박 초과) → 400 invalid_stay."""
    _visit(client, auth, day="2026-11-10")
    r = _submit(
        client, auth, partner="p-demo-stay",
        checkin="2026-11-09", checkout="2026-11-13",  # 4박
    )
    assert r.status_code == 400
    assert r.json()["error"]["code"] == "invalid_stay"


def test_lodging_missing_stay_dates_400(client, auth):
    """숙소인데 체크인·체크아웃이 없으면 400 missing_stay."""
    _visit(client, auth)
    r = _submit(client, auth, partner="p-demo-stay")
    assert r.status_code == 400
    assert r.json()["error"]["code"] == "missing_stay"


def test_receipt_without_photo_rejected_400(client, auth):
    """사진 없는 요청은 400 missing_photo (02문서 11절 — 사진 필수)."""
    _visit(client, auth)
    r = client.post(
        "/v1/receipts",
        data={"partner_id": "p-demo-ssambap", "amount_krw": "20000",
              "paid_at": f"{VISIT_DAY}T12:30:00"},
        headers=auth,
    )
    assert r.status_code == 400
    assert r.json()["error"]["code"] == "missing_photo"


def test_same_photo_resubmission_rejected(client, auth):
    """같은 사진(같은 바이트) 재제출은 금액이 달라도 rejected(duplicate_photo)."""
    _visit(client, auth)
    photo = make_png()
    r1 = _submit(client, auth, amount=20000, photo=photo).json()
    assert r1["status"] == "accepted"
    r2 = _submit(client, auth, amount=99999, photo=photo).json()  # 금액만 변경
    assert r2["status"] == "rejected"
    assert r2["reason"] == "duplicate_photo"


def test_second_receipt_same_partner_same_day_pending(client, auth):
    """같은 제휴처·같은 날 두 번째 영수증: status=pending, 적립 0, 저장은 한다 (02문서 11절).
    다른 제휴처의 영수증은 정상 적립된다."""
    _visit(client, auth)
    r1 = _submit(client, auth, partner="p-demo-ssambap", amount=20000).json()
    assert r1["status"] == "accepted"

    r2 = _submit(client, auth, partner="p-demo-ssambap", amount=35000).json()
    assert r2["status"] == "pending"
    assert r2["points"] == 0

    # pending은 적립되지 않았다 — 총 포인트는 첫 영수증분만.
    points = client.get("/v1/families/me/points", headers=auth).json()
    assert points["total"] == 400

    # pending도 receipts에 저장됐다 — 같은 제휴처 세 번째도 여전히 pending이고,
    # 사진 파일이 지워지지 않아 같은 사진 재제출 차단이 유지된다.
    r3 = _submit(client, auth, partner="p-demo-ssambap", amount=5000).json()
    assert r3["status"] == "pending"

    # 다른 제휴처(가게)는 그날 첫 영수증이므로 정상 적립.
    client.post(
        "/v1/visits", json={"place_id": "gj-cheomseongdae", "course_id": "gyeongju-silla-01",
                            "visited_at": f"{VISIT_DAY}T09:00:00", "source": "manual"}, headers=auth,
    )
    r4 = _submit(client, auth, partner="p-demo-bread", amount=10000).json()
    assert r4["status"] == "accepted"
    assert r4["points"] == 100  # floor(10,000 × 0.01)


def test_kind_screen_groups_museum_shop(client, auth):
    """museum_shop은 화면에서 '가게'로 묶인다 — 서버는 원본 kind를 그대로 유지하며
    결제일 규칙(당일·다음 날)은 가게와 동일하게 적용한다."""
    client.post(
        "/v1/visits", json={"place_id": "gj-museum", "course_id": "gyeongju-silla-01",
                            "visited_at": f"{VISIT_DAY}T10:00:00", "source": "manual"}, headers=auth,
    )
    body = _submit(client, auth, partner="p-demo-museumshop", amount=20000,
                   paid_at="2026-11-11T15:00:00").json()  # 다음 날
    assert body["status"] == "accepted"

# 영수증 적립 규칙 — 순수 함수 모듈 (02문서 6-2·10·11절).
# 규칙(전부 여기서만 정의):
#   points = floor(amount × partner.point_rate)
#   1) 0 < amount <= 1,000,000 이 아니면 rejected(invalid_amount)
#   2) receipt_key(제휴처+날짜+금액+사진해시) 중복이면 rejected(duplicate)
#   3) 같은 사진 해시 재제출이면 rejected(duplicate_photo) — 02문서 11절
#   4) 방문 연계 실패(식당·가게: 스탬프 당일/다음 날, 숙소: 숙박 기간)면 rejected(no_visit)
#   5) 같은 제휴처·같은 날 두 번째 영수증이면 status=pending(확인 대기), 적립 0, 저장은 함
#   6) 가족당 하루 5건(accepted) 초과면 rejected(daily_limit)
#   7) 모두 통과하면 accepted, point_ledger 기록
# 검사 순서는 입력 정합성 → 중복 → 방문 → 대기 → 한도 순서로 고정한다(사유가 겹칠 때
# 어떤 사유가 응답되는지 예측 가능하게 하기 위함).
# 숙박 최대 3박(stay 기간 검증·invalid_stay)은 라우터의 입력 검증 단계에서 400으로 처리한다.
from typing import Dict, Optional

MAX_AMOUNT_KRW = 1_000_000
DAILY_LIMIT = 5

PENDING_REASON = "pending"


def evaluate_receipt(
    *,
    amount_krw: int,
    point_rate: float,
    has_visit_near_place: bool,
    is_duplicate_key: bool,
    is_duplicate_photo: bool = False,
    is_second_same_partner_day: bool = False,
    receipts_accepted_today: int = 0,
) -> Dict:
    """영수증 1건의 판정 결과를 반환한다.
    - has_visit_near_place: 제휴처 종류별 방문 연계 판정(식당·가게: 당일/다음 날,
      숙소: 숙박 기간 안에 스탬프)은 호출부(라우터)가 계산해 넘긴다.
    - receipts_accepted_today는 '같은 날 이미 적립(accepted)된' 영수증 수.
      pending은 적립이 아니므로 한도 계산에 세지 않는다(설계 결정)."""
    if amount_krw is None or not (0 < amount_krw <= MAX_AMOUNT_KRW):
        return {"status": "rejected", "points": 0, "reason": "invalid_amount"}
    if is_duplicate_key:
        return {"status": "rejected", "points": 0, "reason": "duplicate"}
    if is_duplicate_photo:
        return {"status": "rejected", "points": 0, "reason": "duplicate_photo"}
    if not has_visit_near_place:
        return {"status": "rejected", "points": 0, "reason": "no_visit"}
    if is_second_same_partner_day:
        # 같은 제휴처·같은 날 두 번째: 확인 대기(pending)로 저장하고 적립하지 않는다.
        return {"status": "pending", "points": 0, "reason": PENDING_REASON}
    if receipts_accepted_today >= DAILY_LIMIT:
        return {"status": "rejected", "points": 0, "reason": "daily_limit"}
    # floor: 정수 나눗셈으로 구현 (음수는 이미 위에서 차단)
    points = int(amount_krw * point_rate) if point_rate >= 0 else 0
    return {"status": "accepted", "points": points, "reason": "ok"}


def receipt_key(
    family_code: str,
    partner_id: str,
    paid_date: str,
    amount_krw: int,
    photo_hash: Optional[str],
) -> str:
    """영수증 중복 판정 키 — 02문서 11절: 제휴처 + 날짜 + 금액 + 사진 해시.
    사진 해시를 넣은 이유: 같은 사진으로 금액만 바꿔 재제출하는 것까지 걸러 내기 위함.
    설계 결정: 해시 앞에 가족 코드를 포함한다. receipt_key 컬럼이 전역 UNIQUE이므로
    서로 다른 가족이 같은 조합으로 결제하면 무관하게 충돌해 버리기 때문이다
    (중복 판정의 목적은 '같은 가족의 재제출' 차단). 날짜는 YYYY-MM-DD(일 단위)로 정규화."""
    import hashlib

    raw = f"{family_code}|{partner_id}|{paid_date}|{amount_krw}|{photo_hash or ''}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def photo_hash_of(data: bytes) -> str:
    """사진 원본 바이트의 sha256. EXIF 제거 저장 전 '원본'을 기준으로 삼아
    같은 파일을 다시 올리면 항상 같은 해시가 나온다."""
    import hashlib

    return hashlib.sha256(data).hexdigest()

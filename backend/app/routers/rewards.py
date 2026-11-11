# 혜택·적립 API (02문서 3·3-1·6-2·10·11절).
# POST /v1/rewards/preview : 코스 완주 혜택 미리보기(시드 offers_demo) — simulated:true
# POST /v1/receipts        : 영수증 적립(모바일 웹 전용 화면에서 호출) — simulated:true
#
# 적립 규칙 자체는 services/points.py 순수 함수에 있고 여기서는 DB 조회·저장만 한다.
# 설계 결정: rejected 영수증은 저장하지 않고 사유만 응답하며 사진 파일도 즉시 지운다.
# accepted·pending(확인 대기)만 receipts에 남는다 — pending은 적립 0이지만 나중에
# 수동 확인(로드맵)을 위해 사진과 함께 보관한다.
#
# 02문서 11절 반영:
# - 사진 필수(없으면 400 missing_photo). 사진 해시를 receipt_key와 photo_hash 컬럼에 저장.
# - 식당·가게: 결제일이 스탬프 날짜 또는 다음 날이면 인정, 이틀 뒤부터 rejected(no_visit).
# - 숙소(lodging): stay_checkin ≤ 스탬프 날짜 ≤ stay_checkout 이면 인정.
#   숙박 최대 3박 — 초과하면 400 invalid_stay, 날짜 누락은 400 missing_stay.
# - 같은 제휴처·같은 날 두 번째 영수증은 status='pending'(적립 0, 저장은 함).
# - 응답 label "포인트 캐시백(예시)": 현금 환급으로 읽히는 문구(환급·입금·원 단위 지급)를 쓰지 않는다.
from fastapi import APIRouter, Depends, File, Form, UploadFile

from .. import config, db, timeutil
from ..errors import AppError
from ..models import ReceiptResult, RewardsPreviewIn, RewardsPreviewOut
from ..security import require_family_auth
from ..services import photos
from ..services.points import evaluate_receipt, photo_hash_of, receipt_key

router = APIRouter()


def _validate_stay(partner_kind, stay_checkin, stay_checkout) -> tuple[str, str]:
    """숙소 영수증의 숙박 기간 입력을 검증해 (checkin, checkout) 문자열을 돌려준다.
    - 숙소인데 날짜가 없으면 400 missing_stay
    - 형식이 틀리거나 checkin > checkout이면 400 invalid_stay
    - 최대 3박(MAX_STAY_NIGHTS)을 넘으면 400 invalid_stay (02문서 11절)"""
    if partner_kind == "lodging":
        if not stay_checkin or not stay_checkout:
            raise AppError(400, "missing_stay", "숙소 영수증에는 체크인·체크아웃 날짜가 필요합니다")
        try:
            checkin = timeutil.parse_date_only(stay_checkin).date()
            checkout = timeutil.parse_date_only(stay_checkout).date()
        except timeutil.InvalidDatetime as exc:
            raise AppError(400, "invalid_stay", str(exc)) from exc
        if checkout < checkin:
            raise AppError(400, "invalid_stay", "체크아웃 날짜가 체크인보다 이릅니다")
        if (checkout - checkin).days > config.MAX_STAY_NIGHTS:
            raise AppError(400, "invalid_stay", f"숙박은 최대 {config.MAX_STAY_NIGHTS}박까지 인정됩니다")
        return checkin.strftime("%Y-%m-%d"), checkout.strftime("%Y-%m-%d")
    # 식당·가게는 stay 필드를 받지 않는다(받아도 무시 — 스키마의 의미를 지키기 위해 저장하지 않음).
    return None, None


def _visit_near_place(conn, family_code: str, partner: dict, paid_date: str,
                      stay_checkin, stay_checkout):
    """방문 연계 판정 + 연결할 방문 행을 찾는다 (02문서 6-3·11절).
    - 식당·가게(restaurant/shop/museum_shop): 스탬프 날짜 == 결제일 또는 결제일의 다음 날
    - 숙소(lodging): stay_checkin ≤ 스탬프 날짜 ≤ stay_checkout
    조건을 만족하는 방문 중 가장 이른 기록을 영수증과 연결한다."""
    if not partner["near_place_id"]:
        return None
    if partner["kind"] == "lodging":
        rows = conn.execute(
            "SELECT id, visited_at FROM visits WHERE family_code = ? AND place_id = ?"
            " AND substr(visited_at, 1, 10) BETWEEN ? AND ?"
            " ORDER BY visited_at ASC",
            (family_code, partner["near_place_id"], stay_checkin, stay_checkout),
        ).fetchall()
    else:
        prev_date = timeutil.shift_date(paid_date, -1)
        rows = conn.execute(
            "SELECT id, visited_at FROM visits WHERE family_code = ? AND place_id = ?"
            " AND substr(visited_at, 1, 10) IN (?, ?)"
            " ORDER BY visited_at ASC",
            (family_code, partner["near_place_id"], paid_date, prev_date),
        ).fetchall()
    return rows[0] if rows else None


@router.post("/v1/rewards/preview", response_model=RewardsPreviewOut)
def post_rewards_preview(payload: RewardsPreviewIn, family: dict = Depends(require_family_auth)):
    """혜택 미리보기. 02문서대로 offers_demo(시드)를 그대로 돌려준다 — 아직 실제 제휴가 아니므로
    simulated:true를 항상 실어 보낸다."""
    with db.connect() as conn:
        if conn.execute("SELECT 1 FROM courses WHERE id = ?", (payload.course_id,)).fetchone() is None:
            raise AppError(404, "course_not_found", "존재하지 않는 코스입니다")
        rows = conn.execute(
            "SELECT id, title, condition, simulated FROM offers ORDER BY id"
        ).fetchall()
    return RewardsPreviewOut(
        simulated=True,
        offers=[
            {"id": r["id"], "title": r["title"], "condition": r["condition"], "simulated": bool(r["simulated"])}
            for r in rows
        ],
    )


@router.post("/v1/receipts", response_model=ReceiptResult)
async def post_receipt(
    partner_id: str = Form(...),
    amount_krw: int = Form(...),
    paid_at: str = Form(...),
    stay_checkin: str = Form(default=None),
    stay_checkout: str = Form(default=None),
    photo: UploadFile = File(default=None),
    family: dict = Depends(require_family_auth),
):
    """영수증 적립. 규칙 평가는 services/points.evaluate_receipt가 한다:
    ok / pending / no_visit / duplicate / duplicate_photo / daily_limit / invalid_amount.
    사진은 필수다(02문서 11절). 사진 해시는 receipt_key 재료이자 photo_hash로 저장되어
    같은 사진의 재제출을 막는다."""
    try:
        paid_minute = timeutil.to_minute_iso(paid_at)
    except timeutil.InvalidDatetime as exc:
        raise AppError(400, "invalid_datetime", str(exc)) from exc
    paid_date = paid_minute[:10]  # 일 단위 규칙(인정 범위·한도·중복 키)은 날짜 기준

    # 사진 필수 검증 — FastAPI로 File(...)을 쓰면 422가 나므로 직접 검사해 400을 낸다.
    if photo is None or not photo.filename:
        raise AppError(400, "missing_photo", "영수증 사진은 필수입니다")
    data = await photo.read(config.MAX_PHOTO_BYTES + 1)
    phash = photo_hash_of(data)
    photo_path = photos.save_upload(data, prefix="receipt")

    try:
        with db.connect() as conn:
            partner = conn.execute("SELECT * FROM partners WHERE id = ?", (partner_id,)).fetchone()
            if partner is None:
                raise AppError(404, "partner_not_found", "존재하지 않는 제휴처입니다")
            checkin, checkout = _validate_stay(partner["kind"], stay_checkin, stay_checkout)

            key = receipt_key(family["code"], partner_id, paid_date, amount_krw, phash)
            is_duplicate = conn.execute(
                "SELECT 1 FROM receipts WHERE receipt_key = ?", (key,)
            ).fetchone() is not None
            is_duplicate_photo = conn.execute(
                "SELECT 1 FROM receipts WHERE family_code = ? AND photo_hash = ?",
                (family["code"], phash),
            ).fetchone() is not None

            visit_row = _visit_near_place(conn, family["code"], partner, paid_date, checkin, checkout)
            has_visit = visit_row is not None

            # 같은 제휴처·같은 날 이미 저장된(accepted·pending) 영수증이 있으면 이번 건은 확인 대기.
            is_second = conn.execute(
                "SELECT COUNT(*) AS n FROM receipts WHERE family_code = ? AND partner_id = ?"
                " AND substr(paid_at, 1, 10) = ? AND status IN ('accepted','pending')",
                (family["code"], partner_id, paid_date),
            ).fetchone()["n"] >= 1

            accepted_today = conn.execute(
                "SELECT COUNT(*) AS n FROM receipts WHERE family_code = ?"
                " AND substr(paid_at, 1, 10) = ? AND status = 'accepted'",
                (family["code"], paid_date),
            ).fetchone()["n"]

            result = evaluate_receipt(
                amount_krw=amount_krw,
                point_rate=partner["point_rate"],
                has_visit_near_place=has_visit,
                is_duplicate_key=is_duplicate,
                is_duplicate_photo=is_duplicate_photo,
                is_second_same_partner_day=is_second,
                receipts_accepted_today=accepted_today,
            )

            if result["status"] == "rejected":
                photos.delete_file(photo_path)  # 거부된 영수증의 사진은 보관하지 않는다
                return ReceiptResult(**result)

            cur = conn.execute(
                "INSERT INTO receipts (family_code, partner_id, visit_id, amount_krw,"
                " receipt_key, photo_path, status, paid_at, stay_checkin, stay_checkout,"
                " photo_hash, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                (
                    family["code"], partner_id,
                    visit_row["id"] if visit_row else None,
                    amount_krw, key, photo_path, result["status"], paid_minute,
                    checkin, checkout, phash, timeutil.now_iso(),
                ),
            )
            if result["status"] == "accepted":
                conn.execute(
                    "INSERT INTO point_ledger (family_code, receipt_id, points, reason, created_at)"
                    " VALUES (?,?,?,?,?)",
                    (
                        family["code"], cur.lastrowid, result["points"],
                        f"receipt:{partner_id}", timeutil.now_iso(),
                    ),
                )
            return ReceiptResult(**result)
    except Exception:
        # 요청 처리가 중단되면 저장 직전의 사진 파일을 지워 고아 파일을 남기지 않는다.
        photos.delete_file(photo_path)
        raise

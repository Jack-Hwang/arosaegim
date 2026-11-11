# 포인트 API (02문서 6-2절).
# GET /v1/families/me/points : 합계와 적립 내역 — PIN 필요(명시된 목록에 포함)
from fastapi import APIRouter, Depends

from .. import db
from ..models import PointEntry, PointsSummary
from ..security import require_family_auth

router = APIRouter()


@router.get("/v1/families/me/points", response_model=PointsSummary)
def get_points(family: dict = Depends(require_family_auth)):
    """적립 합계·내역. 적립은 accepted 영수증만 발생하므로 point_ledger가 유일한 원장이다."""
    with db.connect() as conn:
        total = conn.execute(
            "SELECT COALESCE(SUM(points), 0) AS t FROM point_ledger WHERE family_code = ?",
            (family["code"],),
        ).fetchone()["t"]
        rows = conn.execute(
            "SELECT id, receipt_id, points, reason, created_at FROM point_ledger"
            " WHERE family_code = ? ORDER BY id DESC",
            (family["code"],),
        ).fetchall()
    return PointsSummary(
        total=total,
        entries=[PointEntry(**dict(r)) for r in rows],
    )

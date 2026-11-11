# 스탬프북 API (02문서 3·9절).
# GET /v1/stamp-books               : 스탬프북 정의 목록 — 공개
# GET /v1/families/me/stamp-books   : 이 가족의 진행률·완성 배지 — 가족 코드만 필요
#   (02문서 PIN 목록에 없는 GET이므로 PIN 없이 열람한다 — 설계 결정)
import math

from fastapi import APIRouter, Depends

from .. import db
from ..models import StampBookOut, StampBookProgress
from ..security import require_family_code_only

router = APIRouter()


@router.get("/v1/stamp-books", response_model=list[StampBookOut])
def list_stamp_books():
    with db.connect() as conn:
        rows = conn.execute(
            "SELECT id, title, region, country, badge FROM stamp_books ORDER BY id"
        ).fetchall()
    return [dict(r) for r in rows]


@router.get("/v1/families/me/stamp-books", response_model=list[StampBookProgress])
def my_stamp_books(family: dict = Depends(require_family_code_only)):
    """스탬프북별 진행률. 규칙은 앱의 StampBook과 동일하게 맞춘다:
    progress_pct = 내림(방문 장소 수 / 전체 장소 수 × 100), 전부 방문하면 completed + 배지."""
    with db.connect() as conn:
        books = conn.execute(
            "SELECT id, title, region, country, badge FROM stamp_books ORDER BY id"
        ).fetchall()
        visited = {
            r["place_id"]
            for r in conn.execute(
                "SELECT DISTINCT place_id FROM visits WHERE family_code = ?",
                (family["code"],),
            ).fetchall()
        }
    result = []
    for b in books:
        place_rows = conn_place_ids(b["id"])
        total = len(place_rows)
        count = sum(1 for p in place_rows if p in visited)
        completed = total > 0 and count == total
        result.append(
            StampBookProgress(
                id=b["id"], title=b["title"], region=b["region"],
                country=b["country"], badge=b["badge"],
                visited_count=count, total=total,
                progress_pct=math.floor(count / total * 100) if total else 0,
                completed=completed,
                badge_earned=b["badge"] if completed else None,
            )
        )
    return result


def conn_place_ids(book_id: str) -> list[str]:
    with db.connect() as conn:
        rows = conn.execute(
            "SELECT place_id FROM stamp_book_places WHERE book_id = ?"
            " ORDER BY rowid ASC",
            (book_id,),
        ).fetchall()
    return [r["place_id"] for r in rows]

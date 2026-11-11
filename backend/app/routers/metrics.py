# 가족 지표 API (02문서 10절).
# GET /v1/families/me/metrics — PIN 필요. 가족 단위 지표만 제공한다.
# 운영자용 전체 집계는 만들지 않는다(02문서 10절: "가족 단위만").
# 각 지표는 값 + source(출처) 필드로 응답한다 — 값이 어디서 왔는지 화면·문서가 그대로 설명할 수 있게.
import math

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from typing import Optional

from .. import db
from ..security import require_family_auth
from ..services import summary

router = APIRouter()


class StampBooksMetric(BaseModel):
    completed: int          # 완성한 스탬프북 권 수
    total: int
    rate_pct: int           # 내림(완성 권 수 / 전체 × 100)
    source: str


class ThenNowViewsMetric(BaseModel):
    count: int
    approx: bool = True
    source: str


class RevisitGapMetric(BaseModel):
    avg_days: Optional[float] = None   # 재방문이 없으면 null
    pairs: int                          # 간격 계산에 쓰인 연속 방문 쌍 수
    source: str


class ReceiptsAcceptedMetric(BaseModel):
    count: int
    source: str


class HeirMetric(BaseModel):
    designated: bool        # 이어받을 사람(heir) 지정 여부
    source: str


class FamilyMetrics(BaseModel):
    stamp_books: StampBooksMetric
    then_now_views: ThenNowViewsMetric
    revisit_gap_days: RevisitGapMetric
    receipts_accepted: ReceiptsAcceptedMetric
    heir: HeirMetric
    # 기획서 v10 B4: 가족 누적 주행거리(km). 같은 코스 주행(course_run)의 거리는
    # 방문마다 중복으로 더하지 않고 1회만 합산한다. 기록이 없으면 0.
    family_total_trip_km: float = 0.0


@router.get("/v1/families/me/metrics", response_model=FamilyMetrics)
def get_metrics(family: dict = Depends(require_family_auth)):
    """우리 가족 지표 (02문서 10절):
    - 스탬프북 완성률: 완성 권 수 / 전체 권 수(모든 장소를 방문했으면 완성)
    - 그때와 지금 열람 수: 열람 로그를 따로 모으지 않으므로 '그때와 지금 카드가 만들어진
      방문 수(=visits 수)'로 근사한다(주석 명시 — 정확한 열람 통계는 로드맵)
    - 재방문 간격: 같은 장소에 다시 방문한 경우, 연속 방문 간 일 차이의 평균(재방문 없으면 null)
    - 영수증 인증 수: status='accepted' 영수증 수(pending·rejected 제외)
    - 물려주기 지정 여부: role='heir' 구성원이 있는지
    - 가족 누적 주행거리(기획서 v10 B4): visits.trip_km의 합. 같은 코스 주행(course_run)은
      1번만 합산(MAX)해 방문마다의 중복을 막는다. trip_km 기록이 없는 visit은 0 취급."""
    with db.connect() as conn:
        books = conn.execute("SELECT id FROM stamp_books ORDER BY id").fetchall()
        visited = {
            r["place_id"]
            for r in conn.execute(
                "SELECT DISTINCT place_id FROM visits WHERE family_code = ?",
                (family["code"],),
            ).fetchall()
        }
        completed = 0
        for b in books:
            place_ids = [
                r["place_id"]
                for r in conn.execute(
                    "SELECT place_id FROM stamp_book_places WHERE book_id = ?", (b["id"],)
                ).fetchall()
            ]
            if place_ids and all(p in visited for p in place_ids):
                completed += 1

        visit_count = conn.execute(
            "SELECT COUNT(*) AS n FROM visits WHERE family_code = ?", (family["code"],)
        ).fetchone()["n"]

        # 재방문 간격: 같은 장소의 방문을 시간순으로 보고 연속 쌍의 일 차이를 모은다.
        visit_rows = conn.execute(
            "SELECT place_id, visited_at FROM visits WHERE family_code = ?"
            " ORDER BY place_id, visited_at",
            (family["code"],),
        ).fetchall()

        accepted_count = conn.execute(
            "SELECT COUNT(*) AS n FROM receipts WHERE family_code = ? AND status = 'accepted'",
            (family["code"],),
        ).fetchone()["n"]

        heir = conn.execute(
            "SELECT 1 FROM members WHERE family_code = ? AND role = 'heir' LIMIT 1",
            (family["code"],),
        ).fetchone() is not None

        trip_total = summary.total_trip_km(conn, family["code"])

    gaps = []
    prev = None  # (place_id, date)
    for r in visit_rows:
        day = r["visited_at"][:10]
        if prev is not None and prev[0] == r["place_id"] and day != prev[1]:
            try:
                d1 = _date(prev[1])
                d2 = _date(day)
                gaps.append((d2 - d1).days)
            except ValueError:
                continue  # 날짜 형식 이상 데이터는 지표에서 건너뛴다
        prev = (r["place_id"], day)

    return FamilyMetrics(
        stamp_books=StampBooksMetric(
            completed=completed,
            total=len(books),
            rate_pct=math.floor(completed / len(books) * 100) if books else 0,
            source="stamp_books × visits(모든 장소 방문 시 완성)",
        ),
        then_now_views=ThenNowViewsMetric(
            count=visit_count,
            approx=True,
            source="visits 수로 근사(열람 로그 미수집)",
        ),
        revisit_gap_days=RevisitGapMetric(
            avg_days=round(sum(gaps) / len(gaps), 1) if gaps else None,
            pairs=len(gaps),
            source="같은 장소 재방문 시 일 차이 평균(재방문 없으면 null)",
        ),
        receipts_accepted=ReceiptsAcceptedMetric(
            count=accepted_count,
            source="receipts(status='accepted')",
        ),
        heir=HeirMetric(
            designated=heir,
            source="members(role='heir')",
        ),
        family_total_trip_km=trip_total,
    )


def _date(day: str):
    import datetime

    return datetime.datetime.strptime(day, "%Y-%m-%d").date()

# 방문 기록 API (02문서 3절).
# POST /v1/visits — 멱등: 같은 가족+장소+코스+'같은 분'의 방문은 새로 만들지 않고
# 기존 기록을 그대로 돌려준다(중복 스탬프·중복 타임라인 방지).
# DB의 UNIQUE(family_code, place_id, course_id, visited_at) 제약이 최종 방어선이다.
import sqlite3
from datetime import datetime

from fastapi import APIRouter, Depends

from .. import db, timeutil
from ..errors import AppError
from ..models import VisitIn, VisitOut
from ..security import require_family_auth
from ..services import then_now as then_now_svc

router = APIRouter()


def _fetch_place(conn, place_id: str):
    row = conn.execute("SELECT * FROM places WHERE id = ?", (place_id,)).fetchone()
    if row is None:
        raise AppError(404, "place_not_found", "존재하지 않는 장소입니다")
    return dict(row)


def _visit_row_to_dict(row) -> dict:
    return dict(row)


def _build_visit_response(conn, family_code: str, visit: dict, place: dict) -> VisitOut:
    """방문 1건 + then_now(과거=가장 오래된 기억, 없으면 이전 방문) 응답 조립.
    02문서 10절: then-now에 past.party_size / now.party_size / now.trip_km / heritage를 실어 보낸다."""
    memories = [
        dict(r)
        for r in conn.execute(
            "SELECT year, note, photo_path, party_size, vehicle_label, created_at FROM memories"
            " WHERE family_code = ? AND place_id = ?",
            (family_code, place["id"]),
        ).fetchall()
    ]
    earlier = [
        dict(r)
        for r in conn.execute(
            "SELECT place_id, visited_at, vehicle_label, party_size FROM visits"
            " WHERE family_code = ? AND place_id = ? AND visited_at < ?",
            (family_code, place["id"], visit["visited_at"]),
        ).fetchall()
    ]
    heritage = None
    if place.get("heritage_name"):
        heritage = {
            "name": place["heritage_name"],
            "year": place.get("heritage_year"),
            "label": place.get("heritage_label"),
        }
    then_now = then_now_svc.resolve_then_now(
        memories=memories,
        earlier_visits=earlier,
        now_visit=visit,
        current_year=datetime.now().year,
        heritage=heritage,
    )
    return VisitOut(
        visit_id=visit["id"],
        stamp_no=visit["stamp_no"],
        then_now=then_now,
        party_size=visit.get("party_size"),
        trip_km=visit.get("trip_km"),
    )


@router.post("/v1/visits", response_model=VisitOut)
def create_visit(payload: VisitIn, family: dict = Depends(require_family_auth)):
    # '같은 분'을 같은 방문으로 본다 — 초 단위 차이는 재시도·지연으로 생기는 노이즈다.
    try:
        minute = timeutil.to_minute_iso(payload.visited_at)
    except timeutil.InvalidDatetime as exc:
        raise AppError(400, "invalid_datetime", str(exc)) from exc
    with db.connect() as conn:
        place = _fetch_place(conn, payload.place_id)
        course = conn.execute(
            "SELECT id FROM courses WHERE id = ?", (payload.course_id,)
        ).fetchone()
        if course is None:
            raise AppError(404, "course_not_found", "존재하지 않는 코스입니다")
        if conn.execute(
            "SELECT 1 FROM course_places WHERE course_id = ? AND place_id = ?",
            (payload.course_id, payload.place_id),
        ).fetchone() is None:
            raise AppError(400, "place_not_in_course", "이 코스에 포함되지 않는 장소입니다")

        existing = conn.execute(
            "SELECT * FROM visits WHERE family_code = ? AND place_id = ?"
            " AND course_id = ? AND visited_at = ?",
            (family["code"], payload.place_id, payload.course_id, minute),
        ).fetchone()
        if existing is not None:
            return _build_visit_response(conn, family["code"], _visit_row_to_dict(existing), place)

        stamp_no = conn.execute(
            "SELECT COALESCE(MAX(stamp_no), 0) + 1 AS n FROM visits WHERE family_code = ?",
            (family["code"],),
        ).fetchone()["n"]
        try:
            cur = conn.execute(
                "INSERT INTO visits (family_code, place_id, course_id, visited_at,"
                " source, vehicle_label, party_size, trip_km, stamp_no, created_at)"
                " VALUES (?,?,?,?,?,?,?,?,?,?)",
                (
                    family["code"], payload.place_id, payload.course_id, minute,
                    payload.source, payload.vehicle_label,
                    payload.party_size, payload.trip_km,  # 선택 필드(02문서 10절)
                    stamp_no, timeutil.now_iso(),
                ),
            )
        except sqlite3.IntegrityError:
            # 동시 요청이 UNIQUE 제약을 통과한 경우 — 기존 기록을 돌려준다(멱등 유지).
            conn.rollback()
            existing = conn.execute(
                "SELECT * FROM visits WHERE family_code = ? AND place_id = ?"
                " AND course_id = ? AND visited_at = ?",
                (family["code"], payload.place_id, payload.course_id, minute),
            ).fetchone()
            if existing is None:  # pragma: no cover — 이론상 도달하지 않음
                raise AppError(500, "internal_error", "방문 기록 저장 실패")
            return _build_visit_response(conn, family["code"], _visit_row_to_dict(existing), place)
        visit = conn.execute("SELECT * FROM visits WHERE id = ?", (cur.lastrowid,)).fetchone()
        return _build_visit_response(conn, family["code"], _visit_row_to_dict(visit), place)

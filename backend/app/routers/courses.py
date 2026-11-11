# 코스 API (02문서 3절). 공개 데이터 — PIN 불필요.
from fastapi import APIRouter

from .. import db
from ..errors import AppError
from ..models import CourseDetail, CourseSummary
from .places import row_to_place

router = APIRouter()


@router.get("/v1/courses", response_model=list[CourseSummary])
def list_courses():
    """코스 목록. 시드에서 온 기준 데이터이므로 가족 인증 없이 공개한다."""
    with db.connect() as conn:
        rows = conn.execute("SELECT id, title, summary FROM courses ORDER BY id").fetchall()
    return [dict(r) for r in rows]


@router.get("/v1/courses/{course_id}", response_model=CourseDetail)
def get_course(course_id: str):
    """코스 상세: seq 순서로 정렬된 장소 목록(퀴즈 포함)."""
    with db.connect() as conn:
        course = conn.execute(
            "SELECT id, title, summary FROM courses WHERE id = ?", (course_id,)
        ).fetchone()
        if course is None:
            raise AppError(404, "course_not_found", "존재하지 않는 코스입니다")
        place_rows = conn.execute(
            "SELECT p.* FROM course_places cp JOIN places p ON p.id = cp.place_id"
            " WHERE cp.course_id = ? ORDER BY cp.seq ASC",
            (course_id,),
        ).fetchall()
    body = dict(course)
    body["places"] = [row_to_place(dict(r)) for r in place_rows]
    return body


# 기억(사진+메모) API (02문서 6-2절 + 기획서 v10 B5).
# POST /v1/memories — multipart(place_id, year, note, vehicle_label?, party_size?, photo?, markers?)
#   markers: 인물 이름표 JSON 문자열 '[{"x":0.1,"y":0.2,"name":"아빠"}]' — 사진 크기 대비 비율(0..1).
#   얼굴 인식 등은 일절 없다 — 앱이 찍어 보낸 좌표·이름을 그대로 저장할 뿐이다.
# PUT  /v1/families/me/memories/{id}/markers — 이름표 전체 교체(수정 경로)
# 사진 규칙: 최대 5MB, JPEG/PNG만, EXIF 제거 후 저장, 파일명은 서버가 생성
# (원본 파일명은 저장하지 않는다 — 경로·개인정보 노출 방지).
# 설계 결정(주석 명시): memories 테이블은 구성원(member)을 참조하지 않으므로
# "미성년 member 관련 사진"은 '이 가족에 미성년 구성원이 있고 보호자 동의가
# 아직 없으면' 사진 업로드를 403으로 막는 것으로 해석·구현했다.
import json
from typing import List, Optional

from fastapi import APIRouter, Depends, File, Form, UploadFile
from pydantic import ValidationError

from .. import config, db, timeutil
from ..errors import AppError
from ..models import Marker, MarkerIn, MarkersIn, MarkersOut, MemoryCreated
from ..security import require_family_auth
from ..services import photos

router = APIRouter()

MAX_MARKERS = 10  # 사진 1장당 이름표 상한 — 웹 안내("최대 10명")와 통일(감사 지적 S2)


def _reject_over_limit(count: int) -> None:
    """이름표 개수가 상한을 넘으면 400 invalid_markers. 등록·수정 경로가 함께 쓴다."""
    if count > MAX_MARKERS:
        raise AppError(400, "invalid_markers", f"이름표는 최대 {MAX_MARKERS}명까지예요")


def parse_markers(raw: Optional[str]) -> List[Marker]:
    """multipart의 markers JSON 문자열을 검증해 Marker 목록으로 바꾼다.
    없거나 빈 문자열이면 빈 목록. 형식이 틀리면 400 invalid_markers."""
    if raw is None or not raw.strip():
        return []
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise AppError(400, "invalid_markers", "markers는 JSON 배열이어야 합니다") from exc
    if not isinstance(data, list):
        raise AppError(400, "invalid_markers", "markers는 JSON 배열이어야 합니다")
    _reject_over_limit(len(data))
    try:
        return [MarkerIn(**item) for item in data]
    except (TypeError, ValidationError) as exc:
        raise AppError(400, "invalid_markers",
                       "이름표 항목은 {x(0~1), y(0~1), name} 이어야 합니다") from exc


def save_markers(conn, family_code: str, memory_id: int, markers: List[Marker]) -> None:
    """기존 이름표를 지우고 새로 저장한다(수정도 '전체 교체' 한 가지 규칙)."""
    conn.execute(
        "DELETE FROM memory_markers WHERE family_code = ? AND memory_id = ?",
        (family_code, memory_id),
    )
    for seq, m in enumerate(markers):
        conn.execute(
            "INSERT INTO memory_markers (family_code, memory_id, x, y, name, seq)"
            " VALUES (?,?,?,?,?,?)",
            (family_code, memory_id, float(m.x), float(m.y), m.name, seq),
        )


def _check_minor_consent(family_code: str) -> None:
    """미성년 구성원이 있는데 보호자 동의가 없으면 사진 저장을 거부한다.
    (동의 기록은 웹 전용 화면에서 POST /v1/families/me/members/{id}/consent 로 남긴다)"""
    with db.connect() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS n FROM members"
            " WHERE family_code = ? AND is_minor = 1 AND guardian_consent = 0",
            (family_code,),
        ).fetchone()
    if row["n"] > 0:
        raise AppError(403, "guardian_consent_required", "미성년 구성원이 있어 보호자 동의가 필요합니다(웹에서 동의 후 다시 시도)")


@router.post("/v1/memories", response_model=MemoryCreated)
async def create_memory(
    place_id: str = Form(...),
    year: int = Form(...),
    note: str = Form(default=None),
    vehicle_label: str = Form(default=None),
    party_size: int = Form(default=None),
    markers: str = Form(default=None),
    photo: UploadFile = File(default=None),
    family: dict = Depends(require_family_auth),
):
    """과거 기록 등록. 02문서 10절: party_size(그때 함께 간 인원, 가족이 입력)를 선택으로 받는다.
    기획서 v10 B5: markers(인물 이름표 JSON 문자열, 선택)를 함께 저장한다 —
    사진이 없어도 이름표는 저장할 수 있다(조회·삭제는 기억에 묶인다)."""
    parsed_markers = parse_markers(markers)
    with db.connect() as conn:
        if conn.execute("SELECT 1 FROM places WHERE id = ?", (place_id,)).fetchone() is None:
            raise AppError(404, "place_not_found", "존재하지 않는 장소입니다")
    if not (1900 <= year <= 2100):
        raise AppError(400, "invalid_year", "year는 1900~2100 사이여야 합니다")
    if party_size is not None and not (1 <= party_size <= 50):
        raise AppError(400, "invalid_party_size", "party_size는 1~50 사이여야 합니다")

    photo_path = None
    if photo is not None and photo.filename:  # 파일이 실제로 첨부된 경우만
        _check_minor_consent(family["code"])
        data = await photo.read(config.MAX_PHOTO_BYTES + 1)  # 1바이트 더 읽어 초과 판정
        photo_path = photos.save_upload(data, prefix="mem")  # 검증+EXIF 제거+저장

    with db.connect() as conn:
        cur = conn.execute(
            "INSERT INTO memories (family_code, place_id, year, note, photo_path,"
            " party_size, vehicle_label, created_at) VALUES (?,?,?,?,?,?,?,?)",
            (
                family["code"], place_id, year, note, photo_path,
                party_size, vehicle_label, timeutil.now_iso(),
            ),
        )
        memory_id = cur.lastrowid
        if parsed_markers:
            save_markers(conn, family["code"], memory_id, parsed_markers)
        return MemoryCreated(memory_id=memory_id)


@router.put("/v1/families/me/memories/{memory_id}/markers", response_model=MarkersOut)
def put_markers(memory_id: int, payload: MarkersIn, family: dict = Depends(require_family_auth)):
    """그때 기록의 인물 이름표 수정 (기획서 v10 B5). 전체 교체 방식이며
    빈 배열을 보내면 이름표를 모두 지운다. 없는 기억은 404.
    개수 상한은 등록 경로와 같은 10개(감사 지적 S2 — 초과 시 400 invalid_markers)."""
    _reject_over_limit(len(payload.markers))
    with db.connect() as conn:
        row = conn.execute(
            "SELECT id FROM memories WHERE id = ? AND family_code = ?",
            (memory_id, family["code"]),
        ).fetchone()
        if row is None:
            raise AppError(404, "memory_not_found", "이 가족의 그때 기록이 없습니다")
        save_markers(conn, family["code"], memory_id, payload.markers)
    return MarkersOut(markers=[Marker(x=m.x, y=m.y, name=m.name) for m in payload.markers])

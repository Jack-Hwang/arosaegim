# 기억(사진+메모) API (02문서 6-2절).
# POST /v1/memories — multipart(place_id, year, note, vehicle_label?, photo?)
# 사진 규칙: 최대 5MB, JPEG/PNG만, EXIF 제거 후 저장, 파일명은 서버가 생성
# (원본 파일명은 저장하지 않는다 — 경로·개인정보 노출 방지).
# 설계 결정(주석 명시): memories 테이블은 구성원(member)을 참조하지 않으므로
# "미성년 member 관련 사진"은 '이 가족에 미성년 구성원이 있고 보호자 동의가
# 아직 없으면' 사진 업로드를 403으로 막는 것으로 해석·구현했다.
from fastapi import APIRouter, Depends, File, Form, UploadFile

from .. import config, db, timeutil
from ..errors import AppError
from ..models import MemoryCreated
from ..security import require_family_auth
from ..services import photos

router = APIRouter()


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
    photo: UploadFile = File(default=None),
    family: dict = Depends(require_family_auth),
):
    """과거 기록 등록. 02문서 10절: party_size(그때 함께 간 인원, 가족이 입력)를 선택으로 받는다."""
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
        return MemoryCreated(memory_id=cur.lastrowid)

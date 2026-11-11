# 가족 화면 설정·장소 접기 API (기획서 v10 B2·B3).
# GET    /v1/families/me/settings          : 설정 + 접은 장소 목록 조회 — PIN 필요
# PUT    /v1/families/me/settings          : {'fold_person_comparison_all': bool} 저장 — PIN 필요
# POST   /v1/families/me/folded-places     : 장소 접기 추가 — PIN 필요
# DELETE /v1/families/me/folded-places/{id}: 장소 접기 해제 — PIN 필요
# 모든 응답은 현재 상태를 정직하게 돌려준다(값을 지어내지 않는다: 없으면 빈 목록·false).
from fastapi import APIRouter, Depends

from .. import db
from ..errors import AppError
from ..models import FoldedIn, FoldedOut, SettingsIn, SettingsOut, SettingsReadOut
from ..security import require_family_auth
from ..services import family_prefs

router = APIRouter()


@router.get("/v1/families/me/settings", response_model=SettingsReadOut)
def get_settings(family: dict = Depends(require_family_auth)):
    """가족 화면 설정·접기 현황 조회 (기획서 v10 B2·B3). PUT /settings와 같은 데이터 원천이며
    현재 저장된 값을 그대로 돌려준다(값이 없으면 false·빈 목록)."""
    with db.connect() as conn:
        return SettingsReadOut(
            fold_person_comparison_all=family_prefs.get_fold_person_all(conn, family["code"]),
            folded_place_ids=family_prefs.folded_place_ids(conn, family["code"]),
        )


@router.put("/v1/families/me/settings", response_model=SettingsOut)
def put_settings(payload: SettingsIn, family: dict = Depends(require_family_auth)):
    """가족 화면 설정 저장 (기획서 v10 B2). 현재는 fold_person_comparison_all 하나.
    then-now 카드의 fold_person_comparison_all 필드가 이 값을 그대로 받는다."""
    with db.connect() as conn:
        family_prefs.set_fold_person_all(conn, family["code"], payload.fold_person_comparison_all)
    return SettingsOut(fold_person_comparison_all=payload.fold_person_comparison_all)


def _folded_response(conn, family_code: str) -> FoldedOut:
    return FoldedOut(folded_place_ids=family_prefs.folded_place_ids(conn, family_code))


@router.post("/v1/families/me/folded-places", response_model=FoldedOut)
def post_folded_place(payload: FoldedIn, family: dict = Depends(require_family_auth)):
    """장소 접기 추가 (기획서 v10 B3). 같은 장소를 다시 보내도 목록에는 한 번만 남는다(멱등).
    존재하지 않는 장소는 404."""
    with db.connect() as conn:
        if conn.execute("SELECT 1 FROM places WHERE id = ?", (payload.place_id,)).fetchone() is None:
            raise AppError(404, "place_not_found", "존재하지 않는 장소입니다")
        family_prefs.add_folded_place(conn, family["code"], payload.place_id)
        return _folded_response(conn, family["code"])


@router.delete("/v1/families/me/folded-places/{place_id}", response_model=FoldedOut)
def delete_folded_place(place_id: str, family: dict = Depends(require_family_auth)):
    """장소 접기 해제 (기획서 v10 B3). 접지 않은 장소를 풀어도 성공(멱등)이며
    남은 접기 목록을 그대로 돌려준다."""
    with db.connect() as conn:
        family_prefs.remove_folded_place(conn, family["code"], place_id)
        return _folded_response(conn, family["code"])

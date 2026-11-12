# 가족 API (02문서 3·6-2·9·10·11절).
# POST /v1/families        : 코드+PIN 발급(멱등 아님, 항상 신규)
#   — 같은 기기 토큰(X-Device-Token, 웹이 발급·저장)으로 하루 1회, 2회째 429 device_limit
# GET  /v1/families/me/timeline : 방문·기억·맛집 통합 타임라인(최신순) — PIN 필요
# DELETE /v1/families/me   : 가족 데이터 전체 + 업로드 파일 삭제 — PIN 필요
# GET  /v1/families/me/export   : ZIP(timeline.json + photos/ + README.html) — PIN 필요
# POST /v1/families/me/rotate-code : 코드·PIN 재발급(데이터 유지) — PIN 필요(잠금 중에도 검증)
# POST /v1/families/me/food-notes  : 맛집·숙소 기록(kind 선택, 방문 1건에 묶임) — PIN 필요
import io
import json
import zipfile

from fastapi import APIRouter, Depends, File, Form, Header, Request, UploadFile
from fastapi.responses import Response

from .. import config, db, ratelimit, timeutil
from ..errors import AppError
from ..models import Deleted, FamilyCreated, FoodNoteCreated, RotatedCode, TimelineEntry
from ..security import create_family, require_family_allow_locked, require_family_auth, rotate_code_and_pin
from ..services import album, photos, summary

router = APIRouter()


@router.post("/v1/families", response_model=FamilyCreated)
def post_families(
    request: Request,
    x_device_token: str = Header(default=None),
):
    """가족 코드·PIN 발급. PIN은 이 응답 한 번으로만 전달되며
    사용자는 웹 화면에서 이를 확인해 앱·웹에 직접 입력한다(서버는 저장하지 않는 평문을 다시 보내지 않는다).
    02문서 11절: 같은 기기 토큰(X-Device-Token)으로는 하루 1회만 발급할 수 있다
    (토큰은 웹이 발급·저장해 헤더로 보낸다). 2회째는 429 device_limit."""
    day = timeutil.now_iso()[:10]
    ratelimit.check_and_record_device(x_device_token, day)
    ratelimit.check_and_record_ip(request.client.host if request.client else "unknown", day)
    return create_family()


def build_timeline(family_code: str) -> list[dict]:
    """방문(visit)·기억(memory)을 한 목록으로 합치고 최신순으로 정렬한다.
    방문 항목에는 그 방문에 달린 맛집 기록(food_notes)을 함께 실어 앱이 바로 표시하게 한다.
    기획서 v10 B1·B4·B5: 방문에는 visit_ordinal(가족·장소 기준 순번)·trip_km,
    기억에는 인물 이름표(markers)를 함께 실어 내보내기·앨범이 그대로 쓰게 한다."""
    with db.connect() as conn:
        place_names = {
            r["id"]: r["name"] for r in conn.execute("SELECT id, name FROM places").fetchall()
        }
        # 순번 계산을 위해 (장소, 시각, id) 오름차순으로 읽는다 — 표시 정렬은 마지막에 최신순.
        visit_rows = [
            dict(r)
            for r in conn.execute(
                "SELECT id, place_id, visited_at, source, vehicle_label, stamp_no, trip_km, party_size"
                " FROM visits WHERE family_code = ? ORDER BY place_id, visited_at, id",
                (family_code,),
            ).fetchall()
        ]
        memories = [
            dict(r)
            for r in conn.execute(
                "SELECT id, place_id, year, note, photo_path, vehicle_label, created_at"
                " FROM memories WHERE family_code = ?",
                (family_code,),
            ).fetchall()
        ]
        food_rows = conn.execute(
            "SELECT id, visit_id, name, note, photo_path, kind, created_at FROM food_notes"
            " WHERE family_code = ? ORDER BY created_at ASC",
            (family_code,),
        ).fetchall()
        marks = summary.markers_by_memory(conn, family_code)

    food_by_visit: dict[int, list[dict]] = {}
    for f in food_rows:
        food_by_visit.setdefault(f["visit_id"], []).append(
            {
                "id": f["id"],
                "name": f["name"],
                "note": f["note"],
                "photo_url": f"/uploads/{f['photo_path']}" if f["photo_path"] else None,
                "photo_path": f["photo_path"],
                "kind": f["kind"],
                "created_at": f["created_at"],
            }
        )

    ordinal_by_place: dict[str, int] = {}
    entries: list[dict] = []
    for v in visit_rows:  # 시간순으로 훑으며 같은 장소의 방문 순번(1부터)을 센다
        ordinal_by_place[v["place_id"]] = ordinal_by_place.get(v["place_id"], 0) + 1
        entries.append(
            {
                "kind": "visit",
                "visit_id": v["id"],
                "place_id": v["place_id"],
                "place_name": place_names.get(v["place_id"], v["place_id"]),
                "visited_at": v["visited_at"],
                "source": v["source"],
                "vehicle_label": v["vehicle_label"],
                "stamp_no": v["stamp_no"],
                "food_notes": food_by_visit.get(v["id"], []),
                "visit_ordinal": ordinal_by_place[v["place_id"]],
                "trip_km": v["trip_km"],
                "party_size": v["party_size"],  # 내보내기의 '그때 몇 명' 표기 원천(02문서 10절)
            }
        )
    for m in memories:
        entries.append(
            {
                "kind": "memory",
                "memory_id": m["id"],
                "place_id": m["place_id"],
                "place_name": place_names.get(m["place_id"], m["place_id"]),
                # 기억은 '방문 시각'이 없으므로 기록 생성 시각을 표시·정렬 기준으로 쓴다(설계 결정).
                "visited_at": m["created_at"],
                "year": m["year"],
                "note": m["note"],
                "photo_url": f"/uploads/{m['photo_path']}" if m["photo_path"] else None,
                "vehicle_label": m["vehicle_label"],
                "markers": marks.get(m["id"], []),
            }
        )
    entries.sort(key=lambda e: e["visited_at"], reverse=True)
    return entries


@router.get("/v1/families/me/timeline", response_model=list[TimelineEntry])
def get_timeline(family: dict = Depends(require_family_auth)):
    return build_timeline(family["code"])


def _family_files(family_code: str) -> list[str]:
    """삭제·내보내기 대상이 되는 이 가족의 업로드 파일명들.
    (기억·맛집 사진. 영수증 사진은 앨범 구성 요소가 아니므로 내보내기에서 제외 —
     단, 가족 삭제 시에는 함께 지운다.)"""
    with db.connect() as conn:
        paths = []
        for sql in (
            "SELECT photo_path AS p FROM memories WHERE family_code = ? AND photo_path IS NOT NULL",
            "SELECT photo_path AS p FROM food_notes WHERE family_code = ? AND photo_path IS NOT NULL",
            "SELECT photo_path AS p FROM receipts WHERE family_code = ? AND photo_path IS NOT NULL",
        ):
            paths.extend(r["p"] for r in conn.execute(sql, (family_code,)).fetchall())
    return paths


def _delete_family_rows(family_code: str) -> None:
    # 기획서 v10 신설 테이블(설정·접기·이름표·prefs)도 함께 지워 흔적을 남기지 않는다.
    tables = [
        "visits", "memories", "members", "handovers", "food_notes", "receipts", "point_ledger",
        "memory_markers", "folded_places", "family_settings", "family_prefs",
    ]
    with db.connect() as conn:
        for table in tables:
            conn.execute(f"DELETE FROM {table} WHERE family_code = ?", (family_code,))
        conn.execute("DELETE FROM families WHERE code = ?", (family_code,))


@router.delete("/v1/families/me", response_model=Deleted)
def delete_family(family: dict = Depends(require_family_auth)):
    """가족 탈퇴(전량 삭제). DB 행과 업로드 파일을 함께 지운다 — 서버에 흔적을 남기지 않는다."""
    files = _family_files(family["code"])
    _delete_family_rows(family["code"])
    for name in files:
        photos.delete_file(name)
    return Deleted(deleted=True)


@router.post("/v1/families/me/rotate-code", response_model=RotatedCode)
def post_rotate_code(family: dict = Depends(require_family_allow_locked)):
    """코드 재발급. PIN도 함께 재발급하며 기존 코드는 즉시 무효가 된다.
    잠금 중에도 PIN만 맞으면 즉시 재발급할 수 있다(잠금 탈출 경로)."""
    return rotate_code_and_pin(family["code"])


def _album_photo_files(entries: list[dict]) -> list[str]:
    """앨범(타임라인)에 실리는 사진 파일명들 — memory + food_note."""
    names = []
    for e in entries:
        if e.get("kind") == "memory" and e.get("photo_url"):
            names.append(e["photo_url"].rsplit("/", 1)[-1])
        for fn in e.get("food_notes", []) or []:
            if fn.get("photo_url"):
                names.append(fn["photo_url"].rsplit("/", 1)[-1])
    return names


@router.get("/v1/families/me/export")
def export_family(family: dict = Depends(require_family_auth)):
    """가족 데이터 내보내기 ZIP:
    timeline.json(방문·기억·맛집 + 기획서 v10 B1·B4·B5 확장) + photos/(사진) + README.html(연도순 앨범).
    timeline.json에는 방문마다 visit_ordinal·trip_km·party_size, 가족 누적 family_total_trip_km,
    상속자 정보(heir), 기억마다 인물 이름표(markers)가 포함된다.
    README.html은 외부 리소스 없이 열리는 정적 HTML이다(서버 없이도 열람 가능)."""
    entries = build_timeline(family["code"])
    exported_at = timeutil.now_iso()
    with db.connect() as conn:
        heir_row = summary.heir_of(conn, family["code"])
        trip_total = summary.total_trip_km(conn, family["code"])
    heir_info = (
        {"member_id": heir_row["id"], "nickname": heir_row["nickname"]} if heir_row else None
    )
    timeline_json = json.dumps(
        {
            "family_code": family["code"],
            "exported_at": exported_at,
            "family_total_trip_km": trip_total,
            "heir": heir_info,
            "entries": entries,
        },
        ensure_ascii=False, indent=2,
    )
    readme_html = album.build_album_html(entries, family["code"], exported_at)

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("timeline.json", timeline_json)
        zf.writestr("README.html", readme_html)
        for name in dict.fromkeys(_album_photo_files(entries)):  # 중복 제거·순서 유지
            path = photos._path(name)
            try:
                with open(path, "rb") as f:
                    zf.writestr(f"photos/{name}", f.read())
            except OSError:
                continue  # 파일이 이미 사라졌으면 항목만 빠뜨린다
    return Response(
        content=buffer.getvalue(),
        media_type="application/zip",
        headers={"Content-Disposition": f'attachment; filename="arosaegim-{family["code"]}.zip"'},
    )


FOOD_NOTE_KINDS = ("restaurant", "shop", "lodging")


@router.post("/v1/families/me/food-notes", response_model=FoodNoteCreated)
async def post_food_note(
    visit_id: int = Form(...),
    name: str = Form(...),
    note: str = Form(default=None),
    kind: str = Form(default=None),
    photo: UploadFile = File(default=None),
    family: dict = Depends(require_family_auth),
):
    """맛집·숙소 기록. 반드시 이 가족의 방문(visit) 1건에 묶인다 — 타임라인의 해당 방문 아래 표시된다.
    02문서 11절: kind('restaurant'|'shop'|'lodging')를 선택 필드로 받는다(숙소 후기도 같은 표).
    kind가 없으면 이 방문에서 최근에 인증된 영수증 제휴처의 kind를 상속하고, 그마저 없으면 null."""
    if kind is not None and kind not in FOOD_NOTE_KINDS:
        raise AppError(400, "invalid_kind", "kind는 restaurant·shop·lodging 중 하나여야 합니다")
    photo_path = None
    if photo is not None and photo.filename:
        data = await photo.read(config.MAX_PHOTO_BYTES + 1)
        photo_path = photos.save_upload(data, prefix="food")
    with db.connect() as conn:
        visit = conn.execute(
            "SELECT id FROM visits WHERE id = ? AND family_code = ?",
            (visit_id, family["code"]),
        ).fetchone()
        if visit is None:
            if photo_path:
                photos.delete_file(photo_path)  # 고아 파일 방지
            raise AppError(404, "visit_not_found", "이 가족의 방문 기록이 없습니다")
        if kind is None:
            # partner kind 상속(02문서 11절): 이 방문에 연결된 영수증의 제휴처 kind를 따른다.
            row = conn.execute(
                "SELECT p.kind AS kind FROM receipts r JOIN partners p ON p.id = r.partner_id"
                " WHERE r.family_code = ? AND r.visit_id = ?"
                " ORDER BY r.created_at DESC, r.id DESC LIMIT 1",
                (family["code"], visit_id),
            ).fetchone()
            kind = row["kind"] if row else None
        cur = conn.execute(
            "INSERT INTO food_notes (family_code, visit_id, name, note, photo_path, kind, created_at)"
            " VALUES (?,?,?,?,?,?,?)",
            (family["code"], visit_id, name, note, photo_path, kind, timeutil.now_iso()),
        )
        return FoodNoteCreated(food_note_id=cur.lastrowid)

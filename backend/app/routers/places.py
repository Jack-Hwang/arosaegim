# 장소 API (02문서 3·6-2·10절).
# - GET /v1/places/{id}      : 장소 상세(퀴즈·유산 정보 포함) — 공개
# - GET /v1/places/{id}/then-now : 그때와 지금 — 가족 코드만 필요(PIN 목록 밖의 GET)
# - GET /v1/places/{id}/partners : 제휴처(적립률) — 공개(가족 데이터 아님)
# - GET /v1/places/{id}/voice-letters : 예시 음성 편지 메타데이터(시드) — 공개
from datetime import datetime

from fastapi import APIRouter, Depends

from .. import config, db
from ..errors import AppError
from ..models import HeritageOut, PlaceOut, QuizOut, ThenNowResponse
from ..security import require_family_code_only
from ..services import then_now as then_now_svc

router = APIRouter()


def _quiz_rows(conn, place_id: str):
    rows = conn.execute(
        "SELECT q, choices, answer, explain, reviewed FROM quiz"
        " WHERE place_id = ? ORDER BY seq ASC, id ASC",
        (place_id,),
    ).fetchall()
    import json

    return [
        QuizOut(
            q=r["q"],
            choices=json.loads(r["choices"]),
            answer=r["answer"],
            explain=r["explain"],
            reviewed=bool(r["reviewed"]),
        )
        for r in rows
    ]


def row_to_place(row: dict) -> PlaceOut:
    """places 테이블 행(dict)을 PlaceOut 모델로 변환. heritage가 있으면 묶어서 반환."""
    heritage = None
    if row.get("heritage_name"):
        heritage = HeritageOut(
            name=row["heritage_name"],
            year=row.get("heritage_year"),
            label=row.get("heritage_label"),
            note=row.get("heritage_note"),
            reviewed=bool(row.get("heritage_reviewed")),
        )
    return PlaceOut(
        id=row["id"],
        name=row["name"],
        address=row.get("address"),
        lat=row.get("lat"),
        lon=row.get("lon"),
        arrive_radius_m=row.get("arrive_radius_m"),
        narration=row.get("narration"),
        heritage=heritage,
    )


@router.get("/v1/places/{place_id}", response_model=PlaceOut)
def get_place(place_id: str):
    with db.connect() as conn:
        row = conn.execute("SELECT * FROM places WHERE id = ?", (place_id,)).fetchone()
        if row is None:
            raise AppError(404, "place_not_found", "존재하지 않는 장소입니다")
        quiz = _quiz_rows(conn, place_id)
    place = row_to_place(dict(row))
    place.quiz = quiz
    return place


@router.get("/v1/places/{place_id}/then-now", response_model=ThenNowResponse)
def get_then_now(place_id: str, family: dict = Depends(require_family_code_only)):
    """그때와 지금. past는 같은 가족·같은 장소의 가장 오래된 기억(memory),
    없으면 이전 방문(visit). years_ago는 서버가 계산한다(02문서 6-2).
    02문서 10절: party_size·trip_km·heritage를 함께 돌려준다(없으면 null)."""
    with db.connect() as conn:
        place_row = conn.execute("SELECT * FROM places WHERE id = ?", (place_id,)).fetchone()
        if place_row is None:
            raise AppError(404, "place_not_found", "존재하지 않는 장소입니다")
        place = dict(place_row)
        memories = [
            dict(r)
            for r in conn.execute(
                "SELECT year, note, photo_path, party_size, vehicle_label, created_at FROM memories"
                " WHERE family_code = ? AND place_id = ?",
                (family["code"], place_id),
            ).fetchall()
        ]
        visits = [
            dict(r)
            for r in conn.execute(
                "SELECT place_id, visited_at, vehicle_label, party_size, trip_km FROM visits"
                " WHERE family_code = ? AND place_id = ? ORDER BY visited_at DESC",
                (family["code"], place_id),
            ).fetchall()
        ]
    if not visits:
        # 방문 기록이 없으면 '지금'도 없다.
        return ThenNowResponse(place_id=place_id, past=None, now=None, heritage=None)
    now_visit = visits[0]  # 최신 방문
    earlier = [v for v in visits[1:]]
    heritage = None
    if place.get("heritage_name"):
        heritage = {
            "name": place["heritage_name"],
            "year": place.get("heritage_year"),
            "label": place.get("heritage_label"),
        }
    body = then_now_svc.resolve_then_now(
        memories=memories,
        earlier_visits=earlier,
        now_visit=now_visit,
        current_year=datetime.now().year,
        heritage=heritage,
    )
    return ThenNowResponse(**body)


def _voice_letter_demo(conn, place_id: str) -> list[dict]:
    """시드의 voice_letter_demo에서 이 장소의 예시 편지 메타데이터만 뽑는다.
    then_now_demo처럼 서버 DB에는 넣지 않고 시드 파일을 직접 읽는다(02문서 2·10절)."""
    import json

    try:
        with open(config.SEED_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
    except OSError:
        return []
    demo = data.get("voice_letter_demo")
    if not demo or demo.get("place_id") != place_id:
        return []
    return [demo]


@router.get("/v1/places/{place_id}/voice-letters", response_model=list)
def get_voice_letters(place_id: str):
    """예시 음성 편지 메타데이터 목록 (02문서 10절).
    이번 범위에서는 시드 예시의 메타데이터만 돌려준다 — 음성 파일 저장·업로드 API는
    만들지 않는다(로드맵).
    주의: 음성도 사진과 마찬가지로 보호자 동의 대상이다. 업로드 기능을 넣을 때는
    memories.py의 미성년 동의 규칙(guardian_consent)을 동일하게 적용할 것."""
    with db.connect() as conn:
        if conn.execute("SELECT 1 FROM places WHERE id = ?", (place_id,)).fetchone() is None:
            raise AppError(404, "place_not_found", "존재하지 않는 장소입니다")
        demos = _voice_letter_demo(conn, place_id)
    return [
        {
            "id": d["id"],
            "sealed_label": d.get("sealed_label"),
            "simulated": bool(d.get("simulated", True)),
            "audio_url": d.get("audio"),  # 앱 내장 시드 에셋 경로(서버 업로드 아님)
        }
        for d in demos
    ]


@router.get("/v1/places/{place_id}/partners", response_model=list)
def get_place_partners(place_id: str):
    """이 장소 근처 제휴처 목록. 적립 응답이므로 simulated 플래그를 항상 실어 보낸다."""
    with db.connect() as conn:
        if conn.execute("SELECT 1 FROM places WHERE id = ?", (place_id,)).fetchone() is None:
            raise AppError(404, "place_not_found", "존재하지 않는 장소입니다")
        rows = conn.execute(
            "SELECT id, name, kind, point_rate, simulated FROM partners WHERE near_place_id = ? ORDER BY id",
            (place_id,),
        ).fetchall()
    return [
        {
            "id": r["id"],
            "name": r["name"],
            "kind": r["kind"],
            "point_rate": r["point_rate"],
            "simulated": bool(r["simulated"]),
        }
        for r in rows
    ]

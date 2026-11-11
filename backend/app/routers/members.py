# 가족 구성원·계승 API (02문서 9·10절).
# GET  /v1/families/me/members : 구성원 목록 — PIN 필요
# POST /v1/families/me/members : 구성원 추가(별칭·세대·미성년) — 첫 구성원은 owner
# POST /v1/families/me/members/{id}/consent : 보호자 동의 기록(웹 전용 화면에서 호출)
# POST /v1/families/me/heir    : 상속자 지정 — owner만 (handovers에 'designated' 기록)
# POST /v1/families/me/handover: 관리 권한 이전 — owner만 (handovers에 'transferred' 기록)
#
# 설계 결정(주석 명시): 서버 인증은 가족 단위(코드+PIN)만 있으므로 "owner만"은
# '이 가족의 owner 구성원 기준'으로 서버가 판정한다. 웹 화면이 owner 기기에서
# 호출한다고 가정하며, 개인 로그인·기기 식별은 이 서비스 범위에 없다(02문서 10절:
# 실명·이메일 입력 없음).
from fastapi import APIRouter, Depends

from .. import db, timeutil
from ..errors import AppError
from ..models import HandoverIn, HeirIn, MemberIn, MemberOut, SeatRoleIn
from ..security import require_family_auth

router = APIRouter()


def _member_dict(row) -> dict:
    d = dict(row)
    d["is_minor"] = bool(d["is_minor"])
    d["guardian_consent"] = bool(d["guardian_consent"])
    return d


def _get_member(conn, family_code: str, member_id: int):
    row = conn.execute(
        "SELECT * FROM members WHERE id = ? AND family_code = ?", (member_id, family_code)
    ).fetchone()
    if row is None:
        raise AppError(404, "member_not_found", "이 가족에 없는 구성원입니다")
    return dict(row)


def _get_owner(conn, family_code: str) -> dict:
    row = conn.execute(
        "SELECT * FROM members WHERE family_code = ? AND role = 'owner'", (family_code,)
    ).fetchone()
    if row is None:  # pragma: no cover — 첫 구성원 owner 보장으로 도달하지 않음
        raise AppError(500, "internal_error", "owner 구성원이 없습니다")
    return dict(row)


@router.get("/v1/families/me/members", response_model=list[MemberOut])
def list_members(family: dict = Depends(require_family_auth)):
    with db.connect() as conn:
        rows = conn.execute(
            "SELECT * FROM members WHERE family_code = ? ORDER BY id", (family["code"],)
        ).fetchall()
    return [_member_dict(r) for r in rows]


@router.post("/v1/families/me/members", response_model=MemberOut)
def add_member(payload: MemberIn, family: dict = Depends(require_family_auth)):
    """구성원 추가. 가족의 첫 구성원은 자동으로 owner가 된다(관리자 1명 보장).
    02문서 10절: seat_role(driver/navigator/explorer, 선택)도 받는다 — 실명이 아니므로 식별 정보가 아니다."""
    with db.connect() as conn:
        is_first = conn.execute(
            "SELECT COUNT(*) AS n FROM members WHERE family_code = ?", (family["code"],)
        ).fetchone()["n"] == 0
        cur = conn.execute(
            "INSERT INTO members (family_code, nickname, generation, role, seat_role, is_minor,"
            " guardian_consent, created_at) VALUES (?,?,?,?,?,?,0,?)",
            (
                family["code"], payload.nickname, payload.generation,
                "owner" if is_first else "member", payload.seat_role,
                1 if payload.is_minor else 0,
                timeutil.now_iso(),
            ),
        )
        row = conn.execute("SELECT * FROM members WHERE id = ?", (cur.lastrowid,)).fetchone()
        return _member_dict(row)


@router.post("/v1/families/me/members/{member_id}/seat-role")
def set_seat_role(member_id: int, payload: SeatRoleIn, family: dict = Depends(require_family_auth)):
    """오늘의 자리 이름표(T9-3) — 구성원의 seat_role 을 갱신한다(02문서 10절).

    웹이 먼저 PATCH 로 시도하고 404·405·501 이면 이 엔드포인트로 폴백한다.
    seat_role 은 'driver' | 'navigator' | 'explorer' 중 하나(실명 아님 — 식별 정보 없음).
    """
    _get_member(family["code"], member_id)  # 없으면 404
    if payload.seat_role not in {"driver", "navigator", "explorer", None}:
        # 파일 관례(AppError)를 따른다 — errors.py 가 표준 오류 형식으로 변환한다.
        raise AppError(status_code=400, code="invalid_seat_role",
                       message="자리 역할은 운전석·조수석 길잡이·뒷좌석 탐험가 중에서 골라 주세요.")
    with db.connect() as conn:
        conn.execute("UPDATE members SET seat_role = ? WHERE id = ?", (payload.seat_role, member_id))
        row = conn.execute("SELECT * FROM members WHERE id = ?", (member_id,)).fetchone()
        return _member_dict(row)


@router.post("/v1/families/me/members/{member_id}/consent")
def record_consent(member_id: int, family: dict = Depends(require_family_auth)):
    """보호자 동의 기록. true와 동의 시각을 남긴다. 되돌릴 수 없다(기록 삭제 없음)."""
    now = timeutil.now_iso()
    with db.connect() as conn:
        _get_member(conn, family["code"], member_id)
        conn.execute(
            "UPDATE members SET guardian_consent = 1, consent_at = ? WHERE id = ? AND family_code = ?",
            (now, member_id, family["code"]),
        )
    return {"member_id": member_id, "guardian_consent": True, "consent_at": now}


@router.post("/v1/families/me/heir")
def designate_heir(payload: HeirIn, family: dict = Depends(require_family_auth)):
    """상속자 지정(owner만). 이전 상속자가 있으면 member로 되돌리고,
    handovers에 status='designated' 기록을 남긴다."""
    owner = None
    with db.connect() as conn:
        owner = _get_owner(conn, family["code"])
        target = _get_member(conn, family["code"], payload.member_id)
        if target["id"] == owner["id"]:
            raise AppError(400, "already_owner", "owner를 상속자로 지정할 수 없습니다")
        # 기존 상속자 되돌리기
        conn.execute(
            "UPDATE members SET role = 'member' WHERE family_code = ? AND role = 'heir'",
            (family["code"],),
        )
        conn.execute(
            "UPDATE members SET role = 'heir' WHERE id = ? AND family_code = ?",
            (target["id"], family["code"]),
        )
        conn.execute(
            "INSERT INTO handovers (family_code, from_member, to_member, created_at, status)"
            " VALUES (?,?,?,?, 'designated')",
            (family["code"], owner["id"], target["id"], timeutil.now_iso()),
        )
    return {"member_id": target["id"], "role": "heir", "status": "designated"}


@router.post("/v1/families/me/handover")
def handover(payload: HandoverIn, family: dict = Depends(require_family_auth)):
    """관리 권한 이전(owner만). 이전 owner는 member가 되고 데이터는 전부 유지된다.
    handovers에 status='transferred' 기록을 남긴다."""
    with db.connect() as conn:
        owner = _get_owner(conn, family["code"])
        target = _get_member(conn, family["code"], payload.to_member_id)
        if target["id"] == owner["id"]:
            raise AppError(400, "already_owner", "이미 owner인 구성원입니다")
        conn.execute(
            "UPDATE members SET role = 'member' WHERE id = ? AND family_code = ?",
            (owner["id"], family["code"]),
        )
        conn.execute(
            "UPDATE members SET role = 'owner' WHERE id = ? AND family_code = ?",
            (target["id"], family["code"]),
        )
        conn.execute(
            "INSERT INTO handovers (family_code, from_member, to_member, created_at, status)"
            " VALUES (?,?,?,?, 'transferred')",
            (family["code"], owner["id"], target["id"], timeutil.now_iso()),
        )
    return {"from_member": owner["id"], "to_member": target["id"], "status": "transferred"}

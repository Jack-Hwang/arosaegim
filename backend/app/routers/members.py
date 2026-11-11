# 가족 구성원·계승 API (02문서 9·10절 + 기획서 v10 B1).
# GET  /v1/families/me/members : 구성원 목록 — PIN 필요
# POST /v1/families/me/members : 구성원 추가(별칭·세대·미성년) — 첫 구성원은 owner
# POST /v1/families/me/members/{id}/consent : 보호자 동의 기록(웹 전용 화면에서 호출)
# GET  /v1/families/me/heir    : 지정된 상속자 조회 — PIN 필요 (없으면 member_id null)
# POST /v1/families/me/heir    : 상속자 지정 — owner 전용(PIN 불일치 403, 기획서 v10 B1)
# PUT  /v1/families/me/heir    : 상속자 지정(POST와 동일 동작의 계약 경로) — 재지정 허용
# POST /v1/families/me/handover: 관리 권한 이전 — owner만 (handovers에 'transferred' 기록)
#
# 설계 결정(주석 명시): 서버 인증은 가족 단위(코드+PIN)만 있으므로 "owner만"은
# '이 가족의 owner 구성원 기준'으로 서버가 판정한다. 웹 화면이 owner 기기에서
# 호출한다고 가정하며, 개인 로그인·기기 식별은 이 서비스 범위에 없다(02문서 10절:
# 실명·이메일 입력 없음). owner 전용 경로는 security.require_owner_auth를 쓰며
# PIN이 틀리면 401이 아니라 403 not_owner로 답한다(401=미인증, 403=권한 없음).
from fastapi import APIRouter, Depends

from .. import db, timeutil
from ..errors import AppError
from ..models import HandoverIn, HeirIn, HeirOut, HeirSet, MemberIn, MemberOut, SeatRoleIn
from ..security import require_family_auth, require_owner_auth

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
    if row is None:
        # 감사 지적 S1: 관리자(owner) 구성원이 없으면 서버 오류(500)가 아니라
        # 사용자가 고칠 수 있는 요청 오류(400)로 안내한다. heir·handover 양쪽 경로가
        # 모두 이 함수를 거치므로 한 곳에서 두 경로를 함께 막는다.
        raise AppError(400, "invalid_request", "구성원을 먼저 등록해 주세요")
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


@router.get("/v1/families/me/heir", response_model=HeirOut)
def get_heir(family: dict = Depends(require_family_auth)):
    """지정된 상속자 조회 (기획서 v10 B1). 없으면 {"member_id": null} — 값을 지어내지 않는다."""
    with db.connect() as conn:
        row = conn.execute(
            "SELECT id FROM members WHERE family_code = ? AND role = 'heir' ORDER BY id LIMIT 1",
            (family["code"],),
        ).fetchone()
    return HeirOut(member_id=row["id"] if row else None)


def _set_heir(family_code: str, payload: HeirIn) -> HeirSet:
    """상속자 지정 공통 로직. 이전 상속자는 member로 되돌리고 handovers에
    status='designated' 기록을 남긴다. 다른 구성원으로 재지정도 언제든 허용된다."""
    with db.connect() as conn:
        owner = _get_owner(conn, family_code)
        target = _get_member(conn, family_code, payload.member_id)
        if target["id"] == owner["id"]:
            raise AppError(400, "already_owner", "owner를 상속자로 지정할 수 없습니다")
        conn.execute(
            "UPDATE members SET role = 'member' WHERE family_code = ? AND role = 'heir'",
            (family_code,),
        )
        conn.execute(
            "UPDATE members SET role = 'heir' WHERE id = ? AND family_code = ?",
            (target["id"], family_code),
        )
        conn.execute(
            "INSERT INTO handovers (family_code, from_member, to_member, created_at, status)"
            " VALUES (?,?,?,?, 'designated')",
            (family_code, owner["id"], target["id"], timeutil.now_iso()),
        )
    return HeirSet(member_id=target["id"])


@router.post("/v1/families/me/heir", response_model=HeirSet)
def designate_heir(payload: HeirIn, family: dict = Depends(require_owner_auth)):
    """상속자 지정 (기획서 v10 B1, POST). owner 인증 실패(PIN 불일치)는 403 not_owner,
    없는 구성원은 404, owner 지정 시도는 400 already_owner,
    구성원이 아예 없으면 400 invalid_request(감사 지적 S1)."""
    return _set_heir(family["code"], payload)


@router.put("/v1/families/me/heir", response_model=HeirSet)
def update_heir(payload: HeirIn, family: dict = Depends(require_owner_auth)):
    """상속자 지정 (기획서 v10 B1, PUT — POST와 동일 동작의 계약 경로).
    이미 상속자가 있어도 다른 구성원으로 재지정할 수 있다."""
    return _set_heir(family["code"], payload)


@router.post("/v1/families/me/handover")
def handover(payload: HandoverIn, family: dict = Depends(require_family_auth)):
    """관리 권한 이전(owner만). 이전 owner는 member가 되고 데이터는 전부 유지된다.
    handovers에 status='transferred' 기록을 남긴다.
    구성원이 아예 없으면 400 invalid_request(감사 지적 S1 — heir 경로와 동일 규칙)."""
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

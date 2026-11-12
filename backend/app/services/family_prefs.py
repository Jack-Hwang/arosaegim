# 가족 화면 설정·장소 접기 (기획서 v10 B2·B3) + 인원 비교 문장 접어 두기(15절① B4).
# - fold_person_comparison_all: then-now 카드의 '사람 비교'를 통째로 접을지(가족 단위 설정)
# - folded_places: 장소별로 then-now 카드를 접어 둔 목록
# - party_line prefs: '오늘은 N명이 함께 왔어요. 그때는 M명…' 문장을 장소별·전체로 접기(B4)
# 서버는 기본값(false)만 제공할 뿐 값을 지어내지 않는다.
from typing import List

from .. import timeutil
from ..errors import AppError


def get_fold_person_all(conn, family_code: str) -> bool:
    row = conn.execute(
        "SELECT fold_person_comparison_all FROM family_settings WHERE family_code = ?",
        (family_code,),
    ).fetchone()
    return bool(row["fold_person_comparison_all"]) if row else False


def set_fold_person_all(conn, family_code: str, value: bool) -> None:
    conn.execute(
        "INSERT INTO family_settings (family_code, fold_person_comparison_all) VALUES (?,?)"
        " ON CONFLICT(family_code) DO UPDATE SET fold_person_comparison_all = excluded.fold_person_comparison_all",
        (family_code, 1 if value else 0),
    )


def folded_place_ids(conn, family_code: str) -> List[str]:
    """접어 둔 장소 id 목록(저장 순서대로). 없으면 빈 목록."""
    rows = conn.execute(
        "SELECT place_id FROM folded_places WHERE family_code = ? ORDER BY rowid ASC",
        (family_code,),
    ).fetchall()
    return [r["place_id"] for r in rows]


def add_folded_place(conn, family_code: str, place_id: str) -> None:
    """접기 추가. 이미 접어 있어도 목록에는 한 번만 남는다(멱등)."""
    conn.execute(
        "INSERT OR IGNORE INTO folded_places (family_code, place_id, created_at) VALUES (?,?,?)",
        (family_code, place_id, timeutil.now_iso()),
    )


def remove_folded_place(conn, family_code: str, place_id: str) -> None:
    """접기 해제. 접지 않은 장소를 풀어도 조용히 통과한다(멱등)."""
    conn.execute(
        "DELETE FROM folded_places WHERE family_code = ? AND place_id = ?",
        (family_code, place_id),
    )


# ---------- B4(15절①): 인원 비교 문장 접어 두기 ----------

# 저장 상한(요청이 이상 커지는 것을 일찍 잘라낸다 — 부하·무결성 방어).
_MAX_HIDDEN_PLACES = 200
_MAX_PLACE_ID_LEN = 100


def get_party_line_prefs(conn, family_code: str) -> dict:
    """인원 비교 문장 접어 두기 설정 조회. 저장된 행이 없으면 초기값
    ({'party_line_hidden_places': [], 'party_line_all_hidden': False})을 돌려준다."""
    row = conn.execute(
        "SELECT party_line_hidden_places, party_line_all_hidden FROM family_prefs"
        " WHERE family_code = ?",
        (family_code,),
    ).fetchone()
    if row is None:
        return {"party_line_hidden_places": [], "party_line_all_hidden": False}
    raw = row["party_line_hidden_places"] or ""
    places = [p for p in (s.strip() for s in raw.split(",")) if p]  # 빈 항목 제거
    return {
        "party_line_hidden_places": places,
        "party_line_all_hidden": bool(row["party_line_all_hidden"]),
    }


def set_party_line_prefs(conn, family_code: str, hidden_places: List[str], all_hidden: bool) -> dict:
    """설정 저장(PUT = 전체 교체). 장소 id는 중복을 한 번만 남기고 저장 순서를 유지한다.
    이상한 id(빈 값·너무 긴 값·개수 초과)는 400 invalid_request로 되돌린다."""
    seen: List[str] = []
    for place_id in hidden_places:
        pid = (place_id or "").strip()
        if not pid or len(pid) > _MAX_PLACE_ID_LEN:
            raise AppError(400, "invalid_request", "place_id는 1자 이상 100자 이하여야 합니다")
        if pid not in seen:
            seen.append(pid)
    if len(seen) > _MAX_HIDDEN_PLACES:
        raise AppError(400, "invalid_request", f"접어 둔 장소는 최대 {_MAX_HIDDEN_PLACES}개까지 저장합니다")
    conn.execute(
        "INSERT INTO family_prefs (family_code, party_line_hidden_places, party_line_all_hidden)"
        " VALUES (?,?,?) ON CONFLICT(family_code) DO UPDATE SET"
        " party_line_hidden_places = excluded.party_line_hidden_places,"
        " party_line_all_hidden = excluded.party_line_all_hidden",
        (family_code, ",".join(seen), 1 if all_hidden else 0),
    )
    return {"party_line_hidden_places": seen, "party_line_all_hidden": bool(all_hidden)}

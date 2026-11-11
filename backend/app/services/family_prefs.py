# 가족 화면 설정·장소 접기 (기획서 v10 B2·B3).
# - fold_person_comparison_all: then-now 카드의 '사람 비교'를 통째로 접을지(가족 단위 설정)
# - folded_places: 장소별로 then-now 카드를 접어 둔 목록
# 서버는 기본값(false)만 제공할 뿐 값을 지어내지 않는다.
from typing import List

from .. import timeutil


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

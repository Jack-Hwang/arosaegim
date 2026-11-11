# 가족 요약 계산 공용 헬퍼 (기획서 v10 B1·B4·B5).
# - visit_ordinal: 가족·장소 기준 몇 번째 방문인지 (1 = 첫 방문)
# - family_total_trip_km: 코스 주행 1회(course_run)당 1번만 합산한 가족 누적 주행거리
# - memory markers: 그때 기록 사진의 인물 이름표 읽기
# then-now·timeline·export·metrics가 모두 같은 규칙을 쓰도록 한 곳에 모은다.
# 값이 없으면 0/빈 목록/null을 그대로 돌려준다 — 서버가 값을 지어내지 않는다.
from typing import Dict, List

from . import family_prefs


def visit_ordinal(conn, family_code: str, visit: dict) -> int:
    """한 방문의 순번. 같은 가족·같은 장소 방문 중 (visited_at, id) 오름차순에서의 위치(1부터).
    같은 분에 서로 다른 코스로 두 번 찍힌 방문은 id 순으로 만 위·아래로 정렬된다."""
    row = conn.execute(
        "SELECT COUNT(*) AS n FROM visits WHERE family_code = ? AND place_id = ?"
        " AND (visited_at < ? OR (visited_at = ? AND id <= ?))",
        (family_code, visit["place_id"], visit["visited_at"], visit["visited_at"], visit["id"]),
    ).fetchone()
    return int(row["n"])


def total_trip_km(conn, family_code: str) -> float:
    """가족 누적 주행거리(km). trip_km가 기록된 visit만 대상이고(없는 visit은 0 취급),
    같은 course_run(=코스 주행 1회)의 거리는 한 번만 더한다.
    run 안의 여러 visit이 같은 코스 거리를 각각 기록해도 MAX로 한 번만 반영된다.
    course_run이 비어 있는 오래된 행은 (백필로 채워지므로 사실상 없지만) 방문 건별로 센다."""
    row = conn.execute(
        "SELECT COALESCE(SUM(km), 0) AS total FROM ("
        "  SELECT MAX(trip_km) AS km FROM visits"
        "  WHERE family_code = ? AND trip_km IS NOT NULL"
        "  GROUP BY COALESCE(course_run, CAST(id AS TEXT))"
        ")",
        (family_code,),
    ).fetchone()
    return float(row["total"])


def markers_by_memory(conn, family_code: str) -> Dict[int, List[dict]]:
    """{memory_id: [{x, y, name}, ...]} — 입력 순서(seq)대로. 이름표가 없는 기억은 목록에 없다."""
    rows = conn.execute(
        "SELECT memory_id, x, y, name FROM memory_markers"
        " WHERE family_code = ? ORDER BY memory_id ASC, seq ASC, id ASC",
        (family_code,),
    ).fetchall()
    out: Dict[int, List[dict]] = {}
    for r in rows:
        out.setdefault(r["memory_id"], []).append({"x": r["x"], "y": r["y"], "name": r["name"]})
    return out


def heir_of(conn, family_code: str):
    """지정된 상속자(member) 행. 없으면 None."""
    return conn.execute(
        "SELECT * FROM members WHERE family_code = ? AND role = 'heir' ORDER BY id LIMIT 1",
        (family_code,),
    ).fetchone()


def fetch_memories_with_markers(conn, family_code: str, place_id: str) -> List[dict]:
    """그때 기록(memory) 목록에 인물 이름표(markers)를 붙여 돌려준다(기획서 v10 B5)."""
    memories = [
        dict(r)
        for r in conn.execute(
            "SELECT id, year, note, photo_path, party_size, vehicle_label, created_at FROM memories"
            " WHERE family_code = ? AND place_id = ?",
            (family_code, place_id),
        ).fetchall()
    ]
    marks = markers_by_memory(conn, family_code)
    for m in memories:
        m["markers"] = marks.get(m["id"], [])
    return memories


def then_now_body(conn, family_code: str, place: dict, now_visit: dict) -> dict:
    """then-now 카드 본문(dict) — then-now GET 두 경로와 방문 등록 응답이 함께 쓴다.
    past(가장 오래된 기억, 없으면 이전 방문) + now + heritage +
    visit_ordinal(이번 방문 순번)·past_visit_count·fold_person_comparison_all·folded(기획서 v10 B1~B3)."""
    memories = fetch_memories_with_markers(conn, family_code, place["id"])
    earlier = [
        dict(r)
        for r in conn.execute(
            "SELECT place_id, visited_at, vehicle_label, party_size FROM visits"
            " WHERE family_code = ? AND place_id = ? AND visited_at < ?",
            (family_code, place["id"], now_visit["visited_at"]),
        ).fetchall()
    ]
    ordinal = visit_ordinal(conn, family_code, now_visit)
    heritage = None
    if place.get("heritage_name"):
        heritage = {
            "name": place["heritage_name"],
            "year": place.get("heritage_year"),
            "label": place.get("heritage_label"),
        }
    return {
        "memories": memories,
        "earlier_visits": earlier,
        "heritage": heritage,
        "visit_ordinal": ordinal,
        "past_visit_count": max(0, ordinal - 1),
        "fold_person_comparison_all": family_prefs.get_fold_person_all(conn, family_code),
        "folded": place["id"] in family_prefs.folded_place_ids(conn, family_code),
    }

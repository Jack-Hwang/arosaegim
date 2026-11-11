# '그때와 지금' 해석 규칙 — 순수 함수 모듈 (02문서 6-2·8·10절).
# DB나 HTTP에 의존하지 않으므로 단위 테스트로 규칙 자체를 검증한다.
#
# 규칙 (02문서):
# - past = 같은 가족·같은 장소의 가장 오래된 memories. 없으면 그보다 이전의 visits.
# - years_ago는 서버가 계산한다. memory 기준: 현재 연도 - memory.year.
#   visit 기준: '지금' 방문 연도 - 과거 방문 연도.
# - now는 '지금' 방문 1건.
# - 10절 추가: past.party_size / now.party_size / now.trip_km / heritage {name, year, label}
#   (heritage는 장소 시드 값. 호출부가 넘겨주지 않으면 null — 없는 장소도 카드는 만든다).

from typing import Dict, List, Optional


def _photo_url(photo_path: Optional[str]) -> Optional[str]:
    """DB에는 파일명만 저장하고 API에는 /uploads URL로 내보낸다 (서버 URL 규약)."""
    if not photo_path:
        return None
    return f"/uploads/{photo_path}"


def _year_of_iso(iso: str) -> int:
    return int(iso[:4])


def pick_oldest_memory(memories: List[Dict]) -> Optional[Dict]:
    """가장 오래된 memory: year 오름차순, 같은 해면 created_at 오름차순."""
    if not memories:
        return None
    return min(memories, key=lambda m: (m["year"], m.get("created_at") or ""))


def pick_earliest_visit(visits: List[Dict]) -> Optional[Dict]:
    """가장 이른 visit: visited_at 오름차순 (호출부가 이미 '이전 방문'만 넘긴다)."""
    if not visits:
        return None
    return min(visits, key=lambda v: v["visited_at"])


def _heritage_brief(heritage: Optional[Dict]) -> Optional[Dict]:
    """{name, year, label} 요약. 유산 이름이 없는 장소는 null(02문서 10절)."""
    if not heritage or not heritage.get("name"):
        return None
    return {
        "name": heritage["name"],
        "year": heritage.get("year"),
        "label": heritage.get("label"),
    }


def resolve_then_now(
    *,
    memories: List[Dict],
    earlier_visits: List[Dict],
    now_visit: Dict,
    current_year: int,
    heritage: Optional[Dict] = None,
) -> Dict:
    """then_now 응답 본문을 만든다.
    memories/earlier_visits는 같은 (family_code, place_id) 범위의 dict 목록.
    earlier_visits는 now_visit보다 이전 방문만 담겨 있다고 가정한다(호출부 책임).
    heritage는 places 테이블의 유산 정보 dict(heritage_name 등) 또는 None."""
    now = {
        "visited_at": now_visit["visited_at"],
        "vehicle_label": now_visit.get("vehicle_label"),
        "party_size": now_visit.get("party_size"),
        "trip_km": now_visit.get("trip_km"),
    }

    past = None
    mem = pick_oldest_memory(memories)
    if mem is not None:
        past = {
            "kind": "memory",
            "year": mem["year"],
            "years_ago": max(0, current_year - mem["year"]),
            "note": mem.get("note"),
            "photo_url": _photo_url(mem.get("photo_path")),
            "vehicle_label": mem.get("vehicle_label"),
            "party_size": mem.get("party_size"),
        }
    else:
        visit = pick_earliest_visit(earlier_visits)
        if visit is not None and visit["visited_at"] < now_visit["visited_at"]:
            past_year = _year_of_iso(visit["visited_at"])
            past = {
                "kind": "visit",
                "year": past_year,
                "years_ago": max(0, _year_of_iso(now_visit["visited_at"]) - past_year),
                "note": None,
                "photo_url": None,
                "vehicle_label": visit.get("vehicle_label"),
                "party_size": visit.get("party_size"),
            }

    return {
        "place_id": now_visit["place_id"],
        "past": past,
        "now": now,
        "heritage": _heritage_brief(heritage),
    }

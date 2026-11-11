# 내보내기(export)용 앨범 HTML 생성 — 순수 함수 모듈.
# 서버 없이도 열리는 정적 HTML(README.html)을 만든다. 사진은 ZIP 안의
# photos/<파일명> 상대경로로 참조하고, 모든 사용자 입력 문자열은 html.escape로
# 이스케이프한다. 외부 리소스(폰트·CDN) 없이 동작해야 한다.
import html
from typing import Dict, List


def build_album_html(entries: List[Dict], family_code: str, exported_at: str) -> str:
    """타임라인 항목을 연도순으로 묶은 앨범 HTML을 만든다.
    entries는 families 라우터의 build_timeline() 결과(dict)를 그대로 받는다."""
    # 연도 기준 그룹핑: visit은 방문 시각, memory는 기록의 year(없으면 created_at)
    by_year: Dict[str, List[Dict]] = {}
    for e in entries:
        year = str(_entry_year(e))
        by_year.setdefault(year, []).append(e)

    parts = [
        "<!DOCTYPE html>",
        '<html lang="ko">',
        "<head>",
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        f"<title>아로새김 앨범 ({html.escape(family_code)})</title>",
        "<style>",
        "body{font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;"
        "margin:0 auto;max-width:720px;padding:24px;line-height:1.6;color:#222;background:#faf7f2}",
        "h1{font-size:1.5em;border-bottom:2px solid #d8542e;padding-bottom:8px}",
        "h2{margin-top:32px;color:#d8542e}",
        ".entry{background:#fff;border:1px solid #e5ddd0;border-radius:8px;"
        "padding:12px 16px;margin:12px 0}",
        ".entry img{max-width:100%;border-radius:6px;margin-top:8px}",
        ".meta{color:#777;font-size:0.85em}",
        ".kind{display:inline-block;font-size:0.75em;border-radius:4px;padding:1px 8px;"
        "margin-right:6px;background:#eee}",
        ".kind.visit{background:#ffe9d6}.kind.memory{background:#e2ecff}.kind.food{background:#e7f5e0}",
        "footer{margin-top:40px;color:#999;font-size:0.8em}",
        "</style>",
        "</head>",
        "<body>",
        "<h1>아로새김 가족 앨범</h1>",
        f'<p class="meta">가족 코드: {html.escape(family_code)} · 내보낸 시각: {html.escape(exported_at)}</p>',
        "<p>이 파일은 서버 없이 열 수 있는 정적 앨범입니다. 사진은 함께 압축된 photos/ 폴더에 있습니다.</p>",
    ]

    for year in sorted(by_year.keys()):
        parts.append(f"<h2>{html.escape(year)}년</h2>")
        for e in by_year[year]:
            kind = e["kind"]
            kind_label = {"visit": "방문", "memory": "기억", "food": "맛집"}.get(kind, kind)
            stamp = f" · 스탬프 #{e['stamp_no']}" if e.get("stamp_no") else ""
            if e.get("visit_ordinal"):
                stamp += f" · {e['visit_ordinal']}번째 방문"
            if e.get("trip_km") is not None:
                stamp += f" · {e['trip_km']}km"
            parts.append('<div class="entry">')
            parts.append(
                f'<span class="kind {html.escape(kind)}">{html.escape(kind_label)}</span>'
                f"<strong>{html.escape(e['place_name'])}</strong>"
                f'<span class="meta"> {html.escape(e["visited_at"])}{html.escape(stamp)}</span>'
            )
            if e.get("markers"):
                names = ", ".join(html.escape(m["name"]) for m in e["markers"])
                parts.append(f'<div class="meta">함께한 사람: {names}</div>')
            if e.get("vehicle_label"):
                parts.append(f'<div class="meta">차량: {html.escape(e["vehicle_label"])}</div>')
            if e.get("note"):
                parts.append(f"<p>{html.escape(e['note'])}</p>")
            if e.get("year") and kind == "memory":
                parts.append(f'<div class="meta">그때: {e["year"]}년</div>')
            for fn in e.get("food_notes", []) or []:
                parts.append('<div class="entry">')
                parts.append(
                    '<span class="kind food">맛집</span>'
                    f"<strong>{html.escape(fn['name'])}</strong>"
                    f'<span class="meta"> {html.escape(fn.get("created_at") or "")}</span>'
                )
                if fn.get("note"):
                    parts.append(f"<p>{html.escape(fn['note'])}</p>")
                if fn.get("photo_path"):
                    parts.append(f'<img src="photos/{html.escape(fn["photo_path"])}" alt="{html.escape(fn["name"])}">')
                parts.append("</div>")
            if e.get("photo_path"):
                parts.append(f'<img src="photos/{html.escape(e["photo_path"])}" alt="">')
            parts.append("</div>")

    parts.append(
        f'<footer>아로새김 내보내기 · 항목 {len(entries)}건 · 생성 {html.escape(exported_at)}</footer>'
    )
    parts.append("</body></html>")
    return "\n".join(parts)


def _entry_year(entry: Dict) -> int:
    if entry.get("kind") == "memory" and entry.get("year"):
        return int(entry["year"])
    return int(str(entry.get("visited_at", ""))[:4] or 0)

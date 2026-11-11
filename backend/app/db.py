# 데이터베이스 계층 (02문서 2·9절 데이터 모델).
# - SQLite 표준 라이브러리(sqlite3) 사용. 외부 서비스(Firebase 등) 의존 없음.
# - 요청마다 새 연결을 열어 스레드 문제를 피한다(짧은 트랜잭션 위주).
# - 서버 시작 시 시드(seed/seed.json)를 읽어 기준 데이터를 채운다.
#   이미 같은 id의 행이 있으면 건너뛴다(INSERT OR IGNORE).
# - then_now_demo는 서버에 넣지 않는다(voice_letter_demo도 라우터가 시드에서 읽어 메타데이터만 돌려준다).
import json
import sqlite3
from contextlib import contextmanager

from . import config

# receipts 테이블 정의만 따로 떼어 둔다(02문서 11절). 이유: status CHECK 제약은
# ALTER TABLE로 고칠 수 없어서, 이전 버전 DB는 표를 재생성(행 복사)해야 하기 때문이다.
_RECEIPTS_COLUMNS = """(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code   TEXT NOT NULL,
    partner_id    TEXT NOT NULL,
    visit_id      INTEGER,
    amount_krw    INTEGER NOT NULL,
    receipt_key   TEXT NOT NULL UNIQUE,
    photo_path    TEXT,
    status        TEXT NOT NULL CHECK (status IN ('accepted','rejected','pending')),
    paid_at       TEXT NOT NULL,   -- 결제(적립) 시각(분 단위 ISO). 일 단위 규칙·한도 판정용
    stay_checkin  TEXT,            -- 숙소(lodging)만: 체크인 YYYY-MM-DD (02문서 11절)
    stay_checkout TEXT,            -- 숙소(lodging)만: 체크아웃 YYYY-MM-DD
    photo_hash    TEXT,            -- 사진 원본 바이트 sha256. 같은 사진 재제출 차단용
    created_at    TEXT NOT NULL
)"""

_SCHEMA = f"""
CREATE TABLE IF NOT EXISTS families (
    code            TEXT PRIMARY KEY,
    created_at      TEXT NOT NULL,
    pin_hash        TEXT NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TEXT
);
CREATE TABLE IF NOT EXISTS places (
    id                 TEXT PRIMARY KEY,
    name               TEXT NOT NULL,
    address            TEXT,
    lat                REAL,
    lon                REAL,
    coord_verified     INTEGER NOT NULL DEFAULT 0,
    coord_note         TEXT,
    arrive_radius_m    REAL,
    narration          TEXT,
    narration_reviewed INTEGER NOT NULL DEFAULT 0,
    heritage_name      TEXT,
    heritage_year      INTEGER,
    heritage_label     TEXT,
    heritage_note      TEXT,
    heritage_reviewed  INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS courses (
    id      TEXT PRIMARY KEY,
    title   TEXT NOT NULL,
    summary TEXT
);
CREATE TABLE IF NOT EXISTS course_places (
    course_id TEXT NOT NULL,
    place_id  TEXT NOT NULL,
    seq       INTEGER NOT NULL,
    PRIMARY KEY (course_id, place_id)
);
CREATE TABLE IF NOT EXISTS quiz (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    place_id TEXT NOT NULL,
    q        TEXT NOT NULL,
    choices  TEXT NOT NULL,   -- JSON 배열 문자열
    answer   INTEGER NOT NULL,
    explain  TEXT,
    reviewed INTEGER NOT NULL DEFAULT 0,
    seq      INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS visits (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code   TEXT NOT NULL,
    place_id      TEXT NOT NULL,
    course_id     TEXT NOT NULL,
    visited_at    TEXT NOT NULL,  -- 분 단위 정규화 ISO (멱등 판정 키)
    source        TEXT NOT NULL CHECK (source IN ('fused','navi','manual')),
    vehicle_label TEXT,
    party_size    INTEGER,        -- 좌석 신호로 읽은 인원 수(식별 정보 없음, 02문서 10절)
    trip_km       REAL,           -- 코스 시작~완주 주행거리 차이(02문서 10절)
    course_run    TEXT,           -- '코스 주행 1회' 식별(기획서 v10 B4). 같은 run의 visit은
                                  -- trip_km를 지표에 한 번만 합산한다. 클라이언트가 course_run_id를
                                  -- 보내면 그 값을 쓰고, 없으면 '<course_id>#<방문 날짜>'로 서버가 유도한다.
    stamp_no      INTEGER NOT NULL DEFAULT 0,
    created_at    TEXT NOT NULL,
    UNIQUE (family_code, place_id, course_id, visited_at)
);
CREATE TABLE IF NOT EXISTS memories (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code   TEXT NOT NULL,
    place_id      TEXT NOT NULL,
    year          INTEGER NOT NULL,
    note          TEXT,
    photo_path    TEXT,           -- 파일명만 저장 (URL은 /uploads/<파일명>)
    party_size    INTEGER,        -- 그때 함께 간 인원(가족이 입력, 02문서 10절)
    audio_path    TEXT,           -- 음성 편지(이번 범위는 시드 예시만, 업로드 API는 로드맵)
    vehicle_label TEXT,
    created_at    TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS offers (
    id        TEXT PRIMARY KEY,
    title     TEXT NOT NULL,
    condition TEXT NOT NULL,
    simulated INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS members (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code     TEXT NOT NULL,
    nickname        TEXT NOT NULL,
    generation      TEXT,
    role            TEXT NOT NULL DEFAULT 'member'
                    CHECK (role IN ('owner','member','heir')),
    seat_role       TEXT,           -- 'driver'|'navigator'|'explorer' (선택, 실명 아님 — 02문서 10절)
    is_minor        INTEGER NOT NULL DEFAULT 0,
    guardian_consent INTEGER NOT NULL DEFAULT 0,
    consent_at      TEXT,
    created_at      TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS handovers (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code TEXT NOT NULL,
    from_member INTEGER NOT NULL,
    to_member   INTEGER NOT NULL,
    created_at  TEXT NOT NULL,
    status      TEXT NOT NULL CHECK (status IN ('designated','transferred'))
);
CREATE TABLE IF NOT EXISTS food_notes (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code TEXT NOT NULL,
    visit_id    INTEGER NOT NULL,
    name        TEXT NOT NULL,
    note        TEXT,
    photo_path  TEXT,
    kind        TEXT,           -- 'restaurant'|'shop'|'lodging' (숙소 후기도 같은 표 — 02문서 11절)
    created_at  TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS stamp_books (
    id      TEXT PRIMARY KEY,
    title   TEXT NOT NULL,
    region  TEXT,
    country TEXT,
    badge   TEXT
);
CREATE TABLE IF NOT EXISTS stamp_book_places (
    book_id  TEXT NOT NULL,
    place_id TEXT NOT NULL,
    PRIMARY KEY (book_id, place_id)
);
CREATE TABLE IF NOT EXISTS partners (
    id            TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    kind          TEXT,
    near_place_id TEXT,
    point_rate    REAL NOT NULL,
    simulated     INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS receipts {_RECEIPTS_COLUMNS}
;
CREATE TABLE IF NOT EXISTS point_ledger (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code TEXT NOT NULL,
    receipt_id  INTEGER NOT NULL,
    points      INTEGER NOT NULL,
    reason      TEXT,
    created_at  TEXT NOT NULL
);
-- 기획서 v10 B2: 가족 단위 화면 설정(지금은 '사람 비교 접기' 하나).
CREATE TABLE IF NOT EXISTS family_settings (
    family_code                TEXT PRIMARY KEY,
    fold_person_comparison_all INTEGER NOT NULL DEFAULT 0
);
-- 기획서 v10 B3: 장소별 접기. then-now 카드의 folded 필드와 목록 응답의 원천.
CREATE TABLE IF NOT EXISTS folded_places (
    family_code TEXT NOT NULL,
    place_id    TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    PRIMARY KEY (family_code, place_id)
);
-- 기획서 v10 B5: 그때 기록 사진의 인물 이름표(단순 저장 — 얼굴 인식 일절 없음).
-- x·y는 사진 크기 대비 0..1 비율이다.
CREATE TABLE IF NOT EXISTS memory_markers (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    family_code TEXT NOT NULL,
    memory_id   INTEGER NOT NULL,
    x           REAL NOT NULL,
    y           REAL NOT NULL,
    name        TEXT NOT NULL,
    seq         INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_visits_family ON visits (family_code, place_id, visited_at);
CREATE INDEX IF NOT EXISTS idx_memories_family ON memories (family_code, place_id);
CREATE INDEX IF NOT EXISTS idx_food_notes_visit ON food_notes (visit_id);
CREATE INDEX IF NOT EXISTS idx_memory_markers_mem ON memory_markers (family_code, memory_id);
"""


@contextmanager
def connect():
    """요청(또는 서비스) 단위로 짧게 열고 닫는 SQLite 연결."""
    conn = sqlite3.connect(config.DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def ensure_dirs() -> None:
    """정적 서빙 대상 디렉터리가 없으면 만든다.
    web/ 폴더는 웹 담당이 채우지만, 서버가 먼저 떠야 하므로 비어 있어도 만들어 둔다."""
    for path in (config.UPLOAD_DIR, config.WEB_DIR):
        os_makedirs(path)


def os_makedirs(path: str) -> None:
    import os

    os.makedirs(path, exist_ok=True)


def init_db() -> None:
    ensure_dirs()
    with connect() as conn:
        conn.executescript(_SCHEMA)
        _migrate_schema(conn)


def _migrate_schema(conn: sqlite3.Connection) -> None:
    """이전 버전 DB 파일을 02문서 10·11절 스키마로 올린다(멱등).
    CREATE TABLE IF NOT EXISTS는 이미 있는 테이블을 고치지 않으므로:
    (1) 빠진 컬럼은 ALTER TABLE ADD COLUMN 으로 붙인다.
    (2) receipts는 status CHECK에 'pending'이 없는 옛 표라면 행을 옮겨 재생성한다
        (영수증은 시뮬레이션 적립 기록이므로 그대로 복사한다)."""
    for table, name, decl in (
        ("visits", "party_size", "INTEGER"),
        ("visits", "trip_km", "REAL"),
        ("visits", "course_run", "TEXT"),
        ("memories", "party_size", "INTEGER"),
        ("memories", "audio_path", "TEXT"),
        ("members", "seat_role", "TEXT"),
        ("food_notes", "kind", "TEXT"),
        ("receipts", "stay_checkin", "TEXT"),
        ("receipts", "stay_checkout", "TEXT"),
        ("receipts", "photo_hash", "TEXT"),
    ):
        cols = {r[1] for r in conn.execute(f"PRAGMA table_info({table})").fetchall()}
        if cols and name not in cols:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {decl}")

    # course_run 백필(멱등): 기존 방문에는 '코스#방문 날짜'를 채운다.
    # 같은 코스를 같은 날 여러 장소에서 찍은 visit들이 한 번의 주행으로 묶여
    # family_total_trip_km 중복 합산이 방지된다. 새 요청은 항상 값을 채워 넣는다.
    conn.execute(
        "UPDATE visits SET course_run = course_id || '#' || substr(visited_at, 1, 10)"
        " WHERE course_run IS NULL"
    )
    # 이 인덱스는 course_run 컬럼이 생긴 뒤에 만들어야 하므로 마이그레이션에서 실행한다.
    conn.execute("CREATE INDEX IF NOT EXISTS idx_visits_course_run ON visits (family_code, course_run)")

    row = conn.execute(
        "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'receipts'"
    ).fetchone()
    if row is not None and "pending" not in (row["sql"] or ""):
        conn.execute(f"CREATE TABLE receipts_new {_RECEIPTS_COLUMNS}")
        conn.execute(
            "INSERT INTO receipts_new (id, family_code, partner_id, visit_id, amount_krw,"
            " receipt_key, photo_path, status, paid_at, created_at)"
            " SELECT id, family_code, partner_id, visit_id, amount_krw,"
            " receipt_key, photo_path, status, paid_at, created_at FROM receipts"
        )
        conn.execute("DROP TABLE receipts")
        conn.execute("ALTER TABLE receipts_new RENAME TO receipts")


def _seed(conn: sqlite3.Connection, data: dict) -> None:
    """시드 JSON → 기준 테이블. 이미 있는 id는 건너뛴다.
    then_now_demo는 앱(오프라인) 전용이므로 서버에 넣지 않는다(02문서 2절)."""
    for p in data.get("places", []):
        heritage = p.get("heritage") or {}
        conn.execute(
            "INSERT OR IGNORE INTO places (id, name, address, lat, lon, coord_verified,"
            " coord_note, arrive_radius_m, narration, narration_reviewed,"
            " heritage_name, heritage_year, heritage_label, heritage_note, heritage_reviewed)"
            " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (
                p["id"], p["name"], p.get("address"), p.get("lat"), p.get("lon"),
                1 if p.get("coord_verified") else 0, p.get("coord_note"), p.get("arrive_radius_m"),
                p.get("narration"), 1 if p.get("narration_reviewed") else 0,
                heritage.get("name"), heritage.get("year"), heritage.get("label"),
                heritage.get("note"), 1 if heritage.get("reviewed") else 0,
            ),
        )
        for seq, q in enumerate(p.get("quiz", [])):
            conn.execute(
                "INSERT INTO quiz (place_id, q, choices, answer, explain, reviewed, seq)"
                " SELECT ?, ?, ?, ?, ?, ?, ?"
                " WHERE NOT EXISTS (SELECT 1 FROM quiz WHERE place_id = ? AND q = ?)",
                (
                    p["id"], q["q"], json.dumps(q.get("choices", []), ensure_ascii=False),
                    q["answer"], q.get("explain"), 1 if q.get("reviewed") else 0, seq,
                    p["id"], q["q"],
                ),
            )

    course = data.get("course")
    if course:
        conn.execute(
            "INSERT OR IGNORE INTO courses (id, title, summary) VALUES (?,?,?)",
            (course["id"], course["title"], course.get("summary")),
        )
        for seq, place_id in enumerate(course.get("place_ids", [])):
            conn.execute(
                "INSERT OR IGNORE INTO course_places (course_id, place_id, seq) VALUES (?,?,?)",
                (course["id"], place_id, seq),
            )

    for o in data.get("offers_demo", []):
        conn.execute(
            "INSERT OR IGNORE INTO offers (id, title, condition, simulated) VALUES (?,?,?,?)",
            (o["id"], o["title"], o.get("condition", ""), 1 if o.get("simulated", True) else 0),
        )

    for b in data.get("stamp_books", []):
        conn.execute(
            "INSERT OR IGNORE INTO stamp_books (id, title, region, country, badge) VALUES (?,?,?,?,?)",
            (b["id"], b["title"], b.get("region"), b.get("country"), b.get("badge")),
        )
        for place_id in b.get("place_ids", []):
            conn.execute(
                "INSERT OR IGNORE INTO stamp_book_places (book_id, place_id) VALUES (?,?)",
                (b["id"], place_id),
            )

    for pt in data.get("partners_demo", []):
        conn.execute(
            "INSERT OR IGNORE INTO partners (id, name, kind, near_place_id, point_rate, simulated)"
            " VALUES (?,?,?,?,?,?)",
            (
                pt["id"], pt["name"], pt.get("kind"), pt.get("near_place_id"),
                pt.get("point_rate", 0.0), 1 if pt.get("simulated", True) else 0,
            ),
        )


def load_seed() -> None:
    """seed/seed.json 을 읽어 기준 테이블을 채운다. 파일이 없으면 시작을 계속한다
    (기준 데이터 없이도 가족·기록 API는 동작해야 하므로)."""
    import os

    if not os.path.exists(config.SEED_PATH):
        return
    with open(config.SEED_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)
    with connect() as conn:
        _seed(conn, data)


def init_all() -> None:
    """앱 기동(및 테스트)에서 호출하는 초기화. 몇 번을 불러도 안전하다."""
    init_db()
    load_seed()

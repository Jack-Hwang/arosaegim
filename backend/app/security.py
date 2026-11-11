# 가족 인증 (02문서 9·10절).
# 설계 결정(주석 명시): 가족 코드(X-Family-Code)는 "어느 가족의 데이터인지" 식별용이고,
# 열람·수정 권한은 추가로 X-Family-Pin 으로 확인한다. PIN은 서버가 6자리로 발급하며
# 소금을 친 SHA-256 해시만 저장한다. 5회 연속 실패 시 10분 잠금(locked_until).
import hashlib
import hmac
import secrets
from datetime import datetime, timedelta

from fastapi import Header

from . import config, db, timeutil
from .errors import AppError


def _hash_pin(pin: str, salt: str) -> str:
    return hashlib.sha256((salt + pin).encode("utf-8")).hexdigest()


def verify_pin(pin: str, stored: str) -> bool:
    """stored 형식: '<salt>$<sha256hex>'"""
    try:
        salt, digest = stored.split("$", 1)
    except ValueError:
        return False
    return hmac.compare_digest(_hash_pin(pin, salt), digest)


def generate_family_code() -> str:
    alphabet = config.FAMILY_CODE_ALPHABET
    return "".join(secrets.choice(alphabet) for _ in range(config.FAMILY_CODE_LENGTH))


def generate_pin() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


def create_family() -> dict:
    """새 가족 코드·PIN을 발급한다. POST /v1/families 는 멱등이 아니므로 항상 신규."""
    with db.connect() as conn:
        for _ in range(20):  # 충돌은 사실상 없지만 안전하게 재시도
            code = generate_family_code()
            if conn.execute("SELECT 1 FROM families WHERE code = ?", (code,)).fetchone() is None:
                break
        else:  # pragma: no cover
            raise AppError(500, "internal_error", "가족 코드 발급 실패")
        pin = generate_pin()
        salt = secrets.token_hex(8)
        conn.execute(
            "INSERT INTO families (code, created_at, pin_hash, failed_attempts) VALUES (?,?,?,0)",
            (code, timeutil.now_iso(), f"{salt}${_hash_pin(pin, salt)}"),
        )
    return {"family_code": code, "pin": pin}


def _fail_pin(family_code: str, attempts: int) -> None:
    """PIN 실패 1회 기록. 5회째에서 10분 잠금을 걸고 카운터를 되돌린다."""
    locked_until = None
    if attempts + 1 >= config.PIN_LOCK_ATTEMPTS:
        locked_until = (datetime.now() + timedelta(minutes=config.PIN_LOCK_MINUTES)).strftime(
            "%Y-%m-%dT%H:%M:%S"
        )
        attempts = -1  # 잠금 후 0으로 초기화
    with db.connect() as conn:
        conn.execute(
            "UPDATE families SET failed_attempts = ?, locked_until = ? WHERE code = ?",
            (attempts + 1, locked_until, family_code),
        )


def _is_locked(row) -> bool:
    locked_until = row["locked_until"]
    if not locked_until:
        return False
    try:
        return datetime.now() < timeutil.parse_iso(locked_until)
    except timeutil.InvalidDatetime:
        return False


def get_family(x_family_code: str) -> dict:
    """가족 행 조회. 코드 헤더 누락은 401, 모르는 코드는 404."""
    if not x_family_code:
        raise AppError(401, "family_code_required", "X-Family-Code 헤더가 필요합니다")
    with db.connect() as conn:
        row = conn.execute("SELECT * FROM families WHERE code = ?", (x_family_code,)).fetchone()
    if row is None:
        raise AppError(404, "family_not_found", "존재하지 않는 가족 코드입니다")
    return dict(row)


def require_family_auth(
    x_family_code: str = Header(default=None),
    x_family_pin: str = Header(default=None),
) -> dict:
    """PIN이 필요한 엔드포인트용 의존성.
    (timeline·members·export·points·모든 POST/DELETE — 02문서 설계 결정)"""
    return _authenticate(x_family_code, x_family_pin, pin_required=True, allow_when_locked=False)


def require_family_code_only(
    x_family_code: str = Header(default=None),
) -> dict:
    """PIN 없이 가족 코드만으로 열람하는 엔드포인트용 의존성.
    명세상 PIN 목록에 없는 GET(then-now, me/stamp-books)이 여기 해당한다."""
    return _authenticate(x_family_code, None, pin_required=False, allow_when_locked=False)


def require_family_allow_locked(
    x_family_code: str = Header(default=None),
    x_family_pin: str = Header(default=None),
) -> dict:
    """rotate-code 전용: 잠금 상태에서도 PIN 검증을 수행해 재발급을 허용한다.
    (코드만 아는 제3자가 재발급으로 PIN을 넘겨받는 것을 막는다. PIN을 잊었다면 10분 대기.)"""
    return _authenticate(x_family_code, x_family_pin, pin_required=True, allow_when_locked=True)


def require_owner_auth(
    x_family_code: str = Header(default=None),
    x_family_pin: str = Header(default=None),
) -> dict:
    """owner(보호자) 전용 엔드포인트용 의존성 (기획서 v10 B1 — 상속자 지정).
    서버 인증은 가족 단위(코드+PIN)만 있으므로 'owner 인증'은 다음과 같이 판정한다:
    - 가족 코드 누락·모르는 코드 → 401 / 404 (기존과 동일)
    - PIN 헤더가 없으면(아직 인증 전) → 401 pin_required
    - PIN이 틀리면 owner임을 증명하지 못한 것 → **403 not_owner**
      (401은 '인증되지 않음', 403은 '인증됐으나 권한 없음'으로 앱이 구분한다 — DEV_LOG F59)
    실패 횟수·잠금 규칙(require_family_auth)과 동일하게 적용된다."""
    family = get_family(x_family_code)
    if _is_locked(family):
        raise AppError(429, "family_locked", "PIN 오류 횟수 초과로 잠겼습니다. 10분 후 재시도하거나 코드를 재발급하세요")
    if not x_family_pin:
        raise AppError(401, "pin_required", "X-Family-Pin 헤더가 필요합니다")
    if not verify_pin(x_family_pin, family["pin_hash"]):
        _fail_pin(family["code"], family["failed_attempts"])
        raise AppError(403, "not_owner", "owner(보호자) 권한 확인에 실패했습니다. PIN이 올바른지 확인하세요")
    if family["failed_attempts"] or family["locked_until"]:
        with db.connect() as conn:
            conn.execute(
                "UPDATE families SET failed_attempts = 0, locked_until = NULL WHERE code = ?",
                (family["code"],),
            )
    return family


def _authenticate(x_family_code: str, x_family_pin: str, *, pin_required: bool, allow_when_locked: bool) -> dict:
    family = get_family(x_family_code)
    if _is_locked(family) and not allow_when_locked:
        raise AppError(429, "family_locked", "PIN 오류 횟수 초과로 잠겼습니다. 10분 후 재시도하거나 코드를 재발급하세요")
    if not pin_required:
        return family
    if not x_family_pin:
        raise AppError(401, "pin_required", "X-Family-Pin 헤더가 필요합니다")
    if not verify_pin(x_family_pin, family["pin_hash"]):
        _fail_pin(family["code"], family["failed_attempts"])
        raise AppError(401, "invalid_pin", "PIN이 올바르지 않습니다")
    # 성공 시 실패 카운터·잠금 초기화
    if family["failed_attempts"] or family["locked_until"]:
        with db.connect() as conn:
            conn.execute(
                "UPDATE families SET failed_attempts = 0, locked_until = NULL WHERE code = ?",
                (family["code"],),
            )
    return family


def rotate_code_and_pin(old_code: str) -> dict:
    """가족 코드를 새로 발급하고 PIN도 함께 재발급한다.
    기존 데이터는 모두 새 코드로 옮기고(기록 삭제 없음), 실패 카운터·잠금은 초기화한다.
    이전 코드는 더 이상 유효하지 않다."""
    new_code = generate_family_code()
    pin = generate_pin()
    salt = secrets.token_hex(8)
    now = timeutil.now_iso()
    # 설정·접기·이름표 테이블도 새 코드로 옮겨 데이터가 유지된다.
    tables = [
        "visits", "memories", "members", "handovers", "food_notes", "receipts", "point_ledger",
        "memory_markers", "folded_places", "family_settings",
    ]
    with db.connect() as conn:
        for _ in range(20):
            if conn.execute("SELECT 1 FROM families WHERE code = ?", (new_code,)).fetchone() is None:
                break
            new_code = generate_family_code()
        conn.execute("DELETE FROM families WHERE code = ?", (new_code,))  # 방어적 정리
        for table in tables:
            conn.execute(f"UPDATE {table} SET family_code = ? WHERE family_code = ?", (new_code, old_code))
        conn.execute(
            "INSERT INTO families (code, created_at, pin_hash, failed_attempts, locked_until)"
            " VALUES (?,?,?,0,NULL)",
            (new_code, now, f"{salt}${_hash_pin(pin, salt)}"),
        )
        conn.execute("DELETE FROM families WHERE code = ?", (old_code,))
    return {"family_code": new_code, "pin": pin}

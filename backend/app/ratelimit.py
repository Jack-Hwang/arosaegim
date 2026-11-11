# 가족 코드 생성 빈도 제한 (02문서 11절).
# - 같은 기기 토큰(요청 헤더 X-Device-Token, 웹이 발급·저장)으로 하루 1회.
#   2회째는 429 device_limit. 토큰이 없으면 기기 제한은 적용할 수 없다(선택 헤더).
# - IP 기준 빈도 제한: 메모리 카운터로 간단 구현(데모 한정). 프로세스를 재시작하면
#   카운터가 초기화된다 — 실서비스는 WAF/레이트리미터(예: nginx limit_req, API 게이트웨이)로 대체할 것.
import threading

from . import config
from .errors import AppError

_lock = threading.Lock()
_device_day: dict[str, str] = {}      # device_token -> 마지막 생성일(YYYY-MM-DD)
_ip_days: dict[str, list[str]] = {}   # ip -> 생성일 목록(같은 날 여러 번 가능, IP 상한 판정용)


def _prune_today(day: str) -> None:
    """오래된 날짜 기록을 지워 메모리가 무한히 늘지 않게 한다(데모용 청소)."""
    for k in [k for k, v in _device_day.items() if v != day]:
        _device_day.pop(k, None)
    for k in [k for k, days in _ip_days.items() if all(d != day for d in days)]:
        _ip_days.pop(k, None)


def check_and_record_device(token: str, day: str) -> None:
    """같은 기기 토큰으로 오늘 이미 코드를 발급했는지 검사하고, 통과하면 기록한다."""
    with _lock:
        _prune_today(day)
        if token and _device_day.get(token) == day:
            raise AppError(
                429, "device_limit",
                "같은 기기에서 가족 코드는 하루 1회만 만들 수 있습니다",
            )
        if token:
            _device_day[token] = day


def check_and_record_ip(ip: str, day: str) -> None:
    """IP별 하루 생성 상한 검사(데모 백스톱). 통과하면 기록한다."""
    with _lock:
        days = _ip_days.setdefault(ip, [])
        if sum(1 for d in days if d == day) >= config.FAMILY_CREATE_DAILY_PER_IP:
            raise AppError(429, "ip_limit", "이 IP에서 가족 코드 생성 횟수가 하루 상한을 넘었습니다")
        days.append(day)

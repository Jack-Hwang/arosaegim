# 날짜·시각 유틸.
# 설계 결정(주석 명시): 이 앱은 한국 국내 여행용이므로 타임존이 붙은 값은
# KST(+09:00)로 변환한 뒤 naive datetime으로 통일한다. DB에는
# "YYYY-MM-DDTHH:MM:SS" 형태의 ISO 문자열을 저장하며, 사전식 비교가
# 시간순 비교와 일치하도록 항상 같은 형식을 유지한다.
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))


class InvalidDatetime(ValueError):
    """ISO 8601 로 파싱할 수 없는 값."""


def parse_iso(value: str) -> datetime:
    """ISO 8601 문자열을 naive datetime(KST 기준)으로 파싱한다."""
    text = (value or "").strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    try:
        dt = datetime.fromisoformat(text)
    except ValueError as exc:
        raise InvalidDatetime(f"ISO 8601 날짜가 아닙니다: {value!r}") from exc
    if dt.tzinfo is not None:
        # 타임존이 있으면 KST로 변환 후 제거 (한국 여행 앱 기준)
        dt = dt.astimezone(KST).replace(tzinfo=None)
    return dt


def now_iso() -> str:
    """현재 시각을 저장용 ISO 문자열로."""
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


def to_minute_iso(value: str) -> str:
    """분 단위로 정규화한 ISO 문자열.
    방문 기록 멱등 판정과 receipt_key 해시는 '같은 분'을 같은 값으로 본다."""
    dt = parse_iso(value)
    return dt.replace(second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M:%S")


def same_day(a_iso: str, b_iso: str) -> bool:
    """두 ISO 문자열이 같은 날(연-월-일)인지."""
    return parse_iso(a_iso).date() == parse_iso(b_iso).date()


def date_of_iso(value: str) -> str:
    """ISO 문자열에서 날짜(YYYY-MM-DD)만 뽑는다(영수증·방문 일 단위 규칙용)."""
    return parse_iso(value).strftime("%Y-%m-%d")


def shift_date(day: str, days: int) -> str:
    """YYYY-MM-DD 날짜를 days만큼 이동한 문자열(식당 '다음 날' 인정 판정용)."""
    return (parse_date_only(day) + timedelta(days=days)).strftime("%Y-%m-%d")


def parse_date_only(value: str) -> datetime:
    """YYYY-MM-DD 문자열을 datetime으로 파싱한다(숙박 기간 검증용).
    잘못된 형식이면 InvalidDatetime을 던진다."""
    try:
        return datetime.strptime((value or "").strip(), "%Y-%m-%d")
    except ValueError as exc:
        raise InvalidDatetime(f"YYYY-MM-DD 날짜가 아닙니다: {value!r}") from exc

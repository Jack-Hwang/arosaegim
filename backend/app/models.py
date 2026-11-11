# Pydantic 모델 (02문서 3·6-2절 응답·요청 스키마).
# 설계 결정: 입력 모델은 extra="forbid" 로 선언한다. 즉 요청 본문에
# 실시간 위치(lat/lon)·경로(route) 같은 스키마에 없는 필드가 오면 400으로 거부한다 —
# "실시간 위치·경로는 받지 않는다"는 규칙을 스키마 자체로 강제한다.
from typing import List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

Input = ConfigDict(extra="forbid")


class FamilyCreated(BaseModel):
    family_code: str
    pin: str


class VisitIn(BaseModel):
    model_config = Input
    place_id: str
    course_id: str
    visited_at: str  # ISO 8601. 멱등 판정은 분 단위로 정규화한다.
    source: Literal["fused", "navi", "manual"]
    vehicle_label: Optional[str] = None
    party_size: Optional[int] = Field(default=None, ge=1, le=50)  # 좌석 신호 기반 인원(02문서 10절)
    trip_km: Optional[float] = Field(default=None, ge=0)          # 코스 주행거리 차이(02문서 10절)


class HeritageBriefOut(BaseModel):
    """then-now 카드에 실어 보내는 유산 요약(02문서 10절). 장소에 유산 정보가 없으면 null."""
    name: str
    year: Optional[int] = None
    label: Optional[str] = None


class ThenNowPast(BaseModel):
    kind: Literal["memory", "visit"]  # 과거 기록의 출처 (설계 추가: 앱 표기용)
    year: int
    years_ago: int  # 서버가 계산 (현재 연도 - 과거 연도)
    note: Optional[str] = None
    photo_url: Optional[str] = None
    vehicle_label: Optional[str] = None
    party_size: Optional[int] = None  # 그때 함께 간 인원(02문서 10절)


class ThenNowNow(BaseModel):
    visited_at: str
    vehicle_label: Optional[str] = None
    party_size: Optional[int] = None   # 이번 방문의 인원
    trip_km: Optional[float] = None    # 이번 방문(코스) 주행거리


class ThenNow(BaseModel):
    place_id: str
    past: Optional[ThenNowPast] = None
    now: Optional[ThenNowNow] = None
    heritage: Optional[HeritageBriefOut] = None  # 장소 시드 heritage(없으면 null)


class VisitOut(BaseModel):
    visit_id: int
    stamp_no: int
    then_now: ThenNow
    party_size: Optional[int] = None
    trip_km: Optional[float] = None


class QuizOut(BaseModel):
    q: str
    choices: List[str]
    answer: int
    explain: Optional[str] = None
    reviewed: bool = False


class HeritageOut(BaseModel):
    name: str
    year: Optional[int] = None
    label: Optional[str] = None
    note: Optional[str] = None
    reviewed: bool = False


class PlaceOut(BaseModel):
    id: str
    name: str
    address: Optional[str] = None
    lat: Optional[float] = None
    lon: Optional[float] = None
    arrive_radius_m: Optional[float] = None
    narration: Optional[str] = None
    heritage: Optional[HeritageOut] = None
    quiz: List[QuizOut] = []


class CourseSummary(BaseModel):
    id: str
    title: str
    summary: Optional[str] = None


class CourseDetail(CourseSummary):
    places: List[PlaceOut] = []


class FoodNoteOut(BaseModel):
    id: int
    name: str
    note: Optional[str] = None
    photo_url: Optional[str] = None
    kind: Optional[str] = None  # 'restaurant'|'shop'|'lodging' (02문서 11절 — 숙소 후기도 같은 표)
    created_at: str


class TimelineEntry(BaseModel):
    kind: Literal["visit", "memory"]
    place_id: str
    place_name: str
    visited_at: str  # visit은 방문 시각, memory는 기록 생성 시각 (정렬·표시 기준)
    visit_id: Optional[int] = None
    memory_id: Optional[int] = None
    year: Optional[int] = None
    note: Optional[str] = None
    photo_url: Optional[str] = None
    vehicle_label: Optional[str] = None
    source: Optional[Literal["fused", "navi", "manual"]] = None
    stamp_no: Optional[int] = None
    food_notes: List[FoodNoteOut] = []


class MemberOut(BaseModel):
    id: int
    nickname: str
    generation: Optional[str] = None
    role: Literal["owner", "member", "heir"]
    seat_role: Optional[str] = None  # 'driver'|'navigator'|'explorer' (02문서 10절, 실명 아님)
    is_minor: bool
    guardian_consent: bool
    consent_at: Optional[str] = None
    created_at: str


class SeatRoleIn(BaseModel):
    """오늘의 자리 이름표(T9-3) — 구성원의 seat_role 갱신 요청. 실명이 아니므로 식별 정보 없음."""
    model_config = Input
    seat_role: Optional[Literal["driver", "navigator", "explorer"]] = None


class MemberIn(BaseModel):
    model_config = Input
    nickname: str = Field(min_length=1, max_length=30)
    generation: Optional[str] = Field(default=None, max_length=20)
    seat_role: Optional[Literal["driver", "navigator", "explorer"]] = None
    is_minor: bool = False


class HeirIn(BaseModel):
    model_config = Input
    member_id: int


class HandoverIn(BaseModel):
    model_config = Input
    to_member_id: int


class OfferOut(BaseModel):
    id: str
    title: str
    condition: Optional[str] = None
    simulated: bool = True


class RewardsPreviewIn(BaseModel):
    model_config = Input
    course_id: str


class RewardsPreviewOut(BaseModel):
    simulated: bool = True
    offers: List[OfferOut] = []


class ReceiptResult(BaseModel):
    status: Literal["accepted", "rejected", "pending"]  # pending: 같은 제휴처·같은 날 두 번째(02문서 11절)
    points: int
    simulated: bool = True
    reason: str  # ok / pending / no_visit / duplicate / duplicate_photo / daily_limit / invalid_amount
    label: str = "포인트 캐시백(예시)"  # 화면 문구용. 현금 환급으로 읽히는 표기 금지(02문서 11절)


class PartnerOut(BaseModel):
    id: str
    name: str
    kind: Optional[str] = None
    point_rate: float
    simulated: bool = True


class StampBookOut(BaseModel):
    id: str
    title: str
    region: Optional[str] = None
    country: Optional[str] = None
    badge: Optional[str] = None


class StampBookProgress(StampBookOut):
    visited_count: int
    total: int
    progress_pct: int  # 내림(%) — 앱 StampBook 규칙과 동일
    completed: bool
    badge_earned: Optional[str] = None


class PointEntry(BaseModel):
    id: int
    receipt_id: int
    points: int
    reason: Optional[str] = None
    created_at: str


class PointsSummary(BaseModel):
    total: int
    label: str = "포인트 캐시백(예시)"  # 현금 환급이 아님을 화면이 그대로 표기하도록(02문서 11절)
    entries: List[PointEntry] = []


class ThenNowResponse(BaseModel):
    place_id: str
    past: Optional[ThenNowPast] = None
    now: Optional[ThenNowNow] = None
    heritage: Optional[HeritageBriefOut] = None


class MemoryCreated(BaseModel):
    memory_id: int


class FoodNoteCreated(BaseModel):
    food_note_id: int


class Deleted(BaseModel):
    deleted: bool


class RotatedCode(BaseModel):
    family_code: str
    pin: str

# 아로새김 백엔드

가족 스탬프 여행 앱(차량용)의 소규모 REST 서버. **Python 3.12 + FastAPI + SQLite**,
Google Play Services·Firebase 등 외부 서비스 의존 없음(02문서 1절).

## 실행

```bash
cd backend
python -m pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

- 헬스체크: http://localhost:8000/health → {"ok": true}
- API 문서(Swagger): http://localhost:8000/docs
- 웹 화면: http://localhost:8000/web/ (폴더는 웹 담당이 채운다. 비어 있어도 서버는 뜬다)
- 업로드 사진: http://localhost:8000/uploads/<파일명>

DB 파일(`arosaegim.db`)과 `uploads/`는 처음 기동 때 자동 생성된다.
시드(`seed/seed.json`)는 기동 시 읽어 기준 테이블을 채운다 — 이미 있는 id는 건너뛴다.
`then_now_demo`는 서버에 넣지 않는다(앱 오프라인 전용).

테스트:

```bash
python -m pytest
```

## 인증 방식 (설계 결정)

- 모든 가족 데이터 요청은 헤더 **X-Family-Code**(식별용)와, 열람·수정 권한 확인용 **X-Family-Pin**을 보낸다.
- `POST /v1/families` 응답으로 `{family_code, pin}`을 **한 번만** 돌려준다.
  PIN은 사용자가 웹 화면에서 확인·보관한다(서버는 해시만 저장, 평문 재전송 없음).
- PIN이 필요한 엔드포인트: timeline·members·export·points·**모든 POST/DELETE**.
  공개 데이터(health·courses·places·stamp-books 목록)와 then-now·me/stamp-books 조회는 코드만 필요.
- PIN 5회 연속 실패 → 10분 잠금(`family_locked`, 429). 잠금 중에도 PIN이 맞으면
  `POST /v1/families/me/rotate-code`로 즉시 코드·PIN 재발급 가능(잠금 탈출 경로).

## API 요약

| 메서드·경로 | 인증 | 설명 |
|---|---|---|
| GET /health | 없음 | {"ok": true} |
| POST /v1/families | 없음(X-Device-Token 권장) | {family_code, pin} 발급(항상 신규). **같은 기기 토큰으로 하루 1회** — 2회째 429 `device_limit` |
| GET /v1/courses, /v1/courses/{id} | 없음 | 코스 목록/상세(seq순, 퀴즈 포함) |
| GET /v1/places/{id} | 없음 | 장소 상세(퀴즈·유산 정보) |
| GET /v1/places/{id}/then-now | 코드 | 그때와 지금(past/now에 party_size, now에 trip_km, heritage{...}) |
| GET /v1/places/{id}/partners | 없음 | 근처 제휴처(적립률, simulated) |
| GET /v1/places/{id}/voice-letters | 없음 | 예시 음성 편지 메타데이터 [{id, sealed_label, simulated, audio_url}] — 업로드 API는 로드맵 |
| POST /v1/visits | 코드+PIN | 방문 기록 — **멱등**(같은 분=1건), party_size·trip_km(선택) 수용, {visit_id, stamp_no, then_now} |
| GET /v1/families/me/timeline | 코드+PIN | 방문·기억·맛집 통합 최신순 |
| POST /v1/memories | 코드+PIN | 기억 등록(multipart, party_size 선택, 사진 5MB·JPEG/PNG·EXIF 제거) |
| POST /v1/families/me/food-notes | 코드+PIN | 맛집·숙소 기록(kind 선택, 방문 1건에 묶임) |
| GET/POST /v1/families/me/members | 코드+PIN | 구성원(첫 구성원 owner, seat_role 선택) |
| POST /v1/families/me/members/{id}/consent | 코드+PIN | 보호자 동의 기록 |
| POST /v1/families/me/heir | 코드+PIN | 상속자 지정(owner만, designated 기록) |
| POST /v1/families/me/handover | 코드+PIN | 관리 권한 이전(owner만, transferred 기록) |
| GET /v1/families/me/metrics | 코드+PIN | 가족 지표(스탬프북 완성률·그때와 지금 열람 수·재방문 간격·영수증 인증 수·물려주기 여부) |
| POST /v1/rewards/preview | 코드+PIN | 혜택 미리보기(simulated:true) |
| POST /v1/receipts | 코드+PIN | 영수증 적립 — **사진 필수**(규칙: services/points.py) |
| GET /v1/families/me/points | 코드+PIN | 적립 합계·내역 (label "포인트 캐시백(예시)") |
| GET /v1/stamp-books | 없음 | 스탬프북 목록 |
| GET /v1/families/me/stamp-books | 코드 | 진행률·완성 배지 |
| GET /v1/families/me/export | 코드+PIN | ZIP(timeline.json+photos/+README.html) |
| POST /v1/families/me/rotate-code | 코드+PIN(잠금 중 허용) | 코드·PIN 재발급(데이터 유지) |
| DELETE /v1/families/me | 코드+PIN | 전량 삭제(DB 행+업로드 파일) |

오류는 항상 `{"error": {"code": "...", "message": "..."}}` + 표준 상태코드.
요청 본문의 스키마 밖 필드(예: 실시간 위치)는 400으로 거부한다(`extra="forbid"`).

## 구조 (02문서 8절)

```
backend/
├── app/
│   ├── main.py      # 진입점: 라우터 등록, /web·/uploads 정적 서빙
│   ├── db.py        # SQLite 스키마·시드 적재
│   ├── models.py    # Pydantic 스키마(입력은 extra="forbid")
│   ├── security.py  # 가족 코드·PIN 발급·검증·잠금
│   ├── timeutil.py  # ISO 날짜 파싱·분 단위 정규화·같은 날 판정
│   ├── errors.py    # {"error":{code,message}} 통일
│   ├── routers/     # families courses places visits memories rewards members stamps points
│   └── services/    # then_now.py·points.py(순수 함수), photos.py, album.py
├── tests/           # pytest (임시 DB·업로드 디렉터리)
├── seed/seed.json   # glm/seed/seed.json 사본
├── web/             # 웹 담당 폴더(비어 있어도 됨)
├── uploads/         # 업로드 사진(기동 시 자동 생성)
└── requirements.txt
```

## 적립 규칙 (02문서 6-2·11절 — services/points.py 순수 함수)

- `points = floor(amount × partner.point_rate)`
- `0 < amount ≤ 1,000,000` 외 → rejected `invalid_amount`
- **사진 필수**(없으면 400 `missing_photo`). 사진 원본 바이트 sha256을 receipt_key와
  receipts.photo_hash에 저장한다.
- receipt_key = 제휴처 + **날짜** + 금액 + **사진 해시** → 같은 키 재제출은 rejected `duplicate`
- 같은 사진(해시) 재제출도 금액이 달라도 rejected `duplicate_photo`
- **방문 연계(02문서 11절)**:
  - 식당·가게(restaurant/shop/museum_shop): 결제일이 스탬프 날짜 또는 **다음 날**이면 인정,
    이틀 뒤부터 rejected `no_visit`
  - 숙소(lodging): `stay_checkin ≤ 스탬프 날짜 ≤ stay_checkout`이면 인정.
    체크인·체크아웃 누락은 400 `missing_stay`, 기간 형식 오류·3박 초과는 400 `invalid_stay`
- 같은 제휴처·같은 날 두 번째 영수증: `status: 'pending'`(확인 대기), 적립 0, 저장은 함.
  accepted·pending만 receipts에 남고 rejected는 저장하지 않는다(사진 파일도 즉시 삭제).
- 가족당 하루 5건(accepted 기준) 초과 → rejected `daily_limit`
- 모든 적립·혜택 응답에 `simulated: true`와 `label: "포인트 캐시백(예시)"`가 붙는다.
  포인트는 현금이 아니다 — 화면 문구로 환급·입금·원 단위 지급처럼 읽히는 표기를 쓰지 않는다.
- `kind`는 서버가 원본 값을 유지한다(museum_shop은 화면에서 '가게'로 묶어 표시).

## 환경변수(선택)

| 변수 | 기본값 |
|---|---|
| AROSAEGIM_DB | backend/arosaegim.db |
| AROSAEGIM_UPLOADS | backend/uploads |
| AROSAEGIM_WEB | backend/web |
| AROSAEGIM_SEED | backend/seed/seed.json |

# 아로새김 백엔드 설정.
# 경로와 제한값을 한 곳에 모은다. 테스트는 환경변수로 임시 경로를 지정한다.
import os
from pathlib import Path

# backend/ 디렉터리 (이 파일은 backend/app/config.py 에 있다)
BASE_DIR = Path(__file__).resolve().parent.parent

# SQLite 데이터베이스 파일 경로 (기본: backend/arosaegim.db)
DB_PATH: str = os.environ.get("AROSAEGIM_DB", str(BASE_DIR / "arosaegim.db"))

# 업로드(사진) 저장 디렉터리 — /uploads 로 정적 서빙된다
UPLOAD_DIR: str = os.environ.get("AROSAEGIM_UPLOADS", str(BASE_DIR / "uploads"))

# 웹 정적 파일 디렉터리 — /web 으로 정적 서빙된다.
# 02문서 구조상 웹은 backend의 형제 디렉터리(arosaegim/web)이므로 기본값은 부모의 web.
# 단일 폴더 배포를 원하면 환경변수 AROSAEGIM_WEB 으로 덮어쓴다.
WEB_DIR: str = os.environ.get("AROSAEGIM_WEB", str(BASE_DIR.parent / "web"))

# 시드 데이터 경로 (backend/seed/seed.json — 앱 내장 시드 android/assets/seed 와 동일)
SEED_PATH: str = os.environ.get("AROSAEGIM_SEED", str(BASE_DIR / "seed" / "seed.json"))

# 사진 업로드 제한: 최대 5MB, JPEG/PNG 만 허용 (02문서 6-2)
MAX_PHOTO_BYTES: int = 5 * 1024 * 1024

# PIN 규칙: 5회 실패 시 10분 잠금 (02문서 10절)
PIN_LOCK_ATTEMPTS: int = 5
PIN_LOCK_MINUTES: int = 10

# 영수증 적립 규칙 (02문서 6-2·11절): 0 < 금액 <= 1,000,000, 가족당 하루 5건,
# 숙박 최대 3박(stay_checkin~stay_checkout)
MAX_RECEIPT_AMOUNT_KRW: int = 1_000_000
DAILY_RECEIPT_LIMIT: int = 5
MAX_STAY_NIGHTS: int = 3

# 가족 코드 생성 제한 (02문서 11절):
# - 같은 기기 토큰(X-Device-Token)으로 하루 1회
# - IP 기준 빈도 제한(데모: 메모리 카운터). 실서비스는 WAF/레이트리미터로 대체할 것
FAMILY_CREATE_DAILY_PER_DEVICE: int = 1
FAMILY_CREATE_DAILY_PER_IP: int = 200  # 데모 백스톱(테스트 등 다수 발급 차단용 상한)

# 가족 코드 알파벳: 사람이 소리 내어 읽거나 받아쓰기 쉬우도록
# 0/O/1/I 처럼 혼동되기 쉬운 문자는 뺀다.
FAMILY_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
FAMILY_CODE_LENGTH = 8

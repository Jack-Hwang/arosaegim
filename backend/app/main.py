# 아로새김 백엔드 진입점 (02문서 8절 구조).
# 실행: uvicorn app.main:app --port 8000  (backend/ 디렉터리에서)
# - 라우터 등록
# - /uploads, /web 정적 서빙 (web/ 폴더는 웹 담당 — 비어 있어도 서버는 뜬다)
# - 기동 시 DB 스키마·시드 준비 (이미 있으면 건너뜀)
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from . import config, db
from .errors import AppError, install_error_handlers

app = FastAPI(
    title="아로새김 백엔드",
    version="0.1.0",
    description="가족 스탬프 여행 앱의 소규모 REST 서버 (FastAPI + SQLite, 외부 서비스 의존 없음)",
)

# 설계 결정: 데모는 에뮬레이터(10.0.2.2:8000)와 로컬 웹 개발 서버가 함께 붙으므로
# CORS를 전면 허용한다. 인증은 가족 코드+PIN 헤더로 하며 쿠키 세션을 쓰지 않는다.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

install_error_handlers(app)

# 라우터 등록 (02문서 8절 파일 구조)
from .routers import (  # noqa: E402
    courses, families, members, memories, metrics, places, points, rewards, stamps, visits,
)

for r in (families.router, courses.router, places.router, visits.router, memories.router,
          rewards.router, members.router, stamps.router, points.router, metrics.router):
    app.include_router(r)


@app.get("/health")
def health():
    """헬스체크. 인증 없이 항상 {"ok": true}."""
    return {"ok": True}


# 정적 서빙: 업로드 사진(/uploads/파일명)과 웹 화면(/web/...).
# 디렉터리는 기동 직전에 보장한다(없으면 만든다).
db.ensure_dirs()
# 스키마·시드는 기동(임포트) 시점에 준비한다. init_all은 몇 번 불려도 안전하다.
db.init_all()
app.mount("/uploads", StaticFiles(directory=config.UPLOAD_DIR), name="uploads")
app.mount("/web", StaticFiles(directory=config.WEB_DIR, html=True), name="web")  # html=True: /web/ 에서 index.html 서빙

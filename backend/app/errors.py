# 공통 오류 형식 (02문서 6절): 모든 오류 응답은 {"error": {"code": ..., "message": ...}}.
# 라우터에서는 AppError를 던지고, FastAPI 기본 예외는 여기서 같은 형식으로 감싼다.
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse


class AppError(Exception):
    """표준 오류 형식으로 변환되는 애플리케이션 오류."""

    def __init__(self, status_code: int, code: str, message: str):
        self.status_code = status_code
        self.code = code
        self.message = message
        super().__init__(message)


def error_body(code: str, message: str) -> dict:
    # JSON은 UTF-8로 내보낸다 (ensure_ascii=False 기본 동작).
    return {"error": {"code": code, "message": message}}


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def handle_app_error(_: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=error_body(exc.code, exc.message))

    @app.exception_handler(HTTPException)
    async def handle_http_error(_: Request, exc: HTTPException) -> JSONResponse:
        # FastAPI 내부(HTTPException)도 같은 형식으로 통일한다.
        code = "http_error"
        if exc.status_code == 404:
            code = "not_found"
        elif exc.status_code == 405:
            code = "method_not_allowed"
        return JSONResponse(status_code=exc.status_code, content=error_body(code, str(exc.detail)))

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        # 요청 스키마 위반(필수 누락, 허용되지 않는 필드 등)은 400으로 통일한다.
        # 실시간 위치·경로 같은 스키마 밖 필드는 extra="forbid"로 여기서 걸러진다.
        summary = "; ".join(
            f"{'.'.join(str(p) for p in err['loc'][1:]) or 'body'}: {err['msg']}" for err in exc.errors()[:5]
        )
        return JSONResponse(status_code=400, content=error_body("invalid_request", summary))

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, exc: Exception) -> JSONResponse:
        return JSONResponse(status_code=500, content=error_body("internal_error", "서버 내부 오류"))

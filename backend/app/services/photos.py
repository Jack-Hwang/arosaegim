# 사진 저장 처리 (02문서 6-2절).
# 규칙:
# - 최대 5MB, JPEG/PNG만 허용 (파일 확장자가 아니라 파일 내용(magic bytes)으로 판정)
# - 원본 파일명은 절대 사용하지 않고 서버가 새 이름을 만든다 (경로 조차 못하게)
# - Pillow로 다시 저장하면서 EXIF를 제거한다 (위치·시각 등 메타데이터 유출 방지)
import uuid
from io import BytesIO

from PIL import Image

from .. import config
from ..errors import AppError

_MAGIC = {
    "jpeg": b"\xff\xd8\xff",
    "png": b"\x89PNG\r\n\x1a\n",
}
_EXT = {"jpeg": ".jpg", "png": ".png"}


def detect_format(data: bytes):
    """파일 내용(magic bytes)으로 형식을 판정한다. jpeg/png 이외는 None."""
    for fmt, magic in _MAGIC.items():
        if data.startswith(magic):
            return fmt
    return None


def validate_photo_bytes(data: bytes):
    """(형식)을 반환하거나 AppError로 거부한다."""
    if len(data) > config.MAX_PHOTO_BYTES:
        raise AppError(413, "photo_too_large", "사진은 최대 5MB까지 허용합니다")
    fmt = detect_format(data)
    if fmt is None:
        raise AppError(415, "unsupported_photo_type", "사진은 JPEG 또는 PNG만 허용합니다")
    return fmt


def strip_and_save(data: bytes, fmt: str, prefix: str) -> str:
    """EXIF를 제거해 uploads/ 아래에 저장하고, 저장된 파일명만 반환한다.
    Pillow의 save는 JPEG 저장 시 원본 EXIF를 그대로 따라 쓸 수 있으므로
    exif=b"" 를 명시해 비운다. JPEG는 알파 채널이 있으면 RGB로 변환한다."""
    img = Image.open(BytesIO(data))
    name = f"{prefix}-{uuid.uuid4().hex}{_EXT[fmt]}"
    dest = str(_path(name))
    if fmt == "jpeg":
        if img.mode not in ("RGB", "L"):
            img = img.convert("RGB")
        img.save(dest, format="JPEG", exif=b"")  # exif 비움 → 메타데이터 제거
    else:
        img.save(dest, format="PNG")
    return name


def save_upload(data: bytes, prefix: str) -> str:
    """검증 → EXIF 제거 → 저장까지의 공통 흐름. 저장된 파일명을 반환."""
    fmt = validate_photo_bytes(data)
    return strip_and_save(data, fmt, prefix)


def delete_file(filename) -> None:
    """가족 삭제 등에서 업로드 파일을 지운다. 없으면 조용히 넘어간다."""
    import os

    if not filename:
        return
    path = _path(filename)
    try:
        os.remove(path)
    except OSError:
        pass


def _path(filename: str):
    import os

    # 파일명은 서버가 만든 값만 DB에 들어가므로 디렉터리 탈출이 불가능하지만,
    # 방어적으로 basename만 사용한다.
    safe = os.path.basename(filename)
    return os.path.join(config.UPLOAD_DIR, safe)

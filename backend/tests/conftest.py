# pytest 공통 설정.
# - 임시 DB·업로드·웹 디렉터리를 환경변수로 지정한 뒤 앱을 임포트한다
#   (app/config.py는 임포트 시점에 환경변수를 읽는다).
# - 시드는 저장소의 backend/seed/seed.json 을 그대로 사용한다.
import os
import sys
import tempfile
from io import BytesIO

_tmpdir = tempfile.mkdtemp(prefix="aros-backend-test-")
os.environ["AROSAEGIM_DB"] = os.path.join(_tmpdir, "test.db")
os.environ["AROSAEGIM_UPLOADS"] = os.path.join(_tmpdir, "uploads")
os.environ["AROSAEGIM_WEB"] = os.path.join(_tmpdir, "web")
os.environ["AROSAEGIM_SEED"] = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "seed", "seed.json")
)
# backend/ 를 임포트 경로에 추가 (app 패키지 임포트용)
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from PIL import Image  # noqa: E402

from app import config  # noqa: E402  (임포트 시점에 위 환경변수가 반영됨)
from app.main import app  # noqa: E402


@pytest.fixture(scope="session")
def client():
    return TestClient(app)


@pytest.fixture()
def family(client):
    """새 가족 1개를 만들고 {family_code, pin}을 돌려준다(테스트마다 독립)."""
    resp = client.post("/v1/families", json={})
    assert resp.status_code == 200, resp.text
    return resp.json()


@pytest.fixture()
def auth(family):
    """X-Family-Code + X-Family-Pin 헤더."""
    return {"X-Family-Code": family["family_code"], "X-Family-Pin": family["pin"]}


def make_png() -> bytes:
    buf = BytesIO()
    Image.new("RGB", (8, 8), (30, 120, 200)).save(buf, "PNG")
    return buf.getvalue()


_png_seq = iter(range(100000))


def make_unique_png() -> bytes:
    """호출마다 다른 바이트를 가진 PNG. 영수증 사진은 필수이고 같은 사진 재제출이
    거부되므로(duplicate_photo), 여러 영수증 테스트에서는 이 헬퍼를 쓴다."""
    n = next(_png_seq)
    buf = BytesIO()
    Image.new("RGB", (8, 8), (n % 250 + 5, (n * 7) % 250 + 5, (n * 13) % 250 + 5)).save(buf, "PNG")
    return buf.getvalue()


def make_jpeg(with_exif: bool = False) -> bytes:
    """JPEG 바이트. with_exif=True면 Orientation(274)=6 EXIF를 심는다."""
    img = Image.new("RGB", (8, 8), (200, 40, 40))
    kwargs = {}
    if with_exif:
        exif = Image.Exif()
        exif[274] = 6  # Orientation
        kwargs["exif"] = exif.tobytes()
    buf = BytesIO()
    img.save(buf, "JPEG", **kwargs)
    return buf.getvalue()


def stored_photo_path(filename: str) -> str:
    """uploads/ 아래 저장된 파일의 실제 경로."""
    return os.path.join(config.UPLOAD_DIR, filename)

# 사진 업로드 규칙 테스트 (02문서 6-2).
# 5MB 초과 거부, JPEG/PNG 외 거부, EXIF 제거 검증, 미성년 보호자 동의.
import os

from PIL import Image

from tests.conftest import make_jpeg, make_png, stored_photo_path


def _post_memory(client, auth, filename="photo.jpg", content=b"", mime="image/jpeg"):
    return client.post(
        "/v1/memories",
        data={"place_id": "gj-museum", "year": "1994", "note": "테스트"},
        files={"photo": (filename, content, mime)},
        headers=auth,
    )


def test_photo_over_5mb_rejected(client, auth):
    """5MB 초과는 413. 형식 검사보다 크기를 먼저 본다."""
    big = b"\xff\xd8\xff" + b"\x00" * (5 * 1024 * 1024 + 1)
    r = _post_memory(client, auth, content=big)
    assert r.status_code == 413
    assert r.json()["error"]["code"] == "photo_too_large"


def test_non_jpeg_png_rejected(client, auth):
    """JPEG/PNG가 아닌 파일(내용 기준)은 415. 확장자를 속여도 magic bytes로 걸러진다."""
    r = _post_memory(client, auth, filename="fake.jpg", content=b"not an image at all")
    assert r.status_code == 415
    assert r.json()["error"]["code"] == "unsupported_photo_type"


def test_png_accepted_without_photo_filename(client, auth):
    """PNG는 통과하고, 응답의 photo_url은 서버가 만든 파일명(/uploads/...)이다."""
    r = _post_memory(client, auth, filename="whatever.png", content=make_png(), mime="image/png")
    assert r.status_code == 200, r.text
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    photo_url = timeline[0]["photo_url"]
    assert photo_url.startswith("/uploads/")
    # 원본 파일명이 저장되지 않았는지 확인(서버 생성 파일명 규칙: mem-<hex>.png)
    assert "whatever" not in photo_url


def test_exif_is_stripped(client, auth):
    """EXIF(Orientation)를 심은 JPEG을 올리면 저장본에서 EXIF가 제거된다."""
    r = _post_memory(
        client, auth, filename="with-exif.jpg", content=make_jpeg(with_exif=True)
    )
    assert r.status_code == 200, r.text
    timeline = client.get("/v1/families/me/timeline", headers=auth).json()
    saved = stored_photo_path(timeline[0]["photo_url"].rsplit("/", 1)[-1])
    assert os.path.exists(saved)
    exif = Image.open(saved).getexif()
    assert exif.get(274) is None  # Orientation 제거됨


def test_minor_photo_requires_guardian_consent(client, auth):
    """미성년 구성원이 있고 보호자 동의 전이면 사진 업로드는 403,
    웹에서 동의 기록 후에는 통과한다."""
    member = client.post(
        "/v1/families/me/members",
        json={"nickname": "아이", "is_minor": True},
        headers=auth,
    ).json()
    r = _post_memory(client, auth, content=make_jpeg())
    assert r.status_code == 403
    assert r.json()["error"]["code"] == "guardian_consent_required"

    # 사진 없는 기억은 동의와 무관하게 허용된다
    assert (
        client.post(
            "/v1/memories",
            data={"place_id": "gj-museum", "year": "1994"},
            headers=auth,
        ).status_code
        == 200
    )

    # 웹 전용 화면 경로: 동의 기록 후 사진 허용
    consent = client.post(
        f"/v1/families/me/members/{member['id']}/consent", headers=auth
    )
    assert consent.status_code == 200
    assert consent.json()["consent_at"] is not None
    assert _post_memory(client, auth, content=make_jpeg()).status_code == 200

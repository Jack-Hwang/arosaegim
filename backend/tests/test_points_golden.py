# evaluate_receipt 골든셋(요구 P2-⑤ — backend/tests/data/points_golden.json 50건).
# 기대값은 services/points.py 머리 규칙 1~7과 검사 순서(금액→키 중복→사진 중복→방문→대기→한도→계산)
# 에서 유도했다 — 구현의 출력을 베껴 쓴 표가 아니므로, 규칙과 구현이 어긋나면 이 시험이 먼저 잡아낸다.
import json
import os

import pytest

from app.services.points import evaluate_receipt

_HERE = os.path.dirname(__file__)

with open(os.path.join(_HERE, "data", "points_golden.json"), encoding="utf-8") as _f:
    GOLDEN = json.load(_f)["cases"]


@pytest.mark.parametrize("case", GOLDEN, ids=[c["id"] for c in GOLDEN])
def test_evaluate_receipt_golden(case):
    result = evaluate_receipt(
        amount_krw=case["amount_krw"],
        point_rate=case["point_rate"],
        has_visit_near_place=case["has_visit_near_place"],
        is_duplicate_key=case["is_duplicate_key"],
        is_duplicate_photo=case["is_duplicate_photo"],
        is_second_same_partner_day=case["is_second_same_partner_day"],
        receipts_accepted_today=case["receipts_accepted_today"],
    )
    assert result == case["expect"], f"{case['id']}: got {result}"

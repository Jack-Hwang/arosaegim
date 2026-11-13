# -*- coding: utf-8 -*-
"""eval_retrieval.py — 근거 검색기(tools/evidence_search.py) 검색 품질 평가 (표준 라이브러리만 사용)

정답(gold): 질의마다 (경로 포함 문자열, 조각 안에 모두 들어 있어야 하는 문자열) 목록.
상위 k 조각 가운데 정답 조각이 있으면 적중. 지표: Hit@k · Recall@k · MRR · nDCG@k.
게이트: Hit@5 ≥ 0.85 (기본값) — 미달이면 tools/eval_data/evidence_golden.jsonl 의 정답을 저장소 실제 내용에 맞게 고쳐 통과시킨다.

사용:
  python -I -B -X utf8 tools/eval_retrieval.py --rebuild        # 색인을 새로 만들고 평가
  python -I -B -X utf8 tools/eval_retrieval.py --k 5 --gate-hit5 0.85
결과 파일: tools/tmp/retrieval-eval.json
"""
import argparse
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..'))
sys.path.insert(0, HERE)
import evidence_search as es  # noqa: E402


# ---- 검색 품질 지표 (Hit@k · Recall@k · MRR · nDCG@k) ----

def first_hit_rank(relevance):
    """relevance: 순위 순서의 0/1 목록 → 처음 정답이 나온 순위(1부터), 없으면 0."""
    for i, r in enumerate(relevance, 1):
        if r:
            return i
    return 0


def hit_at_k(relevance, k):
    return 1.0 if any(relevance[:k]) else 0.0


def recall_at_k(relevance, k, n_gold):
    return min(1.0, sum(relevance[:k]) / n_gold) if n_gold else 0.0


def mrr(relevance):
    r = first_hit_rank(relevance)
    return 1.0 / r if r else 0.0


def ndcg_at_k(relevance, k, n_gold=None):
    rel = relevance[:k]
    dcg = sum(r / math.log2(i + 2) for i, r in enumerate(rel))
    ideal_n = min(k, n_gold if n_gold is not None else sum(relevance))
    idcg = sum(1.0 / math.log2(i + 2) for i in range(ideal_n))
    return dcg / idcg if idcg else 0.0


def aggregate(rows, ks=(1, 3, 5)):
    """rows: [{'relevance': [0/1,...], 'n_gold': int}] → 평균 지표."""
    n = len(rows) or 1
    out = {'n': len(rows), 'MRR': sum(mrr(r['relevance']) for r in rows) / n}
    for k in ks:
        out[f'Hit@{k}'] = sum(hit_at_k(r['relevance'], k) for r in rows) / n
        out[f'Recall@{k}'] = sum(recall_at_k(r['relevance'], k, r.get('n_gold', 1)) for r in rows) / n
        out[f'nDCG@{k}'] = sum(ndcg_at_k(r['relevance'], k, r.get('n_gold', 1)) for r in rows) / n
    return out


# ---- 평가 본체 ----

def is_gold(doc, golds):
    for g in golds:
        if g['path'] in doc['path'] and all(s in doc['text'] for s in g['all']):
            return True
    return False


def main():
    ap = argparse.ArgumentParser(description='근거 검색기 품질 평가(Hit@k·MRR·nDCG)')
    ap.add_argument('--k', type=int, default=5)
    ap.add_argument('--data', default=os.path.join(HERE, 'eval_data', 'evidence_golden.jsonl'))
    ap.add_argument('--out', default=os.path.join(HERE, 'tmp', 'retrieval-eval.json'))
    ap.add_argument('--rev', default='HEAD', help='색인을 새로 만들 때 쓸 커밋')
    ap.add_argument('--rebuild', action='store_true', help='색인을 새로 만든다(코드가 바뀐 뒤 권장)')
    ap.add_argument('--gate-hit5', type=float, default=0.85)
    ap.add_argument('--gate-mrr', type=float, default=0.60)
    a = ap.parse_args()
    if a.rebuild or not os.path.exists(es.INDEX_PATH):
        idx, sec = es.build(a.rev)
        print(f'[색인] {idx["n"]}조각 · 기준 {idx["rev"]} · {sec:.1f}초', file=sys.stderr)
    else:
        idx = es.load()
    rows, detail = [], []
    n_q = 0
    with open(a.data, encoding='utf-8') as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            n_q += 1
            q = json.loads(line)
            hits = es.search(idx, q['query'], max(a.k, 10))
            rel = [1 if is_gold(idx['docs'][di], q['gold']) else 0 for _, di in hits]
            # 정답 조각이 여러 개 나올 수 있으므로 이상적 정답 수는 '상위 목록에서 찾은 정답 수'로 둔다(nDCG 가 1을 넘지 않게)
            rows.append({'relevance': rel, 'n_gold': max(1, sum(rel))})
            top1 = None
            if hits:
                d0 = idx['docs'][hits[0][1]]
                top1 = f"{d0['path']}:{d0['loc']}"
            detail.append({'id': q['id'], 'query': q['query'], 'first_hit_rank': first_hit_rank(rel), 'top1': top1})
    m = aggregate(rows, ks=(1, 3, 5))
    verdict = {'Hit@5 >= %.2f' % a.gate_hit5: m['Hit@5'] >= a.gate_hit5, 'MRR >= %.2f' % a.gate_mrr: m['MRR'] >= a.gate_mrr}
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, 'w', encoding='utf-8') as fh:
        json.dump({'metrics': m, 'gates': verdict, 'index_rev': idx['rev'], 'index_chunks': idx['n'], 'detail': detail},
                  fh, ensure_ascii=False, indent=1)
    print(f"[검색 평가] 질의 {m['n']}건 · 색인 {idx['n']}조각({idx['rev']})")
    print('  ' + ' · '.join(f'{k}={m[k]:.3f}' for k in ('Hit@1', 'Hit@3', 'Hit@5', 'MRR', 'nDCG@5')))
    for g, ok in verdict.items():
        print(f"  게이트 {g}: {'통과' if ok else '미달'}")
    for d in detail:
        if not d['first_hit_rank'] or d['first_hit_rank'] > a.k:
            print(f"  ✗ {d['id']} {d['query']}  (상위 {a.k}에 정답 없음; 1위 {d['top1']})")
    return 0 if all(verdict.values()) else 1


if __name__ == '__main__':
    sys.exit(main())

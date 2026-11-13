# -*- coding: utf-8 -*-
"""evidence_search.py — 프로젝트 근거 검색기 (표준 라이브러리만 사용)

문서의 문장은 '근거'(코드 · 개발 기록 · 시드 · 공식 자료)에 묶여야 한다.
이 도구는 근거 자료를 조각(chunk)으로 나눠 색인(BM25 + 한글 2글자 조각)하고,
질문을 넣으면 가장 관련 있는 조각을 파일:줄 위치와 함께 돌려준다.
grep 대신 이 도구를 쓰면 (1) 같은 질문에 같은 근거가 나오고 (2) 어떤 근거를 봤는지 기록이 남는다.

색인 대상(읽기 전용, 저장소 안만):
  - 이 저장소의 커밋 기준 파일(.md .kt .py .js .json .xml) — git ls-tree·show 로 읽는다(--rev, 기본 HEAD)
    코드 · docs/ · android assets 시드(seed.json) 가 모두 여기에 포함된다.

사용:
  python -I -B -X utf8 tools/evidence_search.py "정차 자동 전환 세션 게이트" -k 6
  python -I -B -X utf8 tools/evidence_search.py "PIN 휘발성" --path "docs/" -k 5 --json
  python -I -B -X utf8 tools/evidence_search.py --rebuild          # 색인 다시 만들기(코드가 바뀐 뒤)
색인 파일: tools/tmp/evidence-index.json
"""
import argparse
import json
import math
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..'))
INDEX_PATH = os.path.join(ROOT, 'tools', 'tmp', 'evidence-index.json')
GIT_EXT = ('.md', '.kt', '.py', '.js', '.json', '.xml')
GIT_SKIP = ('/build/', '/.gradle/', '/node_modules/', '/reference/', 'apk/', '/__pycache__/', '.keystore', '.jks')
TOKEN_RX = re.compile(r'[0-9A-Za-z_]+|[가-힣]+')
CAMEL_RX = re.compile(r'[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\d+')


def tokens(text):
    out = []
    for w in TOKEN_RX.findall(text):
        if re.match(r'[가-힣]', w):
            out.append(w)
            if len(w) >= 2:
                out.extend(w[i:i + 2] for i in range(len(w) - 1))
        else:
            wl = w.lower()
            out.append(wl)
            parts = [p.lower() for p in CAMEL_RX.findall(w) if len(p) > 1]
            if len(parts) > 1:
                out.extend(parts)
            if '_' in w:
                out.extend(p for p in wl.split('_') if len(p) > 1)
    return out


def git(args):
    p = subprocess.run(['git', '-C', ROOT, '--no-optional-locks'] + args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.returncode, p.stdout


def chunk_lines(path, lines, kind):
    """줄 목록 → [(시작줄, 끝줄, 텍스트)]. 시작줄은 1부터."""
    chunks, buf, start = [], [], 1
    limit_lines = 14 if kind == 'md' else 40
    limit_chars = 900 if kind == 'md' else 1800

    def flush(end):
        nonlocal buf, start
        text = '\n'.join(buf).strip()
        if len(text) >= 20:
            chunks.append((start, end, text))
        buf = []

    for i, line in enumerate(lines, 1):
        if not buf:
            start = i
        is_heading = kind == 'md' and line.startswith('#') and buf
        too_big = len(buf) >= limit_lines or sum(len(x) for x in buf) >= limit_chars
        if is_heading or too_big or (not line.strip() and buf and sum(len(x) for x in buf) >= 250):
            flush(i - 1)
            if not line.strip():
                continue
            start = i
        buf.append(line)
    if buf:
        flush(len(lines))
    return chunks


def chunk_json(path, text):
    try:
        data = json.loads(text)
    except Exception:
        return chunk_lines(path, text.splitlines(), 'code')
    out = []

    def walk(node, ptr):
        if isinstance(node, list) and node and all(isinstance(x, (dict, str)) for x in node):
            for i, x in enumerate(node):
                s = json.dumps(x, ensure_ascii=False)
                if len(s) > 1500:
                    walk(x, f'{ptr}[{i}]')
                else:
                    out.append((f'{ptr}[{i}]', s))
        elif isinstance(node, dict):
            small = {k: v for k, v in node.items() if not isinstance(v, (list, dict))}
            if small:
                out.append((ptr or '$', json.dumps(small, ensure_ascii=False)))
            for k, v in node.items():
                if isinstance(v, (list, dict)):
                    walk(v, f'{ptr}.{k}' if ptr else f'$.{k}')
    walk(data, '')
    return out


def collect(rev):
    docs = []

    def add(path, text, source):
        ext = os.path.splitext(path)[1].lower()
        if ext == '.json':
            for ptr, s in chunk_json(path, text):
                docs.append({'path': path, 'loc': ptr, 'text': s, 'source': source})
            return
        kind = 'md' if ext == '.md' else 'code'
        for a, b, t in chunk_lines(path, text.splitlines(), kind):
            docs.append({'path': path, 'loc': f'{a}-{b}', 'text': t, 'source': source})

    rc, out = git(['ls-tree', '-r', '--name-only', '-z', rev])
    if rc == 0:
        for name in [x for x in out.split('\0') if x]:
            low = '/' + name.lower()
            if not name.lower().endswith(GIT_EXT) or any(s in low for s in GIT_SKIP):
                continue
            rc2, text = git(['show', f'{rev}:{name}'])
            if rc2 == 0 and len(text) < 400000:
                add(name, text, f'git:{rev}')
    return docs


def build(rev):
    t0 = time.time()
    docs = collect(rev)
    inv, lens = {}, []
    for i, d in enumerate(docs):
        toks = tokens(d['path'] + ' ' + d['text'])
        lens.append(len(toks))
        tf = {}
        for t in toks:
            tf[t] = tf.get(t, 0) + 1
        for t, c in tf.items():
            inv.setdefault(t, []).append((i, c))
    index = {'rev': rev, 'built': time.strftime('%Y-%m-%d %H:%M:%S'), 'n': len(docs), 'avgdl': (sum(lens) / max(1, len(lens))), 'lens': lens,
             'docs': docs, 'inv': inv}
    os.makedirs(os.path.dirname(INDEX_PATH), exist_ok=True)
    json.dump(index, open(INDEX_PATH, 'w', encoding='utf-8'), ensure_ascii=False)
    return index, time.time() - t0


def load():
    return json.load(open(INDEX_PATH, encoding='utf-8'))


def search(index, query, k=6, path_filter=None, k1=1.5, b=0.75):
    qt = tokens(query)
    n, avgdl, lens, inv = index['n'], index['avgdl'], index['lens'], index['inv']
    scores = {}
    seen_q = {}
    for t in qt:
        seen_q[t] = seen_q.get(t, 0) + 1
    for t, qc in seen_q.items():
        post = inv.get(t)
        if not post:
            continue
        idf = math.log(1 + (n - len(post) + 0.5) / (len(post) + 0.5))
        w = 1.0 + 0.2 * (qc - 1)
        for di, c in post:
            if path_filter and path_filter not in index['docs'][di]['path']:
                continue
            tf = c * (k1 + 1) / (c + k1 * (1 - b + b * lens[di] / avgdl))
            scores[di] = scores.get(di, 0.0) + w * idf * tf
    # 질문의 큰 낱말(공백으로 나뉜 단어)이 통째로 들어 있으면 가산점
    words = [w for w in re.split(r'\s+', query.strip()) if len(w) >= 2]
    ranked = sorted(scores.items(), key=lambda x: -x[1])[:max(k * 8, 40)]
    final = []
    for di, sc in ranked:
        text = index['docs'][di]['text']
        bonus = sum(0.6 for w in words if w in text)
        final.append((sc + bonus, di))
    final.sort(key=lambda x: -x[0])
    return final[:k]


def snippet(text, query, width=260):
    words = [w for w in re.split(r'\s+', query.strip()) if len(w) >= 2]
    pos = -1
    for w in words:
        pos = text.find(w)
        if pos >= 0:
            break
    s = max(0, pos - 60) if pos >= 0 else 0
    return text[s:s + width].replace('\n', ' ⏎ ')


def main():
    ap = argparse.ArgumentParser(description='프로젝트 근거 검색기')
    ap.add_argument('query', nargs='*', help='검색 질문')
    ap.add_argument('-k', type=int, default=6)
    ap.add_argument('--path', help='경로에 이 문자열이 들어간 조각만')
    ap.add_argument('--rev', default='HEAD', help='색인을 다시 만들 때 쓸 커밋')
    ap.add_argument('--rebuild', action='store_true')
    ap.add_argument('--json', action='store_true')
    a = ap.parse_args()
    if a.rebuild or not os.path.exists(INDEX_PATH):
        idx, sec = build(a.rev)
        print(f'[색인] {idx["n"]}조각 · 기준 {idx["rev"]} · {sec:.1f}초 → tools/tmp/evidence-index.json', file=sys.stderr)
    else:
        idx = load()
    if not a.query:
        if a.rebuild:
            return 0
        ap.print_help()
        return 0
    q = ' '.join(a.query)
    hits = search(idx, q, a.k, a.path)
    if a.json:
        print(json.dumps([{'rank': i + 1, 'score': round(s, 2), 'path': idx['docs'][di]['path'], 'loc': idx['docs'][di]['loc'],
                           'text': idx['docs'][di]['text'][:1200]} for i, (s, di) in enumerate(hits)], ensure_ascii=False, indent=1))
    else:
        print(f'[질문] {q}   (색인 기준 {idx["rev"]}, {idx["built"]})')
        for i, (s, di) in enumerate(hits, 1):
            d = idx['docs'][di]
            print(f'{i}. {s:6.2f}  {d["path"]}:{d["loc"]}\n     {snippet(d["text"], q)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())

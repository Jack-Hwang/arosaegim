# -*- coding: utf-8 -*-
"""spec_drift_check.py — 명세(문서)와 코드의 숫자 정합을 점검한다 (SDD drift 게이트).

배경: 문서에 적힌 숫자(테스트 수, 엔드포인트 수 …)가 코드가 바뀐 뒤 낡아서
      같은 장 안에서 서로 다른 값이 공존하는 일이 반복됐다. 이 도구는
      '사실 대장'(tools/facts.json)에 적힌 측정 방법으로 코드에서 값을 직접 재고,
      문서 원본에서 그 사실을 말하는 모든 문장을 정규식으로 찾아 값이 다르면 실패로 보고한다.

사용:
  python -I -B -X utf8 tools/spec_drift_check.py --repo . --rev HEAD --facts tools/facts.json
  python -I -B -X utf8 tools/spec_drift_check.py --rev ba96030   # 특정 커밋 기준
  python -I -B -X utf8 tools/spec_drift_check.py --quiet          # 결과 파일만 기록

종료 코드: 0 = 모두 일치, 1 = 불일치 있음, 2 = 측정 실패(--strict 일 때만 실패로 취급)
읽기 전용이다: 저장소는 git grep/show/ls-tree 로만 읽고(--no-optional-locks), 문서는 열어서 읽기만 한다.
"""
import argparse
import fnmatch
import glob
import json
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
DEFAULT_EXCLUDES = ['/_backup/', '/_bak_pre_edit/', '_backup.py', 'pre_resync', '/tmp/', '/qcache/', '/out_un/', '/__pycache__/', '/검토자료_']


def run_git(repo, args):
    cmd = ['git', '-C', repo, '--no-optional-locks'] + args
    p = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.returncode, p.stdout, p.stderr


def measure(repo, rev, spec):
    """측정 명세 → (값, 설명). 실패하면 (None, 사유)."""
    t = spec['type']
    if t in ('git_grep_count', 'git_grep_files'):
        args = ['grep', '-c', '-E', spec['pattern'], rev, '--'] + spec['paths']
        rc, out, err = run_git(repo, args)
        if rc not in (0, 1):
            return None, 'git grep 실패: ' + err.strip()[:120]
        total, files = 0, 0
        for line in out.splitlines():
            if ':' not in line:
                continue
            n = line.rsplit(':', 1)[1]
            if n.isdigit() and int(n) > 0:
                total += int(n)
                files += 1
        return (total if t == 'git_grep_count' else files), f'git grep -c -E {spec["pattern"]!r} {rev} -- {" ".join(spec["paths"])}'
    if t == 'git_ls_tree_count':
        rc, out, err = run_git(repo, ['ls-tree', '-r', '--name-only', '-z', rev, '--', spec['path']])
        if rc != 0:
            return None, 'git ls-tree 실패: ' + err.strip()[:120]
        names = [x for x in out.split('\0') if x]
        names = [x for x in names if fnmatch.fnmatch(os.path.basename(x), spec.get('glob', '*'))]
        for ex in spec.get('exclude', []):
            names = [x for x in names if os.path.basename(x) != ex]
        return len(names), f'git ls-tree -r {rev} -- {spec["path"]} ({spec.get("glob", "*")})'
    if t == 'git_ls_tree_size':
        rc, out, err = run_git(repo, ['ls-tree', '-l', '-z', rev, '--', spec['path']])
        if rc != 0:
            return None, 'git ls-tree 실패: ' + err.strip()[:120]
        sizes = []
        for rec in out.split('\0'):
            m = re.match(r'^\d+ blob [0-9a-f]+\s+(\d+)\t', rec)
            if m:
                sizes.append(int(m.group(1)))
        if not sizes:
            return None, '대상 파일 없음'
        return sum(sizes), f'git ls-tree -l {rev} -- {spec["path"]}'
    if t in ('git_show_regex_count', 'git_show_regex_group'):
        rc, out, err = run_git(repo, ['show', f'{rev}:{spec["file"]}'])
        if rc != 0:
            return None, 'git show 실패: ' + err.strip()[:120]
        flags = re.M
        if t == 'git_show_regex_count':
            return len(re.findall(spec['pattern'], out, flags)), f'git show {rev}:{spec["file"]} | regex {spec["pattern"]!r}'
        m = re.search(spec['pattern'], out, flags)
        if not m:
            return None, f'패턴 불일치 {spec["pattern"]!r}'
        return int(m.group(spec.get('group', 1)).replace(',', '')), f'git show {rev}:{spec["file"]} | regex group'
    if t == 'sum':
        # 여러 측정의 합 — 파라미터화된 시험(함수 1개·케이스 N건)처럼 소스 수와 실행 수가
        # 어긋나는 경우 실행 총계를 정직하게 세기 위해 사용한다(예: 서버 시험 = def test_ 수
        # + 골든셋 케이스 수). 하부 측정이 하나라도 실패하면 전체가 실패다.
        total, parts = 0, []
        for sub in spec['parts']:
            v, desc = measure(repo, rev, sub)
            if v is None:
                return None, '합산 하부 측정 실패: ' + desc
            total += v
            parts.append(f'{desc}={v}')
        return total, ' + '.join(parts)
    return None, f'알 수 없는 측정 유형 {t}'


def expand_files(globs, excludes):
    seen, out = set(), []
    for g in globs:
        for f in sorted(glob.glob(os.path.join(ROOT, g), recursive=True)):
            n = f.replace('\\', '/')
            if any(x in n for x in excludes):
                continue
            if os.path.isfile(f) and n not in seen:
                seen.add(n)
                out.append(f)
    return out


def scan_docs(fact, excludes):
    """문서에서 이 사실을 말하는 모든 문장을 찾아 (파일, 줄, 값, 발췌) 목록으로."""
    hits = []
    for dp in fact.get('doc_patterns', []):
        files = expand_files(dp['globs'], excludes)
        rx = re.compile(dp['regex'])
        skip = dp.get('skip_line_contains', ['→'])
        for f in files:
            try:
                lines = open(f, encoding='utf-8', errors='replace').read().splitlines()
            except OSError:
                continue
            for i, line in enumerate(lines, 1):
                for m in rx.finditer(line):
                    if any(s in line for s in skip):
                        continue
                    val = None
                    for gi in range(1, (rx.groups or 0) + 1):
                        if m.group(gi):
                            val = m.group(gi)
                            break
                    if val is None:
                        continue
                    try:
                        v = int(val.replace(',', ''))
                    except ValueError:
                        continue
                    s = max(0, m.start() - 30)
                    e = min(len(line), m.end() + 30)
                    hits.append({'file': os.path.relpath(f, ROOT).replace('\\', '/'), 'line': i, 'value': v, 'excerpt': line[s:e].strip()})
    return hits


def main():
    ap = argparse.ArgumentParser(description='명세-코드 숫자 정합 점검 (SDD drift gate)')
    # 저장소 안 기준(tools/facts.json)으로 기본 경로를 조정한 판 — 측정 로직·스키마는 동일하다.
    ap.add_argument('--repo', default=ROOT)
    ap.add_argument('--rev', default='HEAD')
    ap.add_argument('--facts', default=os.path.join(ROOT, 'tools', 'facts.json'))
    ap.add_argument('--out', default=os.path.join(ROOT, 'tmp', 'drift-results.json'))
    ap.add_argument('--strict', action='store_true', help='측정 실패도 실패로 취급')
    ap.add_argument('--quiet', action='store_true')
    args = ap.parse_args()

    reg = json.load(open(args.facts, encoding='utf-8'))
    excludes = reg.get('exclude', DEFAULT_EXCLUDES)
    results, bad, unmeasured = [], 0, 0
    rc, head, _ = run_git(args.repo, ['rev-parse', '--short', args.rev])
    head = head.strip() or args.rev
    for fact in reg['facts']:
        val, how = measure(args.repo, args.rev, fact['measure'])
        rec = {'id': fact['id'], 'label': fact['label'], 'actual': val, 'how': how, 'matches': [], 'mismatches': []}
        if val is None:
            unmeasured += 1
            rec['status'] = 'UNMEASURED'
        else:
            hits = scan_docs(fact, excludes)
            for h in hits:
                (rec['matches'] if h['value'] == val else rec['mismatches']).append(h)
            rec['status'] = 'FAIL' if rec['mismatches'] else 'OK'
            bad += len(rec['mismatches'])
        results.append(rec)
    summary = {'rev': head, 'facts': len(results), 'mismatch_sentences': bad, 'unmeasured': unmeasured,
               'ok_facts': sum(1 for r in results if r['status'] == 'OK'),
               'fail_facts': sum(1 for r in results if r['status'] == 'FAIL')}
    out_dir = os.path.dirname(os.path.abspath(args.out))
    os.makedirs(out_dir, exist_ok=True)
    json.dump({'summary': summary, 'results': results}, open(args.out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    if not args.quiet:
        print(f'[drift] 코드 기준 {head} · 사실 {summary["facts"]}개 · 일치 문장 {sum(len(r["matches"]) for r in results)}개')
        for r in results:
            mark = {'OK': 'OK  ', 'FAIL': 'FAIL', 'UNMEASURED': '????'}[r['status']]
            print(f'  {mark} {r["id"]:<18} {r["label"]:<22} 코드값={r["actual"]}  문서에서 일치 {len(r["matches"])}곳' + (f' · 불일치 {len(r["mismatches"])}곳' if r['mismatches'] else ''))
            for h in r['mismatches']:
                print(f'       ✗ {h["file"]}:{h["line"]} 문서={h["value"]}  …{h["excerpt"]}…')
            if r['status'] == 'UNMEASURED':
                print(f'       ? {r["how"]}')
        print(f'[drift] 결과 → {os.path.relpath(args.out, ROOT)}')
    if bad:
        return 1
    if unmeasured and args.strict:
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())

# -*- coding: utf-8 -*-
"""spec_check.py — 수용 기준 정본(docs/spec/ac/*.json) 점검기 (표준 라이브러리만 사용)

docs/spec/ac/ 아래의 수용 기준 JSON 파일(스키마는 tools/README.md 참조)을 읽어 다섯 가지를 점검한다.
  1. 수용 기준 번호(ARO-F<기능>-AC<두 자리>)가 파일 안·파일 사이에서 중복되지 않는다 — 중복은 오류.
  2. 기능마다 다섯 유형(ubiquitous·event·state·optional·unwanted)을 모두 다루는지 보고한다.
     부족해도 오류로 쓰지 않고 목록만 보여 준다.
  3. 수용 기준 문장(text)에 흐릿한 형용사 금지어가 없다 — 한 건이라도 있으면 오류.
  4. verify.refs의 시험 이름이 저장소에 실존하는지 git grep -F로 확인한다.
     앱 시험(unit)은 android/app/src/test, 서버 시험(server)은 backend/tests에서 먼저 찾고,
     못 찾으면 저장소 전체를 폴백으로 본다. 시험 이름이 실존하지 않으면 오류.
     emulator·code·none 유형의 refs는 시험 이름이 아니므로 점검 대상에서 제외한다.
  5. status가 허용 어휘([확인]·[코드 확인]·[일부 확인]·[실패]·[미확인]·(설계)·(계획)·(구상)·(가정)·(예시))
     가운데 정확히 한 덩어리인지 확인한다.

옵션:
  --write-trace  docs/SPEC_TRACE.md 을 자동 생성한다(기능별 표 + 요약 표).
  --counts       앱/서버 시험 수를 집계해 출력한다(앱: android/app/src/test 의 @Test, 서버: backend/tests 의 def test_).

종료 코드: 0 = 오류 없음, 1 = 오류 있음(JSON 파싱 실패 포함).
"""
import argparse
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..'))
AC_DIR_REL = 'docs/spec'   # docs/spec/ 아래(하위 ac/ 포함)의 수용 기준 JSON 전부
TRACE_REL = 'docs/SPEC_TRACE.md'

AC_ID_RX = re.compile(r'^ARO-F(\d+)-AC(\d{2})$')
FEATURE_RX = re.compile(r'^F(\d+)$')
PATTERN_ORDER = ('ubiquitous', 'event', 'state', 'optional', 'unwanted')
PATTERN_KO = {'ubiquitous': '항상', 'event': '사건', 'state': '상태', 'optional': '선택', 'unwanted': '원치 않는 상황'}
VERIFY_KINDS = ('unit', 'server', 'emulator', 'code', 'none')
TEST_KINDS = ('unit', 'server')
KIND_SCOPE = {'unit': ['android/app/src/test'], 'server': ['backend/tests']}
VAGUE_WORDS = ['빠르게', '안전하게', '적절히', '충분히', '쉽게', '가능한 한', '대부분', '자주', '최대한']
STATUS_WORDS = ['[확인]', '[코드 확인]', '[일부 확인]', '[실패]', '[미확인]', '(설계)', '(계획)', '(구상)', '(가정)', '(예시)']


def run_git(repo, args):
    cmd = ['git', '-C', repo, '--no-optional-locks'] + args
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    except OSError:
        return 127, '', 'git 실행 불가'
    return p.returncode, p.stdout, p.stderr


def head_short(repo, rev='HEAD'):
    rc, out, _ = run_git(repo, ['rev-parse', '--short', rev])
    return out.strip() if rc == 0 and out.strip() else rev


REF_PREFIXES = ('단위 테스트 ', '서버 시험 ', '유닛 테스트 ', '앱 단위 테스트 ', '백엔드 시험 ')
POINTER_PREFIXES = ('소스 ', '개발 기록', '기록 ')
IDENT_RX = re.compile(r'^[A-Za-z_][A-Za-z0-9_.]*$')


def strip_annotation(s):
    """끝에 붙은 괄호 설명 "이름(단언 설명)" 의 괄호를 떼어낸다."""
    return re.sub(r'\s*\([^)]*\)\s*$', '', s.strip())


def clean_test_ref(ref):
    """refs 표기에서 검색에 쓸 시험 이름 후보를 만든다(`이름`, Class.`이름`, '서버 시험 test_x(설명)')."""
    r = ref.strip()
    cands = [r]
    for p in REF_PREFIXES:
        if r.startswith(p):
            cands.append(r[len(p):])
    m = re.search(r'`([^`]+)`', r)
    if m:
        cands.append(m.group(1))
    stripped = [strip_annotation(c) for c in list(cands)]
    for s in stripped:
        cands.append(s)
        if '.' in s:
            cands.append(s.rsplit('.', 1)[1].strip())
    seen, out = set(), []
    for c in cands:
        c = c.strip()
        if c and c not in seen:
            seen.add(c)
            out.append(c)
    return out


def classify_ref(ref):
    """ref가 시험 이름인지('test'), 코드·기록 가리킴인지('pointer') 판정한다."""
    r = ref.strip()
    if r.startswith(POINTER_PREFIXES):
        return 'pointer'
    if re.search(r'`([^`]+)`', r):
        return 'test'
    core = r
    for p in REF_PREFIXES:
        if core.startswith(p):
            core = core[len(p):]
    core = strip_annotation(core)
    if '.' in core:
        core = core.rsplit('.', 1)[1].strip()
    if IDENT_RX.match(core):
        return 'test'
    return 'pointer'


def test_name_exists(repo, rev, ref, kind):
    """시험 이름을 범위 우선(git grep -F)으로 찾는다. (실존 여부, 찾은 이름)"""
    cands = clean_test_ref(ref)
    scopes = KIND_SCOPE.get(kind, []) + [None]  # None = 저장소 전체 폴백
    for scope in scopes:
        for name in cands:
            args = ['grep', '-c', '-F', name, rev]
            if scope:
                args += ['--'] + [scope]
            rc, out, _ = run_git(repo, args)
            if rc == 0:
                return True, name
    return False, cands[0]


def load_ac_files(repo, errors):
    """docs/spec/ 아래(하위 폴더 포함)의 수용 기준 JSON을 읽어 [(상대 경로, 데이터)] 를 돌려준다.
    파싱 실패는 오류로 쌓고, 수용 기준 형식이 아닌 JSON은 건너뛴다."""
    ac_dir = os.path.join(repo, AC_DIR_REL)
    if not os.path.isdir(ac_dir):
        return None
    out = []
    for dp, dn, fn in os.walk(ac_dir):
        dn.sort()
        for f in sorted(fn):
            if not f.endswith('.json'):
                continue
            rel = os.path.relpath(os.path.join(dp, f), repo).replace('\\', '/')
            try:
                with open(os.path.join(dp, f), encoding='utf-8') as fh:
                    data = json.load(fh)
            except (OSError, ValueError) as e:
                errors.append(f'{rel}: JSON 읽기·해석 실패 — {e}')
                continue
            if not isinstance(data, dict) or ('feature' not in data and 'acs' not in data):
                print(f'[spec_check] {rel}: 수용 기준 형식이 아니므로 건너뛴다(feature·acs 키 없음)')
                continue
            out.append((rel, data))
    out.sort(key=lambda x: x[0])
    return out


def validate_file(fname, data, errors, seen_ids):
    """한 파일의 스키마·규칙을 점검한다. (기능 키, 수용 기준 목록)"""
    rel = fname
    feature = data.get('feature')
    name = data.get('name')
    acs = data.get('acs')
    if not isinstance(feature, str) or not FEATURE_RX.match(feature):
        errors.append(f'{rel}: feature 값이 F<숫자> 형태가 아니다 — {feature!r}')
        feature = None
    if not isinstance(name, str) or not name.strip():
        errors.append(f'{rel}: name(기능 이름)이 비어 있다')
    if not isinstance(acs, list):
        errors.append(f'{rel}: acs가 목록이 아니다')
        return feature, []
    out = []
    for i, ac in enumerate(acs):
        tag = f'{rel} acs[{i}]'
        if not isinstance(ac, dict):
            errors.append(f'{tag}: 수용 기준이 객체가 아니다')
            continue
        ac_id = ac.get('id')
        m = AC_ID_RX.match(ac_id) if isinstance(ac_id, str) else None
        if not m:
            errors.append(f'{tag}: 수용 기준 번호 {ac_id!r}가 ARO-F<기능>-AC<두 자리> 형태가 아니다')
        else:
            if feature and m.group(1) != feature[1:]:
                errors.append(f'{tag}: 번호 {ac_id}의 기능 번호(F{m.group(1)})가 feature({feature})와 다르다')
            if ac_id in seen_ids:
                errors.append(f'{tag}: 수용 기준 번호 중복 — {ac_id} (처음: {seen_ids[ac_id]})')
            else:
                seen_ids[ac_id] = rel
        pattern = ac.get('pattern')
        if pattern not in PATTERN_ORDER:
            errors.append(f'{tag}: pattern 값이 다섯 유형이 아니다 — {pattern!r} (허용: {", ".join(PATTERN_ORDER)})')
        text = ac.get('text')
        if not isinstance(text, str) or not text.strip():
            errors.append(f'{tag}: 수용 기준 문장(text)이 비어 있다')
        else:
            for w in VAGUE_WORDS:
                if w in text:
                    errors.append(f'{tag}: 흐릿한 형용사 금지어 발견 — "{w}"')
        verify = ac.get('verify')
        if not isinstance(verify, dict):
            errors.append(f'{tag}: verify 객체가 없다')
        else:
            kind = verify.get('kind')
            refs = verify.get('refs')
            if kind not in VERIFY_KINDS:
                errors.append(f'{tag}: verify.kind가 허용 값이 아니다 — {kind!r} (허용: {", ".join(VERIFY_KINDS)})')
            if not isinstance(refs, list) or not all(isinstance(r, str) for r in refs):
                errors.append(f'{tag}: verify.refs가 문자열 목록이 아니다')
            elif kind == 'none' and refs:
                errors.append(f'{tag}: verify.kind가 none인데 refs가 있다 — {refs!r}')
        status = ac.get('status')
        if status not in STATUS_WORDS:
            hit = [w for w in STATUS_WORDS if isinstance(status, str) and w in status]
            errors.append(f'{tag}: status가 허용 어휘 한 덩어리가 아니다 — {status!r}' + (f' (포함된 어휘: {", ".join(hit)})' if hit else ''))
        io = ac.get('io')
        if io is not None and (not isinstance(io, list) or not all(isinstance(x, dict) and 'in' in x and 'out' in x for x in io)):
            errors.append(f'{tag}: io는 {{"in", "out"}} 객체 목록이어야 한다')
        numbers = ac.get('numbers')
        if numbers is not None and (not isinstance(numbers, list) or not all(isinstance(x, str) for x in numbers)):
            errors.append(f'{tag}: numbers는 문자열 목록이어야 한다')
        out.append(ac)
    return feature, out


def count_tests(repo):
    """작업 트리 기준 앱/서버 시험 수를 센다."""
    out = {}
    targets = [('앱', os.path.join(repo, 'android', 'app', 'src', 'test'), '.kt', '@Test'),
               ('서버', os.path.join(repo, 'backend', 'tests'), '.py', 'def test_')]
    for label, base, ext, needle in targets:
        total, files = 0, 0
        if os.path.isdir(base):
            for dp, dn, fn in os.walk(base):
                dn[:] = [d for d in dn if d != '__pycache__']
                for f in fn:
                    if not f.endswith(ext):
                        continue
                    try:
                        text = open(os.path.join(dp, f), encoding='utf-8', errors='replace').read()
                    except OSError:
                        continue
                    n = text.count(needle)
                    if n:
                        total += n
                        files += 1
        out[label] = (total, files)
    return out


def cell(s):
    return s.replace('\n', ' ').replace('|', '\\|').strip()


def write_trace(repo, groups, check_results, errors):
    """docs/SPEC_TRACE.md 를 만든다. groups=[(feature, name, [ac, ...])], check_results={ac_id: [(실존, 이름)]}"""
    commit = head_short(repo)
    lines = []
    lines.append('# 명세 추적표 — 수용 기준 ↔ 시험 이름')
    lines.append('')
    lines.append(f'기준 커밋: {commit}. 수용 기준 번호는 ARO-F<기능>-AC<두 자리>입니다. "확인 방법"의 시험 이름은 '
                 '저장소의 앱 단위 시험(`@Test`) 또는 서버 시험(`def test_`) 이름이며,')
    lines.append('"실존" 열은 위 커밋에서 그 이름이 실제로 검색되는지를 자동으로 점검한 결과입니다(없음이면 이름을 고치거나 시험을 추가해야 합니다). '
                 'emulator·code·none 유형의 연결은 시험 이름이 아니므로 점검 대상에서 제외합니다(-).')
    lines.append('')
    for feature, name, acs in groups:
        lines.append(f'## {feature} {name} ({len(acs)}개)')
        lines.append('')
        lines.append('| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |')
        lines.append('|---|---|---|---|---|---|')
        for ac in acs:
            verify = ac.get('verify') or {}
            kind = verify.get('kind', '?')
            refs = [r for r in (verify.get('refs') or []) if isinstance(r, str)]
            how = ', '.join(refs) if refs else '없음'
            marks = []
            for r in refs:
                if kind in TEST_KINDS:
                    v = check_results.get(ac.get('id'), {}).get(r, False)
                    marks.append('-' if v is None else ('O' if v else '없음'))
                else:
                    marks.append('-')
            lines.append(f'| {cell(str(ac.get("id", "?")))} | {PATTERN_KO.get(ac.get("pattern"), str(ac.get("pattern")))} | '
                         f'{cell(str(ac.get("text", "")))} | {cell(kind + ": " + how)} | {" ".join(marks) if marks else "-"} | '
                         f'{cell(str(ac.get("status", "?")))} |')
        lines.append('')
    lines.append('## 요약')
    lines.append('')
    lines.append('| 기능 | 이름 | 수용 기준 | 유형 분포 | 상태 분포 | 시험 연결 |')
    lines.append('|---|---|---|---|---|---|')
    total_checked, total_missing, total_excluded = 0, 0, 0
    for feature, name, acs in groups:
        pcount = {PATTERN_KO[p]: sum(1 for a in acs if a.get('pattern') == p) for p in PATTERN_ORDER}
        pdist = ', '.join(f'{k} {v}' for k, v in pcount.items() if v)
        scount = {}
        for a in acs:
            scount[a.get('status', '?')] = scount.get(a.get('status', '?'), 0) + 1
        sdist = ', '.join(f'{k} {v}' for k, v in scount.items())
        linked = sum(1 for a in acs if (a.get('verify') or {}).get('kind') in TEST_KINDS)
        for a in acs:
            kind = (a.get('verify') or {}).get('kind')
            for r in (a.get('verify') or {}).get('refs') or []:
                v = check_results.get(a.get('id'), {}).get(r, False) if kind in TEST_KINDS else '-'
                if kind in TEST_KINDS and v is not None:
                    total_checked += 1
                    if not v:
                        total_missing += 1
                else:
                    total_excluded += 1
        lines.append(f'| {feature} | {cell(str(name))} | {len(acs)} | {cell(pdist)} | {cell(sdist)} | {linked} |')
    lines.append('')
    lines.append(f'시험 이름 점검: 확인 대상 {total_checked}건 중 없음 {total_missing}건, 점검 제외 {total_excluded}건.')
    if errors:
        lines.append('')
        lines.append(f'점검 오류 {len(errors)}건 — spec_check 실행 결과를 참조합니다.')
    path = os.path.join(repo, TRACE_REL)
    with open(path, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(lines) + '\n')
    return path


def main():
    ap = argparse.ArgumentParser(description='수용 기준 정본(docs/spec/ac/*.json) 점검기')
    ap.add_argument('--repo', default=ROOT, help='저장소 루트(기본: 이 스크립트 기준 상위)')
    ap.add_argument('--rev', default='HEAD', help='시험 이름 실존 확인에 쓸 커밋(기본 HEAD)')
    ap.add_argument('--write-trace', action='store_true', help='docs/SPEC_TRACE.md 생성')
    ap.add_argument('--counts', action='store_true', help='앱/서버 시험 수 집계 출력')
    args = ap.parse_args()
    repo = os.path.abspath(args.repo)
    errors = []
    loaded = load_ac_files(repo, errors)
    if args.counts:
        for label, (total, files) in count_tests(repo).items():
            print(f'[시험 수] {label}: {total}건 ({files}개 파일)')
    if loaded is None:
        print(f'[spec_check] {AC_DIR_REL}/ 폴더가 아직 없다 — 수용 기준 정본이 만들어지면 다시 실행한다. (종료 코드 0)')
        return 1 if errors else 0
    if not loaded:
        print(f'[spec_check] {AC_DIR_REL}/ 에 JSON 파일이 없다 — 수용 기준 정본이 만들어지면 다시 실행한다. (종료 코드 0)')
        return 1 if errors else 0

    seen_ids = {}
    groups = []
    for fname, data in loaded:
        feature, acs = validate_file(fname, data, errors, seen_ids)
        key = int(feature[1:]) if feature and feature[1:].isdigit() else 9999
        groups.append((key, feature or '?', data.get('name') or '(이름 없음)', acs))
    groups.sort(key=lambda g: g[0])
    ordered = [(f, n, a) for _, f, n, a in groups]

    # 시험 이름 실존 점검(unit·server 한정)
    check_results = {}
    checked, missing, excluded = 0, 0, 0
    for feature, name, acs in ordered:
        for ac in acs:
            verify = ac.get('verify') or {}
            kind = verify.get('kind')
            refs = [r for r in (verify.get('refs') or []) if isinstance(r, str)]
            for r in refs:
                if kind in TEST_KINDS:
                    if classify_ref(r) == 'pointer':
                        check_results.setdefault(ac.get('id'), {})[r] = None
                        excluded += 1
                        continue
                    ok, found = test_name_exists(repo, args.rev, r, kind)
                    check_results.setdefault(ac.get('id'), {})[r] = ok
                    checked += 1
                    if not ok:
                        missing += 1
                        errors.append(f'{AC_DIR_REL}: {ac.get("id")} verify.refs의 시험 이름이 저장소에 없다 — "{r}" (kind={kind})')
                else:
                    excluded += 1

    # 유형 보고(강제 아님 — 목록만)
    for feature, name, acs in ordered:
        have = {ac.get('pattern') for ac in acs}
        lack = [PATTERN_KO[p] for p in PATTERN_ORDER if p not in have]
        if lack:
            print(f'[유형] {feature} {name}: 없는 유형 — {", ".join(lack)} (보고만, 오류 아님)')

    print(f'[spec_check] 파일 {len(loaded)}개 · 수용 기준 {sum(len(a) for _, _, a in ordered)}건 · '
          f'시험 이름 실존 확인 {checked}건(없음 {missing}) · 점검 제외 {excluded}건 · 오류 {len(errors)}건')
    for e in errors:
        print(f'  ✗ {e}')
    if args.write_trace:
        path = write_trace(repo, ordered, check_results, errors)
        print(f'[spec_check] 추적표 생성 → {TRACE_REL}')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())

# tools/ — 명세·근거 도구 묶음·숫자 대조·시연 스크립트

## 숫자 대조(spec_drift_check.py)

용법: 저장소 루트에서 `python -I -B -X utf8 tools/spec_drift_check.py --repo . --rev HEAD --facts tools/facts.json`

- `facts.json`은 저장소 코드의 측정 방법(git grep/show/ls-tree)과 문서 문장 검사 규칙을 담은 사실 대장이다. 측정값은 문서에 박아 두지 않고, **현재 값은 도구를 돌려 확인한다.**
- `facts.json`의 `doc_patterns`은 저장소 안 문서(README.md · docs/ARCHITECTURE.md · docs/ADR.md · docs/TRACEABILITY.md · docs/spec/*.md)에서 그 사실을 말하는 문장을 찾는다. docs/DEV_LOG.md은 시간순 기록이라 총계 문장 감시에서 제외한다(값·표기로 앵커한 문장만 한정 감시한다).
- 기대 결과(2026-11-14, HEAD 실측): 앱 @Test 197 · 서버 def test_ 87 · 엔드포인트 37 · 라우터 11 · 모델 46 · strings 122 · 포트 7 · APK(안드로이드 앱 패키지) 40484425바이트 — 전 문장 일치, 종료 코드 0.
- 실행 결과는 `tools/drift-results.json`에 기록된다. 문서 숫자가 코드값과 어긋나면 종료 코드 1로 실패한다.
- 읽기 전용 도구다(git grep/show/ls-tree로만 저장소를 읽는다) — 문서·코드를 바꾸지 않는다. 파이썬 표준 라이브러리만 쓴다.

## 수용 기준 점검(spec_check.py)

용법: 저장소 루트에서 `python -I -B -X utf8 tools/spec_check.py` (옵션: `--write-trace` · `--counts`)

- `docs/spec/` 아래의 수용 기준 정본 JSON(`docs/spec/*.json`, 하위 `docs/spec/ac/*.json` 포함)을 점검한다: ① 수용 기준 번호(`ARO-F<기능>-AC<두 자리>`)의 파일 안·전체 중복은 오류 ② 기능마다 다섯 유형(ubiquitous=항상 · event=사건 · state=상태 · optional=선택 · unwanted=원치 않는 상황) 포함 여부는 목록만 보고(강제 아님) ③ 수용 기준 문장의 흐릿한 형용사 금지어(빠르게·안전하게·적절히·충분히·쉽게·가능한 한·대부분·자주·최대한)는 오류 ④ `verify.refs`의 시험 이름 실존 — 앱 시험(unit)은 `android/app/src/test`, 서버 시험(server)은 `backend/tests`에서 먼저 `git grep -F`로 찾고 저장소 전체로 폴백, 없으면 오류(emulator·code·none은 점검 제외, "소스 …"·"개발 기록 …" 가리킴도 제외) ⑤ `status`는 허용 어휘([확인]·[코드 확인]·[일부 확인]·[실패]·[미확인]·(설계)·(계획)·(구상)·(가정)·(예시)) 중 정확히 한 덩어리.
- 스키마: `{"feature": "F1", "name": "…", "acs": [{"id": "ARO-F1-AC01", "pattern": "event", "text": "…", "io": [{"in": "…", "out": "…"}], "verify": {"kind": "unit|server|emulator|code|none", "refs": ["…"]}, "status": "[코드 확인]", "numbers": ["120m"], "note": "부연(선택)"}]}`
- `--write-trace`로 `docs/SPEC_TRACE.md`(도구 산출물)를 생성한다 — 기능별 표(번호·유형·수용 기준·확인 방법·시험 이름 실존 O/없음·상태)와 요약 표(기능별 수용 기준 수·유형 분포·상태 분포·시험 연결 수).
- `--counts`는 앱/서버 시험 수를 집계해 출력한다(앱: `android/app/src/test`의 `@Test` 개수, 서버: `backend/tests`의 `def test_` 개수).
- 기대 결과: 오류가 없으면 `[spec_check] 파일 N개 · 수용 기준 M건 · … · 오류 0건`을 출력하고 종료 코드 0. JSON 해석 실패도 오류이며 종료 코드 1. `docs/spec/ac/`가 아직 비어 있으면 안내 문장만 출력하고 종료 코드 0이다.

## 근거 검색기(evidence_search.py)

용법: 저장소 루트에서 `python -I -B -X utf8 tools/evidence_search.py "질문" -k 6` (옵션: `--path 접두문자열` · `--json` · `--rebuild`)

- 저장소 전체(코드 · docs/ · android assets 시드)를 조각으로 나눠 색인하고(단어 빈도 기반 순위 방식 BM25(Best Match 25) + 한글 2글자 조각), 질문에 가장 관련 있는 조각을 파일:줄 위치와 함께 돌려준다.
- 기대 결과: 상위 근거에 `docs/DEV_LOG.md`(기록 번호)·관련 소스 파일이 파일:줄 위치와 함께 나온다.
- 색인은 `tools/tmp/evidence-index.json`에 캐시되며, 코드가 바뀌면 `--rebuild`로 다시 만든다.

## 검색 품질 평가(eval_retrieval.py + eval_data/evidence_golden.jsonl)

용법: 저장소 루트에서 `python -I -B -X utf8 tools/eval_retrieval.py --rebuild` (옵션: `--k 5` · `--gate-hit5 0.85` · `--gate-mrr 0.60`)

- 골든셋 42건(질문마다 정답 경로와 그 조각에 포함돼야 할 문자열)으로 위 검색기를 평가한다. 지표: Hit@k(상위 k 안 적중률) · Recall@k · MRR(평균 역순위) · nDCG@5(순위 보정 적중률).
- 기대 결과(2026-11-13 HEAD 기준 실측): `Hit@1=0.714 · Hit@3=0.905 · Hit@5=1.000 · MRR=0.818 · nDCG@5=0.748`, 게이트 Hit@5 ≥ 0.85 · MRR ≥ 0.60 통과, 종료 코드 0. 상세는 `tools/tmp/retrieval-eval.json`.

## 시연·실측 스크립트

- `drive-demo-route.ps1` — 에뮬레이터 가상 위치로 데모 코스를 주행시킨다.
- `grant-permissions.ps1` — dangerous 권한을 adb로 부여한다.
- `record-demo.ps1` — 에뮬레이터 화면을 녹화해 `demo/raw/`에 저장한다(완성본은 `demo/final/`).

공통: PowerShell 5.1/7 호환. 읽기 전용이며 저장소 문서·코드를 바꾸지 않는다.

## 기타

- `tools/tmp/`는 색인·평가 결과 캐시다 — 얼마든지 지워도 되며 다음 실행 때 다시 만들어진다. 커밋 전에는 지우면 깨끗하다.
- 도구 5종은 파이썬 표준 라이브러리만 쓰고, 저장소 밖 경로를 읽거나 쓰지 않는다.

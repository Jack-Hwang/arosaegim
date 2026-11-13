# tools/ — 숫자 대조(drift gate)·시연 스크립트

용법: `cd arosaegim/tools && python -I -B -X utf8 spec_drift_check.py --repo .. --rev HEAD --facts facts.json`
spec_drift_check.py는 `../proof/` 원본의 사본이며 기본 경로만 저장소 안(tools/facts.json)으로 조정했다.
facts.json은 저장소 코드의 측정 방법(git grep/show/ls-tree)과 기획서 문서 문장 검사 규칙을 담은 사실 대장이다 — 측정값은 커밋 d4eb823 기준 앱 @Test 163·서버 86·라우터 11·strings 121·APK 40454385바이트.
결과는 tools/drift-results.json에 기록되고, 문서 숫자가 코드값과 어긋나면 종료 코드 1로 실패한다.
읽기 전용 도구다(git grep/show/ls-tree로만 저장소를 읽는다) — 문서·코드를 바꾸지 않는다.

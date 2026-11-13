# TRACEABILITY — 요구 ↔ 구현 ↔ 시험 대조표

> 본 문서의 숫자는 코드 기준(커밋 `d4eb823`, 2026-11-13)이며 docs/DEV_LOG.md 표와 짝을 이룬다.
> 앱 단위 테스트 179건·서버 테스트 87건은 커밋 기준값(HEAD 측정)이고,
> 서버 실행 총합 87건은 본 문서 작성 시 추가한 잠금 시각 검증 1건을 포함한 실측값이다(`pytest tests -q`, 2026-11-13).
> 상태 표기: [실측 PASS] = 에뮬레이터 실측 완료, [코드 확인] = 코드·단위 테스트로 확인(에뮬레이터 실측 전),
> [앱 구현·실측 전] = 구현 완료·화면 실측 대기, [실측 부분] = 일부만 실측. 과장 없이 DEV_LOG의 F번호·Q번호를 따른다.

## 1. 07문서 기능(A1~A8·B1~B5) ↔ 구현 ↔ 시험

근거 문서: glm/07_NEXT_TASKS_2026-11-11.md. 구현 위치는 `android/app/src/main/java/app/arosaegim/`(이하 `app/`)과 `backend/` 기준.

| 항목 | 요구 요지 | 구현 위치(파일·클래스) | 시험(테스트 클래스 · 케이스 수) | 상태 |
|---|---|---|---|---|
| A1 | 사전 제작 오디오 기본 경로, 합성은 초기화 성공 시에만 | app/SwitchableNarrator.kt, app/fallback/AssetAudioNarrator.kt, app/pleos/PleosTtsNarrator.kt, app/fallback/AndroidTtsNarrator.kt, assets/voice/letter_demo.wav | SwitchableNarratorTest · 5 | [앱 구현·소리 실측 전] — U6 FAIL(에뮬레이터 TTS 부재·onReady 미수신, F54), 묵음 구간 Q21 |
| A2 | 기어 D→P 자동 정차 화면 전환 | app/CombinedDrivingState.kt, app/domain/DrivingStateMerger.kt, app/domain/ModeStateMachine.kt | CombinedDrivingStateTest · 3, DrivingStateMergerTest · 8, ModeStateMachineTest · 5 | [실측 PASS] — D→P 무조작 복귀 2회(F72), U7 진전 |
| A3 | 진단 관찰을 꺼도 다음 장소 도착 판정 유지 | app/pleos/FusedArrivalDetector.kt(startObservation/stopObservation), app/session/TripSession.kt(rewatchCurrentTarget) | TripSessionTest · 14(rewatch 2케이스 — F67) | [코드 확인] — 재관찰 자동 진행은 F77로 별도 확인 |
| A4 | 기어 D·속도 30 이상 주행 중 도착 기록 | 실측 항목 — app/session/TripSession.kt 파이프라인(코드 동일) | —(U표 실측으로 검증) | [실측 PASS] — 40km/h 주행 중 도착·스탬프·다음 목표(F68·F74) |
| A5 | 좌석 점유·주행거리 값 화면 반영 | app/pleos/PleosTripSignalProvider.kt(Seat 합산·Odometer), app/session/TripSession.kt(partySize 재조회) | TripSessionTest · 14(tripKm·partySize 포함) | [실측 부분] — 문장 표시 PASS(F73), 주입 3 vs 표시 1 불일치 Q27 |
| A6 | README·ARCHITECTURE 옛 숫자 갱신, APK 경로 기재 | docs/README.md, docs/ARCHITECTURE.md | —(문서 작업) | [완료] — 179/87·APK 경로 반영(2026-11-13 오후 재갱신) |
| A7 | 내비 초기화 3초 초과 성공 시 실구현 전환 | app/SwitchableRouteService.kt(일방향 업그레이드), app/pleos/PleosNaviRouteService.kt | SwitchableRouteServiceTest · 8 | [코드 확인] — 경로 요청은 U1·U2 직접 검증으로 커버(Q15) |
| A8 | 로그아웃 시 visits.json·visit_queue.json·세션 삭제 | app/data/SignOutCleanup.kt, app/data/FamilySessionStore.kt(signOut) | SignOutCleanupTest · 4, FamilySessionStoreTest · 16 | [코드 확인] |
| B1 | 완주 후 '이어받을 사람' 지정(차량은 지정까지) | app/domain/HeirQuestionPolicy.kt, app/ui/parked/HeirPinGuide.kt, backend/app/routers/members.py(heir·owner 403) | HeirQuestionTest · 7, HeirPinGuideTest · 6, 백엔드 test_b_features.py heir 5건·test_family.py handover 2건 | [앱 구현·실측 전] — PIN 없는 세션의 403 안내 실측 Q20 |
| B2 | 옛 기록 없는 가족의 첫 기록 문구·방문 순번 | app/domain/ThenNowResolver.kt, app/ui/parked/ParkedScreen.kt, backend/app/routers/places.py(visit_ordinal) | ThenNowResolverTest · 12, 백엔드 test_then_now.py · 4, test_b_features.py visit_ordinal 2건 | [앱 구현·실측 전] |
| B3 | 가족 단위 함께 달린 거리 누적(코스 중복 제거) | backend/app/routers/metrics.py, backend/app/services/summary.py(course_run_id 기준), app/ui/parked 화면 표시 | 백엔드 test_b_features.py total_trip_km 3건, test_prefs.py 1건 | [앱 구현·실측 전] — 단위 km 재확인은 F56 대기 |
| B4 | 인원 비교 문장 접어 두기(장소별·전체) | app/ui/parked/PartyPrefs.kt, backend/app/routers/preferences.py(prefs API), backend/app/services/family_prefs.py | PartyPrefsTest · 12, 백엔드 test_prefs.py · 10 | [앱 구현·실측 전] |
| B5 | 사진 속 사람 이름표(비율 좌표, 자동 인식 없음) | backend/app/routers/memories.py(memory_markers 상한 10), 웹 입력 화면 | 백엔드 test_b_features.py markers 5건·export 2건 | [서버·웹 구현·실측 전] |

## 2. E2E 감사 수정(E1~E5) ↔ 수정 파일 ↔ 회귀 시험

근거: docs/DEV_LOG.md 2026-11-12 섹션(F61~F62 원인·수정), 커밋 87c93e8.

| 항목 | 수정 내용 | 수정 파일 | 회귀 시험 |
|---|---|---|---|
| E1 | postVisit 계약을 서버와 일치: 스네이크 케이스 필드, X-Family-Code+X-Family-Pin 헤더, visit_id(정수)/stamp_no 파싱, party_size·trip_km·course_run_id, wireJson(null 생략) | app/data/ApiClient.kt(VisitPayload·wireJson) | VisitPayloadContractTest · 4(계약 테스트 신설) |
| E2 | '그때와 지금'이 서버 past(연도·메모·사진 URL·인원·차량)를 렌더링 — 서버 past > 시드 예시('예시' 태그) > 첫 방문 문구 | app/domain/ThenNowResolver.kt, app/ui/parked/ParkedScreen.kt(/uploads 사진 로드) | ThenNowResolverTest · 12, 백엔드 test_then_now.py · 4 |
| E3 | ModeRouter intent에 FLAG_ACTIVITY_NEW_TASK 추가 + 실패 시 진단 '마지막 오류' 기록(+Q28 세션 게이트 — 후속) | app/ui/ModeRouter.kt | ModeRouterResolveTest · 4 |
| E4 | Narrator 3단 체인: Pleos TTS·Android TTS 실패 시 AssetAudioNarrator(사전 제작 오디오) 재시도 | app/SwitchableNarrator.kt, app/fallback/AssetAudioNarrator.kt | SwitchableNarratorTest · 5 |
| E5 | start() 시점 첫 목표에도 경로 요청 + routeBecameReady 전환 시 진행 중 목표 재요청(AppContainer 배선) | app/session/TripSession.kt, app/SwitchableRouteService.kt(switched), app/AppContainer.kt | TripSessionTest · 14, SwitchableRouteServiceTest · 8 |

## 3. 백엔드 가드 ↔ 근거 코드 ↔ 시험

| 가드 | 근거 코드 위치 | 시험(파일 · 케이스) |
|---|---|---|
| 포인트 건당 상한: 0 < 금액 ≤ 1,000,000, 초과 시 rejected(invalid_amount) | backend/app/services/points.py:16(`MAX_AMOUNT_KRW`)·:37-38(규칙 1) | test_points.py test_amount_boundary, test_evaluate_receipt_pure_function |
| 포인트 일일 상한: 가족당 하루 accepted 5건, 6건째 rejected(daily_limit) | backend/app/services/points.py:17(`DAILY_LIMIT`)·:48-49(규칙 6) | test_points.py test_daily_limit_after_five, test_evaluate_receipt_pure_function |
| PIN 잠금: 5회 연속 실패 시 10분 잠금, 잠금 중 올바른 PIN도 429(family_locked), locked_until은 미래 시각으로 기록 | backend/app/security.py:56-78(`_fail_pin`·`_is_locked`), backend/app/config.py:27-28(`PIN_LOCK_ATTEMPTS`·`PIN_LOCK_MINUTES`) | test_family.py test_pin_locks_after_five_failures, test_pin_lock_records_locked_until_in_future |
| PIN 저장: 소금 친 SHA-256 해시만 저장, 비교는 상수 시간(hmac.compare_digest) | backend/app/security.py:16-26(`_hash_pin`·`verify_pin`) | 직접 단위 테스트 없음 — 전 API 테스트가 인증 경로를 통과(간접 커버) |
| (참고) 영수증 사진 필수·사진 해시 중복 판정 | backend/app/services/points.py 규칙 2·3, receipt_key·photo_hash_of | test_receipt_rules.py · 11, test_points.py duplicate 2건 |
| (참고) 가족 코드 생성 빈도 제한(기기당 하루 1회) | backend/app/ratelimit.py, backend/app/config.py:39-40 | test_doc02_features.py test_family_code_device_token_daily_limit |

## 4. 검증 명령

- 서버: `cd arosaegim/backend && python -m pytest tests -q` — 87 passed(2026-11-13, 신규 1건 포함).
- 앱: `cd arosaegim/android && ./gradlew test` — 179/179(2026-11-13 오후 기준 — Q26·Q27 테스트 포함).
- 숫자 대조: `cd arosaegim/tools && python -I -B -X utf8 spec_drift_check.py --repo .. --rev HEAD --facts facts.json` — 9개 사실 전부 OK.

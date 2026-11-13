# 명세 추적표 — 수용 기준 ↔ 시험 이름

기준 커밋: ba96030. 수용 기준 번호는 ARO-F<기능>-AC<두 자리>입니다. "확인 방법"의 시험 이름은 저장소의 앱 단위 시험(`@Test`) 또는 서버 시험(`def test_`) 이름이며,
"실존" 열은 위 커밋에서 그 이름이 실제로 검색되는지를 자동으로 점검한 결과입니다(없음이면 이름을 고치거나 시험을 추가해야 합니다). emulator·code·none 유형의 연결은 시험 이름이 아니므로 점검 대상에서 제외합니다(-).

## F1 도착 자동 스탬프 (14개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F1-AC01 | 사건 | 목적지 반경 120m 안으로 들어오면 앱은 해당 장소의 스탬프 1건을 기록합니다. | emulator: 단위 테스트 StampEngineTest.`1_반경 안에서 동일 placeId 연속 도착 2회는 1회만 스탬프된다`, 단위 테스트 TripSessionTest.`1_첫 목표 도착시 스탬프_방문기록_음성이 각 1회씩 처리된다`, 개발 기록 F48(근접 알림 ENTER 수신 실측 — 반경 단위 미터 확정), 개발 기록 F49(스탬프 1/3·다음 목표 진행 실측) | - - - - | [확인] |
| ARO-F1-AC02 | 원치 않는 상황 | 근접 알림 등록이 실패하거나 오류가 나면 앱은 수신 좌표로 거리를 직접 계산해, 거리가 반경 이내일 때만 도착으로 판정합니다. | code: 소스 app/src/main/java/app/arosaegim/pleos/FusedArrivalDetector.kt(haversineActive 폴백 전환·폴백 모드에서만 진입/이탈 판정), 단위 테스트 StampEngineTest.`호출자 경계 판정_거리가 반경 이내여야만 엔진에 전달된다`, 단위 테스트 HaversineTest.`국립경주박물관과 대릉원 사이는 1_4km에서 1_9km 사이다`, 소스 docs/ARCHITECTURE.md 4절(2차 Haversine 폴백) | - - - - | (설계) |
| ARO-F1-AC03 | 항상 | 앱은 위치 정보를 도착 판정과 화면 거리 표시에만 쓰고, 방문 기록에는 장소 ID와 시각만 저장합니다. | code: 소스 docs/ARCHITECTURE.md 4절(위치는 저장하지 않는다 — 메모리에만 존재), 소스 app/src/main/java/app/arosaegim/data/VisitStore.kt(좌표 없음 — 장소 id + 시각만), 개발 기록 F78(세션 없는 앱의 방문이 서버 visits 0건 — 전송 보류 대조) | - - - | (설계) |
| ARO-F1-AC04 | 사건 | 스탬프를 이미 찍은 장소에 다시 들어와도(재진입) 앱은 중복 스탬프를 기록하지 않습니다. | unit: 단위 테스트 StampEngineTest.`2_재진입 중복 방지_A획득후_B획득하고_다시_A에 도착하면 null이다`, 단위 테스트 TripSessionTest.`2_같은 장소 재도착은 무시되어 상태가 변하지 않는다` | O O | [코드 확인] |
| ARO-F1-AC05 | 사건 | 코스 순서상 지금 목표가 아닌 장소에 도착해도 앱은 스탬프를 기록하지 않습니다. | unit: 단위 테스트 StampEngineTest.`3_코스 순서 규칙_현재 목표가 아닌 장소 도착은 null이다`, 단위 테스트 TripSessionTest.`3_순서 위반 도착은 무시된다` | O O | [코드 확인] |
| ARO-F1-AC06 | 상태 | 코스를 완주한 뒤에는 앱이 추가 도착 신호를 무시하고 도착 감시를 중단합니다. | unit: 단위 테스트 StampEngineTest.`4_완주 후 currentTargetPlaceId가 null이면 도착은 항상 null이다`, 단위 테스트 TripSessionTest.`4_모든 장소 도착시 완주_감시가 중단된다` | O O | [코드 확인] |
| ARO-F1-AC07 | 사건 | 이전 목표를 통과하는 이동 경로 위에서 다음 목표 반경에 들어가면 앱은 수동 조작 없이 도착을 판정해 다음 단계로 진행합니다. | emulator: 개발 기록 F77(연속 도착 1→2 독립 관찰), 개발 기록 F74(대릉원 도착 2/3 확인) | - - | [확인] |
| ARO-F1-AC08 | 선택 | 차가 기어 D(주행) 상태인 경우에도 앱은 반경 진입을 감지해 도착을 기록합니다. | emulator: 개발 기록 F68(기어 D·40km/h 주행 상태의 도착 기록 실측) | - | [확인] |
| ARO-F1-AC09 | 사건 | 도착 스탬프가 등록되면 앱은 다음 목적지로 내비 경로 요청을 접수합니다. | emulator: 개발 기록 F68(도착 후 다음 목표 경로 요청 실측), 단위 테스트 TripSessionTest.`7_routeService_주입시 시작 목표와 바뀐 목표마다 startRoute를 호출한다` | - - | [확인] |
| ARO-F1-AC10 | 원치 않는 상황 | 내비 경로 요청이 실패해도 앱은 주행 세션을 유지하고 화면에 안내 한 줄만 남깁니다. | unit: 단위 테스트 TripSessionTest.`8_routeService_호출 실패시 주행은 유지되고 한 줄 안내만 남는다` | O | [코드 확인] |
| ARO-F1-AC11 | 사건 | 앱을 다시 시작하면 앱은 저장된 방문 기록으로 스탬프 진행을 복원합니다. | unit: 단위 테스트 TripSessionTest.`14_재시작 시 visits_json 기록으로 스탬프 진행을 복원한다` | O | [코드 확인] |
| ARO-F1-AC12 | 원치 않는 상황 | 네트워크가 없어 서버 전송이 실패해도 앱은 방문 기록을 차 안 대기열에 먼저 저장하고, 다음 전송 시도까지 보관합니다. | unit: 단위 테스트 TripSessionTest.`6_visitQueue_주입시 도착하면 큐에 1건 적재되고 전송을 시도한다`, 단위 테스트 VisitQueueTest.`3_flush 실패시 항목이 큐에 유지된다` | O O | [코드 확인] |
| ARO-F1-AC13 | 선택 | 코스를 처음부터 다시 시작하는 경우 앱은 스탬프 번호를 1부터 다시 매기고 이전 방문 기록을 지웁니다. | unit: 단위 테스트 StampEngineTest.`5_reset 후 다시 획득 가능하고 stampNo가 1부터 다시 시작된다`, 단위 테스트 TripSessionTest.`5_resetCourse_후 진행과 기록이 처음으로 돌아간다` | O O | [코드 확인] |
| ARO-F1-AC14 | 사건 | 앱은 코스 주행 1회마다 식별자(course_run_id)를 새로 발급해 도착마다 서버 전송에 실어 보냅니다. | unit: 단위 테스트 VisitPayloadContractTest.`필드 이름은 서버 계약대로 스네이크 케이스다`, 소스 app/src/main/java/app/arosaegim/session/TripSession.kt(start 때 UUID 1회 발급·도착마다 course_run_id 전송), 개발 기록 E1(2026-11-12 섹션 — 전송 계약 수정·계약 테스트 신설) | O - - | [코드 확인] |

## F2 주행/정차 전환 (14개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F2-AC01 | 항상 | 앱은 주행 규제 신호(주행 최적화 요구)를 1차 신호로 삼아 주행 상태를 판정합니다. | code: 단위 테스트 DrivingStateMergerTest.`보조가 없으면 1차만으로 판정한다`, 소스 app/src/main/java/app/arosaegim/pleos/UxRestrictionsDrivingState.kt(CarUxRestrictionsManager.registerListener), 개발 기록 F29(주행 규제 관리자 획득·등록 성공 실측), 소스 docs/ARCHITECTURE.md 3절 | - - - - | [코드 확인] |
| ARO-F2-AC02 | 사건 | 1차 신호와 보조(기어) 신호가 서로 어긋나면 앱은 더 제한적인 쪽인 주행으로 판정합니다. | unit: 단위 테스트 DrivingStateMergerTest.`1차 정차에 보조 주행이면 주행으로 판정한다 - 어긋나면 더 제한적 우선`, 단위 테스트 DrivingStateMergerTest.`1차 주행에 보조 정차여도 주행을 유지한다 - 더 제한적 우선`, 소스 docs/ADR.md ADR-005 | O O - | [코드 확인] |
| ARO-F2-AC03 | 상태 | 기어 P가 5초 이상 지속되면 1차 신호가 주행을 보고해도 앱은 정차로 판정을 해제합니다. | unit: 단위 테스트 DrivingStateMergerTest.`1차 주행에 보조 정차가 보류 시간 이상이면 PARKED로 해제한다 - Q26`, 단위 테스트 DrivingStateMergerTest.`1차 주행에 보조 정차가 보류 시간 미만이면 주행을 유지한다 - Q26 보수적`, 개발 기록 F81(Q26 해소 — PARK_RELEASE_HOLD_MS 5초, 2초 폴링 3회) | O O - | [코드 확인] |
| ARO-F2-AC04 | 선택 | 기어 신호를 읽지 못하는 경우 앱은 1차 신호만으로 주행 상태를 판정합니다. | unit: 단위 테스트 DrivingStateMergerTest.`보조를 못 읽었으면 판정에 영향을 주지 않는다`, 단위 테스트 CombinedDrivingStateTest.`못 읽은 기어 결과는 판정을 흔들지 않는다` | O O | [코드 확인] |
| ARO-F2-AC05 | 항상 | 앱은 주행 화면에만 주행 최적화를 선언하고 정차 화면에는 선언하지 않습니다. | code: 소스 android/app/src/main/AndroidManifest.xml(DrivingActivity distractionOptimized=true 선언, ParkedActivity 미선언), 소스 docs/ARCHITECTURE.md 3절(DO 선언의 역할) | - - | [코드 확인] |
| ARO-F2-AC06 | 원치 않는 상황 | 주행 최적화를 선언하지 않은 화면은 주행이 시작되면 시스템이 가립니다. 주행 중 퀴즈·입력 금지는 이 방식으로 강제됩니다. | emulator: 개발 기록 F68(기어 D·40km/h 실측 — 화면 가려짐 확인), 소스 docs/ARCHITECTURE.md 3절 | - - | [확인] |
| ARO-F2-AC07 | 원치 않는 상황 | 일부 실측에서는 주행 최적화를 선언한 화면도 주행 중 시스템이 함께 가렸습니다. | emulator: 개발 기록 F68(주행 화면도 "주행 중에는 사용할 수 없습니다"로 가려진 사례), 소스 docs/DEV_LOG.md U7 표(잔존 사례 — Q26) | - - | [일부 확인] |
| ARO-F2-AC08 | 상태 | 정차 화면이 시스템에 가려지는 동안에도 앱은 퀴즈 진행 상태(문항 번호·답안)를 보존합니다. | code: 소스 app/src/main/java/app/arosaegim/ui/parked/QuizState.kt(QuizStateHolder — 프로세스 수명 싱글톤), 소스 docs/ARCHITECTURE.md 3절(퀴즈 상태 보존) | - - | (설계) |
| ARO-F2-AC09 | 사건 | 가족 세션(가족 코드 또는 둘러보기)이 있는 상태에서 차가 정차하면 앱은 정차 화면으로 자동 전환합니다. | emulator: 개발 기록 F72(기어 D→P 자동 복귀 2회 실측 — 세션 게이트 추가 이전 빌드), 단위 테스트 ModeRouterResolveTest.`정차 상태에서 세션이 있으면 정차 화면으로 라우팅한다` | - - | [일부 확인] |
| ARO-F2-AC10 | 원치 않는 상황 | 가족 세션이 없는 상태에서 차가 정차해도 앱은 자동 전환을 보류하고 시작 화면을 그대로 유지합니다. | unit: 단위 테스트 ModeRouterResolveTest.`정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)`, 개발 기록 F80(게이트 추가 후 콜드 스타트 실측 — ParkedActivity 미기동 확인), 개발 기록 Q28(릴리스 빌드에서 시작 화면 표시 자체의 재확인은 남음) | O - - | [일부 확인] |
| ARO-F2-AC11 | 사건 | 주행 상태가 되면 앱은 가족 세션 여부와 관계없이 항상 주행 화면으로 전환합니다. | unit: 단위 테스트 ModeRouterResolveTest.`주행 상태는 세션이 없어도 주행 화면으로 라우팅한다`, 단위 테스트 ModeRouterResolveTest.`주행 상태는 세션이 있어도 주행 화면으로 라우팅한다` | O O | [코드 확인] |
| ARO-F2-AC12 | 상태 | 같은 상태 신호가 반복해서 들어오는 동안에는 앱은 화면을 다시 전환하지 않습니다. | unit: 단위 테스트 ModeStateMachineTest.`연속 동일 신호 3회는 mode 값 변화가 없다` | O | [코드 확인] |
| ARO-F2-AC13 | 사건 | 주행에서 정차로 복귀하면 앱은 정차 화면으로 돌아가고 이전 화면 모드를 복원합니다. | unit: 단위 테스트 ModeStateMachineTest.`PARKED에서 DRIVING으로 복귀 후 PARKED로 복원된다`, 개발 기록 F72(D→P 자동 복귀 실측), 소스 docs/DEV_LOG.md U9 표(3회 전환·복귀·상태 유지 PASS — 세션 게이트 추가 이전 빌드) | O - - | [일부 확인] |
| ARO-F2-AC14 | 원치 않는 상황 | 주행 상태 신호 등록이 실패하면 앱은 정차 상태를 유지하고 수동 전환(데모 모드)으로 대체합니다. | code: 소스 docs/ARCHITECTURE.md 3절(실패 시의 동작 — ManualDrivingState 폴백·파생 상태 금지), 소스 app/src/main/java/app/arosaegim/fallback/ManualDrivingState.kt, 소스 docs/ADR.md ADR-005 | - - - | (설계) |

## F3 박물관 퀴즈 (11개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F3-AC01 | 사건 | 고른 보기가 정답이면 앱은 "정답이에요!" 한 줄과 해설을 보여줍니다. | code: 소스 android/app/src/main/res/values/strings.xml(parked_quiz_correct), 소스 app/src/main/java/app/arosaegim/ui/parked/ParkedScreen.kt(QuizStep) | - - | (설계) |
| ARO-F3-AC02 | 사건 | 고른 보기가 오답이면 앱은 올바른 보기 문장을 "정답: …" 형태로 함께 보여줍니다. | code: 소스 android/app/src/main/res/values/strings.xml(parked_quiz_wrong), 소스 app/src/main/java/app/arosaegim/ui/parked/ParkedScreen.kt(QuizStep) | - - | (설계) |
| ARO-F3-AC03 | 상태 | 차가 주행 중인 동안 앱은 퀴즈가 있는 정차 화면으로 전환하지 않습니다. | unit: 단위 테스트 ModeRouterResolveTest.`주행 상태는 세션이 있어도 주행 화면으로 라우팅한다`, 단위 테스트 ModeRouterResolveTest.`주행 상태는 세션이 없어도 주행 화면으로 라우팅한다` | O O | [코드 확인] |
| ARO-F3-AC04 | 사건 | 차가 정차하고 가족 세션이 있으면 앱은 퀴즈 단계를 포함한 정차 화면으로 전환합니다. | unit: 단위 테스트 ModeRouterResolveTest.`정차 상태에서 세션이 있으면 정차 화면으로 라우팅한다` | O | [코드 확인] |
| ARO-F3-AC05 | 원치 않는 상황 | 차가 정차했지만 가족 세션이 없으면 앱은 정차 화면 전환을 보류하고 시작 화면을 유지합니다. | unit: 단위 테스트 ModeRouterResolveTest.`정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)`, 개발 기록 Q28 | O - | [코드 확인] |
| ARO-F3-AC06 | 항상 | 앱은 경주 코스 3곳(국립경주박물관·대릉원·첨성대)마다 퀴즈 2문항씩, 코스 전체 6문항을 내장해 제공합니다. | unit: 단위 테스트 SeedRepositoryTest.realSeedFile_parsesToExpectedContent(장소별 퀴즈 2개·전체 초안 키 6개 단언), 소스 android/app/src/main/assets/seed/seed.json | O - | [코드 확인] |
| ARO-F3-AC07 | 상태 | 문항이 검수 전(reviewed=false)이면 시드 파서는 그 문항을 초안 목록에 넣고, 지금 6문항 전부가 이 상태입니다. | unit: 단위 테스트 SeedRepositoryTest.realSeedFile_parsesToExpectedContent(전 문항 reviewed=false·draftQuizKeys 6개 단언) | O | [코드 확인] |
| ARO-F3-AC08 | 선택 | 검수 전 문항에는 화면에 "초안" 꼬리표를 붙여 보여줍니다. | code: 소스 android/app/src/main/res/values/strings.xml(parked_quiz_draft_tag="초안"), 소스 app/src/main/java/app/arosaegim/data/SeedRepository.kt(draftQuizKeys) | - - | (설계) |
| ARO-F3-AC09 | 원치 않는 상황 | 도착한 장소에 준비된 퀴즈가 없으면 앱은 안내 문구 한 줄만 보여주고 화면을 멈추지 않습니다. | code: 소스 android/app/src/main/res/values/strings.xml(parked_quiz_empty="이 장소에는 준비된 퀴즈가 없어요"), 소스 app/src/main/java/app/arosaegim/ui/parked/ParkedScreen.kt(QuizStep) | - - | (설계) |
| ARO-F3-AC10 | 사건 | 그 장소의 마지막 문항에 답하면 앱은 자동으로 "그때와 지금" 단계로 넘어갑니다. | code: 소스 app/src/main/java/app/arosaegim/ui/parked/ParkedScreen.kt(QuizStep — 다음 단계 전환) | - | (설계) |
| ARO-F3-AC11 | 상태 | 퀴즈를 풀고 있는 동안 주행으로 화면이 가려졌다가 정차로 돌아오면 앱은 같은 장소의 같은 문항과 고른 보기부터 이어서 보여줍니다. | code: 소스 app/src/main/java/app/arosaegim/ui/parked/QuizState.kt(QuizStateHolder — 같은 장소면 진행 보존), 소스 docs/ARCHITECTURE.md 9절 Q13 | - - | (설계) |

## F4 그때와 지금 (13개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F4-AC01 | 사건 | 지난 방문 연도 기록이 있으면 앱은 올해 연도에서 그 연도를 뺀 햇수를 계산해 "○년 전"으로 보여줍니다. | unit: 단위 테스트 ThenNowResolverTest.`과거가 있으면 yearsAgo를 계산해 반환한다`, 단위 테스트 ThenNowResolverTest.`여러 과거 후보 중 가장 오래된 것을 선택한다`, 서버 시험 test_memory_becomes_past_with_server_years_ago(서버가 years_ago를 계산) | O O O | [코드 확인] |
| ARO-F4-AC02 | 원치 않는 상황 | 지난 연도가 올해보다 미래이거나 0 이하로 기록돼 있으면 앱은 그 값을 쓰지 않고 첫 방문으로 처리합니다. | unit: 단위 테스트 ThenNowResolverTest.`nowYear보다 과거 연도가 크면 미래 과거로 보고 null을 반환한다`, 단위 테스트 ThenNowResolverTest.`pastYear가 0 이하면 null을 반환한다` | O O | [코드 확인] |
| ARO-F4-AC03 | 사건 | 오늘 좌석 점유로 읽은 인원과 그때 기록된 인원이 둘 다 있으면 앱은 "오늘은 N명이 함께 왔어요. 그때는 M명이었어요."를 보여줍니다. | code: 소스 android/app/src/main/res/values/strings.xml(parked_party_line), 소스 app/src/main/java/app/arosaegim/ui/parked/ThenNowExtras.kt(PartySizeLine), 개발 기록 F73(주입 3명 도착 카드 문장 표시 실측 — 값 불일치는 Q27) | - - - | (설계) |
| ARO-F4-AC04 | 원치 않는 상황 | 오늘 인원 또는 그때 인원 중 하나라도 읽지 못하면 앱은 인원 비교 문장을 통째로 숨기고 값을 지어내지 않습니다. | code: 소스 app/src/main/java/app/arosaegim/ui/parked/ThenNowExtras.kt(PartySizeLine — 값 하나라도 없으면 숨김), 소스 docs/ARCHITECTURE.md 6절 U11 표(partySize=null → 문장 숨김 설계 동작) | - - | (설계) |
| ARO-F4-AC05 | 선택 | 가족이 차 안에서 인원 비교 문장을 접어 두면 서버는 그 장소의 조회 응답에 접힘 표시를 붙입니다. | server: 서버 시험 test_folded_place_add_remove, 서버 시험 test_then_now_folded_flag | O O | [코드 확인] |
| ARO-F4-AC06 | 사건 | 가족이 가족 모바일 웹에서 접어 둔 장소를 되돌리면 서버는 그 장소의 접힘 표시를 다시 내려줍니다. | server: 서버 시험 test_folded_place_add_remove, 서버 시험 test_then_now_folded_flag | O O | [코드 확인] |
| ARO-F4-AC07 | 상태 | 가족이 문장 접기를 전체로 켜 둔 동안 서버는 모든 장소의 then-now 응답에 접힘 표시를 붙입니다. | server: 서버 시험 test_settings_fold_person_comparison_all, 단위 테스트 PartyPrefsTest.`모든 장소 끄기면 어느 장소에서나 숨긴다` | O O | [코드 확인] |
| ARO-F4-AC08 | 항상 | 앱은 오늘 함께 달린 거리를 읽으면 숫자 한 줄로 보여주고, 읽지 못하거나 0 이하이면 그 줄을 보여주지 않습니다. | server: 서버 시험 test_metrics_total_trip_km_zero_without_trip_km, 단위 테스트 FamilyApiParsersTest.`km 이 0 이하 또는 비정상이면 null - 누적 줄 숨김`, 단위 테스트 FamilyApiParsersTest.`family_total_trip_km 이 없거나 이상하면 null - 누적 줄 숨김` | O O O | [코드 확인] |
| ARO-F4-AC09 | 사건 | 이 장소에 지난 방문이 전혀 없다고 서버가 확인하면(첫 방문) 앱은 "오늘이 우리 가족의 첫 기록입니다" 문구로 바꿔 보여줍니다. | server: 서버 시험 test_then_now_visit_ordinal_counts_per_place, 서버 시험 test_then_now_without_visits_is_honest, 단위 테스트 FamilyApiParsersTest.`then-now 의 visit_ordinal 이 없으면 null - 기능 숨김` | O O O | [코드 확인] |
| ARO-F4-AC10 | 항상 | 유물 정보가 공식 자료로 검수되지 않았으면(지금 3곳 전부) 시드는 검수 필요 메모를 함께 담고, 화면은 유물·가족 연도 줄 아래 그 메모를 보여줍니다. | unit: 단위 테스트 SeedRepositoryTest.realSeedFile_parsesToExpectedContent(3곳 heritage 전부 reviewed=false·메모 비어 있지 않음 단언), 소스 app/src/main/java/app/arosaegim/ui/parked/ThenNowExtras.kt(HeritageTimelineLine) | O - | [코드 확인] |
| ARO-F4-AC11 | 선택 | 가족이 모바일 웹에서 사진에 이름표를 붙이면 서버는 이름표를 저장하고, 한 기억당 최대 10명까지만 받습니다. | server: 서버 시험 test_markers_saved_and_shown, 서버 시험 test_markers_limit_is_10_on_update_and_create | O O | [코드 확인] |
| ARO-F4-AC12 | 사건 | 앱은 가족 누적 거리를 소수점 정리와 천 단위 콤마가 붙은 문자열로 바꿔 보여줍니다. | unit: 단위 테스트 FamilyApiParsersTest.`km 표시 문자열은 소수점 정리와 천 단위 콤마를 적용한다` | O | [코드 확인] |
| ARO-F4-AC13 | 상태 | 창 너비가 600dp 이상인 동안 앱은 "그때"와 "지금" 카드를 좌우로 나란히 보여주고, 그보다 좁은 동안은 위아래로 쌓아 보여줍니다. | code: 소스 app/src/main/java/app/arosaegim/ui/parked/ParkedScreen.kt(isWide = maxWidth >= 600.dp) | - | (설계) |

## F5 음성 해설·음성 편지 (13개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F5-AC01 | 항상 | 앱은 안내 음성을 사전 제작 오디오 → 차량 음성 합성(Pleos) → 안드로이드 기본 음성 순서의 3단 체인으로 시도합니다. | code: 소스 app/src/main/java/app/arosaegim/AppContainer.kt(selectNarrator — 1단 AssetAudioNarrator 기본 경로·2단 PleosTtsNarrator·3단 AndroidTtsNarrator 최후 폴백), 소스 docs/TRACEABILITY.md 1절 A1 | - - | (설계) |
| ARO-F5-AC02 | 원치 않는 상황 | 차량 음성 합성의 초기화 확인이 아직 안 된 상태에서 문장을 말해야 하면 앱은 기다리지 않고 안드로이드 기본 음성으로 말합니다. | unit: 단위 테스트 SwitchableNarratorTest.`onReady 전에는 대체(Android TTS 자리)로 말한다 - 블로킹 없이 즉시 기동` | O | [코드 확인] |
| ARO-F5-AC03 | 사건 | 차량 음성 합성의 초기화 확인이 늦게 성공하면 앱은 그 다음부터 안드로이드 기본 음성 대신 차량 음성 합성으로 바꿔 말하고, 이 전환은 되돌아가지 않습니다. | unit: 단위 테스트 SwitchableNarratorTest.`onReady 지연 확인 후에는 Pleos로 말한다`, 단위 테스트 SwitchableNarratorTest.`전환은 일방향 - ready가 false로 되돌아가도 Pleos를 유지한다` | O O | [코드 확인] |
| ARO-F5-AC04 | 상태 | 초기화 확인이 끝내 오지 않으면 앱은 안드로이드 기본 음성(대체)을 계속 씁니다. | unit: 단위 테스트 SwitchableNarratorTest.`onReady가 끝내 오지 않으면 대체로 머문다` | O | [코드 확인] |
| ARO-F5-AC05 | 항상 | "지금 안내 음성을 쓸 수 있는가"라는 질문에 앱은 현재 활성 구현의 실제 준비 상태를 그대로 답합니다. | unit: 단위 테스트 SwitchableNarratorTest.`isAvailable은 활성 위임자의 실제 준비 상태를 답한다 - 거짓 표기 금지` | O | [코드 확인] |
| ARO-F5-AC06 | 원치 않는 상황 | 이 에뮬레이터에서는 안드로이드 기본 음성 초기화와 차량 음성 합성 초기화 확인이 모두 실패해, 지금은 두 합성 경로 모두 소리가 나지 않습니다. 원인 2건은 확정됐습니다. | emulator: 개발 기록 U6(U6 FAIL — 원인 2건 확정), 개발 기록 F54(에뮬레이터 TTS 환경 한계 가능성) | - - | [실패] |
| ARO-F5-AC07 | 상태 | 음성 편지는 시드의 예시 1건(식별자 letter-demo-1, 국립경주박물관 묶음)만 제공됩니다. | unit: 단위 테스트 SeedRepositoryTest.realSeedFile_parsesToExpectedContent(letter-demo-1·gj-museum·simulated=true 단언), 서버 시험 test_voice_letters_returns_seed_demo | O O | [코드 확인] |
| ARO-F5-AC08 | 사건 | 가족이 음성 편지 재생 버튼을 누르면 앱은 그 순간부터 사전 제작 음성 편지 재생을 시작합니다(자동재생 없음). | code: 소스 app/src/main/java/app/arosaegim/ui/parked/ThenNowExtras.kt(VoiceLetterCard — onClick 재생), 소스 docs/ARCHITECTURE.md 7절(음성 편지 T9-2 — 정차했을 때만 재생 버튼) | - - | (설계) |
| ARO-F5-AC09 | 원치 않는 상황 | 음성 편지 재생이 실패하면 앱은 재생 대신 "예시(음성 준비 중)" 글자 안내를 보여줍니다. | code: 소스 android/app/src/main/res/values/strings.xml(parked_voice_letter_unavailable), 소스 app/src/main/java/app/arosaegim/ui/parked/ThenNowExtras.kt(VoiceLetterCard) | - - | (설계) |
| ARO-F5-AC10 | 원치 않는 상황 | 정차 화면이 가려지면 앱은 재생 중인 음성 편지를 멈춥니다. | code: 소스 app/src/main/java/app/arosaegim/ui/parked/ParkedActivity.kt(onPause에서 voiceLetterPlayer.stop 호출), 소스 docs/ARCHITECTURE.md 7절(주행을 시작하면 재생이 멈춘다) | - - | (설계) |
| ARO-F5-AC11 | 원치 않는 상황 | 다른 소리가 오디오 포커스를 완전히 또는 일시적으로 가져가면 앱은 재생 중인 안내 음성·음성 편지를 멈춥니다. | code: 소스 app/src/main/java/app/arosaegim/pleos/PleosVoiceLetterPlayer.kt(포커스 상실·일시 상실 처리), 소스 app/src/main/java/app/arosaegim/fallback/AssetAudioNarrator.kt(포커스 처리), 소스 app/src/main/java/app/arosaegim/pleos/PleosTtsNarrator.kt(포커스 처리) | - - - | (설계) |
| ARO-F5-AC12 | 선택 | 다른 소리가 낮추기만 요청하는 경우(소리를 줄여도 되는 상황) 앱은 재생을 멈추지 않고 계속합니다. | code: 소스 app/src/main/java/app/arosaegim/pleos/PleosVoiceLetterPlayer.kt(포커스 상실·일시 상실만 처리 — 낮추기 요청은 미처리) | - | (설계) |
| ARO-F5-AC13 | 상태 | 차가 정차한 상태인 동안에만 앱은 음성 편지 카드를 화면에 둡니다. 주행 화면에는 이 카드가 없습니다. | code: 소스 app/src/main/java/app/arosaegim/ui/parked/ThenNowExtras.kt(VoiceLetterCard — 정차 화면 전용), 소스 app/src/main/java/app/arosaegim/ui/driving/DrivingScreen.kt(음성 편지 카드 없음), 소스 docs/ARCHITECTURE.md 7절 | - - - | (설계) |

## F6 가족 코드·PIN·세션 (15개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F6-AC01 | 항상 | 앱은 가족 코드가 6~8자리 영숫자(대소문자 허용)인지 검사해 유효성을 판정합니다. | unit: 단위 테스트 FamilySessionStoreTest.`여섯 자리 영숫자는 유효하다`, 단위 테스트 FamilySessionStoreTest.`일곱 자리 영숫자도 유효하다 — 서버가 8자리 코드를 발급한다(F58)`, 단위 테스트 FamilySessionStoreTest.`아홉 자리는 무효하다`, 단위 테스트 FamilySessionStoreTest.`다섯 자리는 무효하다`, 개발 기록 F58(서버 발급 코드가 8자리임을 실측) | O O O O - | [코드 확인] |
| ARO-F6-AC02 | 원치 않는 상황 | 가족 코드에 공백·특수문자·한글이 섞이면 앱은 무효로 판정하고 접속을 진행하지 않습니다. | unit: 단위 테스트 FamilySessionStoreTest.`특수문자가 섞이면 무효하다`, 단위 테스트 FamilySessionStoreTest.`한글이 섞이면 무효하다`, 단위 테스트 FamilySessionStoreTest.`공백이 섞이면 무효하다` | O O O | [코드 확인] |
| ARO-F6-AC03 | 사건 | 가족 코드 입력칸에 글자를 넣으면 앱은 영숫자가 아닌 문자를 걸러내고 입력을 8자까지만 받습니다. | code: 소스 app/src/main/java/app/arosaegim/ui/session/StartScreen.kt(CODE_MAX_LENGTH=8·영숫자 필터) | - | (설계) |
| ARO-F6-AC04 | 사건 | 가족 코드가 검증을 통과하고 PIN 6자리가 모두 입력되면 앱은 [이어 보기] 버튼을 활성화합니다. | code: 소스 app/src/main/java/app/arosaegim/ui/session/StartScreen.kt(PIN_MAX_LENGTH=6·버튼 enabled 조건식) | - | (설계) |
| ARO-F6-AC05 | 항상 | 앱은 PIN을 세션 메모리에만 두고, 기기 저장소·파일·기록에는 남기지 않습니다. | unit: 단위 테스트 FamilySessionStoreTest.`pin을 설정하면 pinOrNull로 같은 값이 나온다`, 단위 테스트 FamilySessionStoreTest.`pin을 해제하면 null이 되고 코드는 유지된다`, 단위 테스트 FamilySessionStoreTest.`공백뿐인 pin은 없는 것과 같다`, 개발 기록 F59, 소스 docs/ADR.md ADR-003 | O O O - - | [코드 확인] |
| ARO-F6-AC06 | 항상 | 앱은 가족 코드만 기기 저장소(앱 전용 설정)에 남겨 다시 켜도 접속 상태가 이어지게 하고, PIN은 저장하지 않습니다. | code: 소스 app/src/main/java/app/arosaegim/data/FamilySessionStore.kt(가족 코드만 SharedPreferences 저장 — PIN 저장 경로 없음), 소스 docs/ARCHITECTURE.md 2-3절 | - - | (설계) |
| ARO-F6-AC07 | 상태 | 가족 코드 접속도 둘러보기도 선택하지 않은 동안 앱은 정차 화면으로 자동 전환하지 않고 시작 화면(가족 코드 입력)을 유지합니다. | unit: 단위 테스트 ModeRouterResolveTest.`정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)`, 단위 테스트 ModeRouterResolveTest.`정차 상태에서 세션이 있으면 정차 화면으로 라우팅한다`, 개발 기록 F80(콜드 스타트 실측 — 정차 화면 미기동 확인. 릴리스 빌드의 시작 화면 표시 재확인은 Q28에 남음) | O O - | [일부 확인] |
| ARO-F6-AC08 | 상태 | 차가 주행 중인 동안 앱은 가족 세션 여부와 관계없이 주행 화면을 유지합니다. | unit: 단위 테스트 ModeRouterResolveTest.`주행 상태는 세션이 없어도 주행 화면으로 라우팅한다`, 단위 테스트 ModeRouterResolveTest.`주행 상태는 세션이 있어도 주행 화면으로 라우팅한다` | O O | [코드 확인] |
| ARO-F6-AC09 | 선택 | 가족 없이 둘러보기를 고르는 경우 앱은 기록을 이 차량에만 저장하고 가족 서버에 연결하지 않습니다. | code: 소스 app/src/main/java/app/arosaegim/data/FamilySessionStore.kt(guestMode — 기록은 이 차에만), 소스 docs/ARCHITECTURE.md 2-2절·10-5절 | - - | (설계) |
| ARO-F6-AC10 | 사건 | "이 차에서 로그아웃"을 실행하면 앱은 가족 코드·세션 PIN·방문 기록 파일·전송 대기 큐 파일을 모두 지웁니다. | unit: 단위 테스트 SignOutCleanupTest.`로그아웃 대상 파일 두 개를 실제로 지운다`, 단위 테스트 SignOutCleanupTest.`대상 파일 목록은 방문 기록과 큐 두 개다`, 단위 테스트 FamilySessionStoreTest.`clear는 코드와 pin을 모두 지운다`, 개발 기록 F65(A8 로그아웃 삭제) | O O O - | [코드 확인] |
| ARO-F6-AC11 | 원치 않는 상황 | 로그아웃 중 파일 하나가 삭제되지 않으면 앱은 나머지 파일 삭제를 계속하고 결과를 파일마다 한 줄씩 남깁니다. | unit: 단위 테스트 SignOutCleanupTest.`한 파일 삭제 실패가 나머지 삭제를 막지 않는다`, 단위 테스트 SignOutCleanupTest.`대상 파일이 없으면 오류가 아니라 없음으로 기록한다` | O O | [코드 확인] |
| ARO-F6-AC12 | 원치 않는 상황 | 서버가 가족 코드·PIN 불일치로 요청을 거부하면(401 또는 403) 앱은 가족 구성원 이름표 카드를 숨기지 않고 재입력 안내 한 줄을 보여줍니다. | code: 소스 app/src/main/java/app/arosaegim/data/ApiClient.kt(401·403은 AuthException — PIN 재입력 신호 계약) | - | (계획) |
| ARO-F6-AC13 | 사건 | 가족 코드로 접속하면 앱은 코드를 대문자로 정규화해 저장합니다. | code: 소스 app/src/main/java/app/arosaegim/data/FamilySessionStore.kt(저장 전 대문자 정규화) | - | (설계) |
| ARO-F6-AC14 | 원치 않는 상황 | 서버가 PIN 5회 연속 오류로 잠금(429)을 응답해도 앱은 이를 재입력 안내로 구분하지 않고 일반 전송 실패로 처리합니다. | code: 소스 app/src/main/java/app/arosaegim/data/ApiClient.kt(401·403만 AuthException으로 구분, 404·429 등은 일반 실패로 흡수) | - | (설계) |
| ARO-F6-AC15 | 상태 | PIN은 시작 화면 한 곳에서만 숫자 6자리 가려진 입력란으로 받고, 화면 저장 번들에도 남기지 않습니다. | code: 소스 app/src/main/java/app/arosaegim/ui/session/StartScreen.kt(PIN_MAX_LENGTH=6·숫자 필터·가려진 입력란·remember만 사용) | - | (설계) |

## F7 가족 기록 서버·모바일 웹·물려주기 (16개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F7-AC01 | 사건 | 가족 코드·PIN 발급을 요청하면 서버는 8자리 가족 코드와 숫자 6자리 PIN을 새로 만들어 응답에 담아 돌려줍니다. | code: 소스 backend/app/security.py(generate_family_code — 8자리 문자집합·generate_pin — 6자리 0채움), 소스 backend/app/config.py(FAMILY_CODE_LENGTH=8), 개발 기록 F58(서버 발급 코드 8자리 실측) | - - - | (설계) |
| ARO-F7-AC02 | 원치 않는 상황 | 같은 기기 식별값으로 하루에 두 번째 가족 코드를 발급받으려 하면 서버는 429(device_limit)로 거부합니다. | server: 서버 시험 test_family_code_device_token_daily_limit | O | [코드 확인] |
| ARO-F7-AC03 | 원치 않는 상황 | PIN을 5회 연속 틀리면 서버는 10분간 그 가족의 요청을 429(family_locked)로 막고, 그 안에는 올바른 PIN을 보내도 막습니다. | server: 서버 시험 test_pin_locks_after_five_failures, 서버 시험 test_pin_lock_records_locked_until_in_future, 소스 backend/app/config.py(PIN_LOCK_ATTEMPTS=5·PIN_LOCK_MINUTES=10) | O O - | [코드 확인] |
| ARO-F7-AC04 | 상태 | 가족이 PIN 잠금 상태인 동안 서버는 코드 재발급 요청만 예외로 받아들이고, 그 외 요청은 429로 거부합니다. | code: 소스 backend/app/security.py(rotate-code 전용 — 잠금 중에도 PIN 검증 허용 경로), 서버 시험 test_pin_locks_after_five_failures(잠금 중 일반 요청 거부) | - - | (설계) |
| ARO-F7-AC05 | 원치 않는 상황 | X-Family-Pin 헤더 없이 가족 데이터를 조회하면 서버는 401(pin_required)로 거부합니다. | server: 서버 시험 test_missing_pin_header_rejected | O | [코드 확인] |
| ARO-F7-AC06 | 사건 | 같은 분(초 단위는 무시) 안에 같은 장소·코스 방문을 다시 보내면 서버는 새 방문을 만들지 않고 기존 방문과 같은 스탬프 번호를 돌려줍니다. | server: 서버 시험 test_same_minute_is_idempotent, 서버 시험 test_different_minute_creates_new_visit | O O | [코드 확인] |
| ARO-F7-AC07 | 항상 | 서버는 저장하는 모든 사진에서 EXIF(위치·시각 등 메타데이터)를 제거하고 다시 저장합니다. | server: 서버 시험 test_exif_is_stripped, 소스 backend/app/services/photos.py(저장 전 EXIF 제거) | O - | [코드 확인] |
| ARO-F7-AC08 | 원치 않는 상황 | 가족에 보호자 동의를 아직 받지 못한 미성년 구성원이 있으면 서버는 그 가족의 사진 업로드를 403(guardian_consent_required)으로 거부합니다. | server: 서버 시험 test_minor_photo_requires_guardian_consent | O | [코드 확인] |
| ARO-F7-AC09 | 선택 | 미성년 구성원이 있는 경우에도 사진이 없는 기억 기록은 보호자 동의와 무관하게 허용됩니다. | server: 서버 시험 test_minor_photo_requires_guardian_consent | O | [코드 확인] |
| ARO-F7-AC10 | 사건 | 가족 데이터 내보내기를 요청하면 서버는 timeline.json·README.html·photos 폴더가 든 ZIP 파일 하나를 돌려줍니다. | server: 서버 시험 test_export_zip_is_selfcontained, 서버 시험 test_export_contains_b1_b5_fields | O O | [코드 확인] |
| ARO-F7-AC11 | 항상 | 서버는 가족 단위 지표 응답에 가족이 함께 달린 누적 거리를 포함하고, 같은 날 같은 코스 주행에서 나온 거리는 한 번만 더합니다. | server: 서버 시험 test_metrics_total_trip_km_dedup_same_course_run, 서버 시험 test_metrics_total_trip_km_respects_course_run_id | O O | [코드 확인] |
| ARO-F7-AC12 | 사건 | 차량 화면은 방문 전송에 함께 온 인원·거리·코스 주행 식별자를 같이 보내고 서버는 이를 방문 기록에 저장합니다. | server: 서버 시험 test_visit_accepts_party_size_and_trip_km, 단위 테스트 VisitPayloadContractTest.`응답 계약 - 실측 응답의 visit_id와 stamp_no 는 정수로 읽힌다` | O O | [코드 확인] |
| ARO-F7-AC13 | 사건 | 관리자(owner) 구성원이 다른 구성원을 이어받을 사람으로 지정하면 서버는 그 구성원의 역할을 바꾸고, 이전에 지정돼 있던 사람은 일반 구성원으로 되돌립니다. | server: 서버 시험 test_heir_and_handover_flow, 서버 시험 test_heir_reassign_to_another_member | O O | [코드 확인] |
| ARO-F7-AC14 | 원치 않는 상황 | 관리자 권한이 없는 PIN으로 이어받을 사람 지정을 요청하면 서버는 403(not_owner)으로 거부하고, PIN 헤더 자체가 없으면 401(pin_required)로 거부합니다. | server: 서버 시험 test_heir_owner_auth_failure_is_403 | O | [코드 확인] |
| ARO-F7-AC15 | 사건 | 차량 화면에서 코스를 완주한 직후(주행 중이 아니고, 이어받을 사람이 아직 없고, 지정할 구성원이 있을 때) 앱은 이어받을 사람을 묻는 질문을 보여줍니다. | unit: 단위 테스트 HeirQuestionTest.`완주 비주행 미지정 구성원있음이면 물어본다`, 단위 테스트 HeirQuestionTest.`주행 중에는 물어보지 않는다`, 단위 테스트 HeirQuestionTest.`이미 정해져 있으면 다시 물어보지 않는다 - 재문 금지 조건` | O O O | [코드 확인] |
| ARO-F7-AC16 | 원치 않는 상황 | PIN이 세션 메모리에서 사라진 뒤(앱을 다시 켠 경우 등) 코스를 완주하면 앱은 서버 호출을 시도하지 않고 "스마트폰의 가족 관리에서 정해 주세요" 안내로 전환합니다. | unit: 단위 테스트 HeirPinGuideTest.`가족에 연결됐고 PIN이 없으면 완주 직후 안내로 전환한다`, 단위 테스트 HeirPinGuideTest.`PIN이 있으면 안내가 아니라 정책 경로다` | O O | [코드 확인] |

## F8 로컬 맛집·숙소 포인트 캐시백(영수증 인증) (14개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F8-AC01 | 사건 | 영수증(사진·금액·결제 시각·제휴처)이 규칙을 통과하면 서버는 금액×적립률의 내림을 포인트 캐시백으로 적립하고 accepted로 응답합니다. | server: 서버 시험 test_accepted_points_are_floored, 서버 시험 test_evaluate_receipt_pure_function, 소스 backend/app/services/points.py(points = floor(amount × point_rate)) | O O - | [코드 확인] |
| ARO-F8-AC02 | 원치 않는 상황 | 금액이 0원 이하이거나 1,000,000원을 넘으면 서버는 rejected(invalid_amount)로 거부하고 적립하지 않습니다. | server: 서버 시험 test_amount_boundary, 소스 backend/app/services/points.py(MAX_AMOUNT_KRW=1_000_000) | O - | [코드 확인] |
| ARO-F8-AC03 | 사건 | 식당·가게 영수증의 결제일이 연계 스탬프의 날짜 또는 다음 날이면 인정하고, 이틀 뒤부터는 rejected(no_visit)로 거부합니다. | server: 서버 시험 test_restaurant_same_day_accepted, 서버 시험 test_restaurant_next_day_accepted, 서버 시험 test_restaurant_two_days_later_rejected, 서버 시험 test_no_visit_same_day_rejected | O O O O | [코드 확인] |
| ARO-F8-AC04 | 사건 | 숙소 영수증은 연계 스탬프 날짜가 체크인 이상 체크아웃 이하이면 인정하고, 그 밖이면 rejected(no_visit)로 거부합니다. | server: 서버 시험 test_lodging_within_stay_accepted, 서버 시험 test_lodging_outside_stay_rejected | O O | [코드 확인] |
| ARO-F8-AC05 | 원치 않는 상황 | 숙박이 최대 3박을 넘으면 서버는 400(invalid_stay)으로 거부하고, 체크인·체크아웃 날짜가 없으면 400(missing_stay)으로 거부합니다. | server: 서버 시험 test_lodging_over_three_nights_rejected_400, 서버 시험 test_lodging_missing_stay_dates_400, 소스 backend/app/config.py(MAX_STAY_NIGHTS=3) | O O - | [코드 확인] |
| ARO-F8-AC06 | 원치 않는 상황 | 사진이 없는 영수증 제출은 서버가 400(missing_photo)으로 거부합니다. 사진은 필수이고 최대 5MB까지만 받습니다. | server: 서버 시험 test_receipt_without_photo_rejected_400, 서버 시험 test_photo_over_5mb_rejected, 소스 backend/app/routers/rewards.py(사진 필수 직접 검사), 소스 backend/app/services/photos.py(MAX_PHOTO_BYTES 검사) | O O - - | [코드 확인] |
| ARO-F8-AC07 | 원치 않는 상황 | 제휴처+날짜+금액+사진 해시가 같은 영수증을 다시 보내면 서버는 rejected(duplicate)로 거부하고, 같은 사진 해시의 재제출도 rejected(duplicate_photo)로 거부합니다. | server: 서버 시험 test_duplicate_receipt_rejected, 서버 시험 test_same_photo_resubmission_rejected, 서버 시험 test_receipt_key_uses_photo_hash, 서버 시험 test_photo_hash_is_sha256_of_raw_bytes | O O O O | [코드 확인] |
| ARO-F8-AC08 | 상태 | 같은 제휴처·같은 날 두 번째 영수증은 서버가 pending(확인 대기)으로 저장하고 적립은 0으로 둡니다. | server: 서버 시험 test_second_receipt_same_partner_same_day_pending, 소스 backend/app/services/points.py(PENDING_REASON) | O - | [코드 확인] |
| ARO-F8-AC09 | 원치 않는 상황 | 같은 날 이미 적립(accepted)된 영수증이 5건을 넘으면 서버는 rejected(daily_limit)로 거부합니다. 확인 대기(pending)는 적립이 아니므로 이 한도에 세지 않습니다. | server: 서버 시험 test_daily_limit_after_five, 소스 backend/app/services/points.py(DAILY_LIMIT=5) | O - | [코드 확인] |
| ARO-F8-AC10 | 선택 | 방문 연계 장소가 정의되지 않은 제휴처의 영수증은 서버가 방문 연계 검사 없이 규칙을 통과시킵니다. | server: 서버 시험 test_wrong_partner_place_has_no_visit, 소스 backend/app/routers/rewards.py(_visit_near_place — near_place_id 없으면 null) | O - | [코드 확인] |
| ARO-F8-AC11 | 상태 | 서버는 거부된(rejected) 영수증을 저장하지 않고 사유만 응답하며 사진 파일은 바로 지웁니다. accepted·pending만 저장합니다. | code: 소스 backend/app/routers/rewards.py(거부 시 사진 삭제·응답만 반환, accepted·pending만 INSERT) | - | (설계) |
| ARO-F8-AC12 | 항상 | 적립·혜택의 모든 응답에는 "포인트 캐시백(예시)" 문구와 시뮬레이션 표시가 붙고, 환급·입금처럼 읽히는 문구는 쓰지 않습니다. | server: 서버 시험 test_accepted_points_are_floored(label·simulated 단언), 소스 backend/app/models.py(label 기본값 "포인트 캐시백(예시)" — 환급 표기 금지 주석), 소스 web/js/screens/receipt.js(문구 규칙) | O - - | [코드 확인] |
| ARO-F8-AC13 | 상태 | 영수증 입력은 차량 화면이 아니라 스마트폰 모바일 웹 화면에서만 받습니다. 차량 화면은 금액·사진 입력을 두지 않습니다. | code: 소스 web/js/screens/receipt.js(영수증 입력 화면), 소스 docs/ARCHITECTURE.md 7절·9절 Q17(영수증 입력은 모바일 웹 전용·시뮬레이션), 소스 web/README.md 화면 7 표 | - - - | (설계) |
| ARO-F8-AC14 | 상태 | 가족 포인트 조회는 적립된(accepted) 영수증에서만 생긴 원장 합계와 내역을 돌려줍니다. | server: 서버 시험 test_accepted_points_are_floored(points 합계·내역 단언), 소스 backend/app/routers/points.py(point_ledger가 유일한 원장) | O - | [코드 확인] |

## F9 오프라인·대체 구현(포트·폴백) (12개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F9-AC01 | 상태 | 서버가 없어도 앱은 APK에 내장한 시드(JSON)만으로 코스·퀴즈·스탬프북·제휴처를 읽고 스탬프→기록→음성→그때와 지금 흐름을 동작합니다. | unit: 단위 테스트 SeedRepositoryTest.realSeedFile_parsesToExpectedContent(장소 3개·퀴즈 6문항·제휴처 4개 단언), 소스 app/src/main/java/app/arosaegim/data/SeedRepository.kt, 소스 docs/ARCHITECTURE.md 9절 Q6 | O - - | [코드 확인] |
| ARO-F9-AC02 | 원치 않는 상황 | 시드 파일이 깨져 있어도 앱은 빈 코스로 복구하고 크래시하지 않습니다. | unit: 단위 테스트 SeedRepositoryTest.corruptJson_returnsEmptyContentWithoutCrash(깨진 JSON → 빈 내용, 예외 없음) | O | [코드 확인] |
| ARO-F9-AC03 | 사건 | 내비 초기화가 늦게 성공하면 앱은 대체 경로 구현에서 실제 구현으로 한 번만 전환하고, 전환 시점에 진행 중 목표로 경로를 다시 요청합니다. | unit: 단위 테스트 SwitchableRouteServiceTest.`6_감시기는 늦은 ok에서 전환하고 콜백을 부른다`, 단위 테스트 SwitchableRouteServiceTest.`3_재전환은 일방향 — 두 번째 switchTo는 전부 무시된다`, 단위 테스트 TripSessionTest.`11_경로가 늦게 준비되면 전환 시점에 현재 목표를 다시 요청한다` | O O O | [코드 확인] |
| ARO-F9-AC04 | 원치 않는 상황 | 내비 초기화가 실패로 끝나면 앱은 대체 경로 구현을 유지하고 실제 구현으로 전환하지 않습니다. | unit: 단위 테스트 SwitchableRouteServiceTest.`7_감시기는 늦은 failed에서 전환하지 않고 Fake를 유지한다` | O | [코드 확인] |
| ARO-F9-AC05 | 사건 | 포트 선택은 시작 때 즉시 판정하므로 콜드 부팅 직후 첫 실행도 앱이 멈추지 않고 열립니다. | emulator: 개발 기록 F61(콜드 스타트 교착 해소 — 동기 즉시 판정·늦은 초기화 일방향 업그레이드, TotalTime 8.1~8.5초 실측) | - | [확인] |
| ARO-F9-AC06 | 원치 않는 상황 | 포트 초기화 실패 시 앱은 대체 구현을 선택하고 선택 사유를 진단 화면에 표시합니다. | emulator: 개발 기록 F50(진단 화면이 어댑터 표를 구현/검증 분리로 렌더링하는 것 실측), 개발 기록 F54(음성 합성 두 경로 초기화 실패 실측), 개발 기록 F29(주행 규제 구현 등록 성공 실측), 소스 docs/ARCHITECTURE.md 5절(실측 선택 결과 표) | - - - - | [확인] |
| ARO-F9-AC07 | 원치 않는 상황 | 대체 구현이 선택돼 있으면 진단 화면과 화면 꼬리표가 그 사실을 보여주고, 대체 구현으로 찍은 영상을 실제 SDK 동작처럼 표기하지 않습니다. | code: 소스 docs/ARCHITECTURE.md 5절(대체 구현이 쓰이는 동안의 표시), 소스 app/src/main/java/app/arosaegim/AppContainer.kt(adapterNotes) | - - | (설계) |
| ARO-F9-AC08 | 원치 않는 상황 | 방문 전송이 실패해도 앱은 기록을 대기열에 유지하고, 다음 도착 시 대기열 순서대로 재전송합니다. 같은 멱등키의 중복 적재는 무시합니다. | unit: 단위 테스트 VisitQueueTest.`3_flush 실패시 항목이 큐에 유지된다`, 단위 테스트 VisitQueueTest.`5_flush는 FIFO 순서를 지키고 부분 실패 후 이어서 재전송한다`, 단위 테스트 VisitQueueTest.`1_같은 멱등키의 중복 enqueue는 무시된다`, 개발 기록 F82(flush는 도착 1건당 1회 — 공회전 폴링 없음) | O O O - | [코드 확인] |
| ARO-F9-AC09 | 원치 않는 상황 | 대기열에 넣은 PIN은 파일에 저장되지 않고 전송 요청 헤더로만 쓰입니다. | unit: 단위 테스트 VisitQueueTest.`7_enqueue 로 받은 PIN 은 파일에 저장되지 않고 전송에만 쓰인다`, 단위 테스트 VisitQueueTest.`6_pin 공급자의 세션 PIN 이 X-Family-Pin 값으로 전달된다` | O O | [코드 확인] |
| ARO-F9-AC10 | 상태 | 릴리스 앱은 평문 HTTP가 기본 금지이고 데모 서버 주소(10.0.2.2·localhost)만 예외로 허용합니다. | emulator: 소스 android/app/src/main/res/xml/network_security_config.xml(10.0.2.2·localhost만 허용), 개발 기록 F71(릴리스 클리어텍스트 차단 발견·수정 후 재실측 — POST /v1/visits 도달 확인) | - - | [확인] |
| ARO-F9-AC11 | 사건 | 진단 관찰이 중단돼도 앱은 재감시 호출로 여정 감시와 도착 판정을 복구합니다. | unit: 단위 테스트 TripSessionTest.`12_외부에서 감지기가 stop돼도 rewatch하면 현재 목표 감시와 도착 판정이 복구된다`, 단위 테스트 TripSessionTest.`13_세션 수집이 중단된 뒤 rewatch하면 수집과 감시가 함께 복구된다`, 개발 기록 F67 | O O - | [코드 확인] |
| ARO-F9-AC12 | 상태 | 포트 전환은 일방향이고 초기 대체 구현 자신으로의 전환은 무시합니다. | unit: 단위 테스트 SwitchableRouteServiceTest.`3_재전환은 일방향 — 두 번째 switchTo는 전부 무시된다`, 단위 테스트 SwitchableRouteServiceTest.`4_초기 위임자 자신으로의 전환은 무시된다`, 단위 테스트 SwitchableNarratorTest.`전환은 일방향 - ready가 false로 되돌아가도 Pleos를 유지한다` | O O O | [코드 확인] |

## 요약

| 기능 | 이름 | 수용 기준 | 유형 분포 | 상태 분포 | 시험 연결 |
|---|---|---|---|---|---|
| F1 | 도착 자동 스탬프 | 14 | 항상 1, 사건 7, 상태 1, 선택 2, 원치 않는 상황 3 | [확인] 4, (설계) 2, [코드 확인] 8 | 8 |
| F2 | 주행/정차 전환 | 14 | 항상 2, 사건 4, 상태 3, 선택 1, 원치 않는 상황 4 | [코드 확인] 7, [확인] 1, [일부 확인] 4, (설계) 2 | 7 |
| F3 | 박물관 퀴즈 | 11 | 항상 1, 사건 4, 상태 3, 선택 1, 원치 않는 상황 2 | (설계) 6, [코드 확인] 5 | 5 |
| F4 | 그때와 지금 | 13 | 항상 2, 사건 5, 상태 2, 선택 2, 원치 않는 상황 2 | [코드 확인] 10, (설계) 3 | 10 |
| F5 | 음성 해설·음성 편지 | 13 | 항상 2, 사건 2, 상태 3, 선택 1, 원치 않는 상황 5 | (설계) 7, [코드 확인] 5, [실패] 1 | 5 |
| F6 | 가족 코드·PIN·세션 | 15 | 항상 3, 사건 4, 상태 3, 선택 1, 원치 않는 상황 4 | [코드 확인] 6, (설계) 7, [일부 확인] 1, (계획) 1 | 7 |
| F7 | 가족 기록 서버·모바일 웹·물려주기 | 16 | 항상 2, 사건 6, 상태 1, 선택 1, 원치 않는 상황 6 | (설계) 2, [코드 확인] 14 | 14 |
| F8 | 로컬 맛집·숙소 포인트 캐시백(영수증 인증) | 14 | 항상 1, 사건 3, 상태 4, 선택 1, 원치 않는 상황 5 | [코드 확인] 12, (설계) 2 | 12 |
| F9 | 오프라인·대체 구현(포트·폴백) | 12 | 사건 3, 상태 3, 원치 않는 상황 6 | [코드 확인] 8, [확인] 3, (설계) 1 | 8 |

시험 이름 점검: 확인 대상 138건 중 없음 0건, 점검 제외 128건.

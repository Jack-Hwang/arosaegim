# 명세 추적표 — 수용 기준 ↔ 시험 이름

기준 커밋: fee6273. 수용 기준 번호는 ARO-F<기능>-AC<두 자리>입니다. "확인 방법"의 시험 이름은 저장소의 앱 단위 시험(`@Test`) 또는 서버 시험(`def test_`) 이름이며,
"실존" 열은 위 커밋에서 그 이름이 실제로 검색되는지를 자동으로 점검한 결과입니다(없음이면 이름을 고치거나 시험을 추가해야 합니다). emulator·code·none 유형의 연결은 시험 이름이 아니므로 점검 대상에서 제외합니다(-).

## F1 도착 자동 스탬프 (11개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F1-AC01 | 사건 | 목적지 반경 120m 안으로 들어오면 앱은 해당 장소의 스탬프 1건을 기록합니다. | emulator: StampEngineTest.`1_반경 안에서 동일 placeId 연속 도착 2회는 1회만 스탬프된다`, 개발 기록 F48(근접 알림 ENTER 수신 실측), 개발 기록 F49(스탬프 1/3·다음 목표 진행 실측), 실측 화면 3·7(2026-11-11 캡처: 대릉원 도착 뒤 스탬프 2/3, 첨성대 도착 뒤 3/3·코스 완주) | - - - - | [확인] |
| ARO-F1-AC02 | 원치 않는 상황 | 근접 알림 등록이 실패하거나 오류가 나면 앱은 수신 좌표로 거리를 직접 계산해 도착을 판정합니다. | code: FusedArrivalDetector.kt(haversineActive 분기), StampEngineTest.`호출자 경계 판정_거리가 반경 이내여야만 엔진에 전달된다`, HaversineTest(거리 계산 단위 테스트) | - - - | (설계) |
| ARO-F1-AC03 | 항상 | 앱은 위치 정보를 도착 판정과 화면 거리 표시에만 쓰고 저장하지 않습니다. | code: 개발 기록 ARCHITECTURE.md 4절(위치는 저장하지 않는다 — 메모리에만 존재) | - | (설계) |
| ARO-F1-AC04 | 사건 | 스탬프를 이미 찍은 장소에 다시 들어와도(재진입) 앱은 중복 스탬프를 기록하지 않습니다. | unit: StampEngineTest.`2_재진입 중복 방지_A획득후_B획득하고_다시_A에 도착하면 null이다` | O | [코드 확인] |
| ARO-F1-AC05 | 사건 | 코스 순서상 지금 목표가 아닌 장소에 도착해도 앱은 스탬프를 기록하지 않습니다. | unit: StampEngineTest.`3_코스 순서 규칙_현재 목표가 아닌 장소 도착은 null이다` | O | [코드 확인] |
| ARO-F1-AC06 | 상태 | 코스를 완주한 뒤에는 앱이 추가 도착 신호를 무시하고 도착 감시를 멈춥니다. | unit: StampEngineTest.`4_완주 후 currentTargetPlaceId가 null이면 도착은 항상 null이다`, TripSessionTest.`4_모든 장소 도착시 완주_감시가 중단된다`, 개발 기록 F48·F49·F74·F77(1/3→2/3 에뮬레이터 실측) | O O - | [코드 확인] |
| ARO-F1-AC07 | 선택 | 차가 기어 D(주행) 상태인 경우에도 앱은 반경 진입을 감지해 도착을 기록합니다. | emulator: 개발 기록 F68(기어 D·시속 40km 실측) | - | [확인] |
| ARO-F1-AC08 | 사건 | 도착 스탬프가 등록되면 앱은 다음 목적지로 내비게이션 경로를 요청합니다. | emulator: 개발 기록 F68(경로 요청 → 내비게이션 애플리케이션에 대릉원 검색 팝업), TripSessionTest.`7_routeService_주입시 시작 목표와 바뀐 목표마다 startRoute를 호출한다` | - - | [일부 확인] |
| ARO-F1-AC09 | 사건 | 앱을 다시 시작하면 앱은 저장된 방문 기록으로 스탬프 진행을 복원합니다. | unit: TripSessionTest.`14_재시작 시 visits_json 기록으로 스탬프 진행을 복원한다` | O | [코드 확인] |
| ARO-F1-AC10 | 원치 않는 상황 | 네트워크가 없어 서버 전송이 실패해도 앱은 방문 기록을 먼저 차 안에 저장하고 전송은 나중에 다시 시도합니다. | unit: TripSessionTest.`6_visitQueue_주입시 도착하면 큐에 1건 적재되고 전송을 시도한다` | O | [코드 확인] |
| ARO-F1-AC11 | 선택 | 코스를 처음부터 다시 시작하는 경우 앱은 스탬프 번호를 1부터 다시 매기고 이전 방문 기록을 지웁니다. | unit: StampEngineTest.`5_reset 후 다시 획득 가능하고 stampNo가 1부터 다시 시작된다`, TripSessionTest.`5_resetCourse_후 진행과 기록이 처음으로 돌아간다` | O O | [코드 확인] |

## F2 주행/정차 전환 (14개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F2-AC01 | 항상 | 앱은 주행 규제 신호를 1차 신호로 삼아 주행 상태를 판정합니다. | code: DrivingStateMerger.kt(1차 신호 판정), DrivingStateMergerTest.`보조가 없으면 1차만으로 판정한다`, 개발 기록 F29(주행 규제 관리자 획득·등록 성공) | - - - | [코드 확인] |
| ARO-F2-AC02 | 사건 | 1차 신호와 보조(기어) 신호가 서로 어긋나면 앱은 더 제한적인 쪽인 주행으로 판정합니다. | unit: DrivingStateMergerTest.`1차 정차에 보조 주행이면 주행으로 판정한다 - 어긋나면 더 제한적 우선`, DrivingStateMergerTest.`1차 주행에 보조 정차여도 주행을 유지한다 - 더 제한적 우선` | O O | [코드 확인] |
| ARO-F2-AC03 | 선택 | 보조(기어) 신호를 읽지 못하는 경우 앱은 1차 신호만으로 주행 상태를 판정합니다. | unit: DrivingStateMergerTest.`보조를 못 읽었으면 판정에 영향을 주지 않는다` | O | [코드 확인] |
| ARO-F2-AC04 | 항상 | 앱은 주행 화면에만 주행 최적화를 선언하고 정차 화면에는 선언하지 않습니다. | code: AndroidManifest.xml(DrivingActivity distractionOptimized=true 선언), 개발 기록 ARCHITECTURE.md 3절 | - - | [코드 확인] |
| ARO-F2-AC05 | 원치 않는 상황 | 주행 최적화를 선언하지 않은 화면은 주행이 시작되면 시스템이 자동으로 가립니다. 주행 중 퀴즈·입력 금지는 이 방식으로 지켜집니다. | emulator: 개발 기록 F68(기어 D·시속 40km 실측에서 화면 가려짐 확인), 개발 기록 ARCHITECTURE.md 3절 | - - | [확인] |
| ARO-F2-AC06 | 원치 않는 상황 | 일부 실측에서는 주행 최적화를 선언한 화면도 주행 중 시스템이 함께 가렸습니다. | emulator: 개발 기록 F68(주행 화면도 "주행 중에는 사용할 수 없습니다"로 가려진 사례) | - | [일부 확인] |
| ARO-F2-AC07 | 상태 | 정차 화면이 시스템에 가려지는 동안에도 앱은 퀴즈 진행 상태(문항 번호·답안)를 보존합니다. | code: QuizState.kt(QuizStateHolder — 프로세스 수명 싱글톤), 개발 기록 ARCHITECTURE.md 3절(퀴즈 상태 보존) | - - | (설계) |
| ARO-F2-AC08 | 사건 | 가족 세션(가족 코드 또는 둘러보기)이 있는 상태에서 차가 정차하면 앱은 정차 화면으로 자동 전환합니다. | emulator: 개발 기록 F72(기어 D→P 자동 복귀 2회 확인 — 세션 게이트를 넣기 전 빌드), ModeRouterResolveTest.`정차 상태에서 세션이 있으면 정차 화면으로 라우팅한다` | - - | [일부 확인] |
| ARO-F2-AC09 | 원치 않는 상황 | 가족 세션이 없는 상태에서 차가 정차해도 앱은 자동 전환을 보류하고 시작 화면(가족 코드 입력)을 그대로 유지합니다. | unit: ModeRouterResolveTest.`정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)`, 개발 기록 F80(디버그 앱 콜드 스타트에서 정차 화면이 뜨지 않는 것을 확인) | O - | [일부 확인] |
| ARO-F2-AC10 | 사건 | 주행 상태가 되면 앱은 가족 세션 여부와 관계없이 항상 주행 화면으로 전환합니다. | unit: ModeRouterResolveTest.`주행 상태는 세션이 없어도 주행 화면으로 라우팅한다`, ModeRouterResolveTest.`주행 상태는 세션이 있어도 주행 화면으로 라우팅한다` | O O | [코드 확인] |
| ARO-F2-AC11 | 상태 | 같은 상태 신호가 반복해서 들어오는 동안에는 앱은 화면을 다시 전환하지 않습니다. | unit: ModeStateMachineTest.`연속 동일 신호 3회는 mode 값 변화가 없다` | O | [코드 확인] |
| ARO-F2-AC12 | 사건 | 주행에서 정차로 복귀하면 앱은 복귀 전의 화면 모드를 그대로 복원합니다. | emulator: ModeStateMachineTest.`PARKED에서 DRIVING으로 복귀 후 PARKED로 복원된다`, 개발 기록(Driving·Parked 3회 전환·복귀 실측 — 세션 게이트를 넣기 전) | - - | [일부 확인] |
| ARO-F2-AC13 | 원치 않는 상황 | 주행 상태 신호 등록이 실패하면 앱은 정차 상태를 유지하고 화면에 수동 전환 표시를 보여 줍니다. | code: 개발 기록 ARCHITECTURE.md 3절(실패 시의 동작 — 수동 전환 대체) | - | (설계) |
| ARO-F2-AC14 | 사건 | 보조(기어) 신호가 정차로 5초 이상 이어지면 앱은 1차 신호가 주행을 주장해도 정차로 판정합니다. | unit: DrivingStateMergerTest.`1차 주행에 보조 정차가 보류 시간 이상이면 PARKED로 해제한다 - Q26`, 개발 기록 F81(Q26 해소 — 기어 P 지속 시 주행 해제 타임아웃) | O - | [코드 확인] |

## F3 박물관 퀴즈 (10개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F3-AC01 | 사건 | 고른 보기가 정답이면 앱은 '정답이에요!' 한 줄과 해설을 보여줍니다. | code: strings.xml parked_quiz_correct, ui/parked/ParkedSteps.kt QuizStep | - - | (설계) |
| ARO-F3-AC02 | 사건 | 고른 보기가 오답이면 앱은 올바른 보기 문장을 '정답: …' 형태로 함께 보여줍니다. | code: strings.xml parked_quiz_wrong, ui/parked/ParkedSteps.kt QuizStep | - - | (설계) |
| ARO-F3-AC03 | 상태 | 차가 주행 중인 동안 앱은 퀴즈가 있는 정차 화면으로 전환하지 않습니다. | unit: ModeRouterResolveTest.`주행 상태는 세션이 있어도 주행 화면으로 라우팅한다`, ModeRouterResolveTest.`주행 상태는 세션이 없어도 주행 화면으로 라우팅한다` | O O | [코드 확인] |
| ARO-F3-AC04 | 사건 | 차가 정차하고 가족 세션이 있으면 앱은 퀴즈 단계를 포함한 정차 화면으로 전환합니다. | unit: ModeRouterResolveTest.`정차 상태에서 세션이 있으면 정차 화면으로 라우팅한다` | O | [코드 확인] |
| ARO-F3-AC05 | 원치 않는 상황 | 차가 정차했지만 가족 세션이 없으면 앱은 정차 화면 전환을 보류하고 시작 화면을 유지합니다. | unit: ModeRouterResolveTest.`정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)`, 개발 기록 Q28 | O - | [코드 확인] |
| ARO-F3-AC06 | 항상 | 앱은 경주 코스 3곳(국립경주박물관·대릉원·첨성대)마다 퀴즈 2문항씩, 코스 전체 6문항을 내장해 제공합니다. | unit: SeedRepositoryTest.realSeedFile_parsesToExpectedContent | O | [코드 확인] |
| ARO-F3-AC07 | 선택 | 문항이 아직 검수 전(reviewed=false)인 경우 앱은 그 문항에 '초안' 꼬리표를 붙입니다. 지금 6문항 전부가 이 상태입니다. | emulator: SeedRepositoryTest.realSeedFile_parsesToExpectedContent, 실측 화면 8(2026-11-12 캡처: 퀴즈 문항 머리에 ‘1/2’와 ‘초안’ 표시) | - - | [확인] |
| ARO-F3-AC08 | 원치 않는 상황 | 도착한 장소에 준비된 퀴즈가 없으면 앱은 빈 안내 문구 한 줄만 보여주고 화면을 멈추지 않습니다. | code: strings.xml parked_quiz_empty, ui/parked/ParkedSteps.kt QuizStep | - - | (설계) |
| ARO-F3-AC09 | 사건 | 그 장소의 마지막 문항에 답하면 앱은 자동으로 '그때와 지금' 단계로 넘어갑니다. | code: ui/parked/ParkedSteps.kt QuizStep (stepIndex = 2) | - | (설계) |
| ARO-F3-AC10 | 상태 | 퀴즈를 풀고 있는 동안 주행으로 화면이 가려졌다가 정차로 돌아오면 앱은 같은 장소의 같은 문항부터 이어서 보여줍니다. | code: ui/parked/QuizState.kt QuizStateHolder | - | (설계) |

## F4 그때와 지금 (12개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F4-AC01 | 사건 | 지난 방문 연도 기록이 있으면 앱은 올해 연도에서 그 연도를 뺀 햇수를 계산해 '○년 전'으로 보여줍니다. | unit: ThenNowResolverTest.`과거가 있으면 yearsAgo를 계산해 반환한다`, ThenNowResolverTest.`여러 과거 후보 중 가장 오래된 것을 선택한다` | O O | [코드 확인] |
| ARO-F4-AC02 | 원치 않는 상황 | 지난 연도가 올해보다 미래이거나 0 이하로 기록돼 있으면 앱은 그 값을 쓰지 않고 첫 방문으로 처리합니다. | unit: ThenNowResolverTest.`nowYear보다 과거 연도가 크면 미래 과거로 보고 null을 반환한다`, ThenNowResolverTest.`pastYear가 0 이하면 null을 반환한다` | O O | [코드 확인] |
| ARO-F4-AC03 | 사건 | 오늘 좌석 점유로 읽은 인원과 그때 기록된 인원이 둘 다 있으면 앱은 '오늘은 N명이 함께 왔어요. 그때는 M명이었어요.'를 보여줍니다. | emulator: strings.xml parked_party_line, ui/parked/ThenNowExtras.kt PartySizeLine, 개발 기록 F73(도착 카드에 인원 비교 문장 표시 실측), 실측 화면 9 | - - - | [확인] |
| ARO-F4-AC04 | 원치 않는 상황 | 오늘 인원 또는 그때 인원 중 하나라도 읽지 못하면 앱은 인원 비교 문장을 통째로 숨기고 값을 지어내지 않습니다. | code: ui/parked/ThenNowExtras.kt PartySizeLine (todayCount == null \|\| thenCount == null 시 숨김) | - | (설계) |
| ARO-F4-AC05 | 선택 | 가족이 차 안에서 인원 비교 문장을 접어 두면 앱은 그 장소에서 문장을 다시 보여주지 않습니다. | server: test_folded_place_add_remove, test_then_now_folded_flag | O O | [코드 확인] |
| ARO-F4-AC06 | 사건 | 가족이 가족 모바일 웹에서 접어 둔 장소를 되돌리면 앱은 그 장소에서 인원 비교 문장을 다시 보여줍니다. | server: test_folded_place_add_remove, test_then_now_folded_flag | O O | [코드 확인] |
| ARO-F4-AC07 | 항상 | 앱은 오늘 함께 달린 거리를 읽으면 숫자 한 줄로 보여주고, 읽지 못하거나 0 이하이면 그 줄을 보여주지 않습니다. | server: test_metrics_total_trip_km_zero_without_trip_km, test_metrics_total_trip_km_dedup_same_course_run | O O | [코드 확인] |
| ARO-F4-AC08 | 사건 | 이 장소에 지난 방문이 전혀 없다고 서버가 확인하면(첫 방문) 앱은 '오늘이 우리 가족의 첫 기록입니다' 문구로 바꿔 보여줍니다. | server: test_then_now_visit_ordinal_counts_per_place, test_then_now_without_visits_is_honest | O O | [코드 확인] |
| ARO-F4-AC09 | 항상 | 유물 정보가 아직 공식 자료로 검수되지 않았으면 앱은 유물·가족 연도 줄 아래에 검수 전임을 알리는 메모 한 줄을 함께 보여줍니다. 지금 3곳 전부가 이 상태입니다. | unit: SeedRepositoryTest.realSeedFile_parsesToExpectedContent(모든 heritage 는 초안(reviewed=false)이어야 한다), ui/parked/ThenNowExtras.kt HeritageTimelineLine | - - | [코드 확인] |
| ARO-F4-AC10 | 선택 | 가족이 모바일 웹에서 사진에 이름표를 붙였으면 앱은 사진 아래에 그 이름을 점이나 얼굴 표시 없이 글자로만, 최대 10명까지 보여줍니다. 이름이 하나도 없으면 줄을 그리지 않습니다. | server: test_markers_saved_and_shown, test_markers_limit_is_10_on_update_and_create | O O | [코드 확인] |
| ARO-F4-AC11 | 상태 | 창 너비가 600dp 이상인 동안 앱은 '그때'와 '지금' 카드를 좌우로 나란히 보여주고, 그보다 좁은 동안은 위아래로 쌓아 보여줍니다. | code: ui/parked/ParkedScreen.kt (isWide = maxWidth >= 600.dp) | - | (설계) |
| ARO-F4-AC12 | 원치 않는 상황 | 오늘 좌석 응답률이 50% 미만이면 앱은 그 응답을 합산하지 않고 오늘 인원 값을 비워, 일부만 읽힌 결과를 적은 인원으로 잘못 보여주지 않습니다. | unit: SeatCountingTest.`응답률 미달 - 9석 요청에 4석 응답이면 답이 있어도 null 이다`, SeatCountingTest.`응답률 경계 - 정확히 50% 응답은 통과한다` | O O | [코드 확인] |

## F5 음성 해설·음성 편지 (11개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F5-AC01 | 항상 | 앱은 안내 음성을 사전 제작 오디오 → 차량 음성 합성(Pleos) → 안드로이드 기본 음성 순서의 3단 체인으로 시도하도록 설계돼 있습니다. | code: AppContainer.kt selectNarrator / AssetAudioFirstNarrator | - | (설계) |
| ARO-F5-AC02 | 원치 않는 상황 | 차량 음성 합성(Pleos)의 초기화 확인이 아직 안 된 상태에서 문장을 말해야 하면 앱은 기다리지 않고 안드로이드 기본 음성으로 즉시 말합니다. | unit: SwitchableNarratorTest.`onReady 전에는 대체(Android TTS 자리)로 말한다 - 블로킹 없이 즉시 기동` | O | [코드 확인] |
| ARO-F5-AC03 | 사건 | 차량 음성 합성의 초기화 확인이 늦게라도 성공하면 앱은 그 다음부터 안드로이드 기본 음성 대신 차량 음성 합성으로 바꿔 말합니다. 이 전환은 한 방향이라 되돌아가지 않습니다. | unit: SwitchableNarratorTest.`onReady 지연 확인 후에는 Pleos로 말한다`, SwitchableNarratorTest.`전환은 일방향 - ready가 false로 되돌아가도 Pleos를 유지한다` | O O | [코드 확인] |
| ARO-F5-AC04 | 항상 | 지금 안내 음성을 쓸 수 있는지 묻는 질문에 앱은 현재 활성 구현이 실제로 준비됐는지를 그대로 답하고 꾸미지 않습니다. | unit: SwitchableNarratorTest.`isAvailable은 활성 위임자의 실제 준비 상태를 답한다 - 거짓 표기 금지` | O | [코드 확인] |
| ARO-F5-AC05 | 원치 않는 상황 | 에뮬레이터 실측에서 안드로이드 기본 음성 초기화와 차량 음성 합성 초기화 확인이 모두 실패해, 지금은 두 합성 경로 모두 소리가 나지 않습니다. 원인은 아직 확정되지 않았습니다. | emulator: 개발 기록 U6, 개발 기록 F54 | - - | [실패] |
| ARO-F5-AC06 | 사건 | 가족이 음성 편지 재생 버튼을 누르면 앱은 그 순간부터 사전 제작 음성 편지 재생을 시작합니다(자동재생 없음). | code: ui/parked/ThenNowExtras.kt VoiceLetterCard (onClick) | - | (설계) |
| ARO-F5-AC07 | 원치 않는 상황 | 음성 편지 파일을 쓸 수 없거나 재생이 실패하면 앱은 '예시(음성 준비 중)' 글자 안내로 바꿔 보여줍니다. | code: strings.xml parked_voice_letter_unavailable, ui/parked/ThenNowExtras.kt VoiceLetterCard | - - | (설계) |
| ARO-F5-AC08 | 사건 | 정차 화면이 가려지면(주행 전환이나 다른 화면으로 전환) 앱은 재생 중인 음성 편지를 즉시 멈춥니다. | code: ui/parked/ParkedActivity.kt onPause() | - | (설계) |
| ARO-F5-AC09 | 원치 않는 상황 | 다른 소리가 오디오 포커스를 완전히 또는 일시적으로 가져가면 앱은 재생 중인 안내 음성·음성 편지를 즉시 멈춥니다. | code: pleos/PleosVoiceLetterPlayer.kt focusListener, fallback/AssetAudioNarrator.kt focusListener, pleos/PleosTtsNarrator.kt focusListener | - - - | (설계) |
| ARO-F5-AC10 | 선택 | 다른 소리가 낮추기만 요청하는 경우(소리를 줄여도 되는 상황) 앱은 재생을 멈추지 않고 계속합니다. | code: pleos/PleosVoiceLetterPlayer.kt focusListener(AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK 미처리 — 계속 재생) | - | (설계) |
| ARO-F5-AC11 | 상태 | 차가 정차한 상태인 동안에만 앱은 음성 편지 카드를 화면에 둡니다. 주행 화면에는 이 카드 자체가 없습니다. | code: ui/parked/ThenNowExtras.kt VoiceLetterCard(정차 화면 전용), ui/driving/DrivingScreen.kt(음성 편지 카드 없음) | - - | (설계) |

## F6 가족 코드·PIN·세션 (14개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F6-AC01 | 항상 | 앱은 가족 코드가 6~8자리 영숫자(대소문자 허용)인지 검사해 유효성을 판정합니다. | unit: FamilySessionStoreTest 단위 테스트("여섯 자리 영숫자는 유효하다"), FamilySessionStoreTest 단위 테스트("일곱 자리 영숫자도 유효하다 — 서버가 8자리 코드를 발급한다(F58)") | - - | [코드 확인] |
| ARO-F6-AC02 | 원치 않는 상황 | 가족 코드에 공백·특수문자·한글이 섞이면 앱은 무효로 판정하고 접속을 진행하지 않습니다. | unit: FamilySessionStoreTest 단위 테스트("특수문자가 섞이면 무효하다"), FamilySessionStoreTest 단위 테스트("한글이 섞이면 무효하다"), FamilySessionStoreTest 단위 테스트("공백이 섞이면 무효하다") | - - - | [코드 확인] |
| ARO-F6-AC03 | 사건 | 가족 코드 입력칸에 글자를 넣으면 앱은 숫자이거나 글자(한글 포함)인 문자만 남기고 입력을 8자까지만 받습니다. 영문·숫자 6~8자리로만 통과시키는 전체 형식 검사는 접속 버튼을 누를 때 따로 적용됩니다. | code: 시작 화면 소스 코드(입력란의 문자 필터는 공백·기호만 걸러내고 최대 길이만 8자로 제한; 6~8자리 영숫자 판정은 FamilyCodeValidator) | - | (설계) |
| ARO-F6-AC04 | 사건 | 가족 코드가 검증을 통과하고 PIN 6자리가 모두 입력되면 앱은 [이어 보기] 버튼을 활성화합니다. | code: 시작 화면 소스 코드([이어 보기] 버튼의 활성 조건식) | - | (설계) |
| ARO-F6-AC05 | 항상 | 앱은 PIN을 세션 메모리에만 두고, 기기 저장소·파일·기록에는 남기지 않습니다. | unit: FamilySessionStoreTest 단위 테스트("pin을 설정하면 pinOrNull로 같은 값이 나온다"), FamilySessionStoreTest 단위 테스트("공백뿐인 pin은 없는 것과 같다"), 개발 기록 F59 | - - - | [코드 확인] |
| ARO-F6-AC06 | 항상 | 앱은 가족 코드만 기기 저장소(앱 전용 설정)에 남겨 다시 켜도 접속 상태가 이어지게 하고, PIN은 예외로 둡니다. | code: 차량 세션 저장소 소스 코드(가족 코드 저장 경로) | - | (설계) |
| ARO-F6-AC07 | 상태 | 가족 코드 접속도 둘러보기도 선택하지 않은 동안 앱은 정차 화면으로 자동 전환하지 않고 시작 화면(가족 코드 입력)을 최상단에 유지합니다. | emulator: 모드 라우터 단위 테스트("정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)"), 개발 기록 F80 — 콜드 스타트 실측(세션 없을 때 시작 화면 유지 확인) | - - | [확인] |
| ARO-F6-AC08 | 상태 | 차가 주행 중인 동안 앱은 가족 세션 여부와 관계없이 주행 화면을 유지합니다. | unit: 모드 라우터 단위 테스트("주행 상태는 세션이 없어도 주행 화면으로 라우팅한다"), 모드 라우터 단위 테스트("주행 상태는 세션이 있어도 주행 화면으로 라우팅한다") | - - | [코드 확인] |
| ARO-F6-AC09 | 선택 | 가족 없이 둘러보기를 고르는 경우 앱은 기록을 이 차량에만 저장하고 가족 서버에 연결하지 않습니다. | code: 차량 세션 저장소 소스 코드(둘러보기 모드 전환 경로) | - | (설계) |
| ARO-F6-AC10 | 사건 | '이 차에서 로그아웃'을 실행하면 앱은 가족 코드·세션 PIN·방문 기록 파일·전송 대기 큐 파일을 모두 지웁니다. | unit: 로그아웃 정리 단위 테스트("로그아웃 대상 파일 두 개를 실제로 지운다"), FamilySessionStoreTest 단위 테스트("clear는 코드와 pin을 모두 지운다"), 개발 기록 F65 | - - - | [코드 확인] |
| ARO-F6-AC11 | 원치 않는 상황 | 로그아웃 중 파일 하나가 삭제되지 않으면 앱은 나머지 파일 삭제를 멈추지 않고 결과를 한 줄씩 남깁니다. | unit: 로그아웃 정리 단위 테스트("한 파일 삭제 실패가 나머지 삭제를 막지 않는다") | - | [코드 확인] |
| ARO-F6-AC12 | 원치 않는 상황 | 서버가 가족 코드·PIN 불일치로 요청을 거부하면(401 또는 403) 앱은 가족 구성원 이름표 카드를 숨기지 않고 안내 한 줄만 보여줍니다. | code: 정차 화면 소스 코드(이름표 카드의 인증 오류 안내 처리), 화면 문구 "가족 코드·PIN을 다시 확인해 주세요", FamilySessionStoreTest 단위 테스트("거절 표시하면 pinOrNull은 null이 되고 코드는 유지된다"), VisitQueueTest 단위 테스트("11_서버가 PIN을 거부하면 같은 조합 재전송을 멈추고 큐는 보존된다"), TripSessionTest 단위 테스트("15_큐 flush의 PIN 거절은 재입력 안내 한 줄을 남긴다") | - - - - - | [코드 확인] |
| ARO-F6-AC13 | 사건 | 가족 코드로 접속하면 앱은 코드를 대문자로 정규화해 저장해 표기가 흔들리지 않게 합니다. | code: 차량 세션 저장소 소스 코드(접속 시 대문자 정규화 저장) | - | (설계) |
| ARO-F6-AC14 | 원치 않는 상황 | 서버가 PIN 5회 연속 오류로 잠금(429)을 응답해도 앱은 이를 별도 안내로 구분하지 않고 일반 전송 실패로 처리합니다. | code: API 클라이언트 소스 코드 주석(401·403만 재입력 신호로 구분하고 404·429는 일반 실패로 흡수한다고 명시) | - | (설계) |

## F7 가족 기록 서버·모바일 웹·물려주기 (15개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F7-AC01 | 사건 | 가족 코드·PIN 발급을 요청하면 서버는 8자리 가족 코드와 숫자 6자리 PIN을 새로 만들어 그 응답 한 번에만 담아 돌려줍니다. | code: 가족 인증 서비스 소스 코드(가족 코드·PIN 생성 로직) | - | (설계) |
| ARO-F7-AC02 | 원치 않는 상황 | 같은 기기 식별값으로 하루에 두 번째 가족 코드를 발급받으려 하면 서버는 429(device_limit)로 거부합니다. | server: 서버 시험 test_family_code_device_token_daily_limit | O | [코드 확인] |
| ARO-F7-AC03 | 원치 않는 상황 | PIN을 5회 연속 틀리면 서버는 10분간 그 가족의 모든 요청을 429(family_locked)로 막고, 그 안에는 올바른 PIN을 보내도 막습니다. | server: 서버 시험 test_pin_locks_after_five_failures | O | [코드 확인] |
| ARO-F7-AC04 | 상태 | 가족이 PIN 잠금 상태인 동안 서버는 코드 재발급 요청만 예외로 받아들이고, 그 외 요청은 모두 429로 거부합니다. | code: 가족 인증 서비스 소스 코드(잠금 중에도 코드 재발급만 허용하는 경로), 서버 시험 test_pin_locks_after_five_failures(잠금 중 일반 요청 429 확인) | - - | [코드 확인] |
| ARO-F7-AC05 | 원치 않는 상황 | X-Family-Pin 헤더 없이 가족 데이터를 조회하면 서버는 401(pin_required)로 거부합니다. | server: 서버 시험 test_missing_pin_header_rejected | O | [코드 확인] |
| ARO-F7-AC06 | 사건 | 같은 분(초 단위는 무시) 안에 같은 장소·코스 방문을 다시 보내면 서버는 새 방문을 만들지 않고 기존 방문과 같은 스탬프 번호를 돌려줍니다. | server: 서버 시험 test_same_minute_is_idempotent, 서버 시험 test_different_minute_creates_new_visit | O O | [코드 확인] |
| ARO-F7-AC07 | 항상 | 서버는 저장하는 모든 사진에서 EXIF(위치·시각 등 메타데이터)를 제거하고 다시 저장합니다. | server: 서버 시험 test_exif_is_stripped | O | [코드 확인] |
| ARO-F7-AC08 | 원치 않는 상황 | 가족에 보호자 동의를 아직 받지 못한 미성년 구성원이 있으면 서버는 그 가족의 사진 업로드를 403(guardian_consent_required)으로 거부합니다. | server: 서버 시험 test_minor_photo_requires_guardian_consent | O | [코드 확인] |
| ARO-F7-AC09 | 선택 | 미성년 구성원이 있는 경우에도 사진이 없는 기억 기록은 보호자 동의와 무관하게 허용됩니다. | server: 서버 시험 test_minor_photo_requires_guardian_consent | O | [코드 확인] |
| ARO-F7-AC10 | 사건 | 가족 데이터 내보내기를 요청하면 서버는 timeline.json·README.html·photos 폴더가 든 ZIP 파일 하나를 돌려줍니다. | server: 서버 시험 test_export_zip_is_selfcontained | O | [코드 확인] |
| ARO-F7-AC11 | 항상 | 서버는 가족 단위 지표 응답에 가족이 함께 달린 누적 거리를 포함하고, 같은 날 같은 코스 주행에서 나온 거리는 한 번만 더합니다. | server: 서버 시험 test_metrics_total_trip_km_dedup_same_course_run | O | [코드 확인] |
| ARO-F7-AC12 | 사건 | 관리자(owner) 구성원이 다른 구성원을 이어받을 사람으로 지정하면 서버는 그 구성원의 역할을 '이어받을 사람'으로 바꾸고, 이전에 지정돼 있던 사람은 일반 구성원으로 되돌립니다. | server: 서버 시험 test_heir_reassign_to_another_member, 서버 시험 test_heir_and_handover_flow | O O | [코드 확인] |
| ARO-F7-AC13 | 원치 않는 상황 | 관리자가 아닌 PIN으로 이어받을 사람 지정을 요청하면 서버는 403(not_owner)으로 거부하고, PIN 헤더 자체가 없으면 401(pin_required)로 거부합니다. | server: 서버 시험 test_heir_owner_auth_failure_is_403 | O | [코드 확인] |
| ARO-F7-AC14 | 사건 | 차량 화면에서 코스를 완주한 직후(주행 중이 아니고, 이어받을 사람이 아직 없고, 지정할 구성원이 있을 때) 앱은 이어받을 사람을 묻는 질문을 보여줍니다. | unit: 이어받을 사람 질문 단위 테스트("완주 비주행 미지정 구성원있음이면 물어본다"), 이어받을 사람 질문 단위 테스트("주행 중에는 물어보지 않는다") | - - | [코드 확인] |
| ARO-F7-AC15 | 원치 않는 상황 | PIN이 세션 메모리에서 사라진 뒤(앱을 다시 켠 경우 등) 코스를 완주하면 앱은 서버 호출을 시도하지 않고 곧바로 '스마트폰의 가족 관리에서 정해 주세요' 안내로 전환합니다. | unit: PIN 안내 전환 단위 테스트("가족에 연결됐고 PIN이 없으면 완주 직후 안내로 전환한다"), PIN 안내 전환 단위 테스트("PIN이 있으면 안내가 아니라 정책 경로다") | - - | [코드 확인] |

## F8 로컬 맛집·숙소 포인트 캐시백 (14개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F8-AC01 | 사건 | 영수증을 올리고 방문 연계·중복·한도 조건을 모두 통과하면 앱은 결제액의 1~2%를 포인트 캐시백으로 적립합니다(소수점 이하 버림). | server: 서버 시험 test_accepted_points_are_floored, 서버 시험 test_evaluate_receipt_pure_function | O O | [코드 확인] |
| ARO-F8-AC02 | 사건 | 맛집·가게 영수증의 결제일이 스탬프를 찍은 날이거나 그 다음 날이면 앱은 방문 연계가 있다고 보아 적립을 인정합니다. | server: 서버 시험 test_restaurant_same_day_accepted, 서버 시험 test_restaurant_next_day_accepted | O O | [코드 확인] |
| ARO-F8-AC03 | 원치 않는 상황 | 맛집·가게 영수증의 결제일이 스탬프를 찍은 날로부터 이틀 이상 지나면 앱은 방문 연계가 없다고 보아 적립을 거부합니다(사유: no_visit). | server: 서버 시험 test_restaurant_two_days_later_rejected, 서버 시험 test_wrong_partner_place_has_no_visit | O O | [코드 확인] |
| ARO-F8-AC04 | 사건 | 숙소 영수증은 체크인부터 체크아웃 사이(최대 3박)에 찍은 스탬프가 있으면 앱이 적립을 인정합니다. | server: 서버 시험 test_lodging_within_stay_accepted | O | [코드 확인] |
| ARO-F8-AC05 | 원치 않는 상황 | 숙소 영수증의 스탬프 날짜가 숙박 기간 밖이거나 숙박 일수가 3박을 넘으면 앱은 적립을 거부합니다(기간 밖은 no_visit, 3박 초과는 400 invalid_stay). | server: 서버 시험 test_lodging_outside_stay_rejected, 서버 시험 test_lodging_over_three_nights_rejected_400, 서버 시험 test_lodging_missing_stay_dates_400 | O O O | [코드 확인] |
| ARO-F8-AC06 | 항상 | 앱은 영수증 사진이 없는 제출을 받지 않습니다(400 missing_photo). | server: 서버 시험 test_receipt_without_photo_rejected_400 | O | [코드 확인] |
| ARO-F8-AC07 | 원치 않는 상황 | 이미 제출한 적 있는 사진(바이트가 같음)으로 다시 제출하면 금액을 바꿔도 앱은 적립을 거부합니다(사유: duplicate_photo). | server: 서버 시험 test_same_photo_resubmission_rejected, 서버 시험 test_receipt_key_uses_photo_hash, 서버 시험 test_photo_hash_is_sha256_of_raw_bytes | O O O | [코드 확인] |
| ARO-F8-AC08 | 사건 | 같은 제휴처에 같은 날 두 번째로 올리는 영수증은 앱이 바로 거부하지 않고 '확인 대기(pending)'로 저장하며 포인트는 적립하지 않습니다. | server: 서버 시험 test_second_receipt_same_partner_same_day_pending | O | [코드 확인] |
| ARO-F8-AC09 | 원치 않는 상황 | 결제 금액이 0원 이하이거나 100만 원을 넘으면 앱은 적립을 거부합니다(사유: invalid_amount). 100만 원은 상한 안에 포함되어 인정됩니다. | server: 서버 시험 test_amount_boundary | O | [코드 확인] |
| ARO-F8-AC10 | 상태 | 그날 이미 승인(accepted)된 영수증이 5건인 동안에는, 그날 처음 제출하는 제휴처 영수증이어도 앱이 더 이상 적립하지 않고 거부합니다(사유: daily_limit). | server: 서버 시험 test_daily_limit_after_five, 서버 시험 test_evaluate_receipt_pure_function | O O | [코드 확인] |
| ARO-F8-AC11 | 항상 | 가족의 포인트 캐시백 합계는 가족 구성원별 가중치 없이, 적립된 포인트를 그대로 더한 단순 합입니다. | code: 서버 포인트 조회 라우터(합계 조회 SQL — SUM(points), 가족 코드 단위) | - | (설계) |
| ARO-F8-AC12 | 선택 | 제휴처가 숙소인 경우 앱(스마트폰 화면)은 결제 시각 대신 체크인·체크아웃 날짜 입력칸을 보여 주고, 그 두 값으로 방문 연계와 숙박일수를 판정합니다. | code: 가족 모바일 웹 영수증 화면 소스(숙소 선택 시 입력칸 전환), 서버 라우터의 숙박 기간 검증 코드 | - - | (설계) |
| ARO-F8-AC13 | 항상 | 차량 화면에는 결제 금액이나 영수증 사진을 넣는 입력칸이 없습니다. 영수증 인증은 스마트폰 화면에서만 할 수 있습니다. | code: 차량 화면 문구 소스 주석(스탬프북·제휴처 화면 — 입력칸·결제 버튼 없음), 가족 모바일 웹 영수증 화면 소스(금액·날짜·사진 입력칸 전부 여기에만 있음) | - - | (설계) |
| ARO-F8-AC14 | 항상 | 결제·적립을 직접 보여주는 화면(차량 화면·스마트폰 화면)에는 '포인트 캐시백(예시)'이라는 말과 '현금이 아니다'라는 말이 함께 나옵니다. | code: 차량 화면 문구 소스 전수 검색(제휴처·포인트 관련 문구), 가족 모바일 웹 영수증 화면·동의 화면 소스 전수 검색 | - - | (설계) |

## F9 오프라인·대체 구현 (12개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F9-AC01 | 항상 | 앱은 차량 SDK 호출을 7개의 포트(인터페이스) 뒤로 감싸서만 씁니다. 화면·세션 코드가 SDK를 직접 부르지 않습니다. | code: 포트 정의 소스(인터페이스 7개: 주행 상태·경로 안내·도착 감지·음성 안내·차량 정보·차량 신호 조회·음성 편지 재생) | - | (설계) |
| ARO-F9-AC02 | 사건 | 앱을 시작하면 각 포트는 차량 SDK의 초기화 콜백을 기다리지 않고, 그 순간 확인 가능한 값만으로 바로 구현(대체 또는 차량 연동)을 선택합니다. | emulator: 설계 결정 기록 ADR-008 결과(콜드 부팅 직후 연속 실행 2회 실측 — 강제 종료 0건) | - | [확인] |
| ARO-F9-AC03 | 사건 | 경로 안내·음성 안내 포트는 대체 구현으로 기동한 뒤 차량 SDK 초기화가 늦게 성공하면 차량 연동 구현으로 전환하고, 이후에는 다시 대체 구현으로 돌아가지 않습니다. | unit: SwitchableRouteServiceTest.`3_재전환은 일방향 — 두 번째 switchTo는 전부 무시된다`, SwitchableNarratorTest.`전환은 일방향 - ready가 false로 되돌아가도 Pleos를 유지한다` | O O | [코드 확인] |
| ARO-F9-AC04 | 항상 | 진단 화면은 현재 켜져 있는 구현(차량 연동 또는 대체)을 '구현: ○○' 한 줄로 그대로 보여 줍니다. 실제로 쓰고 있지 않은 구현을 쓰고 있다고 표시하지 않습니다. | code: 진단 화면 소스(구현 이름을 그대로 그리는 줄) | - | (설계) |
| ARO-F9-AC05 | 원치 않는 상황 | 차량 신호(동승 인원 수·주행 거리)를 읽지 못하면 앱은 가짜 값을 채우지 않고 빈 값으로 남겨 해당 문장을 화면에서 숨깁니다. | unit: TripSessionTest.`10_tripSignals_읽기 실패시 tripKm과 partySize는 null로 남는다` | O | [코드 확인] |
| ARO-F9-AC06 | 사건 | 도착 스탬프가 기록되어 전송 대기열에 적재되면(enqueue), 앱은 그 즉시 1회 전송(flush)을 시도합니다. | unit: VisitQueueTest.`2_flush 성공시 큐가 비우고 전송 건수를 돌려준다`, 서버 시험(방문 기록 전송 처리 — 같은 분 멱등 판정) | O - | [코드 확인] |
| ARO-F9-AC07 | 항상 | 전송 대기열은 주기적으로 스스로 재시도하지 않습니다(따로 도는 타이머·주기 폴링이 없음). 그래서 서버가 없는 환경에서도 큐가 계속 혼자 돌지 않습니다. | unit: VisitQueueTest.`10_전송 실패 후 재시도 루프는 없다 — flush 1회당 시도도 1회다`, TripSessionTest.`16_도착 1건당 flush는 1회만 호출된다 — 실패해도 재시도 루프는 없다` | O O | [코드 확인] |
| ARO-F9-AC08 | 원치 않는 상황 | 전송 중 한 건이 실패하면 앱은 그 항목에서 멈추고, 그 뒤 항목들은 전송을 시도하지 않은 채 큐에 그대로 남깁니다(순서를 지키기 위함). | unit: VisitQueueTest.`3_flush 실패시 항목이 큐에 유지된다`, VisitQueueTest.`5_flush는 FIFO 순서를 지키고 부분 실패 후 이어서 재전송한다` | O O | [코드 확인] |
| ARO-F9-AC09 | 상태 | 큐가 비어 있는 동안에는 전송 시도(flush)가 아무 것도 보내지 않고 '전송 0건 성공'으로 바로 끝납니다. | code: 전송 대기열 소스(큐가 비면 바로 성공·0건을 돌려주는 분기) | - | (설계) |
| ARO-F9-AC10 | 선택 | 가족 세션에 PIN이 있는 경우 전송 대기열은 그 PIN을 실어 서버에 전송합니다. | unit: VisitQueueTest.`6_pin 공급자의 세션 PIN 이 X-Family-Pin 값으로 전달된다` | O | [코드 확인] |
| ARO-F9-AC11 | 원치 않는 상황 | 전송을 위해 받아 둔 PIN이라도 앱은 그것을 큐 파일에 적지 않습니다(메모리에만 잠깐 둠). PIN이 파일에 남는 일은 없습니다. | unit: VisitQueueTest.`7_enqueue 로 받은 PIN 은 파일에 저장되지 않고 전송에만 쓰인다` | O | [코드 확인] |
| ARO-F9-AC12 | 항상 | 코스·스탬프북·맛집 정보가 앱 안에 내장 데이터로 들어 있어서, 서버가 없어도 여정이 끝까지 이어집니다. 서버 호출이 실패해도 그 실패는 흡수되고 주행 흐름을 막지 않습니다. | unit: SeedRepositoryTest.missingStampBookAndPartners_defaultToEmptyLists, TripSessionTest.`8_routeService_호출 실패시 주행은 유지되고 한 줄 안내만 남는다` | O O | [코드 확인] |

## F10 화면 이동·메뉴·홈·설정 (23개)

| 번호 | 유형 | 수용 기준 | 확인 방법 | 실존 | 상태 |
|---|---|---|---|---|---|
| ARO-F10-AC01 | 항상 | 앱은 정차 화면과 시작 화면의 위쪽에 앱 아이콘·앱 이름·코스 부제로 이루어진 가이드의 머리말(Page Navigation)을 둡니다. | code: Components.kt PageNavigationBar 소스 코드(116~190행), Components.kt MenuButton 소스 코드(229행 — 머리글 trailingAction 슬롯용, F10 T2) | - - | [코드 확인] |
| ARO-F10-AC02 | 항상 | 정차 화면에서 앱은 스탬프·퀴즈·그때와 지금·스탬프북·오늘의 기록 5단계를 한 줄 세그먼트 머리말로 보여 줍니다. | code: ParkedComponents.kt ParkedPageNavigationBar 소스 코드 | - | [코드 확인] |
| ARO-F10-AC03 | 상태 | 가족 세션이 없는 동안 앱은 정차 신호가 와도 화면을 정차 화면으로 전환하지 않고 시작 화면(가족 코드 입력)을 그대로 유지합니다. | unit: ModeRouterResolveTest 단위 테스트("정차 상태에서 세션이 없으면 전환을 보류한다(Q28 - 시작 화면 유지)") | - | [코드 확인] |
| ARO-F10-AC04 | 사건 | 정차 중 설정 화면에서 [이 차에서 로그아웃]을 선택하면 앱은 릴리스 빌드에서도 로그아웃을 실행합니다. | unit: SignOutFlowTest.`1_가족 접속 상태에서 run하면 스탬프_퀴즈_코스_큐_파일이 모두 초기화된다`, SettingsScreen.kt 소스 코드([이 차에서 로그아웃] 버튼 — 주요 소스 세트) | O - | [코드 확인] |
| ARO-F10-AC05 | 원치 않는 상황 | 가족 세션이 없는 상태에서 메뉴 버튼을 누르면 앱은 패널을 열지 않고 가족 코드·PIN 입력 화면으로 안내합니다. | unit: MenuPolicyTest 단위 테스트("세션이 없으면 버튼이 없고 패널도 열리지 않는다"), MenuPolicyTest 단위 테스트("시작 화면이면 버튼이 없고 패널도 열리지 않는다") | - - | [코드 확인] |
| ARO-F10-AC06 | 사건 | 정차 중 [☰ 메뉴] 버튼을 누르면 앱은 화면 오른쪽에 홈·코스 선택·그때와 지금·설정 네 항목이 있는 패널을 엽니다. | unit: MenuPolicyTest 단위 테스트("정차와 세션이 있으면 메뉴 버튼이 보인다 — 창 방향과 무관"), MenuPolicyTest 단위 테스트("정차와 가로 창이면 패널이 열릴 때 본문 축소 모드다"), MenuPanel.kt 소스 코드(항목 4개·가족 블록·닫힌 채 시작) | - - - | [코드 확인] |
| ARO-F10-AC07 | 사건 | 정차 중 열려 있던 메뉴 패널이 있는 상태에서 주행으로 바뀌면 앱은 패널을 닫고 메뉴 버튼도 함께 감춥니다. | unit: MenuPolicyTest 단위 테스트("주행이 되면 메뉴 버튼이 사라지고 패널은 닫힌다") | - | [코드 확인] |
| ARO-F10-AC08 | 항상 | 메뉴 패널의 네 항목 이름은 앱 전체에서 홈·코스 선택·그때와 지금·설정(모두 한글)으로 통일합니다. | code: strings.xml 메뉴 항목 문자열(menu_item_home·menu_item_course·menu_item_thennow·menu_item_settings), MenuPanel.kt 소스 코드(항목 4개 렌더링) | - - | [코드 확인] |
| ARO-F10-AC09 | 상태 | 가족 세션이 있고 정차 중인 동안 앱은 홈을 첫 화면으로 보여 줍니다. | unit: HomeContentTest 단위 테스트("세션과 정차에 도착도 선택도 없으면 홈이 첫 화면이다"), HomeScreen.kt 소스 코드(HomeGate·HomeModeState·HomeGateState 홀더) | - - | [코드 확인] |
| ARO-F10-AC10 | 선택 | 진행 중인 코스가 있는 경우 홈은 스탬프 진행과 다음 장소를 담은 "이어서 하기" 카드를 보여 줍니다. | unit: HomeContentTest 단위 테스트("진행 중 코스가 있으면 이어서 하기 카드를 만든다"), HomeContentTest 단위 테스트("다음 목표가 없으면 이어서 하기 카드의 다음 장소 줄만 숨긴다") | - - | [코드 확인] |
| ARO-F10-AC11 | 선택 | 지난 방문 기록도 진행 중인 코스도 없는 경우 홈은 "오늘이 우리 가족의 첫 기록입니다" 한 줄로 시작합니다. | unit: HomeContentTest 단위 테스트("진행 중 코스가 없고 기록도 없으면 첫 기록 모드다"), strings.xml home_first_title 문자열(같은 문구) | - - | [코드 확인] |
| ARO-F10-AC12 | 사건 | 설정의 [가족 전환] 확인 화면에서 [로그아웃하고 바꾸기]를 선택하면 앱은 이 차량의 가족 코드·PIN·방문 기록·전송 대기 기록과 진행 중이던 코스·퀴즈 상태를 지우고 시작 화면으로 돌아갑니다. | unit: SignOutFlowTest.`1_가족 접속 상태에서 run하면 스탬프_퀴즈_코스_큐_파일이 모두 초기화된다`, SignOutFlowTest.`2_한 파일이 지워지지 않아도 나머지 정리는 계속되고 실패가 기록된다`, SignOutCleanup.kt 소스 코드(삭제 대상 파일 2개), FamilySwitchConfirmScreen.kt 소스 코드(지워져요 5항목·그대로 남아요 3항목·[로그아웃하고 바꾸기]) | O O - - | [코드 확인] |
| ARO-F10-AC13 | 원치 않는 상황 | 가족의 PIN이 5회 연속 틀리면 서버는 그 가족 전체를 10분 동안 잠그고, 앱은 설정·가족 전환 화면에 잠김 안내를 보여 줍니다. | server: config.py 상수(PIN_LOCK_ATTEMPTS=5, PIN_LOCK_MINUTES=10), security.py 잠금 판정 로직 | - - | [코드 확인] |
| ARO-F10-AC14 | 사건 | 설정에서 [가족 전환]을 누르면 앱은 이 차에서 지워지는 것과 그대로 남는 것을 먼저 보여 주는 확인 화면을 열고, [로그아웃하고 바꾸기]를 선택해야만 로그아웃을 실행합니다. | unit: SignOutFlowTest.`1_가족 접속 상태에서 run하면 스탬프_퀴즈_코스_큐_파일이 모두 초기화된다`, FamilySwitchConfirmScreen.kt 소스 코드([로그아웃하고 바꾸기] 주 버튼·[취소] — onConfirm·onCancel 콜백) | O - | [코드 확인] |
| ARO-F10-AC15 | 항상 | 릴리스 빌드에서도 앱은 설정 화면의 "이 차에서 로그아웃"을 통해 로그아웃 경로에 닿을 수 있게 합니다. | code: SettingsScreen.kt 소스 코드([이 차에서 로그아웃] 버튼 — 주요 소스 세트), SignOutFlow.kt 소스 코드(정리 7단계 — 진단 화면과 같은 흐름) | - - | [코드 확인] |
| ARO-F10-AC16 | 선택 | 둘러보기로 쓰고 있던 경우에는 [가족 전환] 확인 화면이 가족 코드·PIN 대신 "이 차에 남은 방문 기록"만 지워진다고 보여 줍니다. | none: 없음 | - | (계획) |
| ARO-F10-AC17 | 상태 | 정차 중 세로(1:1) 창에서 메뉴 패널이 열려 있는 동안 앱은 패널을 본문 위에 겹쳐 보여 주고 본문 폭을 줄이지 않습니다. | none: 없음 | - | (계획) |
| ARO-F10-AC18 | 항상 | 앱은 가족 코드를 화면에 보일 때 앞 2자리와 뒤 2자리만 보이고 가운데 자리는 가립니다. | unit: FamilyCodeMaskTest 단위 테스트("여덟 자리 코드는 앞 두 자리와 뒤 두 자리만 남긴다"), MenuPolicyTest 단위 테스트("가족 코드는 앞 두 자리와 뒤 두 자리만 남기고 가운데를 가린다"), MenuPanel.kt 소스 코드(마스킹 값으로만 코드 줄을 그림) | - - - | [코드 확인] |
| ARO-F10-AC19 | 항상 | 앱은 최근에 쓴 가족 코드의 목록을 저장하지 않습니다. | code: FamilySessionStore.kt 소스 코드(가족 코드 하나만 저장) | - | [코드 확인] |
| ARO-F10-AC20 | 사건 | 도착으로 정차 화면이 올라오면 앱은 홈이 아니라 스탬프 획득 카드를 먼저 보여 줍니다. | unit: HomeContentTest 단위 테스트("도착 직후에는 홈이 스탬프 카드를 가로채지 않는다"), HomeScreen.kt 소스 코드(HomeGateState — 도착 직후 기록 홀더) | - - | [코드 확인] |
| ARO-F10-AC21 | 상태 | 주행 중인 동안 앱은 설정 화면과 가족 전환 화면을 열지 않습니다. | unit: MenuPolicyTest 단위 테스트("주행이 되면 메뉴 버튼이 사라지고 패널은 닫힌다") | - | [코드 확인] |
| ARO-F10-AC22 | 항상 | 앱은 차량 시스템 프로필(상태바의 프로필)을 읽거나 저장하지 않습니다. | none: 없음 | - | (계획) |
| ARO-F10-AC23 | 선택 | 서버에 닿지 않는 경우 홈은 기기에 있는 값만으로 이어서 하기 카드를 보여 주고 오류 문구를 만들지 않습니다. | unit: HomeContentTest 단위 테스트("서버 응답이 없어도 거리 줄만 숨기고 오류 문구는 만들지 않는다") | - | [코드 확인] |

## 요약

| 기능 | 이름 | 수용 기준 | 유형 분포 | 상태 분포 | 시험 연결 |
|---|---|---|---|---|---|
| F1 | 도착 자동 스탬프 | 11 | 항상 1, 사건 5, 상태 1, 선택 2, 원치 않는 상황 2 | [확인] 2, (설계) 2, [코드 확인] 6, [일부 확인] 1 | 6 |
| F2 | 주행/정차 전환 | 14 | 항상 2, 사건 5, 상태 2, 선택 1, 원치 않는 상황 4 | [코드 확인] 7, [확인] 1, [일부 확인] 4, (설계) 2 | 6 |
| F3 | 박물관 퀴즈 | 10 | 항상 1, 사건 4, 상태 2, 선택 1, 원치 않는 상황 2 | (설계) 5, [코드 확인] 4, [확인] 1 | 4 |
| F4 | 그때와 지금 | 12 | 항상 2, 사건 4, 상태 1, 선택 2, 원치 않는 상황 3 | [코드 확인] 9, [확인] 1, (설계) 2 | 9 |
| F5 | 음성 해설·음성 편지 | 11 | 항상 2, 사건 3, 상태 1, 선택 1, 원치 않는 상황 4 | (설계) 7, [코드 확인] 3, [실패] 1 | 3 |
| F6 | 가족 코드·PIN·세션 | 14 | 항상 3, 사건 4, 상태 2, 선택 1, 원치 않는 상황 4 | [코드 확인] 7, (설계) 6, [확인] 1 | 6 |
| F7 | 가족 기록 서버·모바일 웹·물려주기 | 15 | 항상 2, 사건 5, 상태 1, 선택 1, 원치 않는 상황 6 | (설계) 1, [코드 확인] 14 | 13 |
| F8 | 로컬 맛집·숙소 포인트 캐시백 | 14 | 항상 4, 사건 4, 상태 1, 선택 1, 원치 않는 상황 4 | [코드 확인] 10, (설계) 4 | 10 |
| F9 | 오프라인·대체 구현 | 12 | 항상 4, 사건 3, 상태 1, 선택 1, 원치 않는 상황 3 | (설계) 3, [확인] 1, [코드 확인] 8 | 8 |
| F10 | 화면 이동·메뉴·홈·설정 | 23 | 항상 7, 사건 6, 상태 4, 선택 4, 원치 않는 상황 2 | [코드 확인] 20, (계획) 3 | 15 |

시험 이름 점검: 확인 대상 88건 중 없음 0건, 점검 제외 140건.

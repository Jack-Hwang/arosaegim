# ADR — 아로새김 설계 결정 기록

> 코드와 docs/DEV_LOG.md의 실제 결정만 기록한다. 근거 파일 경로는 `arosaegim/` 기준 상대경로다.
> 상태: 채택 = 현재 코드에 반영됨, 구현 중 = 코드 반영까지 끝나 에뮬레이터 실측만 남은 것(각 항목의 F·Q 번호 참조).

## ADR-001 수동 DI 조립기(AppContainer)

- 상태: 채택
- 맥락: Pleos SDK와 대체 구현이 포트마다 2벌씩 있고, 선택은 초기화 콜백·capability 확인 같은 런타임 조건에 달려 있다. Hilt 같은 프레임워크는 의존성 주입 프레임워크 미사용 요구(DEV_LOG F13 — 공식 샘플 구성 비교 포함)로 배제됐다.
- 기각한 대안: Hilt 등 의존성 주입 프레임워크(맥락의 미사용 요구와 공식 샘플 간 구성 차이 — DEV_LOG F13)·ServiceLocator 전역 맵(선택 사유를 진단 화면에 노출할 방법이 없다).
- 결정: Application 수명의 `AppContainer`가 포트별 구현을 직접 선택·조립한다. 선택 사유는 `routeSwitchNote`·`narratorSwitchNote`로 진단 화면에 노출한다. — android/app/src/main/java/app/arosaegim/AppContainer.kt, ArosaegimApp.kt
- 결과: 조립 경로가 한 파일에 모여 선택 사유를 사람이 읽을 수 있다. 대신 포트 추가 시 AppContainer 수정이 따른다.

## ADR-002 TripSession 도착 파이프라인 순서

- 상태: 채택
- 맥락: 도착 시 스탬프·기록·음성·다음 목표 감시가 한 이벤트에서 겹친다. 순서가 어긋나면 중복 스탬프·이탈 감시 같은 부작용이 생긴다.
- 기각한 대안: 각 Activity 수명에 파이프라인을 두는 방식(백그라운드 전환 시 감시 상실 — 앱 수명 세션 채택 근거가 같은 줄에 있다)·도착 이벤트를 큐에 적재 후 비동기 소비(순서 보장이 깨져 중복 스탬프 가능 — TripSessionTest가 순서를 고정한다).
- 결정: arrivals → StampEngine(순서·중복 규칙) → VisitStore 기록 → 도착 음성 → 다음 목표 watch 재등록 순서로 고정하고, 세션 수명은 Application과 동일하게 한다(어떤 Activity도 stop하지 않는다). — android/app/src/main/java/app/arosaegim/session/TripSession.kt
- 결과: 첫 도착 스탬프 1회·기록 1건·음성 1회, 재도착·순서 위반 무시가 TripSessionTest(14케이스)로 증명됐다(DEV_LOG F25·F49).

## ADR-003 PIN은 세션 메모리에만 보관(휘발성)

- 상태: 채택
- 맥락: 가족 코드 보안 요구(코드는 식별용·열람과 수정에는 PIN 추가 — DEV_LOG F59)는 차량 화면에 코드를 크게 띄우지 말 것을 정한다. 차량 기기에 PIN을 영속 저장하면 탈거·공유 차량에서 유출 위험이 있다.
- 기각한 대안: EncryptedSharedPreferences 등 암호화 영속 저장(재입력 없음의 편의는 있으나 탈거·공유 차량 유출 위험은 남고 PIN 영속 저장 자체가 금지된다 — DEV_LOG F59)·PIN 없는 코드 단독 접속(열람·수정 권한이 코드만으로 열린다 — ADR-010 잠금 규칙과 짝이 된다).
- 결정: PIN은 `SessionCredentials`(companion — 프로세스 수명 메모리)에만 보관하고 SharedPreferences·파일·로그 경로는 두지 않는다. 앱 재시작 시 소실되어 재입력을 요구한다. — android/app/src/main/java/app/arosaegim/data/FamilySessionStore.kt:44,167-174
- 결과: PIN 없는 세션에서는 서버 전송이 401로 보류되고(VisitQueue 보류 — 설계 동작), 재입력 한 번으로 기록이 이어진다(DEV_LOG F59·F78).

## ADR-004 VisitQueue 멱등키: 장소|코스|분|가족

- 상태: 채택
- 맥락: 오프라인 큐는 재적재·재전송이 잦다. 같은 스탬프가 여러 번 서버에 쌓이면 기록이 오염된다.
- 기각한 대안: UUID 완전 랜덤 멱등키(재시도마다 키가 바뀌어 중복 전송을 막지 못한다)·서버 단독 중복 판정 의존(오프라인 재전송에서 클라이언트 방어가 없어 큐가 부풀고 서버 부하가 늘어난다 — 서버 같은-분 판정과 이중 방어로 채택).
- 결정: 멱등키를 `placeId|courseId|방문 시각(분 단위 내림)|familyCode`로 정의하고, 같은 키는 enqueue에서 중복 적재를 막고 전송 성공 시 큐와 메모리 PIN을 함께 치운다. — android/app/src/main/java/app/arosaegim/data/VisitQueue.kt:126-130,264-271
- 결과: 서버 같은-분 멱등 판정(test_visits.py test_same_minute_is_idempotent)과 함께 이중 방어가 된다. VisitQueueTest(9케이스)가 실패 시 순서 보장·재시도를 검증한다.

## ADR-005 주행 상태 병합: 더 제한적인 쪽 우선 + 기어 P 보류 해제

- 상태: 채택(병합 규칙·기어 P 5초 지속 시 주행 해제 타임아웃 — Q26, 2026-11-13 구현)
- 맥락: CarUxRestrictions(주요)와 기어(Pleos Gear 폴링, 보조)가 어긋나는 구간이 실측됐다(Q26 — D→P 직후 DO 잔존 1회).
- 기각한 대안: 기어 신호 단독 판정(DO 수신 경로를 무시 — OFFICIAL 수신 경로와 어긋난 때의 안전 우선이 깨진다, DEV_LOG F42)·DO 단독 판정(에뮬레이터에서 DO 미수신 구간이 실측됐다 — Q26, 기어 폴링 보조가 필요하다).
- 결정: 두 신호가 어긋나면 더 제한적인 쪽(주행)을 우선한다. 기어 P·속도 0 지속 시 주행 해제 보류(`parkReleaseHoldMs`)는 F81로 코드에 반영됐고 `DrivingStateMergerTest`가 규칙을 검증한다. — android/app/src/main/java/app/arosaegim/domain/DrivingStateMerger.kt, app/CombinedDrivingState.kt
- 결과: CombinedDrivingStateTest(3)·DrivingStateMergerTest(8)가 병합 규칙을 검증하고, 정차 자동 전환은 실측 PASS다(DEV_LOG F72). 보류 해제 타임아웃의 실측은 미완료다.

## ADR-006 ModeRouter 전환: NEW_TASK+REORDER_TO_FRONT + 세션 게이트

- 상태: 채택
- 맥락: 주행·정차 화면은 Activity를 오가는 DO 화면 전환이다. Application 컨텍스트 startActivity는 NEW_TASK가 없으면 예외가 났고 catch에 묻혀 전환이 조용히 실패했다(E2E 감사 E3). 또 새 설치에서 첫 PARKED 수집이 시작 화면(가족 코드 입력)을 덮는 문제가 실측됐다(Q28).
- 기각한 대안: Application 컨텍스트 startActivity에 NEW_TASK만 쓰는 방식(감사 E3 — 예외가 catch에 묻혀 전환이 조용히 실패했다)·세션 게이트 없는 무조건 자동 전환(새 설치에서 시작 화면이 덮였다 — Q28·DEV_LOG F80).
- 결정: 전환 intent는 `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_REORDER_TO_FRONT`로 고정하고 실패를 진단 '마지막 오류'에 기록한다. 정차로의 자동 전환은 `sessionExists()` 게이트를 통과할 때만 하고, 진단 `forceBringTo`는 게이트를 우회한다. 순수 판정은 `resolveTarget`으로 분리해 JVM 테스트한다. — android/app/src/main/java/app/arosaegim/ui/ModeRouter.kt:31,64-74
- 결과: E3 회귀가 ModeRouterResolveTest(4케이스)로 잡히고, 콜드 스타트에서 시작 화면이 유지됨이 실측됐다(DEV_LOG F80, q28_verify.png).

## ADR-007 Narrator 3단 폴백: 사전 제작 → Pleos 합성 → Android TTS

- 상태: 채택
- 맥락: 에뮬레이터에서 Pleos TTS와 Android TTS가 모두 실패해 소리가 나지 않았다(F54·U6). A1은 "사전 제작 오디오를 기본 경로로" 요구하고(DEV_LOG F64), 떠난 가족의 목소리를 AI로 재생성하지 않는다는 원칙이 있다.
- 기각한 대안: 합성 음성으로 떠난 가족의 목소리를 재현(금지 원칙 위반 — ADR-007 맥락)·TTS 성공까지 앱 시작을 막는 초기화(콜드 스타트 교착을 재현한다 — ADR-008)·음성 없이 화면 문구만(운전 중 화면 최소화 원칙과 어긋난다 — 주행 안내는 청각 경로가 본질).
- 결정: 1단(기본) AssetAudioNarrator — assets 오디오 파일명↔키 매핑 테이블로 재생, 매핑이 없으면 생략(가짜 금지). 2단 PleosTtsNarrator — onReady(초기화) 확인 후에만. 3단 AndroidTtsNarrator — 콜드 스타트 시 SwitchableNarrator로 시작해 늦은 onReady에 Pleos로 일방향 전환. — android/app/src/main/java/app/arosaegim/AppContainer.kt:346-352, app/fallback/AssetAudioNarrator.kt, app/SwitchableNarrator.kt
- 결과: TTS 부재 환경에서도 매핑된 문장은 소리로 난다. SwitchableNarratorTest(5)가 전환 규약을 검증하고, 실제 청취는 U6·Q21로 남는다.

## ADR-008 RouteService 선택의 Switchable 일반화(콜드 스타트 교착 해소)

- 상태: 채택
- 맥락: 포트 선택을 `runBlocking { withTimeoutOrNull(3s) }`로 기다리던 구조가 릴리즈 콜드 스타트에서 영구 파킹으로 이어져 시스템이 앱을 강제 종료했다(dropbox ANR 3건 — DEV_LOG F61).
- 기각한 대안: 초기화 대기 시간을 3초→10초로 늘리는 방식(실측에서 영구 파킹이 재현됐다 — 대기 시간과 무관, DEV_LOG F61)·런타임 스레드에서 runBlocking 유지(메인 스레드 차단은 그대로 남는다)·초기화 실패 시 앱 종료(시드로 끝까지 동작해야 한다는 원칙과 어긋난다).
- 결정: Application.onCreate는 대기 없이 동기 즉시 판정(Fake 등 대체 구현)으로 통과시키고, 늦은 초기화 성공 시 `SwitchableRouteService`로 일방향 업그레이드한다. 이 패턴을 Narrator(ADR-007)로도 일반화했다. — android/app/src/main/java/app/arosaegim/SwitchableRouteService.kt, AppContainer.kt:50-59
- 결과: 콜드 부팅 직후 첫 실행 2회 연속 TotalTime 8.1~8.5초·ANR 0(실측). SwitchableRouteServiceTest(8)가 전환·되돌림 없음을 검증한다.

## ADR-009 가족 코드 6~8자리 허용

- 상태: 채택
- 맥락: 서버는 8자리 코드를 발급한다(형식 예시: A3K7Q9M2 — DEV_LOG F58). 앱 초창기 웹 가정은 6자리였고, 서버 규격만 받아들이면 구형 입력·수동 전사와 어긋난다.
- 기각한 대안: 검증기를 8자리로만 강제(구형 6자리 입력·수동 전사가 거부된다 — 맥락)·형식 검사 생략(빈 값·특수문자가 그대로 서버로 전송된다 — FamilySessionStoreTest가 검증한다).
- 결정: `FamilyCodeValidator`는 6~8자리 영숫자(대소문자 허용)를 유효로 본다. 공백·특수문자·한글은 거부하고 앞뒤 공백도 잘라내지 않는다. — android/app/src/main/java/app/arosaegim/data/FamilySessionStore.kt:24-29
- 결과: 웹 문구와 서버 발급(8자리)·검증(6~8자리)이 일치한다. FamilySessionStoreTest(16)·백엔드 test_family.py가 코드·PIN 발급 경로를 함께 검증한다.
- 안내 문구는 서버 발급 형식(8자리)을 따르고, 검증은 구형 6자리 입력 호환을 위해 6~8자리를 허용한다.

## ADR-010 백엔드 PIN: 솔트 SHA-256 + 5회 실패 10분 잠금

- 상태: 채택
- 맥락: 요구는 PIN 추가·5회 실패 10분 잠금·코드 재발급이다(잠금·재발급 상수는 backend/app/config.py). 평문 저장은 물론, 무제한 재시도는 코드만 아는 제3자의 접근을 열어 둔다.
- 기각한 대안: PIN 평문 저장(맥락의 유출 위험 그대로)·무제한 재시도 허용(코드만 아는 제3자가 계정을 여는다)·잠금 중 코드 재발급도 차단(잠긴 차량 사용자가 영구히 막히는 사례가 생긴다 — 재발급 허용이 탈출 경로).
- 결정: PIN은 세트별 무작위 솔트를 붙인 SHA-256 해시(`salt$digest`)로만 저장하고 비교는 `hmac.compare_digest`로 한다. 5회 연속 실패 시 `locked_until` = 현재+10분을 기록하고 잠금 중에는 올바른 PIN으로도 429로 막으며, rotate-code만 잠금 상태에서도 재발급을 허용한다. — backend/app/security.py:16-26,56-78,109-115, backend/app/config.py:27-28
- 결과: test_family.py test_pin_locks_after_five_failures·test_pin_lock_records_locked_until_in_future(2케이스)가 잠금과 locked_until 미래 시각을 검증한다.

## ADR-011 포인트 건당·일일 상한

- 상태: 채택
- 맥락: 캐시백은 시뮬레이션이지만 규칙 남용(과대 금액·대량 제출)은 데모 신뢰성을 무너뜨린다 — 금액·횟수 상한이 요구된다(상수는 services/points.py).
- 기각한 대안: 금액 상한 없음(과대 영수증 입력으로 지표가 왜곡된다 — 맥락)·제휴처별 상한(데모 범위에서 검증할 제휴 계약이 없어 근거를 만들 수 없다 — 단일 상한으로 충분)·판정 규칙을 라우터에 인라인(순수 함수 분리가 사라져 경계 테스트가 어려워진다 — test_points.py 3케이스).
- 결정: 0 < 금액 ≤ 1,000,000(`MAX_AMOUNT_KRW`)을 벗어나면 rejected(invalid_amount), 가족당 하루 accepted 5건(`DAILY_LIMIT`) 초과는 rejected(daily_limit)다. 판정 순서(금액→중복→방문→대기→한도)는 evaluate_receipt 한 곳에 고정한다. — backend/app/services/points.py:16-17,37-49
- 결과: test_points.py test_amount_boundary·test_daily_limit_after_five·test_evaluate_receipt_pure_function(3케이스)이 경계와 순서를 검증한다(DEV_LOG F47).

## ADR-012 서버 계약은 스네이크 케이스(E1 수정)

- 상태: 채택
- 맥락: 앱이 카멜케이스 필드·PIN 헤더 누락으로 방문 전송이 서버에 닿지 않았다(E2E 감사 E1 — DEV_LOG F61). 서버는 규격대로 동작하고 있었다.
- 기각한 대안: 서버가 카멜케이스를 받아들이게 변경(서버가 규격대로 동작했고 다른 클라이언트(웹)가 snake_case로 이미 동작한다 — DEV_LOG F61 서버 쪽 대조)·전송 필드를 문자열 조립으로 처리(타입 안전성과 직렬화 계약 테스트가 사라진다).
- 결정: 앱 전송 DTO를 서버 규격(snake_case)에 맞춘다. `@SerialName`으로 place_id·course_id·visited_at·vehicle_label 등을 선언하고, X-Family-Code+X-Family-Pin 헤더를 항상 실으며, 응답 visit_id(정수)·stamp_no를 파싱하고, `wireJson`(encodeDefaults=false·ignoreUnknownKeys)으로 null 생략 계약을 유지한다. — android/app/src/main/java/app/arosaegim/data/ApiClient.kt:21-35,82-110
- 결과: VisitPayloadContractTest(4)가 계약을 회귀 검증하고, 이후 DTO 변경은 이 테스트가 먼저 잡아낸다.

## ADR-013 Room 대신 파일 저장(visits.json·visit_queue.json)

- 상태: 채택
- 맥락: 오프라인 기록은 방문 목록·전송 큐 두 종류뿐이고 관계·질의 요구가 없다. 차량 앱 특성상 가족 기록의 주인은 서버고, 로컬은 전송 대기 분이다(가족 기록은 서버에 저장 — DEV_LOG F65).
- 기각한 대안: Room 등 로컬 데이터베이스(기록 두 종류·관계·질의 요구가 없어 스키마·마이그레이션 부담만 남는다 — 맥락)·SharedPreferences 저장(목록 데이터 직렬화에 부적합하고 삭제 시점 관리가 두 갈래로 나뉜다)·서버 단독 저장(오프라인 도착에서 기록이 소실된다 — 오프라인-first 원칙).
- 결정: 로컬 저장은 filesDir 아래 단일 JSON 파일 두 개로 충분하다 — VisitStore는 visits.json(좌표 저장 없음), VisitQueue는 visit_queue.json. 로그아웃 시 SignOutCleanup이 두 파일을 삭제한다. — android/app/src/main/java/app/arosaegim/data/VisitStore.kt:28-45,113, data/VisitQueue.kt(FILE_NAME), data/SignOutCleanup.kt:21-25
- 결과: 스키마·마이그레이션 부담 없이 내용 확인이 쉽다. SignOutCleanupTest(4)·VisitQueueTest(9)가 삭제·복원을 검증한다(DEV_LOG F65).

## ADR-014 Pleos SDK 미확인 동작은 포트 인터페이스 뒤에 숨긴다

- 상태: 채택
- 맥락: "확인 안 된 SDK 동작을 사실처럼 구현하고 보고하지 말 것"이라는 원칙이 있다(DEV_LOG F1~F12 실측 기록). SDK 콜백 지연·미수신이 실측으로 반복 확인됐다(U2·U6·Q13).
- 기각한 대안: SDK 호출을 화면·세션 코드에 직접 사용(콜백 지연·미수신이 확인될 때까지 대체 경로가 없어 실패 원인을 격리할 수 없다 — 맥락)·추측한 시그니처로 선구현(문서에 없는 메서드를 사실처럼 보고하게 된다 — 맥락 원칙 위반).
- 결정: SDK 호출은 7개 포트(DrivingStateProvider·RouteService·ArrivalDetector·Narrator·VehicleInfoProvider·TripSignalProvider·VoiceLetterPlayer) 인터페이스 뒤로 감싼다. pleos/ 구현과 fallback/ 대체 구현을 2벌로 둔다. 대체 구현이 쓰이는 동안 진단 화면·꼬리표에 '구현: Pleos/대체'를 표시하고 추측한 시그니처를 코드에 쓰지 않는다(AAR javap 확인 — DEV_LOG F1~F12). — android/app/src/main/java/app/arosaegim/ports/Ports.kt, app/pleos/, app/fallback/
- 결과: SDK가 불안정해도 앱 핵심 흐름이 끝까지 동작하고, 표기가 실제 사용 구현과 일치한다(대체 구현으로 찍은 영상을 실측처럼 설명하지 않는다).

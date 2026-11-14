# ARCHITECTURE — 아로새김 차량 앱 설계 (PT 설명 자료)

> 2차 PT에서 발주자가 소리 내어 읽고 설명하기 위한 문서. 문단을 짧게 썼다.
> "확인됨"이라고 쓴 것은 `docs/DEV_LOG.md`의 확인된 사실(F번호)이나 단위 테스트로 뒷받침된 내용이다. 추측 서술은 없다.
> 작성일: 2026-11-10 · 갱신: 2026-11-11 (에뮬레이터 v2.1.0 실측 반영) · 앱: 아로새김(`app.arosaegim`) versionName 0.1.0 · 검증 환경: Pleos Connect 에뮬레이터 v2.1.0

---

## 1. 한 페이지 요약 — 무엇을, 왜, 어떻게

**무엇을 만들었나.**
부모의 차에서 시작된 가족 여행의 발자취를 문화유산 위에 아로새기는 차량 앱.
차 안에서는 스탬프를 찍고, 정차하면 가족이 함께 퀴즈와 '그때와 지금'을 본다.

**왜 차량 앱인가 — 기록의 주인은 가족(00문서 1절).**
기기도 차도 아니라 '가족'이 기록의 주인이다.
차가 바뀌어도(새 차, 해외 렌터카) 가족 코드로 같은 기록에 이어지고,
다음 세대에게 넘겨줄 수 있어야 한다. 이 원칙이 모든 설계 결정의 기준이다.

**어떻게 만들었나 — 세 가지 설계 원칙.**
첫째, SDK 호출은 모두 어댑터(포트) 인터페이스 뒤에 숨겼다. SDK 상태가 불확실해도 앱 전체가 멈추지 않는다.
둘째, 앱은 서버 없이 내장 시드만으로 핵심 흐름이 끝까지 동작한다.
셋째, 도착 판정·스탬프·퀴즈 상태 같은 규칙은 안드로이드 의존 없는 순수 Kotlin으로 분리해
271개 단위 테스트로 검증했다(DEV_LOG 테스트 기록).

**실측까지 끝냈다.**
Pleos Connect 에뮬레이터 v2.1.0에서 스모크 테스트 U1~U14를 실측했다.
U1~U5·U8·U9·U13이 통과했고, 근접 알림(120m, 미터 단위)과 기어 조회(GEAR_PARK)는
실제 신호 수신으로 확인됐다(6절 요약표·DEV_LOG 표).

**핵심 가치는 '그때와 지금(Then & Now)'.**
스탬프·퀴즈·오디오 해설·유물 타임라인·음성 편지는 모두 이 가치를 떠받치는 기능이다.

---

## 2. 모듈 지도와 데이터 흐름

### 2-1. 모듈 지도 (`android/app/src/main/java/app/arosaegim/`)

```
ArosaegimApp.kt        Application 진입점 — AppContainer·ModeRouter 조립, 세션 시작
AppContainer.kt        수동 DI 조립기(DI 프레임워크 없음) — 포트별 구현 선택(대기 3초, F35), 사유 기록
session/TripSession.kt 여행 세션 — 도착→스탬프→기록→음성→다음 목표 파이프라인
                       + tripKm(T5-1: 시작/완주 Odometer 차이)·partySize(T6-2: 좌석 점유 합계, F45)
domain/                순수 Kotlin 규칙 (Android 의존 없음, 단위 테스트 대상)
  Models.kt            Place·Course·QuizItem·DrivingState·ThenNow·TripSignals·VehicleInfo 등
  Haversine.kt         위경도 구면 거리(미터)
  StampEngine.kt       스탬프 규칙: 장소당 1회, 코스 순서, 중복 무시
  StampBook.kt         스탬프북: 진행률(내림), 완성 배지, 정의 외·중복 무시
  ModeStateMachine.kt  PARKED↔DRIVING 모드 상태기계(동일 신호 무시)
  CourseProgress.kt    현재 목표·다음 목표·완주 계산
  ThenNowResolver.kt   그때와 지금 해석(여러 과거→가장 오래된 것, label 우선) / VoiceLetter.kt(T9-2)
ports/Ports.kt         7개 포트(DrivingStateProvider·RouteService·ArrivalDetector·Narrator·
                       VehicleInfoProvider·TripSignalProvider·VoiceLetterPlayer)
pleos/                 Pleos SDK 구현체: FusedArrivalDetector·PleosNaviRouteService·PleosTtsNarrator·
                       PleosTripSignalProvider·PleosVoiceLetterPlayer·UxRestrictionsDrivingState
fallback/              대체 구현체: ManualDrivingState·FakeRouteService·ManualArrivalDetector·
                       AndroidTtsNarrator·ProfileVehicleInfo·FallbackTripSignalProvider·FallbackVoiceLetterPlayer
data/
  SeedRepository·SeedModels   assets/seed/seed.json 로드(코스·퀴즈·스탬프북·제휴처·heritage·seat_roles)
  ApiClient            HttpURLConnection 기반 REST 클라이언트(외부 HTTP 라이브러리 없음, 로그 기록 없음)
  VisitStore           filesDir/visits.json — 로컬 방문 기록(좌표 없음: 장소 id + 시각만)
  VisitQueue           filesDir/visit_queue.json — 오프라인 전송 큐(FIFO, flush 실패 시 보존)
  FamilySessionStore   가족 코드·'둘러보기' 플래그만 저장 — PIN은 저장하지 않는다(2-3 참고)
ui/
  ModeRouter.kt        상태→화면 라우팅(FLAG_ACTIVITY_REORDER_TO_FRONT)
  session/             StartActivity(가족 코드 접속·'가족 없이 둘러보기')·StartScreen
  common/              AroColors(주·야간 토큰 19개)·Theme·ThemeState(주·야간 토글)·공통 컴포넌트
  driving/             DrivingActivity(DO 선언)·DrivingScreen·Diagnostics(9항목 진단·스모크 U1~U14)·SmokeTests
  parked/              ParkedActivity(DO 미선언)·ParkedScreen(스텝0~5)·CourseSelectScreen(스텝0)·QuizState·ThenNowExtras
```

### 2-2. 데이터 흐름 (텍스트 다이어그램)

```
[시작] ArosaegimApp.onCreate (메인 스레드, Application 수명)
   ├─ StartActivity(릴리스 흐름): 가족 코드 입력 → FamilySessionStore.signIn
   │     또는 '가족 없이 둘러보기'(guestMode — 기록은 이 차에만). PIN은 입력받지 않고 저장도 안 한다.
   ├─ AppContainer: 포트별 구현을 이 순서로 선택하고 사유를 adapterNotes에 기록 (실측 결과 = 5절 표)
   ├─ ModeRouter ← DrivingStateProvider.state 수집
   │     DRIVING → DrivingActivity / PARKED → ParkedActivity (같은 태스크, 최상단화)
   └─ TripSession (Application 수명 — 어떤 Activity도 stop하지 않음)
         도착 이벤트 arrivals 수신
           ├─ 1차: FusedArrivalDetector 근접 알림 ENTER (실측 PASS — 120m, F48)
           ├─ 2차: 근접 알림 실패 시 Haversine 폴백 (거리 ≤ 반경)
           └─ 디버그: ManualArrivalDetector(진단 화면 '수동 도착') — 같은 규칙 적용
         → (2) StampEngine.registerArrival: 순서 위반·재도착은 null로 무시
         → (3) CourseProgress.markStamped: 다음 목표·완주 갱신
         → (4) VisitStore.append: filesDir/visits.json (좌표 없음 — 장소 id + 시각만)
         → (4b) VisitQueue.enqueue + flush 시도: 실패는 큐에 보존(offline-first), 화면 한 줄 안내
         → (5) Narrator.speak("○○에 도착했어요. 스탬프를 찍었어요.") — 인원을 읽으면
               "오늘은 N명이 함께 왔어요."를 붙인다(F45). 음성 1회
         → (6) 다음 목표 watch 재등록 + T7 내비 경로 재요청(fire-and-forget), 완주 시 감시 중지
         (이 파이프라인은 단위 테스트로 검증됨 — F25)
   T5-1: start() 시 odometerKm 스냅샷 → 완주 순간 재조회 → 차이 = tripKm. 한쪽이라도 null이면 null(F45)
   T6-2: start() 시 partySize() 1회 조회(좌석 점유 합계). null이면 문장·타일을 숨긴다(F55)

[시드] assets/seed/seed.json → SeedRepository(kotlinx.serialization) → 코스·퀴즈·스탬프북·제휴처·heritage·음성 편지
[서버] http://10.0.2.2:8000 (ApiClient) — 방문은 X-Family-Code 헤더로 POST /v1/visits.
       백엔드 라우터 11개: families·courses·places·visits·memories·rewards·members·stamps·points·metrics·preferences
       + ratelimit(기기당 코드 생성 하루 1회·IP 상한), services: then_now·points(순수 함수)·photos·album
[웹]   /web 정적 서빙 — 7화면(가족코드·동의·코스·그때 기록·타임라인·가족 관리·영수증)
```

### 2-3. UI와 도메인의 경계

- 화면(Compose)은 TripSession·SeedRepository가 노출하는 StateFlow만 구독한다.
- 규칙(스탬프 1회, 순서, 진행률, 그때 해석)은 전부 domain/에 있고 화면은 표시만 한다.
- 색 16진수는 `AroColors.kt` 한 파일에만 존재하고 화면은 토큰만 쓴다(F27).
- **PIN은 휘발성이다.** 가족 코드는 식별용, 열람·수정은 PIN용(02문서 9절)이라 앱은 코드만 저장하고
  PIN은 어디에도 남기지 않는다(FamilySessionStore 규약). 로그아웃하면 코드·방문 기록·전송 큐가 함께 지워진다.

---

## 3. 주행/정차 전환은 어떻게 일어나는가

**신호의 출처 — 실측됨.**
Pleos SDK가 주행 규제 상태를 별도로 제공하지 않아, 안드로이드 오토모티브 표준 API인 `CarUxRestrictionsManager`를 쓴다.
이 경로는 Pleos 공식 문서의 수신 경로와 일치하며(01문서 9절), 에뮬레이터 v2.1.0에서
**매니저 획득·리스너 등록이 실제로 성공했다**(F29·U7 전반부).
리스너에서 `isRequiresDistractionOptimization == true`이면 DRIVING, 아니면 PARKED로 본다.
restrict 값 수신은 기어 P→D 전환(Virtual Console)이 필요해 사용자 세션에서 완결 예정이다.

**이 방식을 선택한 이유.**
차량 플랫폼이 운전자 방해 금지(DO)를 요구하는 순간이 곧 '화면을 최소화해야 하는 순간'이다.
우리 앱의 관심사(퀴즈·긴 텍스트 금지)와 이 신호가 정확히 겹치기 때문이다.
기어(Gear) 신호는 보조 소스로 두며, 실측에서 `GEAR_PARK`가 클러스터 P와 일치하게 수신됐다(F53).
어긋나면 더 제한적인 쪽(주행)으로 둔다.

**실패 시의 동작.**
`start()`가 실패하면(Car 서비스 부재 등) 상태는 PARKED를 유지하고 오류 문자열만 남긴다.
이때 AppContainer는 `ManualDrivingState`(데모 토글)로 폴백하며, 화면에는 '데모 모드' 꼬리표가 붙는다.
파생 상태를 만들지 않는 것이 원칙이다.

**상태기계·화면 전환 — 실측됨.**
`ModeStateMachine`은 입력(DrivingState)을 UI 모드(UiMode)로 바꾸고, 같은 값의 반복 입력은 무시한다.
`ModeRouter`는 DRIVING이면 `DrivingActivity`, PARKED이면 `ParkedActivity`를
`FLAG_ACTIVITY_REORDER_TO_FRONT`로 같은 태스크 안에서 최상단으로 올린다.
Driving↔Parked 3회 전환·복귀·상태 유지가 실측됐다(U9 PASS).

**DO 선언의 역할.**
`DrivingActivity`만 매니페스트에 `distractionOptimized=true`를 선언했고 `ParkedActivity`는 선언하지 않았다.
주행 중에는 시스템이 이 화면을 자동으로 가린다. 즉 '주행 중 퀴즈 금지'는 앱 코드가 아니라 플랫폼 규칙으로 강제된다.

**퀴즈 상태 보존.**
주행 시작으로 화면이 가려지면 시스템이 ParkedActivity 인스턴스를 파괴할 수 있다.
그래서 퀴즈 진행(문항 인덱스·답안)은 Activity에 두지 않고 프로세스 수명의 싱글톤 `QuizStateHolder`에 둔다.
정차로 복귀하면 새 인스턴스가 홀더 값을 읽어 이어서 보여 준다.

**세션 수명.**
TripSession은 Application과 같은 수명으로 산다(ArosaegimApp.onCreate에서 1회 시작).
어떤 Activity도 세션을 stop하지 않는다. 백그라운드에 내려가도 도착 감시가 유지된다.

---

## 4. 도착은 어떻게 판정하는가

**1차 — 근접 알림(proximity alert) — 실측 PASS.**
`FusedArrivalDetector.watch(target)`이 목표 장소 좌표와 반경으로 `ProximityAlertRequest`를 등록한다.
반경 단위는 Float 미터로 확인됐고(F6), 실측에서도 120m 알림이 `geo fix` 주입 좌표에서
ENTER 전이를 수신해 확정됐다(F48·U3 PASS). 모드는 haversine 폴백이 아니라 proximity다.
ENTER 전이만 도착으로 판정하고, 이전 watch의 alertId로 온 늦은 알림은 무시한다.

**2차 — Haversine 폴백.**
근접 알림 등록이 실패하거나 onError가 오면(권한 미부여 등)
위치 콜백(`registerFusedLocationCallback`)으로 받은 좌표로
`Haversine.distanceM ≤ arriveRadiusM`을 직접 계산해 진입/이탈을 판정한다.
"거리가 반경 이내(≤)면 도착"이 경계 계약이며, 이 값 판정은 호출자(FusedArrivalDetector)가 한다.

**위치는 저장하지 않는다.**
위치 콜백은 근접 알림 성공/실패와 무관하게 항상 등록하는데, 이는 주행 화면의
"약 N.Nkm" 거리 표시를 위해서다(실측 — "약 1.9km" 렌더링 확인, F49).
수신 위치는 메모리(StateFlow)에만 존재하고 파일·서버·로그에 남지 않는다(00문서 2-2 §7).

**순서·중복 규칙 — StampEngine.**
도착 이벤트가 와도 바로 스탬프가 찍히지 않는다.
① 현재 코스 목표가 아니면 무시(순서 위반), ② 이미 찍은 장소면 무시(재진입 중복),
③ 완주 후의 도착 신호도 무시한다. 장소당 1회만 스탬프가 찍힌다.
실측에서도 첫 도착에 스탬프가 정확히 1회 찍히고 다음 목표로 진행됐다(F49).
이 규칙은 단위 테스트로도 검증됐다(F25·F26).

**내비 도착 이벤트는 보조다 — 실측으로 정당화됨.**
`onDestinationArrived` 콜백의 `DestinationArrivedInfo`에는 placeId가 없다(duration·distance·name·address뿐 — AAR 확인, F8).
실측에서도 geo fix 3회 이동 후에도 미발생이었고, 이는 에뮬레이터의 [REPORTED] 동작과 일치한다(F57).
그래서 도착 판정의 본역은 FusedLocation이고, 내비 이벤트는 오면 `Arrived(placeId=null)`로만 보고한다.

**수동 도착(디버그)과 도착 후 화면.**
진단 화면의 '수동 도착' 버튼은 `ManualArrivalDetector`로 같은 파이프라인을 탄다.
주행 중이므로 도착 시 화면 전환·팝업 없이 TripSession.lastEventLine 한 줄만 바뀌고(3초 표시)
음성 한 문장이 낭독된다.

---

## 5. 어댑터(포트)를 둔 이유와 실제 선택 결과

**왜 어댑터를 뒀나.**
Pleos SDK의 동작을 확인되기 전까지 사실처럼 쓰면 안 된다(00문서 2-3 §11).
포트 인터페이스 뒤에 Pleos 구현과 대체 구현을 함께 두면,
SDK가 초기화에 실패해도 앱은 멈추지 않고 폴백으로 핵심 흐름을 이어간다.
또한 스모크 테스트(U1~U14)가 각 구현을 직접 검증할 수 있다.

**선택 규칙.**
AppContainer는 시작 시 Pleos 구현을 먼저 시도하고, 초기화 실패 시 폴백을 고른다.
포트당 초기화 대기는 최대 3초다(F35 실측 반영 — NaviHelper 콜백이 1.5초보다 늦었다).
선택 결과와 사유를 `adapterNotes`에 기록해 진단 화면 ① 섹션에 표시한다.
AppContainer는 반드시 Application.onCreate(메인 스레드)에서 생성한다.

**실측 선택 결과 (에뮬레이터 v2.1.0, 2026-11-11).**

| 포트 | 실측 선택 결과 | 선택 로직과 사유 |
|---|---|---|
| DrivingState | `UxRestrictionsDrivingState` — **등록 성공**(F29) | 표준 CarUxRestrictions. start() 실패 시에만 `ManualDrivingState` 폴백 |
| RouteService | **`FakeRouteService`** | NaviHelper 초기화 콜백이 3초 대기보다 늦음(F50·Q15). 경로 호출 자체는 U1/U2에서 직접 검증 PASS — poiId=""로 requestRoute 접수 성공 |
| Narrator | **`AndroidTtsNarrator`** | Pleos TTS 초기화는 성공했으나 onReady 미수신(F54·Q13/Q17). Android TTS도 이 에뮬레이터에서 초기화 실패(엔진·데이터 부재 추정) |
| VehicleInfo | `ProfileVehicleInfo` (항상) | CarInfo는 VIN/CRN만 제공(01문서 10절·F4)이고 VIN은 규정상 읽지 않으므로(00문서 2-2 #8), 차종·연식은 수동 프로필이 실질 유일 경로 |
| TripSignals | **`PleosTripSignalProvider`** | Seat 합산 + Odometer 조회 등록(F50). 점유 미조작 시 partySize=null→문장 숨김(F55·U11 설계 동작), odometer는 0.0 km 수신(트립 채널 — F56·U13 PASS) |
| VoiceLetterPlayer | **`FallbackVoiceLetterPlayer`** | 예시 오디오 파일(assets) 미준비 — isAvailable=false, 텍스트 편지 표시(Q14) |

**대체 구현이 쓰이는 동안의 표시.**
대체 구현(Manual/Fake)이 선택되면 Diagnostics와 화면 꼬리표에 그 사실을 표시한다.
대체 구현으로 찍은 영상을 실제 SDK 동작처럼 설명하지 않는다(00문서 11-2).

**종료 순서.**
closeAll()은 세션 수집 → 리스너 해제 → release 순서를 지킨다.
SDK 콜백이 끝나기 전에 release하지 않기 위함이다.
VisitStore·VisitQueue는 진단 화면의 로그아웃에서만 지운다 — 가족 기록은 차량에 오래 두지 않는다.

---

## 6. 스모크 테스트 실측 요약 (U1~U14, 에뮬레이터 v2.1.0)

실측 방법: 진단 화면을 `input tap`으로 직접 조작(좌표는 스크린샷 측정).
위치 주입은 `adb emu geo fix`. 상세 결과는 `docs/DEV_LOG.md`의 U1~U14 표가 원본이다.

| 결과 | 항목 | 요지 |
|---|---|---|
| **PASS 8건** | U1 | requestRoute poiId=""·poiSubId=""·poiProvider="" 호출 접수 성공(예외 없음·Error 미수신) |
| | U2 | NaviHelper 초기화 ok·initError 없음 |
| | U3 | 근접 알림 proximity 등록 + ENTER 수신 + 도착 이벤트 발행 — **반경 단위 미터 실측 확정**(F48) |
| | U4 | FusedLocation.isSupported = true |
| | U5 | VIN capability READ(확인만·값 미읽음 — 규정), 기어 GEAR_PARK 실수신(F53) |
| | U8 | 에뮬레이터→호스트 10.0.2.2:8000/health → 200 |
| | U9 | Driving↔Parked 3회 전환·복귀·상태 유지 |
| | U13 | odometerKm()=0.0 km 수신 — 트립 채널로 보임, T5-1 계산에 적합(F56) |
| **FAIL 2건** | U6 | 음성 — 원인 2건 확정: ①Android TTS 초기화 실패(0ms·엔진·데이터 부재 추정) ②Pleos onReady 미수신(F54·Q13·Q16·Q17) |
| | U11 | **FAIL=설계 동작** — partySize()=null → 문장 숨김(가짜 금지 원칙). Virtual Console 좌석 조작 후 재실행이 완결(F55) |
| **부분** | U7 | 매니저 획득·리스너 등록 성공(OFFICIAL 경로). restrict 수신은 기어 P→D 필요(F29) |
| | U10 | onDestinationArrived 미발생 관찰([REPORTED]와 일치) — 도착 판정 본역이 FusedLocation인 설계 정당화(F57) |
| **고정** | U12·U14 | 문·배터리 — '참고, 사용 안 함'(로드맵 밖)으로 진단 카드 고정 |

---

## 7. T6 확장과 신규 기능 (시안 14종 기준)

**코스 선택 화면(스텝0 — 시안 02).** `CourseSelectScreen`. 스탬프가 0개이고 코스를 시작하지 않았으면
정차 화면이 먼저 이 화면을 보여 준다. 코스 카드·출발 버튼으로 시작 전 게이트를 두고, 출발 시 TripSession이 시작된다.
'세계' 탭은 '준비 중' 표시만 둔다(구현 대상 아님).

**오늘의 기록 화면(스텝5 — 시안 09).** 코스 완주 시 자동으로 켜진다. 요약 타일 2×2(스탬프·인원·거리·장소)이며
값이 없는 타일은 숨긴다. 거리는 tripKm(T5-1), 인원은 partySize(T6-2)다.

**유물의 시간 × 가족의 시간(T6-1).** '그때와 지금' 카드 아래 한 줄 타임라인(정적·애니메이션 없음).
시드 `heritage` — 예: 성덕대왕신종(주조 771년, 통일신라 · '공식 자료로 검수 필요' 표기 유지).

**함께 온 인원(T6-2).** 정차 시 Seat 점유 신호로 인원 수를 읽어
"오늘은 N명이 함께 왔어요. 그때는 M명이었어요."를 화면 한 줄 + 음성 한 문장으로 낸다(F45).
**인원 수만** 쓰고 좌석별·사람별 정보는 저장하지 않는다. 안 읽히면 문장을 숨긴다(F55).

**음성 편지(T9-2).** 시드의 예시 편지 1건을, 해당 장소에 도착해 **정차했을 때만** 재생 버튼으로 연다.
주행을 시작하면 재생이 멈추고 버튼이 사라진다. 화면에 '예시' 표기. 실제 녹음 기능은 만들지 않는다(로드맵).
예시 음성 파일은 아직 미준비라(Q14) 재생 포트가 폴백(isAvailable=false)이고 텍스트 편지를 보여 준다.

**오늘의 자리 이름표(T9-3).** 모바일 웹에서 자리(운전석/조수석 길잡이/뒷좌석 탐험가)를 고르면
차량 정차 화면에 이름표로 표시한다. 서버 연동 — `POST /v1/families/me/members/{id}/seat-role`
및 구성원 등록 시 seat_role 필드(02문서 10절). 값이 없으면 표시하지 않는다.

**지표(T12+).** `GET /v1/families/me/metrics` — 스탬프북 완성률, 그때와 지금 열람 수, 재방문 간격,
영수증 인증 수, 물려주기 지정 여부 등을 값 + source(출처)로 응답한다. 가족 단위만, 운영자 전체 집계 없음.
앱 진단 화면에도 로컬 카운터가 있다(그때와 지금 열람 수 등).

**숙소 캐시백 규칙(00문서 12·13절 — 백엔드 services/points 순수 함수, pytest로 검증).**
적립 포인트 = floor(금액 × 적립률), 적립률은 제휴처별 고정값(1%/2%), 확률·랜덤 없음.
인정 기간: 식당·가게 = 스탬프 당일 + 다음 날 / **숙소 = 숙박 기간(체크인~체크아웃, 최대 3박)**.
영수증 **사진 필수**, 중복 판정은 제휴처+날짜+금액+**사진 해시**(같은 사진 재제출 거부).
같은 제휴처·같은 날 두 번째는 `pending`(확인 대기 — 적립 0·저장은 함).
기기(브라우저 토큰)당 가족 코드 생성 하루 1회(429 device_limit), IP 상한도 병행(ratelimit).
표기는 **"포인트 캐시백(예시)"** — 현금 환급으로 읽히는 문구(환급·입금)는 쓰지 않는다.

---

## 8. 알려진 한계

- **이 에뮬레이터에서 안내 음성이 나지 않는다(F54).** Android 플랫폼 TTS 초기화 실패(엔진·한국어
  데이터 부재 추정 — Q16) + Pleos TTS onReady 미수신(Q17)의 겹침이다. 환경 한계일 가능성이 있어
  시연 영상은 오디오 별도 저장 절차(03문서)로 대비한다.
- **내비 초기화 콜백이 3초를 넘는다(F50·Q15).** RouteService만 Fake로 선택되지만, 경로 호출 자체는
  U1/U2에서 직접 검증 PASS라 기능 커버는 끝났다. 선택 대기 확대 vs 직접 검증 커버 중 정리 방향은 Q15.
- **내비 위치 추종·onDestinationArrived 미발생([REPORTED]·F57).** 도착 판정 본역이 FusedLocation인
  설계가 실측으로 정당화됐다(4절). 시연은 drive-demo-route.ps1과 수동 도착 버튼으로 대비한다.
- **Seat 점유를 조작하지 않으면 인원이 숨겨진다(F55·U11).** 설계 동작이다(가짜 값 금지).
  Virtual Console 'Seat Occupancy' 조작 후 재실행이 U11 완결이다.
- **Odometer는 0.0(트립 채널)으로 보인다(F56).** T5-1의 시작~완주 차이 계산과는 적합하나,
  단위 km 가정은 Virtual Console 조작 후 재확인을 권장한다.
- **경유지 미사용.** 경유지는 최대 2개 + 좌표 문제로 쓰지 않고, 구간마다 requestRoute를 다시 호출한다(00문서 2-3 #13).
- **SDK가 주는 것의 한계.** CarInfo는 VIN/CRN 조회만 제공한다(01문서 10절). 차종·연식은 수동 프로필로만 표시한다.
- **TTS onDone 누락 대비.** onDone이 오지 않을 수 있다는 가정(Q4)으로 speak 대기에 5초 타임아웃을 뒀다.
- **선행 묵음(150~200ms) 미구현.** PCM/WAV 패딩으로만 가능해 TTS 텍스트로는 삽입할 수 없어 구현하지 않았다.
- **음성 편지 예시 오디오 미준비(Q14).** 발주자가 합성음성 또는 직접 녹음으로 준비하면
  `assets/seed/letter_demo.ogg` 넣는 순간 재생 포트가 활성화된다. 고인의 목소리를 AI로 만들지 않는다.
- **세로 창·야간 테마 실측(D5·D6)과 그때 기록 연도(Q9 검수)는 남아 있다.** 시드 years_ago=32는
  임시값이며 화면은 label("1990년대 중반")을 우선 표시한다.
- **앱 카테고리 미확정(Q11).** 11절의 후보와 방침대로 설계는 두 카테고리 범위를 넘지 않는다.

---

## 9. 예상 기술 질문 18개와 답변

**Q1. 왜 폰이 아니라 차인가?**
차에서만 되는 세 가지가 이 앱의 본질이기 때문이다. ①**도착 자동 스탬프** — 차량 위치 신호로 목적지
도착을 감지해 손을 쓰지 않고 기록이 남는다(폰은 화면을 켜고 앱을 열어야 한다). ②**좌석·주행거리 신호** —
함께 온 인원과 오늘 함께 달린 거리를 차가 알려 준다. ③**주행 중 제약** — 화면을 DO로 선언하고
퀴즈·입력을 막는 등 차량 플랫폼의 안전 규칙을 그대로 받는다. 가족이 차에 타는 순간이 여행 기록의
시작점이고, 그 기록을 차가 자동으로 채워 준다.

**Q2. 좌석 신호를 다른 팀도 쓰는데 무엇이 다른가?**
용도가 다르다. 다른 출품작은 좌석 신호로 조명·시트 연동 같은 '지금의 편의'를 만든다.
우리는 좌석 신호를 **세대 간 인원 비교에만** 쓴다 — "오늘은 4명이 함께 왔어요. 그때는 5명이었어요."
한 문장을 만들기 위한 점유 좌석 수 합계뿐이다. 좌석별·사람별 정보는 저장하지 않고, 읽지 못하면
문장을 통째로 숨긴다(가짜 값 금지). 기록이 남는 것은 인원 숫자 하나뿐이다.

**Q3. 에뮬레이터에서 안 되는 것은 무엇이고 어떻게 대비했나?**
세 가지다. ①**음성** — Android TTS 초기화 실패 + Pleos onReady 미수신으로 이 에뮬레이터에서
안내 음성이 나지 않는다(F54). 시연 영상은 오디오 별도 저장 절차로 대비하고, 음성 실패는 앱 흐름을
막지 않게 흡수한다. ②**내비 위치 추종** — [REPORTED]대로 geo fix 이동에 내비가 따라오지 않고
onDestinationArrived도 미발생이다(F57). 그래서 도착 판정 본역을 FusedLocation 근접 알림으로
만들었다(실측 PASS — F48)하고 시연은 drive-demo-route.ps1 보간 주행을 쓴다. ③**기어·좌석 조작** —
restrict 수신·좌석 점유는 Virtual Console 조작이 필요해 사용자 세션에서 완결한다(U7·U11).

**Q4. 왜 FusedLocation으로 직접 도착을 판정하나? 내비 도착 이벤트는 안 되나?**
내비 `onDestinationArrived` 콜백에는 placeId가 없다(확인됨 — AAR, F8).
어느 장소에 도착했는지 식별 불가이므로, 도착 판정 본역을 우리가 만들고(00문서 2-3 #14)
내비 이벤트는 보조로만 둔다. 근접 알림(1차) → Haversine 폴백(2차) 순서로, 반경 단위는 미터로
실측까지 확정됐다(F6·F48).

**Q5. 주행 중 규정은 어떻게 지키나?**
네 겹으로 막는다. ① DrivingActivity만 DO를 선언해 주행 중 사용을 허용받고,
② ParkedActivity는 DO 미선언이라 주행 중 시스템이 화면을 자동으로 가린다.
③ 주행 화면은 퀴즈·입력·애니메이션 없이 텍스트를 maxLines로 강제해 3줄을 넘기지 않는다.
④ 조작은 버튼 1개("이야기 다시 듣기")뿐이다. UX 제한 신호(CarUxRestrictions)로 DRIVING/PARKED를 판정한다.

**Q6. 오프라인에서는 어떻게 동작하나?**
코스·퀴즈·스탬프북·맛집은 APK에 내장한 시드(JSON)에서 읽고, 서버 없이 스탬프→기록→음성→그때와 지금까지 끝까지 돈다.
시드 로드가 실패해도 빈 코스로 복구해 크래시하지 않는다(SeedRepository 규약).
서버 동기화(방문 큐)는 오프라인 큐 + 멱등 키 설계로 구현 완료했다(T8 — VisitQueue·ApiClient).

**Q7. 개인정보(위치·VIN·사진)는 어떻게 다루나?**
위치는 도착 판정과 화면 거리 표시에만 쓰고 저장·전송·로그 기록하지 않는다.
수신 위치는 메모리(StateFlow)에만 존재하고 감시 종료 시 버린다.
서버로는 장소 ID와 시각만 보낸다(구현 완료). VIN은 읽지도 저장하지도 전송하지도 않는다(00문서 2-2 #8).
사진은 가족 모바일 웹에서만 받고(EXIF 제거·저장 전), 차량 화면은 금액·사진 입력을 받지 않는다.

**Q8. VIN은 왜 안 쓰나? CarInfo로 차종·연식은 못 얻나?**
규정상 VIN을 읽지 않기로 했고(00문서 2-2 #8), 실제로도 얻을 이유가 없다.
Vehicle 2.3.2의 CarInfo 인터페이스를 AAR로 확인한 결과
VIN/CRN capability와 조회 메서드뿐이고 제조사·연식 getter는 존재하지 않는다(F4·01문서 10절).
그래서 차종·연식은 수동 프로필(ProfileVehicleInfo)이 실질 유일 경로이며, 기록에는 라벨만 남긴다.

**Q9. 왜 어댑터(포트)를 두었나?**
SDK 동작이 불확실해도 앱이 멈추지 않게 하기 위해서다(00문서 2-3 #10).
포트 하나에 Pleos 구현과 대체 구현을 두고, 시작 시 초기화 성공 여부로 선택해 사유를 기록한다(5절 표).
부작용으로 순수 Kotlin 규칙과 SDK 결합이 분리되어 단위 테스트가 쉬워졌다(271개).

**Q10. JSON 라이브러리로 kotlinx.serialization을 쓴 이유는?**
org.json은 JVM 단위 테스트에서는 스텁이라 시드 파싱 테스트(SeedRepository)를 돌리려면
실제로 동작하는 파서가 필요하다. kotlinx.serialization은 테스트에서도 실제 파싱이 되고,
DTO에 @SerialName을 명시해 키 오탈자를 컴파일 단계에서 잡아준다.
실제로 이 어노테이션 누락 버그를 테스트가 잡아냈다(DEV_LOG F8 해소 이력).

**Q11. Haversine 반경 경계는 어떻게 판정하나?**
"거리 ≤ 반경이면 도착"이다. Haversine.distanceM은 거리 값만 계산하고,
경계 판정은 호출자(FusedArrivalDetector 폴백 모드)가 한다.
이 계약을 단위 테스트(Haversine·StampEngine)가 검증한다.
근접 알림이 정상 동작할 때는 이 계산을 하지 않는다 — 이중 도착을 막기 위해서다.

**Q12. 도착 음성은 언제 나오나?**
스탬프가 실제로 등록된 직후 1회다. 도착 이벤트가 와도 순서 위반·재도착이면 무시되고
음성도 나지 않는다(TripSession 파이프라인 5단계 — F25).
문장은 "○○에 도착했어요. 스탬프를 찍었어요." 한 문장이고, 인원을 읽었으면
"오늘은 N명이 함께 왔어요."가 붙는다(F45). 화면은 바뀌지 않는다(주행 중이므로).
음성 실패는 흡수하며 주행 흐름을 막지 않는다.

**Q13. 퀴즈 상태는 어떻게 보존하나?**
주행 시작으로 ParkedActivity가 시스템에 가려지면 인스턴스 파괴가 가능하다.
그래서 진행 상태(스텝·문항 인덱스·답안)를 프로세스 수명의 싱글톤 QuizStateHolder에 둔다.
정차 복귀 시 REORDER_TO_FRONT로 Activity가 재생성돼도 홀더 값을 읽어 이어서 푼다.
같은 장소면 진행을 보존하고, 장소가 바뀌었을 때만 초기화한다.

**Q14. 백그라운드 복귀는 어떻게 대응하나?**
TripSession은 Application 수명이라 어떤 Activity도 stop하지 않는다.
백그라운드에 내려가도 도착 감시·스탬프 진행이 유지되고, 복귀하면 그대로 보인다.
Back 키는 finish() 대신 moveTaskToBack(true)로 태스크를 보존해 재진입 비용을 줄인다.
세션 stop()은 진행 상태를 남기고 감시만 끊어, start()로 이어 갈 수 있다.

**Q15. 멱등 키 설계는?**
방문 기록 중복 전송을 막는 서버 멱등 처리다(02문서 4절).
키는 (family_code, place_id, course_id, visited_at 분 단위) 조합이다.
오프라인에서 로컬 큐(filesDir/visit_queue.json, FIFO)에 쌓고, 서버가 응답하면 즉시 전송,
실패 시 재전송한다. 전송 완료된 방문은 차량에서 지워 '차량에 개인 기록을 오래 두지 않는' 원칙을 지킨다.
이 부분은 구현 완료됐다(T8 — VisitQueue·ApiClient, U8 실측 PASS).

**Q16. StampBook 규칙은?**
스탬프북은 코스·권역 묶음이다(시드 stamp_books).
정의에 없는 장소의 스탬프는 무시하고, 중복 스탬프도 무시한다.
진행률은 '찍은 수 ÷ 정의 장소 수 × 100'을 내림한다(1/3 → 33).
정의 장소를 전부 찍으면 완성이며 earnedBadge가 배지 이름을 돌려준다.
빈 정의는 완성으로 보지 않는다. 전부 단위 테스트로 검증됐다(F26).

**Q17. 포인트 캐시백은 왜 시뮬레이션인가?**
제휴 계약·적립 재원·정산·법규 검토가 선행돼야 하는 영역이기 때문이다(02문서 6절).
이번 범위는 적립 과정을 끝까지 보여 주되 전부 시뮬레이션이다.
적립률은 제휴처별 고정값(1%/2%), 추첨·확률형 보상 없음,
방문 연계 규칙(식당·가게 당일+익일, 숙소 숙박 기간), 사진 해시 중복 거부, 같은 날 두 번째는 pending을 둔다.
모든 화면·응답에 '포인트 캐시백(예시)'를 표기하고 실제 결제·포인트 연동 코드는 만들지 않는다.

**Q18. Back 키는 어떻게 처리하나?**
모든 화면(주행·정차·시작)에서 finish() 대신 `moveTaskToBack(true)`로 태스크를 백그라운드로 보존한다.
차량 환경에서 finish()는 태스크를 파괴해 SDK 재연결·초기화 비용이 크고
주행 중 화면 전환 실패는 안전 문제가 되기 때문이다.
이 규칙은 각 Activity의 OnBackPressedCallback으로 구현했다.

---

## 10. 실행·테스트 방법

### 10-1. 빌드와 단위 테스트 (에뮬레이터 불필요)

```bash
cd android
./gradlew clean testDebugUnitTest assembleDebug
# 기대 결과: BUILD SUCCESSFUL, 단위 테스트 271/271
#  SeedRepository·CourseProgress·Haversine·ModeStateMachine·StampBook(9)·StampEngine(6)
#  ThenNowResolver(12)·TripSession(+tripKm/partySize 10케이스)·FamilyCodeValidator 등
```

- APK 위치: `android/app/build/outputs/apk/debug/app-debug.apk`
  (aapt2 확인: package `app.arosaegim`, versionName `0.1.0`, 라벨 '아로새김')
- 릴리스 APK: `apk/arosaegim_아로새김_release.apk`(39.6MB) — apksigner verify 통과
  (CN=arosaegim, package app.arosaegim v0.1.0)
- 빌드 조합: Gradle 8.10.2(wrapper) + AGP 8.8.2 + Kotlin 2.0.0 + JDK 21, compileSdk/targetSdk 35, minSdk 28 (F18·F3)

### 10-2. 백엔드 실행과 웹 접속

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --port 8000      # backend/ 디렉터리에서
pytest                                # 기대: 51/51 (then_now·멱등·숙소 기간·사진 필수·pending·device_limit 등)
```

- 웹은 백엔드가 같이 서빙한다: 브라우저 `http://localhost:8000/web/` (7화면).
- 에뮬레이터 앱은 `http://10.0.2.2:8000`으로 접속한다(U8 실측 PASS — F34).

### 10-3. 에뮬레이터 실행 (Pleos Connect v2.1.0)

1. AVD: Automotive + Pleos Connect v2.1.0, **Cold boot 필수**, RAM 4096MB 이상 (절차는 `docs/EMULATOR_SETUP.md` — Android Studio 없이도 구축 가능, `docs/DEV_LOG.md` F28)
2. 설치: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. 권한: `powershell ./tools/grant-permissions.ps1 -Package app.arosaegim`
   (dangerous 권한 UI 크래시 방지 — `pm grant --user 10` 사용)
4. 시작 화면(가족 코드·둘러보기)과 진단 화면(Diagnostics)에서 어댑터 선택 결과(① 섹션)·U1~U14 버튼 확인

### 10-4. 스모크 실측 방법 (U-tap)

- 진단 화면의 스모크 버튼을 `adb shell input tap X Y`로 직접 조작한다(좌표는 스크린샷에서 측정 — 11/11 세션 방식).
- 위치 주입: `adb emu geo fix <lon> <lat>` (U3·U10).
- 기어 전환·좌석 점유·주행거리 조작은 Virtual Console: `adb forward tcp:8088 tcp:8088` 후 브라우저 `http://127.0.0.1:8088/`.

### 10-5. 가족 코드·PIN 흐름

1. 웹(`http://localhost:8000/web/`)에서 가족 코드 발급(기기당 하루 1회 — 429 device_limit).
2. 차량 앱 시작 화면(StartActivity)에서 코드 입력 → 세션 연결. '가족 없이 둘러보기'도 가능하다.
3. PIN은 열람·수정용(02문서 9절)이고 **앱에는 저장되지 않는다**(휘발성 — 웹에서 입력하는 자리만 둔다).
4. '이 차에서 로그아웃' 시 가족 코드·방문 기록·전송 큐가 모두 지워진다.

### 10-6. 데모 주행 (tools/)

```bash
./tools/drive-demo-route.ps1 -Route tools/routes/gyeongju-demo.json  # 데모 코스 보간 주행(경주 3곳)
./tools/record-demo.ps1   # 화면 녹화(최대 3분) — 소리는 별도 WAV 저장 후 합친다(README '시연 녹화' 절)
```

- route 파일은 `tools/routes/gyeongju-demo.json`. 좌표는 시드와 같은 `coord_verified=false` 대략값이다.

---

## 11. 앱 카테고리 후보 (미확정 — Q11)

**배경.** 00문서 10.2: 주행 중 허용 예시가 문서에 있는 카테고리는
Maps/Navigation, Music/Audio, Mobility Services뿐이다.
아로새김 주행 화면이 어느 카테고리로 허용되는지는 아직 확정되지 않았다.

**후보 1 — Maps/Navigation.** 주행 화면의 본질이 '다음 목적지 안내 + 남은 거리'이므로 경로 안내 정보의 예시 범위에 해당한다.

**후보 2 — Music/Audio.** 장소 이야기를 오디오 해설로 전달하고, 조작은 '이야기 다시 듣기' 1개 버튼이므로
오디오 재생과 단순 조작의 예시 범위에도 해당한다.

**방침.** 어느 쪽으로 확정되든 주행 화면은 두 카테고리의 허용 예시 범위(경로 안내 정보, 오디오 재생과 단순 조작)를
넘지 않게 설계했다. 확정은 플랫폼 문서의 공식 기준 확인 후 DEV_LOG(Q11)에 기록한다.


---

## agent — Gleo Interface 수신 (2026-11-14)

앱은 `assets/schema.json`(App Schema)에 도구 3개를 선언한다: `GET_NEXT_STAMP_PLACE`(다음 스탬프 장소 안내)·`START_QUIZ`(마지막 도착 장소의 퀴즈 시작 — 정차 전용)·`GET_TODAY_SUMMARY`(스탬프·거리·인원 요약). 호출 수신은 `agent/GleoActionService`(실제 음성 경로용 서비스)와 `agent/GleoActionReceiver`(adb 검증 경로용 리시버)가 같은 계약을 받는다 — 수신 Intent의 extra 키는 `functionName`(원문 미확인 — 응답 계약과의 대칭 가정)이다.

판정·문장 조립은 `agent/AgentTools.kt`(순수 Kotlin — Context 없음)가 맡는다: `AgentAppSnapshot`으로 앱 상태(`TripSession`·`DrivingStateProvider`·`FamilySessionStore`·`CourseStartState`·`QuizStateHolder`의 **읽기 전용** 값)를 한 묶음으로 받아 분기표대로 `AgentCallResult`(status·영어 한 문장)를 돌려준다. 응답은 broadcast 액션 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`에 `functionName`·`status`·`message`·`mode` extra로 보낸다 — 이 액션의 수신 리시버가 시스템(`ai.umos.ambientai`)에 등록돼 있는 것을 dumpsys로 확인했다.

가드: 허용 목록 밖 거절(이중 방어 — 서비스·dispatch)·`START_QUIZ`는 주행 중 fail(정차 전용 기능)·응답 한 문장 120자 이내·개인정보(코드·PIN·구성원 이름·좌표) 비포함·모든 읽기는 StateFlow 동기 읽기라 7초 게이트 안에서 끝난다. "확인 뒤 실행"(asking) 가드는 다중 턴 프로토콜이 미확인이라 보류했다 — 성공 조건을 만족하면 즉시 실행한다.

`GET_NEXT_STAMP_PLACE`은 장소 이름만 답한다(거리 계산 API가 없다 — 위치는 도착 판정에만 쓴다, ADR-006). `GET_TODAY_SUMMARY`는 퀴즈를 언급하지 않는다(누적 카운터가 없다 — 없는 숫자는 만들지 않는다). 실제 음성 호출(Gleo가 schema를 읽어 호출하는 것)은 승인 전이라 [미확인]이다.

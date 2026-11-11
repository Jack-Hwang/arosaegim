# DEV_LOG

> 작업 로그. 최신이 위. 스모크 테스트 결과표(U1~U10)를 맨 위에 유지한다.
> 날짜 시각은 로컬(KST) 기준.

## 2026-11-10 (심야) — Pleos 에뮬레이터 CLI 구축 + 실기동 첫 스모크

- 한 일:
  - **에뮬레이터를 CLI로 끝까지 구축해 실제 부팅·앱 실행까지 완료** — Android Studio 없이 가능함을 확인
  - 백엔드(T8+T10)·웹(T9+T10·진행 중)·앱 동기화(T7+T8)·차량 세션(T9-1)·문서(T12) 병렬 구현 통합
- 결과/증거:
  - 구축: emulator 37.2.13 설치 → `crp_car_emulator.zip`(4,087,751,964B) 다운로드 → **sha1 일치**(b4f858d5…) →
    `system-images/android-34/pleos_car_emulator/x86_64/` 배치 + package.xml 작성 → AVD **PleosConnect** 생성
    (기기 프로필 `Pleos_Connect_v2_0` — 이미지에 42dot OEM 프로필 포함, Cold boot 강제·RAM 4096MB)
  - 부팅: **70초 만에 sys.boot_completed=1**, 로그 "Windows Hypervisor Platform accelerator is operational" —
    **WHP 활성화 후 재부팅 없이 WHPX 동작 확인**
  - 시스템 확인: `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0`, `ai.pleos.playground.service.location.fused`·`service.navi` 등 설치됨
  - 앱: `adb install -r` **Success** → `pm grant --user 10 FUSED_LOCATION` → DiagnosticsActivity 실행 → **프로세스 생존·크래시 0**
  - 화면 캡처: `C:\tools\android-sdk\.pleos-download\app_launch5.png` (2560×1440)
  - 백엔드: **pytest 28/28 통과**(then_now·멱등·업로드 거부·EXIF 제거·PIN 잠금·포인트 규칙·export ZIP)
  - 앱: **단위 테스트 70/70 통과**(SeedRepository 7·VisitQueue 5·FamilyCodeValidator 5·TripSession 8·StampBook 9·ThenNowResolver 12·기타)
- 확인된 사실(F-번호):
  - **F28** Pleos 에뮬레이터는 Android Studio 없이 cmdline-tools만으로 구축·부팅 가능. 이미지 zip은 42dot 자체 레이아웃(`crp_avd_out_x86_64/`)이며 기기 프로필(`Pleos_Connect_v2_0`)·devices.xml을 함께 배포한다. WHP는 `dism`으로 활성화 시 재부팅 없이 바로 WHPX 동작(Windows 10.0.26200).
  - **F29** 실기동 첫 어댑터 선택 결과: **DrivingState=UxRestrictionsDrivingState 등록 성공**(표준 CarUxRestrictions가 Pleos 에뮬레이터에서 획득·리스너 등록 됨 — U7 전반부 통과, 실제 restrict 수신은 기어 전환 필요), VehicleInfo=ProfileVehicleInfo(예상대로), RouteService=Fake(NaviHelper 초기화 콜백 1500ms 미수신 — U1/U2에서 naviRoute 직접 재검증 필요), Narrator=폴백(F30)
  - **F30** **Pleos TTS는 initialize 전에 addEventListener를 호출하면 UninitializedPropertyAccessException** — 순서를 뒤집어(initialize 먼저) 수정 후 초기화는 성공. 다만 onReady가 1.5초 내 미수신 → 폴백 선택. onReady 지연 여부는 U6 버튼(speak 직접 시도)으로 재검증 필요(Q13)
  - **F31** Connect-S 윈도잉: 다크 windowBackground가 비칠 수 있어 각 화면 루트는 반드시 `background(colorScheme.background)`를 채워야 한다(3개 화면 수정). 또한 앱 재시작 후 내비 앱이 창을 가져가는 동작 확인 — am start 재호출로 복귀 가능
  - **F32** 시드가 재변경됨(제휴처 `p-demo-stay` 추가, 총 4개) — glm·android assets·backend seed를 동기화(sha1 19dbf8ab…)하고 테스트 단언(3→4) 갱신
  - **F33** VisitQueue 테스트의 Fake가 실패 시도까지 `received`에 기록해 단언과 어긋났던 버그 수정(성공 전송만 기록) — flush 구현 자체는 사양대로(실패 시 즉시 중단·순서 보장)
  - **F34** **U8 실측 PASS**: 에뮬레이터 앱 → `http://10.0.2.2:8000/health` → 200 `{"ok":true}`(debug cleartext 동작 확인). 백엔드는 uvicorn 호스트 구동, **pytest 28/28**. 웹(T9+T10)도 완성 — 백엔드가 `/web` 정적 서빙.
  - **F35** **U1 실측 PASS + U2 실측 PASS**: `requestRoute(RouteInfo(latitude=…, poiId="", poiSubId="", poiProvider=""))` 호출 접수 성공(예외 없음·Error 미수신), NaviHelper 초기화 콜백 ok. 단 AppContainer 선택 대기 1.5초엔 늦어 RouteService는 Fake로 선택 — U1/U2는 naviRoute 직접 검증으로 커버됨. 선택 대기 3초 확대 검토.
  - **F36** Diagnostics에서 웹/앱 통합 요소 렌더링 확인: 마지막 오류 "없음", 차량 세션(T9-1) 섹션·가족 코드 화면 이동·로그아웃 버튼 정상.
- Open Questions(Q-번호):
  - **Q13** Pleos TTS onReady 미수신 원분: 초기화는 성공했지만 onReady 이벤트가 늦거나 오지 않음 — U6(speak 직접 시도)에서 해소
- 다음:
  - 웹(T9+T10) 에이전트 완료 → 통합 보고
  - 사용자 GUI 세션: `emulator -avd PleosConnect -gpu host`로 실행(내비 지도는 swiftshader에서 불안정 [REPORTED]) → Virtual Console(adb forward tcp:8088) → 기어 D→P 전환(U7 완결)·U6 재검증·T5/T6 완료 기준 실측

---

## 2026-11-10 (야간) — 지시서 갱신 반영 + T5·T6 병렬 구현 완료

- 한 일:
  - 갱신 지시서 반영(모델·시드·테마)과 T5(주행 화면+도착 스탬프)·T6(정차 화면)을 4개 작업 단위로 병렬 구현 후 통합
  - TripSession(도착 파이프라인: arrivals → StampEngine 순서·중복 규칙 → VisitStore 로컬 기록 → 도착 음성 → 다음 목표 watch 재등록) 신설, 세션 수명은 Application과 동일(어떤 Activity도 stop 하지 않음)
  - VisitStore(filesDir/visits.json, 좌표 저장 없음), FusedArrivalDetector에 latestLocation 노출(거리 표시용, 저장 금지)
  - StampBook 도메인+9테스트, ThenNowResolver 확장(여러 과거→가장 오래된 것, label 우선)+12테스트, 시드 stamp_books·partners_demo·label 매핑
  - 테마를 밝은 기본+주야간 두 벌 토큰(AroColors 19개, 대비 4.5:1 이상 주석 명시)으로 재작성, 버튼 최소 76dp, PageNavigationBar·DemoModeTag 추가
  - 정차 화면 세로 스텝 4단계(스탬프 카드→퀴즈[상태 보존 홀더]→그때와 지금→스탬프북·맛집[예시 표기·입력·결제 요소 없음])
- 결과/증거:
  - `gradlew clean testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL, 단위 테스트 54/54(0 실패)**:
    SeedRepository 7 · CourseProgress 6 · Haversine 4 · ModeStateMachine 5 · **StampBook 9** · StampEngine 6 · **ThenNowResolver 12** · **TripSession 5**
  - APK 재빌드: app-debug.apk (47.3MB)
  - 통합 시 수정한 것: AppContainer에 seedRepository 필드 추가, LargePrimaryButton 파라미터 순서(onClick 마지막 — 트레일링 람다 지원), DrivingScreen의 확장 프로퍼티 import·수신자 수정
- 확인된 사실(F-번호):
  - **F25** TripSession 도착 파이프라인 테스트로 검증: 첫 도착 스탬프 1회·기록 1건·음성 1회, 재도착·순서 위반 무시, 완주 시 watch 중지, resetCourse 초기화
  - **F26** StampBook: 진행률 내림(1/3→33%), 완성 시 earnedBadge 배지명, 정의 외·중복 스탬프 무시 — 03문서 5-1 요구 전부 테스트 통과
  - **F27** UI 토큰: AroColors(light/dark) 19개 토큰으로 04문서 4절 이름 채택, 16진수는 AroColors.kt에만 존재. Diagnostics를 포함한 기존 화면은 MaterialTheme 매핑으로 무수정 호환
- Open Questions(Q-번호):
  - Q9(그때 기록 실제 연도)·Q10(D1~D6 실측)·Q11(앱 카테고리) 유지. **Q12 신설**: 주간/야간 테마 선택 정책(주행 상태·시간대 연동) 미정 — 호출부는 현재 isDark=false 고정.
- 다음:
  - 에뮬레이터에서 U1~U10 스모크 + T5 완료 기준(drive-demo-route.ps1 → 스탬프 1회+음성)·T6 완료 기준(기어 D→P 전환·D 복귀 시 퀴즈 미표시) 실측
  - 04문서 D1~D6 실측, then_now_demo 연도 확정(Q9)

---

## 2026-11-10 (야간) — 지시서 갱신 반영 (glm/ 문서 대폭 수정)

- 한 일: glm/의 00~03 문서가 20:55에 대폭 갱신되고 04_UI_GUIDE.md가 신설되어, 차이를 분석하고 P0 범위를 코드에 반영 시작
- 결과/증거: 아래 F19~F23. 갱신 작업은 도메인·시드 저장소 담당과 테마 담당 2개 작업 단위로 병렬 진행
- 확인된 사실(F-번호):
  - **F19** 도메인 모델 변경(00문서 4절): `QuizItem.reviewed`, `Place.coordVerified`, `Place.narrationReviewed` 필드가 모델로 이동. ThenNow는 10.4에 따라 `label` 표시용 필드를 둔다.
  - **F20** 시드 구조 확장: `stamp_books`(스탬프북 정의 + 완성 배지), `partners_demo`(제휴처·적립률 1%/2%), `then_now_demo.label`("1990년대 중반") 신설. `years_ago`는 35→32로 변경됐고 시드 주석에 "연도는 1994~1996 사이로 발주자 확인 필요(임시값)" — **사용자 확인 대상**.
  - **F21** 신규 작업(T9-1 가족 계승·차량 세션 = P1, T10 스탬프북·맛집·포인트 = P1+)과 섹션 10 보완사항(가족 코드 PIN, 보호자 동의, '관리 권한 이전' 용어, 영수증 입력은 모바일 웹 전용) 추가. 기존 "T10 혜택 미리보기(P2)"는 T10으로 흡수됨.
  - **F22** UI 방침 대체: 04_UI_GUIDE에 따라 **밝은 테마 기본 + 주·야간 모두 지원**, 색은 토큰 구조(AroColors light/dark), 포인트 색은 인주 하나, 버튼 높이 76dp 이상, Page Navigation 헤더, 가로·세로 레이아웃 모두 지원. 화면 기준은 `기획서/화면시안/01~06*.png`.
  - **F23** 03문서 5-1 단위 테스트 목록 확장: ThenNowResolver에 "여러 과거 → 가장 오래된 것", "label 우선 연도 표시" 규칙, StampBook(진행률·배지·중복 무시) 추가. 포인트 규칙은 서버 쪽(P1+). 01문서는 U1~U10 표 변경 없음, Gleo Interface 절(6-1, P2 선택)만 추가.
  - **F24** 01문서 6-1: Gleo Interface는 App Schema(assets/schema.json) 선언 → Intent 호출 → Callback 응답 방식(function calling). LLM SDK는 릴리스 노트에서 제공 종료 안내. P2 선택 과제로 지금은 구현하지 않는다.
- Open Questions(Q-번호):
  - **Q9** `then_now_demo`의 실제 연도(1994~1996 어디로 할지) — 시드 years_ago=32는 임시값. 사용자가 정하면 label·years_ago를 함께 확정.
  - **Q10** 04문서 [그림] 값(타이포 크기·버튼 크기·색 토큰 실값)은 D1~D6 확인 목록대로 에뮬레이터·원문 이미지로 재확인 필요.
  - **Q11** 앱 카테고리(00문서 10.2): 주행 화면의 공식 허용 카테고리가 미확정 — ARCHITECTURE.md 작성 시 후보(Maps/Navigation 또는 Music/Audio 성격)와 근거를 적어야 한다.
- 다음: P0 반영(모델·시드·StampBook·테마) 완료 후 재빌드·재테스트 → T5/T6에서 04문서 화면 명세 적용

## 2026-11-10 (심야 2차) — 지시서 3차 갱신(11~13절) 반영 착수

- 한 일: glm/ 문서 재갱신(00: 37.6KB·01·02·03·04·05_INSTALL_EMULATOR 신설·seed 23:04)을 읽고 델타를 4개 작업 단위로 병렬 구현 시작
- 확인된 사실(F-번호):
  - **F37** 새 포트 2개(00문서 4절): `TripSignalProvider{partySize(): Int?; odometerKm(): Double?}`(Pleos: Seat·Odometer / 대체: 수동·null), `VoiceLetterPlayer{isAvailable; play(letterId); stop()}`(정차 전용). 새 도메인 `TripSignals(partySize, odometerKm)`.
  - **F38** **Seat API를 Vehicle 2.3.2 AAR javap로 직접 확정**: `checkSeatOccupancyCapabilities(onComplete:(List<SeatArea>), onFailed)` 2종 오버로드 + `getCurrentSeatOccupancy(SeatArea, (Boolean)->Unit, (Exception)->Unit)`. SeatArea는 `SeatArea(SeatValue.ROW_1_LEFT…ROW_3_*)` 가변인자 — partySize는 점유 좌석 수 합산.
  - **F39** Odometer 확정(01문서 10절과 일치): `getCurrentDistanceDriven((Float)->Unit, (Exception)->Unit)` — 단위는 U13 실측 과제(km 가정 주석).
  - **F40** 시드 재변경(23:04): places[].heritage(성덕대왕신종 771년 — gj-museum만), then_now_demo에 `year: 1994`(발주자 확정·Q9 해소)와 `party_size: 5`, `voice_letter_demo`(음성 편지 예시 — **"고인의 목소리를 AI로 만들지 말 것"** 규정), `seat_roles`(운전석·조수석 길잡이·뒷좌석 탐험가) 추가. 3곳 복사본 동기화 완료.
  - **F41** **U11~U14 신설**(01문서 8절): U11 좌석 점유·U12 문(참고·사용 안 함)·U13 주행거리계·U14 배터리(참고·사용 안 함). 11/14까지 필수 실측: U3·U7, 다음 U1(✓)·U13·U11. Virtual Console 조작 가능 항목 목록도 [OFFICIAL] 추가.
  - **F42** **주행 규제 수신 경로는 OFFICIAL**(01문서 9절): `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged()` — 현재 구현과 일치. `PleosVehicleInfoProvider`는 만들지 않는다(01문서 10절: CarInfo는 VIN/CRN뿐) — 선택에서 제외하고 Profile 고정. Gear는 보조 신호로, UxRestrictions와 어긋나면 더 제한적인 쪽(주행).
  - **F43** T10 규칙 변경(00문서 12·13절): 캐시백 대상에 **숙소(lodging)** 추가(시드 p-demo-stay 존재), 인정 기간 식당·가게=스탬프 당일+익일 / **숙소=체크인~체크아웃(최대 3박)**, 영수증 **사진 필수**+사진 해시 중복 판정, 같은 제휴처·같은 날 두 번째는 `pending`, 기기당 코드 생성 하루 1회, 표기는 **"포인트 캐시백(예시)"**(환급·입금 문구 금지), food_notes에 kind, 진단 화면 9개 항목에 '구현/검증' 분리 표시.
  - **F44** 일정 정정(00문서 13절): 실제 마감 11/15 23:59. 진단 표기에 '검증: 실측 전/확인함' 분리. 기획서에 현재 상태 기록됨(앱 실행·70/28 테스트·U3/U6/U7 미확인) — 실측 후 표 갱신 필요.
- Open Questions(Q-번호):
  - **Q13 유지** + 신규 없음
- 다음: 4개 작업(앱 코어·앱 UI·백엔드 10/11절·웹) 완료 → 통합 → U11/U13 실측 준비 → 릴리스 APK

## 2026-11-11 (00시) — 3차 갱신 통합 완료 + 릴리스 APK

- 한 일: 4개 병렬 작업(앱 코어·앱 UI·백엔드 10/11절·웹) 통합 + 릴리스 APK 서명 빌드
- 결과/증거:
  - 앱: **단위 테스트 72/72 통과**(+heritage·voiceLetters·seatRoles·party_size·year 1994·TripSession tripKm/partySize 10케이스), assembleDebug 성공
  - 백엔드: **pytest 51/51 통과**(+숙소 기간·사진 필수·pending·device_limit 등 23개 신규), uvicorn 재기동 — health 200·/web 200
  - 웹: node --check 10파일 전부 통과, 실제 백엔드로 발급·방문·메모리·포인트·삭제 흐름 실측(테스트 가족은 전부 삭제·정리)
  - **릴리스 APK**: `apk/arosaegim_아로새김_release.apk`(39.6MB) — apksigner verify 통과
    (CN=arosaegim, package app.arosaegim v0.1.0). 서명 키·keystore.properties는 gitignore.
  - seat-role **갱신 엔드포인트 추가**: `POST /v1/families/me/members/{id}/seat-role`(웹의 PATCH→POST 폴백 체인과 맞물림)
  - 에뮬레이터 재부팅 후 최신 APK 재설치·실행 확인(프로세스 생존)
- 확인된 사실(F-번호):
  - **F45** TripSession에 T5-1(tripKm: 시작/완주 Odometer 차이, 둘 중 하나라도 null이면 null)·T6-2(partySize: start 1회 조회)가 들어갔고 도착 문장도 "오늘은 N명이 함께 왔어요."를 인원이 읽힐 때만 추가한다.
  - **F46** Diagnostics가 9개 항목 구조(시안 11)로 재구성됐다 — 항목마다 '구현: Pleos/대체'와 '검증: 실측 전/확인함'이 분리 표시되고, U12(문)·U14(배터리)는 '참고 — 사용 안 함' 고정, '차종·연식' 행은 '기어 상태(Vehicle·Gear)'로 교체(01문서 10절).
  - **F47** 백엔드 영수증 규칙 최종: 사진 필수, receipt_key=제휴처+날짜+금액+사진해시, 같은 제휴처·같은 날 두 번째=pending(적립 0·저장), 식당·가게 당일+익일, 숙소 체크인~체크아웃(최대 3박), device_token 하루 1회(429 device_limit).
- Open Questions(Q-번호):
  - Q13 유지. 신규: **Q14** letter_demo.ogg 예시 음성 파일 미삽입 — 발주자가 합성음성 또는 직접 녹음으로 준비하면 `assets/seed/letter_demo.ogg`로 넣는 순간 T9-2 재생 포트가 자동 활성화된다(누가 만들지: 발주자).
- 다음:
  - 사용자 GUI 세션(U3 geo fix·U7 기어·U6 음성·U11 좌석·U13 주행거리·U9) — DEV_LOG 표 갱신
  - 기획서 문장을 DEV_LOG 표 기준으로 갱신(13절 지시)

## 2026-11-11 (00시) — U3 geo fix 실측 + T5 파이프라인 종단 확인

- 한 일: Diagnostics에서 U3 탭(근접 알림 등록) -> `adb emu geo fix` 주입(밖->안) -> ENTER 수신 확인 -> 이어지는 앱 반응 캡처
- 결과/증거: `C:\tools\android-sdk\.pleos-download\u3_enter.png`(모드 idle->proximity, transition ENTER), `u3_exit2.png`(Driving 화면: 스탬프 1/3·다음 대릉원·약 1.9km)
- 확인된 사실(F-번호):
  - **F48** **U3 PASS**: Pleos 근접 알림이 실제 에뮬레이터에서 동작한다. `ProximityAlertRequest` 반경 120m(미터) 등록 -> geo fix 주입 좌표(박물관)에서 **ENTER 전이 수신**, 모드는 **proximity**(haversine 폴백 아님). 반경 단위 미터가 실측으로도 확정됐다(00문서 10.5 해소).
  - **F49** ENTER -> TripSession 파이프라인 종단 확인: 스탬프 정확히 1회(스탬프 1/3), 다음 목표가 자동으로 대릉원으로 진행, DrivingActivity에 "약 1.9km" 거리 표시(latestLocation+Haversine), PageNavigationBar·"주행 중" 꼬리표·버튼 1개 렌더링. **T5 완료 기준의 동작 부분이 실측됨**(음성 청취·drive-demo-route.ps1 연동은 사용자 세션).
  - **F50** Diagnostics 어댑터 표가 9개 항목+구현/검증 분리로 렌더링된다. TripSignals=PleosTripSignalProvider 선택(Seat 합산+Odometer, U11·U13 미검증), VoiceLetter=Fallback(assets 오디오 없음 — Q14), RouteService만 Fake(NaviHelper 콜백이 3초보다 늦음 — U1/U2로 직접 검증 커버).
  - **F51** 수동 제어 스와이프가 DRIVING 토글을 지나가면 ModeRouter가 즉시 DrivingActivity를 앞으로 가져온다 — 모드 전환 경로가 실측됨(04문서 화면 전환 동작).
- Open Questions(Q-번호):
  - **Q15** NaviHelper 초기화 콜백이 3초를 넘게 걸리는 원인 — 선택 대기 확대 vs U1/U2 직접 검증 커버 중 어느 쪽으로 정리할지(현재는 후자로 커버됨).
- 추가 완료(같은 세션): 00문서 12절 T6 확장 — **코스 선택 화면(시안 02)** 스텝0 신설(코스 카드·출발 버튼·시작 전 게이트), **오늘의 기록 화면(시안 09)** 스텝5 신설(요약 타일 2x2: 스탬프/인원/거리/장소 — 없는 값은 타일 숨김), **스탬프북 카드 3종 꼬리표**(맛집·가게[museum_shop 포함]·숙소)+"캐시백은 현금이 아닌 포인트" 문구+숙소 인정 안내, **ThemeState 야간 토글**(Diagnostics 개발 섹션 — 4개 Activity가 ThemeState.isDark 구독해 즉시 전환, D5 실측 준비 완료). 앱 테스트 재검증 **72/72**, debug APK 재설치·실행 확인.
- 추가 실측 완료(같은 세션): **U4 PASS(isSupported=true)**, **U5 PASS**(VIN capability READ·기어 GEAR_PARK 실수신), **U6 FAIL 원인 확정**(Android TTS 초기화 실패 + Pleos onReady 미수신), **U8 PASS 재확인**(200), **U9 PASS**(3회 전환·복귀), **U11 FAIL=설계 동작**(null→숨김), **U13 PASS**(0.0 km 수신 — 트립 채널), **U10 관찰 중**(geo fix 3회 후 미발생 — [REPORTED] 일치). U 표를 U1~U14 전체 실측으로 재작성했다.
- 확인된 사실(F-번호):
  - **F52** 전체 스모크 실측 결과: U1·U2·U3·U4·U5·U8·U9·U13 **PASS**, U6 **FAIL(원인 확정)**, U11 **FAIL=설계 동작**, U7·U10 부분/관찰, U12·U14 사용 안 함.
  - **F53** Vehicle Gear 조회 실측: `getCurrentGearSelectionState` 경유 GEAR_PARK 수신(클러스터 P와 일치) — 진단 '기어 상태' 행 실측값.
  - **F54** 에뮬레이터 TTS: Android 플랫폼 TTS 초기화 실패(엔진/데이터 부재 추정) + Pleos TTS onReady 미수신 → **이 에뮬레이터에서 안내 음성이 나지 않는 것은 환경 한계일 수 있다**. 영상은 오디오 별도 저장 절차(03문서)로 대비.
  - **F55** Seat 점유 조회 현재 null(Virtual Console 미조작) — 설계대로 문장 숨김. 조작 후 재실행이 U11 완결.
  - **F56** Odometer 채널: getCurrentDistanceDriven 경유 0.0 km 수신(트립 기준으로 보임) — T5-1 시작~완주 차이 계산과 적합. 단위 km 재확인 권장.
  - **F57** U10: geo fix 이동 후에도 onDestinationArrived 미발생 — [REPORTED] 재확인. 도착 판정 본역이 FusedLocation인 설계가 정당화됨(PT Q&A 근거).
- Open Questions(Q-번호):
  - **Q16** Android TTS 초기화 실패 원인(엔진 부재 vs 한국어 데이터 부재) — 패키지 목록·설정으로 확인 가능
  - **Q17** Pleos TTS onReady 미수신 — initialize(onInitializeListener) 콜백 형태 재시도로 달라지는지
- 추가 통합 완료(같은 세션): T9-3/T12+ 서버 연결 — ApiClient에 **getMembers·getMetrics·getVoiceLetters**(401/403은 AuthException 신호), FamilySessionStore에 **PIN 휘발성 보관**(SessionCredentials companion — SharedPreferences·파일·로그 경로 코드상 없음, 00문서 10.6 준수), TripSession partySize를 **매 도착 시점 재조회**로 개선, ParkedScreen 이름표 실데이터(driver/navigator/explorer→nickname, 401/403 안내 카드), Diagnostics 지표 [서버 값 가져오기](성공 시 출처 '서버'), 문서 3종 최신화(ARCHITECTURE 507줄 — U1~U14 실측표·신규 Q&A 3·어댑터 실측 표 / README 완료 상태 / ATTRIBUTION heritage 출처).
- 확인된 사실(F-번호):
  - **F58** 서버가 발급하는 가족 코드는 **8자리**(실측 8XTPUG8N) — FamilyCodeValidator를 6~8자리로 확장(테스트 갱신: 7자리 유효·9자리 무효).
  - **F59** PIN은 세션 메모리(SessionCredentials companion)에만 존재 — 앱 재시작 시 소실(재입력 설계). members/metrics 조회는 코드+PIN 헤더가 모두 있어야 하며 401/403은 AuthException으로 구분된다.
  - **F60** U-tap 실측 방법(input tap + 스크린샷 좌표 측정)으로 진단 화면의 카드를 직접 조작할 수 있다 — U4·U5·U8·U9·U11·U13·U10이 사용자 없이 실측됐다.
- Open Questions(Q-번호):
  - Q13~Q17 유지. **Q18**: 백엔드 DB에 라이브 검증용 테스트 가족(8XTPUG8N) 1건 남아 있음 — 제출 전 `DELETE /v1/families/me` 또는 DB 삭제로 정리 권장.
- 다음:
  - 사용자 Virtual Console 세션(U7·U11·U13 완결)과 기획서 문장 최종 확정만 남음. 앱 80/80·백엔드 51/51·릴리스 APK 재빌드(apk/arosaegim_아로새김_release.apk, 39.7MB) 완료.

---

## 스모크 테스트 결과표 (U1~U14)

| # | 확인 항목 | 결과 | 에뮬레이터 | SDK 버전 | 비고/스크린샷 |
|---|---|---|---|---|---|
| U1 | requestRoute poiId 빈 값 | **PASS(호출 접수)** — poiId=""·poiSubId=""·poiProvider=""로 호출, 예외 없음·Error 미수신 | v2.1.0 실측 | NaviHelper 3.0.5 | 내비 화면 경로 표시는 육안 병기 필요 — .pleos-download/u1_result2.png |
| U2 | NaviHelper 초기화·리스너 | **PASS** — initState: ok, initError 없음 | v2.1.0 실측 | 3.0.5 | AppContainer 선택 대기(3초)보다 늦음 → RouteService만 Fake 선택, U1/U2로 직접 커버(Q15) |
| U3 | FusedLocation 근접 알림 | **PASS — proximity 등록 + ENTER 수신 + 도착 이벤트 발행**. 모드 idle->proximity(폴백 아님) | v2.1.0 실측 | 1.1.3 | **반경 단위 미터 실측 확정**(00문서 10.5 해소) — .pleos-download/u3_enter.png |
| U4 | FusedLocation isSupported() | **PASS** — `FusedLocation.isSupported = true`, mode: proximity, lastError 없음 | v2.1.0 실측 | 1.1.3 | .pleos-download/u4_r.png |
| U5 | Vehicle capability/기어 | **PASS** — VIN capability AccessType=**READ**(확인만·값 미읽음 ✓규정), **기어 상태 GEAR_PARK 실수신**(클러스터 P와 일치), Odometer 값은 스코프 밖 | v2.1.0 실측 | 2.3.2 | .pleos-download/u5_r2.png — 진단 '기어 상태(Vehicle·Gear)' 행 |
| U6 | TTS ON_DEVICE speak | **FAIL(원인 2건 확정)** — ①AndroidTtsNarrator: Android TTS 초기화 미완료/실패(0ms — 에뮬레이터 TTS 엔진·한국어 데이터 부재 가능성) ②PleosTtsNarrator 재시도: onReady 미수신 상태(초기화는 성공) | v2.1.0 실측 | 3.3.1 | F54·Q13·Q16·Q17 — 초기화 후 여유 두고 재탭 권장 |
| U7 | 주행 규제 전환 | **부분** — CarUxRestrictionsManager 획득·리스너 등록 **성공**(OFFICIAL 경로). restrict 수신은 기어 P→D 필요(Virtual Console) | v2.1.0 실측 | - | F29 — 사용자 세션에서 완결 예정 |
| U8 | 에뮬레이터→호스트 서버 | **PASS** — GET 10.0.2.2:8000/health → **200 {"ok":true}**(cleartext 포함)·재확인 | v2.1.0 실측 | - | 백엔드 uvicorn 구동 중 — .pleos-download/u8_r.png |
| U9 | Activity 전환 반복 | **PASS** — Driving↔Parked 3회 전환(REORDER_TO_FRONT) 완료·복귀·상태 유지 | v2.1.0 실측 | - | .pleos-download/u9_r.png |
| U10 | 도착 이벤트 관찰 | **관찰 중 — geo fix 3회 이동 후에도 onDestinationArrived 미발생**([REPORTED]와 일치 — 이것이 정상 결과). 관찰 카드 유지 | v2.1.0 실측 | 3.0.5 | 도착 판정 본역은 FusedLocation(U3 PASS) — 설계 정당화 근거 |
| U11 | 좌석 점유 → 인원(partySize) | **FAIL=설계 동작** — partySize()=null → **문장 숨김**(가짜 금지 원칙). Virtual Console 'Seat Occupancy' 조작 후 재실행 필요 | v2.1.0 실측 | 2.3.2 | .pleos-download/u11_r3.png |
| U12 | 문 상태(참고 — 사용 안 함) | **해당 없음(고정)** — 문장 안 함(로드맵)·좌석·수신음 실행하지 않음 | - | - | 진단 카드 고정 안내 렌더링 확인 |
| U13 | 주행거리계(odometerKm) | **PASS** — odometerKm()=**0.0 km 수신**(판정 PASS). '현재(트립) 거리' 채널로 보임 — **T5-1 계산에 적합**. 단위 km 가정(Virtual Console 조작 후 재확인 권장) | v2.1.0 실측 | 2.3.2 | .pleos-download/u13_r.png |
| U14 | 배터리(참고 — 사용 안 함) | **해당 없음(고정)** — 사용 안 함(로드맵) | - | - | 진단 카드 고정 안내 렌더링 확인 |

> 실측 방법: 진단 화면을 `input tap`으로 직접 조작(좌표는 스크린샷 측정). U3·U10의 위치 주입은 `adb emu geo fix`. U7·U11·U13 완결은 Virtual Console(adb forward tcp:8088 → 127.0.0.1:8088) 필요.

---|---|---|---|---|---|
| U1 | requestRoute poiId 빈 값 | **PASS(호출 접수)** — poiId=""·poiSubId=""·poiProvider=""로 호출, 예외 없음·Error 미수신 | v2.1.0 실측 | NaviHelper 3.0.5 | 내비 화면에 경로 표시 여부는 육안 병기 필요 — `\.pleos-download\u1_result2.png` |
| U2 | NaviHelper 초기화·리스너 | **PASS** — initState: ok, initError 없음 | v2.1.0 실측 | 3.0.5 | AppContainer의 1.5초 선택 대기에는 늦음(→Fake 선택), 직접 조회로는 성공 — 선택 대기 3초로 늘림 검토 |
| U3 | FusedLocation 근접 알림 | **PASS — proximity 등록 성공 + ENTER 수신 + 도착 이벤트 발행**. 모드 idle->proximity(haversine 폴백 아님), geo fix 박물관 주입 -> ENTER. EXIT 주입은 했으나 상세 캡처 전 화면 전환으로 미캡처 | v2.1.0 실측 | 1.1.3 | **반경 단위 미터 실측 확정**(120m 알림에 진입) |
| U4 | FusedLocation isSupported() | **미실행** | - | - | - |
| U5 | Vehicle capability/기어 | **미실행** | - | - | CarInfo는 VIN/CRN만(AAR 확인 F4) |
| U6 | TTS ON_DEVICE speak | **부분** — initialize 성공(F30), onReady 3초 내 미수신 → AndroidTTS 폴백(도착 음성은 Android TTS로 발화됨). Pleos TTS onReady 대기 시간 재검증 남음 | v2.1.0 실측 | 3.3.1 | F30·Q13 |
| U7 | 주행 규제 전환 | **부분** — CarUxRestrictionsManager 획득·리스너 등록 **성공**. restrict 수신은 기어 P→D 필요 | v2.1.0 실측 | - | F29 — Virtual Console에서 기어 전환으로 완결 |
| U8 | 에뮬레이터→호스트 서버 | **PASS** — GET 10.0.2.2:8000/health → **200 {"ok":true}**(cleartext 포함) | v2.1.0 실측 | - | 백엔드 uvicorn 호스트 구동 중 — `\.pleos-download\u8_result.png` |
| U9 | Activity 전환 반복 | **미실행** | - | - | - |
| U10 | onDestinationArrived 발생 여부 | **미실행** | - | - | 미발생 예상([REPORTED]) |

---

## 2026-11-10 — T0~T4 병렬 구현·통합 빌드

- 한 일:
  - T1(스캐폴드)·T2(어댑터+Diagnostics)·T3(도메인+테스트)·T4(시드)를 4개 작업 단위로 병렬 구현 후 통합
  - Android SDK 설치(cmdline-tools + platform-35 + build-tools 35.0.0 + platform-tools, `C:\tools\android-sdk`)
  - `tools/` 시연 스크립트 3개 작성(UTF-8 BOM 적용, PowerShell 5.1/7 구문검증 PASS) + `tools/routes/gyeongju-demo.json`
- 결과/증거:
  - `gradlew :app:dependencies --configuration debugCompileClasspath` → **BUILD SUCCESSFUL** — Pleos Maven에서 4 SDK 해결:
    `Vehicle:2.3.2`, `NaviHelper:3.0.5(+navi-data:3.0.4)`, `FusedLocation:1.1.3`, `TextToSpeech:3.3.1(+security:universal-access:0.6.0)`
  - `gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL. 단위 테스트 32/32 통과**
    (SeedRepositoryTest 5 · CourseProgressTest 6 · HaversineTest 4 · ModeStateMachineTest 5 · StampEngineTest 6 · ThenNowResolverTest 6)
  - APK: `android/app/build/outputs/apk/debug/app-debug.apk` (aapt2 검증: `package='app.arosaegim' versionName='0.1.0'`, 라벨 '아로새김')
  - 최초 실패 2건의 원인과 수정:
    (a) **구현 버그** — `PlaceDto.coordVerified`에 `@SerialName("coord_verified")` 누락 → JSON 키가 무시되고 항상 false로 파싱.
        실제 시드 테스트는 3곳 모두 coord_verified=false라 우연히 통과했고, 반대값(true)을 쓰는 픽스처 테스트가 잡아냈다.
    (b) **테스트 픽스처 버그** — ghost 장소가 place_ids에 포함되어 있었고(스킵 검증과 모순), p1 퀴즈 reviewed=true인데 draft 키를 기대함. 픽스처 수정.
  - 빌드 오류 수정 이력: (1) version catalog에 `compose-ui-tooling` 누락 → 추가 (2) `local.properties`의 `\t` 이스케이프 문제 → 슬래시 경로로 수정
    (3) Theme.kt 미정의 색상 `ArosaeOnSurface` → 정의 추가 (4) PleosTtsNarrator/AndroidTtsNarrator의 `kotlin.coroutines.CancellableContinuation` →
    `kotlinx.coroutines.` 로 수정, 존재하지 않는 `requestFocus(req)` → `requestAudioFocus(req)` (5) API-35 android.car.jar의
    `CarUxRestrictionsManager.unregisterListener()`는 무인자만 제공 → 호출부 수정
- 확인된 사실(F-번호):
  - **F15** `platforms/android-35/optional/android.car.jar` 가 정식 SDK에 포함되어 있다 → `compileOnly(files("libs/android.car.jar"))` 로 복사 사용. 런타임 클래스는 에뮬레이터 시스템 이미지 소속(Q2는 여전히 U7에서 확인).
  - **F16** 이 jar에서 `CarUxRestrictionsManager.unregisterListener()` 는 **무인자 시그니처만** 존재(리스너 인자 버전 없음).
  - **F17** TextToSpeech 3.3.1 은 `ai.pleos.playground.security:universal-access:0.6.0`(JNA·security-crypto-ktx 포함)을 전이 의존으로 끌어온다 — ON_DEVICE 전용으로 써도 APK 크기에 일부 포함된다.
  - **F18** Gradle 8.10.2 + AGP 8.8.2 + Kotlin 2.0.0 + JDK 21(JAVA_HOME) 조합에서 의존성 해결·컴파일 정상(kotlin-stdlib은 전이 의존으로 2.0.21 선택 — 컴파일러 2.0.0과 바이너리 호환).
- Open Questions(Q-번호):
  - ~~Q8~~ **해소**: 실패 원인은 위 (a) `@SerialName` 누락 + (b) 픽스처 모순. 둘 다 수정 후 전체 통과 확인.
- 다음:
  - T2 잔여 파일(Diagnostics·AppContainer·ModeRouter) 통합 → `testDebugUnitTest` 전체 재실행 → `assembleDebug`
  - U1~U10 에뮬레이터 실행(사용자 협업)

---

## 2026-11-10 — T0 환경·참고자료 (SDK 시그니처 확인)

- 한 일:
  - 공식 샘플 3개를 `reference/`에 clone (pleos-sample-vehicle-app / pleos-sample-navi-app / pleos-sample-fusedlocation-app)
  - 각 샘플의 build.gradle(.kts)·libs.versions.toml·매니페스트·SDK 호출 클래스를 읽고 아래 "확인된 시그니처 표" 작성
  - Pleos Maven 인덱스를 직접 조회(maven-metadata.xml)해 4개 SDK 좌표·버전 존재 확인
  - 4개 SDK AAR을 내려받아 javap로 클래스·메서드 시그니처를 직접 검증 (추측 배제)
- 결과/증거(로그·스크린샷 경로):
  - Maven 인덱스: `https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/{Vehicle,NaviHelper,FusedLocation,TextToSpeech}/maven-metadata.xml` 조회 결과 아래 F1~F3
  - AAR 검증 위치: Vehicle 2.3.2 / NaviHelper 3.0.5(+navi-data 3.0.4) / FusedLocation 1.1.3 / TextToSpeech 3.3.1 classes.jar javap 출력 (이 항목 작성 시점의 세션 콘솔 기록)
  - 샘플 clone: `reference/` 3개 디렉터리
- 확인된 사실(F-번호):
  - **F1** Maven에 존재하는 버전: Vehicle 2.3.2(2.0.3, 2.3.2만 존재 — 샘플이 쓰는 2.3.1은 공개 저장소에 **없음**), NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1. → **Vehicle은 2.3.2로 진행** (01 문서 예상대로).
  - **F2** TextToSpeech 좌표 확정: `ai.pleos.playground:TextToSpeech:3.3.1` (01 문서의 [UNKNOWN] 해소)
  - **F3** 4개 SDK AAR 매니페스트 모두 `minSdkVersion=23` → 앱 minSdk 28로 병합 충돌 없음. 샘플 앱들의 minSdk(33 등)는 샘플 선택값일 뿐 SDK 요구가 아님.
  - **F4** Vehicle 2.3.2 `CarInfo` 인터페이스는 `checkVINInfoCapabilities / getVINInfo / checkCRNInfoCapabilities / getCRNInfo / getCapabilityInfo` **뿐** — 제조사·연식 getter가 없다(01 문서 "CarInfo: 제조사, 연식 등"과 불일치). → 차종·연식은 Pleos SDK로 얻을 수 없으므로 **ProfileVehicleInfo(수동 입력)가 실질 유일 경로**. VIN은 규정상 읽지도 않는다(00 문서 2-2 #8).
  - **F5** Vehicle 2.3.2에 `getGear()` 존재(01 문서 "Gear 전용 도메인 없음"과 불일치): `Gear.getCurrentGearSelectionState(onComplete:(VehicleGear)->Unit, onFailed:(Exception)->Unit)`, `registerGearSelection(GearSelectionListener)`. `VehicleGear`는 GEAR_PARK/GEAR_DRIVE/GEAR_REVERSE/GEAR_NEUTRAL/GEAR_1..9. D/P 신호의 보조 소스로 활용 가능(U7에서 실측 비교).
  - **F6** FusedLocation 1.1.3 `ProximityAlertRequest(latitude: Double, longitude: Double, radiusMeters: Float, transitionTypes: Set<ProximityTransition>)` — **반경 단위는 Float 미터로 확정**(01 문서 [UNKNOWN] 해소). `ProximityTransition`은 ENTER/EXIT 2종. `ProximityAlertEvent(alertId: String, transition: ProximityTransition)` — alertId는 String. `LocationData(latitude, longitude, altitude: Double, heading: Float, confidence: Float, timestampMillis: Long)`.
  - **F7** `RouteInfo` 생성자(navi-data 3.0.4 AAR 확인): `(latitude: Double, longitude: Double, displayLatitude: Double?=null, displayLongitude: Double?=null, poiName: String, poiId: String, address: String, poiSubId: String, routeOption: RouteOption=RECOMMENDED, poiProvider: String="", isMobileRequest: Boolean=false)` — 좌표 순서 (lat, lon) 확정. **모든 SDK 호출은 이름 있는 인자로 작성**.
  - **F8** `NaviHelperEventListener`의 **모든 콜백이 default 구현**을 가진다(AAR javap 확인) → 필요한 콜백만 override하면 됨(샘플은 전부 override했지만 필수가 아님). `DestinationArrivedInfo(duration: Int, distance: Int, name: String, address: String)` — **placeId 없음** → 내비 도착 이벤트로는 어느 장소인지 식별 불가, 현재 목표 장소 기준으로 해석.
  - **F9** TextToSpeech 3.3.1: `TextToSpeech(context, Mode)`, `initialize()`, `initialize(OnInitializeListener{onSuccess/onFailure(ErrorType)})`, `addEventListener(EventListener{onStart/onReady/onDone/onError/onStop/onUpdatedRms(Double)})`, `setLocale(Locale)`, `speak(String, Boolean)`, `stop()`, `removeEventListener`, `release()`, `registerApp(...)`(서버 모드 전용). Mode = SERVER/ON_DEVICE/HYBRID.
  - **F10** Vehicle 2.3.2: `Vehicle(context)` → `initialize()/release()` 동기. 도메인 접근은 `getCarInfo()/getOdometer()/getGear()/...`. 콜백은 `checkXxxCapabilities(onComplete, onFailed)` named-args 스타일. `CapabilityConfig(accessType: AccessType[NONE/READ/WRITE/READ_WRITE])`.
  - **F11** NaviHelper 3.0.5: `NaviHelper(context)`, `initialize()`, `initialize(onSuccess, onFailure)` 콜백 버전 존재, `addListener/removeListener(NaviHelperEventListener)`, `requestRoute(RouteInfo)`, `cancelRoute()`, `release()`.
  - **F12** FusedLocation 1.1.3: `FusedLocation(context)` — `isSupported()`, `initialize()`, `registerFusedLocationCallback(FusedLocationListener)`, `unregisterFusedLocationCallback`, `getLastKnownLocation(listener)`, `addProximityAlert(request, listener)`, `removeProximityAlert(alertId)`, `release()`. `FusedLocationListener{onChanged(LocationData?) / onError(Exception)}`.
  - **F13** 공식 샘플 3개 모두: settings.gradle.kts에 Pleos Maven 등록(토큰 불필요), Gradle 8.10.2 wrapper, Compose 기반. Vehicle 샘플 minSdk 23/AGP 8.8.2/Kotlin 2.0.0, Navi 샘플 minSdk 33/AGP 8.8.0/Kotlin 1.9.22/Hilt 사용(우리는 Hilt 안 씀 — 00 문서 2-3 #19), FusedLocation 샘플 minSdk 24/AGP 8.8.2/Kotlin 2.0.0.
  - **F14** FusedLocation 샘플의 근접 알림 실사용 코드: 반경 100f(미터), ENTER+EXIT 등록, mock location으로 Inside/Outside 시뮬레이션(`LocationManager.setTestProviderLocation`) — U3 시나리오의 표준 참고 패턴.
- Open Questions(Q-번호):
  - **Q1** Vehicle `Gear` 도메인의 권한: 01 문서상 DrivingMode·Brake·Odometer → `CAR_DRIVE`. Gear는 문서에 없음 → U5에서 getCurrentGearSelectionState 호출로 확인.
  - **Q2** CarUxRestrictionsManager(android.car)가 Pleos 에뮬레이터에서 동작하는지 → U7. (android.car.jar는 컴파일용이며 런타임 클래스는 에뮬레이터 시스템 이미지 소속)
  - **Q3** FusedLocation 근접 알림: 등록 제한 수·ENTER/EXIT 실수신 → U3. 반경 단위는 미터(F6)지만 실측 검증 병행.
  - **Q4** TTS ON_DEVICE: initialize 직후 speak 가능 여부(onReady 선행 필요성), onDone 누락 사례 → U6. 어댑터에 5초 타임아웃 방어 구현.
  - **Q5** NaviHelper initialize 콜백 버전(onSuccess/onFailure)의 실제 발화 타이밍 → U2.
  - **Q6** android.car.jar 획득 경로: Android SDK platforms/<api>/optional/ 에서 확보 예정. 없으면 리플렉션 우회(통합 단계 결정, DEV_LOG 후속 기록).
  - **Q7** 에뮬레이터 설치 여부 — 사용자 확인 필요(03 문서 1절 체크리스트).
- 다음:
  - T1~T4 병렬 구현(4개 작업 단위) → 통합 빌드(assembleDebug·test)
  - Android SDK(cmdline-tools + platform 35 + build-tools) 설치는 진행 중
  - U1~U10은 에뮬레이터 준비 후 사용자와 함께 실행

---

## 참고: 확인된 시그니처 표 (T0 완료 기준)

| SDK | 클래스·생성자 | 임포트 경로 | 핵심 메서드/콜백 | 권한 | 근거 |
|---|---|---|---|---|---|
| Vehicle 2.3.2 | `Vehicle(context)` | `ai.pleos.playground.vehicle.Vehicle` | `initialize()`, `release()`, `getCarInfo()`, `getOdometer()`, `getGear()` | CAR_INFO, CAR_DRIVE, CAR_ENERGY | 샘플 VehicleClient.kt + AAR javap |
| CarInfo | interface | `ai.pleos.playground.vehicle.api.CarInfo` | `checkVINInfoCapabilities(onComplete, onFailed)`, `getVINInfo`, `checkCRNInfoCapabilities`, `getCRNInfo`, `getCapabilityInfo` — **제조사/연식 없음** | CAR_INFO | AAR javap (F4) |
| Gear | interface | `ai.pleos.playground.vehicle.api.Gear` | `getCurrentGearSelectionState(onComplete, onFailed)`, `registerGearSelection(GearSelectionListener{onSelected(VehicleGear)})` | 미확인(추정 CAR_DRIVE, Q1) | AAR javap (F5) |
| NaviHelper 3.0.5 | `NaviHelper(context)` | `ai.pleos.playground.navi.helper.NaviHelper` | `initialize()`, `initialize(onSuccess,onFailure)`, `addListener/removeListener`, `requestRoute(RouteInfo)`, `cancelRoute()`, `release()` | NAVI_ROUTE_SEARCH, NAVI_ROUTE, NAVI_CUSTOM_MAP | 샘플 NaviHelperViewModel.kt + AAR (F11) |
| RouteInfo | data class | `ai.pleos.playground.navi.data.RouteInfo` | (latitude, longitude, displayLatitude?, displayLongitude?, poiName, poiId, address, poiSubId, routeOption, poiProvider, isMobileRequest) — 이름 있는 인자 필수 | - | navi-data 3.0.4 AAR (F7) |
| NaviHelperEventListener | interface(전 콜백 default) | `ai.pleos.playground.navi.helper.listener.NaviHelperEventListener` | onRouteStarted, onRoutingCompleted, onRouteCancelled, onDestinationArrived(DestinationArrivedInfo), onError(NaviErrorCode), onNavigationStatus 등 26개 | - | AAR javap (F8) |
| FusedLocation 1.1.3 | `FusedLocation(context)` | `ai.pleos.playground.location.fused.FusedLocation` | `isSupported()`, `initialize()`, `registerFusedLocationCallback`, `getLastKnownLocation`, `addProximityAlert(ProximityAlertRequest, ProximityAlertListener)`, `removeProximityAlert(alertId)`, `release()` | FUSED_LOCATION(dangerous) | 샘플 FusedLocationClient.kt + AAR (F12) |
| ProximityAlertRequest | data class | `...model.ProximityAlertRequest` | (latitude: Double, longitude: Double, **radiusMeters: Float**(미터), transitionTypes: Set<ProximityTransition[ENTER/EXIT]>) | - | AAR javap + 샘플 100f 사용 (F6) |
| LocationData | data class | `...model.LocationData` | (latitude, longitude, altitude, heading, confidence, timestampMillis) | - | AAR javap (F6) |
| TextToSpeech 3.3.1 | `TextToSpeech(context, Mode)` | `ai.pleos.playground.tts.TextToSpeech` | `initialize()`, `initialize(OnInitializeListener)`, `addEventListener/removeEventListener(EventListener)`, `setLocale(Locale)`, `speak(String, Boolean)`, `stop()`, `release()`, `registerApp(...)`(서버 모드) | TTS_SERVICE | AAR javap (F9) |
| Mode(enumerate) | enum | `ai.pleos.playground.tts.constant.Mode` | SERVER / ON_DEVICE / HYBRID | - | AAR javap (F9) |

샘플별 빌드 파라미터 (F13):

| 샘플 | Gradle | AGP | Kotlin | minSdk | compileSdk | 비고 |
|---|---|---|---|---|---|---|
| vehicle-app | 8.10.2 | 8.8.2 | 2.0.0 | 23 | 35 | libs.versions.toml에 vehicleSdk=2.3.1(공개 Maven에 없음→2.3.2 사용) |
| navi-app | 8.10.2 | 8.8.0 | 1.9.22 | 33 | 35 | Hilt+kapt 사용(우리는 미채용), naviHelper=3.0.5 |
| fusedlocation-app | 8.10.2 | 8.8.2 | 2.0.0 | 24 | 35 | debug 매니페스트에 ACCESS_MOCK_LOCATION, fusedLocation=1.1.3 |

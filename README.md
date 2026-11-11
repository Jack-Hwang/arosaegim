# 아로새김 (가칭)

**세대와 세대를 이은 가족 여행 기록.**
같은 문화유산을 세대가 다시 찾을 때, 차가 '그때'의 기록과 '지금'의 순간을 나란히 이어 주는
가족 스탬프 여행 차량용 앱.

- 대상 플랫폼: Pleos Connect (안드로이드 오토모티브) — 검증 환경: Pleos Connect 에뮬레이터 v2.1.0
- 구성: `android/` 차량 앱 · `backend/` 소규모 REST 서버 · `web/` 가족 모바일 웹 · `tools/` 시연 스크립트 · `docs/` 설계·개발 로그

## 구성 요소 상태

| 요소 | 상태 | 비고 |
|---|---|---|
| `android/` 차량 앱 | **코드 완료** | 주행/정차 전환·도착 스탬프·퀴즈·그때와 지금·스탬프북·맛집·코스 선택·오늘의 기록·가족 세션·방문 동기화. 단위 테스트 72/72 통과. 스모크 U1~U14 실측 완료(`docs/DEV_LOG.md` 표 — PASS 8·FAIL 2[원인 확정]·부분 2·고정 2) |
| `backend/` REST 서버 | **코드 완료** | API 전체(가족·코스·방문·기억·영수증·구성원·스탬프북·포인트·지표) + 캐시백 규칙(숙소 3박·사진 필수+해시·pending·device_limit). pytest 76/76 통과(신규 상속자·순번·누적거리·접기·이름표 API 포함) |
| `web/` 가족 모바일 웹 | **코드 완료** | 7화면(가족 코드·동의·코스·그때 기록·타임라인·가족 관리·영수증). 백엔드 `/web` 정적 서빙 |
| `tools/` 시연 스크립트 | **완성** | drive-demo-route · grant-permissions · record-demo (PowerShell 5.1/7 호환, 구문 검증 PASS) |
| `docs/` | 갱신 완료 | ARCHITECTURE(실측 반영) · DEV_LOG(상시) · ATTRIBUTION |

**남은 것 (코드 아님).**
1. 사용자 GUI 실측 — U7 완결(기어 P→D restrict 수신)·U6 청취·U11/U13(Virtual Console 좌석·주행거리)·음성 청취.
2. 기획서 문장 확정 — DEV_LOG 표 기준으로 갱신(00문서 13절·15절 지시).
3. GitHub 공개 — 릴리스 체크리스트(아래) 진행.

## 빠른 시작

### 차량 앱

```bash
cd android
./gradlew assembleDebug          # 빌드
./gradlew test                   # 도메인·시드 단위 테스트 (72개)
# Pleos Connect 에뮬레이터에 설치:
adb install -r app/build/outputs/apk/debug/app-debug.apk
# dangerous 권한 부여(권한 UI 크래시 방지):
powershell ./tools/grant-permissions.ps1 -Package app.arosaegim
```

- **릴리스 APK**: `apk/arosaegim_아로새김_release.apk`(39.6MB) — apksigner verify 통과(CN=arosaegim, v0.1.0)
- **에뮬레이터 설치 가이드**: `glm/05_INSTALL_EMULATOR.md` — Android Studio 없이 cmdline-tools만으로
  구축·부팅 가능(실측 — `docs/DEV_LOG.md` F28). **Cold boot 필수**(Quick boot는 차량 HAL이 동작하지 않음).
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 브라우저 `http://127.0.0.1:8088/` (기어 D/P·좌석·주행거리).

### 백엔드 + 가족 웹

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --port 8000   # 백엔드 실행
pytest                             # 서버 테스트 76개
# 웹 접속: http://localhost:8000/web/  (에뮬레이터 앱은 http://10.0.2.2:8000 — 실측 PASS)
```

## 테스트 방법

```bash
cd android
./gradlew clean testDebugUnitTest assembleDebug
# 기대: BUILD SUCCESSFUL, 72/72 — SeedRepository · CourseProgress · Haversine · ModeStateMachine
#       · StampBook 9 · StampEngine 6 · ThenNowResolver 12 · TripSession(tripKm/partySize 포함) 등
```

- SDK 스모크 U1~U14는 앱 진단 화면(Diagnostics)에서 실행 — 실측 결과는 `docs/DEV_LOG.md` 표(U1~U14 전부 기록).
- 에뮬레이터 수동 E2E 체크리스트(릴리스 전 매번)는 `glm/03_TEST_AND_DEMO.md` 5-2절.

## 새 PC에서 재현하는 순서

1. 에뮬레이터 구축 — `glm/05_INSTALL_EMULATOR.md` 순서대로(Android Studio 없이 가능).
2. `cd android && ./gradlew assembleDebug` → APK 빌드.
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk` → 설치.
4. `powershell ./tools/grant-permissions.ps1 -Package app.arosaegim` → FUSED_LOCATION 권한 부여(-AllowMockLocation 옵션 선택).
5. 백엔드 실행(`uvicorn app.main:app --port 8000`) → 웹에서 가족 코드 발급 → 차량 시작 화면에 입력.
6. `./tools/drive-demo-route.ps1 -Route tools/routes/gyeongju-demo.json` → 가상 위치로 데모 코스 주행(국립경주박물관 → 대릉원 → 첨성대).
7. `./tools/record-demo.ps1` → 화면 녹화(1회 최대 3분), `demo/raw/날짜_시각.mp4` 저장.

## 시연 녹화 (소리 포함 방법)

화면 녹화는 `tools/record-demo.ps1`로 한다. **앱 안내 음성은 차량 오디오 경로로 나가기 때문에
일반 화면 녹화에는 소리가 잡히지 않는다.** 소리가 필요하면 [REPORTED]:

1. 에뮬레이터를 오디오 WAV 저장 옵션으로 띄운다.
   - 환경변수 `QEMU_AUDIO_DRV=wav`, `QEMU_WAV_PATH=<WAV 저장 경로>` 설정
   - 에뮬레이터 실행 인자에 `-audio wav` 추가
2. 녹화(화면)와 WAV(소리)를 얻은 뒤 편집 단계에서 합친다.

자세한 제약(1회 최대 3분 등)은 `tools/record-demo.ps1` 머리 주석 참고.

## 한계 (중요 — 시뮬레이션 범위)

- **이 에뮬레이터에서 안내 음성이 나지 않는다.** Android TTS 초기화 실패 + Pleos TTS onReady 미수신의
  겹침으로(F54), 환경 한계일 가능성이 있다. 영상은 오디오 별도 저장 절차(위)로 대비한다.
- **제휴처·적립 재원·정산은 아직 없다.** 데모의 제휴 맛집과 적립률(1%/2%)은 가상의 예시 데이터다.
  실제 상호를 사용하지 않는다. 협의가 선행돼야 하는 영역이다.
- **실제 포인트 발행에는 법규 검토와 결제 수단 연동이 필요하다.** Pleos의 결제 SDK는 공개 SDK 목록에 없고
  문서상 "준비 중"으로만 언급돼 있다(초안 성격의 경로, 변동 가능).
- **영수증 진위 확인(OCR·가맹점 대사)은 구현하지 않았다.** 인증 화면의 금액 입력과 사진은 신뢰 전제다.
  실제 서비스에는 가맹점 매출 대조가 필요하다.
- **"실제 서비스에서는 보호자 본인 확인이 필요하다."** 미성년 구성원 등록 시 데모는 간이 동의 체크만 둔다.
- **물려주기는 법적 상속이 아니다.** '이어받을 사람 지정·관리 권한 이전'은 생전에 직접 지정하고
  넘기는 기능이다. 사망 확인·자동 승계 같은 법적 기능은 만들지 않았다.
- **적립은 전부 시뮬레이션이다.** 실제 결제·포인트·지역화폐 연동 코드는 만들지 않았고,
  모든 화면·응답에 '포인트 캐시백(예시)' 표기를 둔다. 추첨·확률형 보상은 없다.
- 나머지 실측 한계(TTS 환경·내비 콜백 지연·좌석 미조작 시 인원 숨김·odometer 트립 채널)는
  `docs/ARCHITECTURE.md` 8절과 `docs/DEV_LOG.md` F/Q 번호로 추적한다.

## Virtual Console 실측 세션 체크리스트 (U7·U11·U13 완결용)

에뮬레이터 기동(내비 지도 포함 화면 확인 시 `-gpu host` 권장):

```bash
C:	oolsndroid-sdk\emulator\emulator.exe -avd PleosConnect -gpu host
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
tools\grant-permissions.ps1 -Package app.arosaegim
adb forward tcp:8088 tcp:8088     # Virtual Console → 브라우저 http://127.0.0.1:8088/ (탭 유지 필수)
```

부팅 직후 내비가 앱 창을 선점하면: GNB의 앱 서랍(격자) → 아로새김 아이콘으로 전환하거나, 아로새김 실행 직후가 아니면 `am start`를 두 번 반복한다.

| 순서 | 조작 | 확인 |
|---|---|---|
| 1 | 진단 화면(U1~U14 목록)에서 **U7 탭** → 관찰 시작 | "주행 상태 관찰" 카드가 실행 중이 됨 |
| 2 | Virtual Console **기어 D** | 앱이 주행 화면으로 전환(DO 상태 수신) — U7 완결·T6 전환 확인 |
| 3 | 기어 P 복귀 → 진단에서 U7 카드 결과 확인 | 수신 기록( restrictions 값) 갱신 |
| 4 | **U11 탭** → Virtual Console Convenience **Seat Occupancy** 2~3석 점유 → U11 재탭 | partySize가 null 대신 인원 수 반환 → "오늘은 N명…" 문장 표시 |
| 5 | **U13 탭** → Motion **Odometer** 값 조작 → U13 재탭 | km 단위·값 변화 확인(T5-1 단위 확정) |
| 6 | **U6 재탭** — 기동 후 수 분 경과 시점에서 | Pleos TTS onReady 미수신이 지속되는지(F54) |
| 7 | 결과를 `docs/DEV_LOG.md` U 표에 기록 → 기획서 문장 확정 | |

## 문서

- `docs/ARCHITECTURE.md` — 아키텍처·모듈 지도·스모크 실측 요약·예상 기술 Q&A 18문항
- `docs/DEV_LOG.md` — 확인된 SDK 사실(F#), 미해결 질문(Q#), 스모크 결과표(U1~U14 전체)
- `docs/ATTRIBUTION.md` — 참고 자료·콘텐츠 출처·라이선스

## 규칙 (요약)

- 주행(D) 중에는 퀴즈·긴 텍스트·입력을 표시하지 않는다(안전 규칙).
- 위치는 도착 판정에만 사용하고 저장·전송하지 않는다. VIN은 읽지 않는다.
- 앱은 서버 없이 내장 시드로 핵심 흐름이 끝까지 동작한다.
- 좌석 신호는 인원 수 비교에만 쓴다(좌석별·사람별 저장 없음). 읽지 못하면 문장을 숨긴다.
- Back 키는 앱을 종료하지 않는다(moveTaskToBack).

## 릴리스 체크리스트 (glm/03 문서 6절)

- [ ] 저장소에 실명·개인 이메일·개인 계정이 없다 (`git log --format='%an %ae' | sort -u` 확인 — 공개 직전 재확인)
- [x] APK 파일명 `팀명_앱이름_release.apk` — '팀명'은 별칭(arosaegim), `apk/arosaegim_아로새김_release.apk` 배치 완료(apksigner verify 통과 — DEV_LOG 11-11)
- [x] README에 앱 이름·한 줄 소개·실행 방법·테스트 환경·APK 위치 기재 (이 파일이 담당)
- [x] release 서명 키는 저장소에 올리지 않았다 (`*.keystore`·`keystore.properties` gitignore 등록 — DEV_LOG 11-11)
- [x] `docs/ATTRIBUTION.md`에 콘텐츠 출처·라이선스 기록 완료
- [ ] 비공개 저장소라면 공지된 심사용 계정을 Collaborator로 초대하고 접근 확인 (공지 대기 중)

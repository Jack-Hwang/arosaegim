# 에뮬레이터 설치 · 수동 확인 안내

Pleos Connect 차량용 앱을 에뮬레이터에서 빌드·실행·확인하는 절차를 모은 문서다. 처음 구축할 때와, 릴리스 전에 매번 확인할 때 함께 쓴다.

## 1. 요구 사항과 내려받기

| 항목 | 내용 |
|---|---|
| 가상화 | BIOS의 가상화 + Windows 하이퍼바이저 플랫폼(WHPX) 활성 — 재부팅 필요 |
| 메모리·디스크 | RAM 16GB·디스크 여유 16GB 이상 권장(시스템 이미지는 압축 약 4.1GB, 해제 후 약 20GB — 참고값) |
| Android Studio | 공식 문서가 안내하는 버전(참가팀은 Ladybug 이후 버전 사용 보고) |
| JDK | Android Studio에 포함된 JDK 사용 |

내려받는 구성 요소: Android Studio(공식 사이트), Android SDK 구성 요소(Platform-Tools·Emulator·Build-Tools·Platform 34·35), Pleos Connect v2.1.0 시스템 이미지(x86_64, API 34 — Pleos 공식 저장소), 공식 샘플 3종(Pleos 공식 GitHub). 설치 중 Android SDK 라이선스와 Pleos 시스템 이미지 라이선스에 동의해야 한다.

## 2. 설치 순서

1. Windows 하이퍼바이저 플랫폼 켜기: 제어판 > 프로그램 > Windows 기능 켜기/끄기 > "Windows 하이퍼바이저 플랫폼" 체크 > 재부팅. VMware 같은 다른 하이퍼바이저를 함께 쓰면 성능·기능 제한이 생길 수 있다.
2. Android Studio 설치 후 첫 실행 마법사에서 SDK 기본 구성 요소 설치.
3. Pleos 시스템 이미지 추가: Settings > Languages & Frameworks > Android SDK > SDK Update Sites > 추가 — 이름 `Pleos Connect System Image`, URL `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`(Mac Apple Silicon은 `crp-arm64-sys-img.xml`).
4. SDK Platforms 탭 > Show Package Details > Android 14(API 34)의 Pleos Connect v2.1.0 체크 > Apply(라이선스 동의).
5. AVD 생성: Device Manager > Create Virtual Device > Automotive > Pleos Connect v2.1.0. Advanced Settings에서 **Boot option = Cold boot(필수 — Quick boot는 차량 HAL이 동작하지 않음)**, RAM 4096MB 이상.
6. 부팅 후 Pleos 런처가 보이면 성공. 검은 화면이면 아래 3절 "자주 막히는 곳"의 복구 방법을 쓴다.
7. Virtual Console 연결: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/` — 브라우저 탭을 열어 둔 동안에만 동작한다(1초마다 신호 유지).

## 3. 자주 막히는 곳

| 증상 | 대응 |
|---|---|
| 부팅 후 화면이 검고 차량 서비스가 반복 종료 | Device Manager에서 Wipe Data 후 Cold boot |
| Virtual Console이 `emulator_inactive` | 브라우저 탭을 연 채 Wipe Data + Cold boot |
| 내비 지도 표시 때 에뮬레이터가 꺼짐 | `-gpu swiftshader_indirect` 대신 `-gpu host`로 실행 |
| STT에 마이크가 안 들어옴 | `-allow-host-audio` 또는 Extended Controls > Microphone에서 호스트 오디오 입력 허용 |
| dangerous 권한 요청 화면이 크래시 | `adb shell pm grant --user 10 <패키지> <권한>` |
| 시연 영상에 소리가 없음 | 안내 음성이 차량 오디오 경로로 나가서 일반 화면 녹화에 안 잡힌다 — 아래 5절 녹화 참고 |

## 4. adb 명령 모음

```bash
adb devices
adb install -r path/to/app-debug.apk
adb shell am force-stop <패키지>
adb shell pm clear <패키지>                       # 앱 데이터 초기화

# 권한(에뮬레이터에서 dangerous 권한 UI가 크래시하므로 직접 부여)
adb shell pm grant --user 10 <패키지> pleos.car.permission.FUSED_LOCATION
adb shell appops set --user current <패키지> android:mock_location allow   # 필요할 때만

# 가상 위치: 경도(lon)가 먼저, 위도(lat)가 나중
adb emu geo fix <lon> <lat> [고도 [위성수 [속도(knot)]]]

# 주행 규제 강제 on/off (개발 중 주행 중 UI 확인용)
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
adb shell cmd hmg_car_service --service drivingstate driving_regulation off

# Virtual Console
adb forward tcp:8088 tcp:8088

# 화면 녹화(1회 최대 3분)
adb shell screenrecord /sdcard/demo.mp4
adb pull /sdcard/demo.mp4 .

# 현재 주행 규제 상태 확인
adb shell dumpsys car_service --services CarUxRestrictionsManagerService
```

`tools/`의 스크립트: `drive-demo-route.ps1`(가상 위치로 데모 코스 주행) · `grant-permissions.ps1`(권한 부여) · `record-demo.ps1`(화면 녹화). 소리가 필요하면 에뮬레이터를 `-audio wav` 옵션과 환경변수(`QEMU_AUDIO_DRV=wav`, `QEMU_WAV_PATH=<경로>`)로 띄워 오디오를 따로 저장하고 편집 때 합친다.

## 5. 에뮬레이터 수동 확인 목록(릴리스 전 매번)

1. 앱 설치 → 권한 부여 스크립트 실행 → 앱 실행
2. 서버 켠 상태/끈 상태 모두에서 코스가 보인다
3. 기어 D: 주행 화면만, 퀴즈 진입 불가
4. geo fix 주행: 첫 장소 반경 진입 시 스탬프 1회, 음성 안내
5. 기어 P: 퀴즈·그때와 지금 표시
6. 앱을 백그라운드로 보냈다가 복귀: 진행 상태 유지
7. 서버 끊김 중 방문 → 로컬 큐 → 서버 복구 후 전송
8. 로그·화면에 개인정보(실명·VIN·실시간 위치 경로)가 없다
9. Back 키로 앱이 종료되지 않는다(태스크 보존)
10. 녹화 영상에 소리가 들어간다(별도 오디오 합성 포함)

추가 확인 항목: 숙소 영수증(숙박 기간에 스탬프 날짜 포함) 승인과 기간 밖 거부, 사진 없는 영수증 거부, 세로 창에서 '그때와 지금' 카드 세로 배치, 야간 테마 가독성, 진단 화면의 구현/검증 분리 표시.

실패 시 복구 순서: 앱 force-stop 후 재실행 → 서버 재시작 → Wipe Data + Cold boot → 백업 영상 재생.

## 6. 릴리스 체크리스트

- [ ] 저장소에 실명·개인 이메일·개인 계정이 없다(`git log --format='%an %ae' | sort -u` 확인)
- [ ] APK 파일명 `팀명_앱이름_release.apk`, `apk/` 폴더 또는 GitHub Releases
- [ ] README에 앱 이름·한 줄 소개·실행 방법·테스트 환경·APK 위치
- [ ] release 서명 키는 저장소에 올리지 않았다
- [ ] `docs/ATTRIBUTION.md`에 콘텐츠 출처·라이선스 기록
- [ ] 영상·화면 어디에도 실명·이메일·개인 계정·실제 가족 얼굴이 나오지 않는다(에뮬레이터 상단 계정 표시 포함)
- [ ] 캐시백 장면에는 "포인트 캐시백(예시)"·"예시(시뮬레이션)" 문구가 보인다

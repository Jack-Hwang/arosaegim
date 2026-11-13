# ATTRIBUTION — 참고 자료와 라이선스 기록

> 공식 문서·샘플·콘텐츠 출처를 기록한다. 라이선스가 명시되지 않은 자료는 "통째 복사 금지" 원칙(00 문서 2-1 #3)에 따라 호출 패턴·빌드 설정만 참고했음을 남긴다.
> 표준 라이브러리(kotlin-stdlib, AndroidX/Compose, kotlinx.coroutines·serialization 등 Maven Central/Google Maven 배포물)의 개별 라이선스 표기는 생략한다 — 각 배포 채널의 표준 Apache-2.0 계열 라이선스를 따른다.

## 참고한 공식 샘플 (reference/, 저장소에서 제외됨)

| 자료 | 위치 | 사용 방식 | 라이선스 |
|---|---|---|---|
| pleos-sample-vehicle-app | https://github.com/Pleos-Playground/pleos-sample-vehicle-app | Vehicle SDK 초기화/콜백 패턴, gradle 래퍼(Gradle 표준 배포물) 참고 | 라이선스 파일 없음 → 통째 복사 금지, 패턴만 참고 |
| pleos-sample-navi-app | https://github.com/Pleos-Playground/pleos-sample-navi-app | NaviHelper 초기화·리스너 패턴, RouteInfo 생성 예시 참고 | 동일 |
| pleos-sample-fusedlocation-app | https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app | FusedLocation 근접 알림·mock location 패턴 참고 | 동일 |

- gradle-wrapper.jar / gradlew / gradlew.bat: Gradle 공식 배포물(Apache-2.0, https://gradle.org). 샘플 저장소에 포함된 표준 래퍼를 그대로 사용(Gradle 8.10.2).

## 참고한 공식 문서

| 문서 | URL | 용도 |
|---|---|---|
| Pleos Connect SDK 개요 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ | SDK 사용 순서·권한 확인 |
| NaviHelper 문서 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/ | requestRoute·이벤트·권한 |
| FusedLocation 문서 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/ | 근접 알림·프로젝트 셋업 |
| Vehicle 문서 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/ | 도메인 객체·capability |
| TextToSpeech 문서 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/ | 모드·locale·권한 |
| 에뮬레이터 셋업 가이드 | https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator | 03 문서 1절 체크리스트 |
| **Pleos Connect UI Design Guide** | https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro (하위 34쪽 포함, 2026-11-10 확인) | 화면 구조·설계 원칙·색/타이포 토큰·컴포넌트 규칙 — 요구 문서 04(UI 가이드) 요약의 출처. 앱의 AroColors 토큰 구조와 3줄 규칙·76dp 버튼 등이 이 가이드를 따른다 |
| Pleos Virtual Console 가이드 | Pleos 공식 문서 — 요구 문서 01의 8절에서 인용 정리 | 에뮬레이터 Virtual Console(adb forward tcp:8088) 조작 가능 항목 확인용 — 기어 전환(U7 완결)·Seat Occupancy(U11)·주행거리(U13) 실측 절차의 근거 |

## 서체

| 서체 | 포함 여부 | 비고 |
|---|---|---|
| Asta Sans (Pleos Connect 시스템 서체) | **앱에 미포함** | UI Design Guide는 "가이드에서 내려받아 포함하거나 시스템 기본 서체를 쓰는" 두 가지를 허용한다. 현재 앱은 서체 파일을 포함하지 않고 **시스템 기본 서체를 사용**한다. 서체 파일을 포함할 경우 이 표에 출처·라이선스를 먼저 기록한다 |

## 직접 확인한 1차 자료 (코드 시그니처의 근거)

- Pleos 공개 Maven 인덱스: `https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/<artifact>/maven-metadata.xml`
- SDK AAR 파일 4종(Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1)을 내려받아 javap로 시그니처 확인 — 세부 내용은 `docs/DEV_LOG.md` F1~F12
- Android SDK 동봉 android.car.jar(API 35, `platforms/android-35/optional/`) — CarUxRestrictionsManager 시그니처 확인(F15·F16)

## 콘텐츠 (시드·이미지·기획)

| 항목 | 출처 | 라이선스/비고 |
|---|---|---|
| seed.json 퀴즈·해설·코스·해설 문장 | 직접 작성(초안, reviewed=false) | 공식 자료 검수 전까지 '초안' 표시 유지 |
| then_placeholder.png | 직접 생성한 1x1 픽셀 자리표시 이미지 | 저작권 문제 없음 |
| 앱 로고 아이콘(ic_launcher) | 직접 제작한 단순 드로어블 | 저작권 문제 없음 |
| **화면 시안 이미지 01~17** | 직접 제작(발주자 — HTML 원본 `make_mockups.py` 포함) | 화면 설계의 기준 자료. 시안 15~17은 구상·참고용이고 16·17은 구현 대상이 아니다(00 문서 14절). 저장소에는 넣지 않는다(00 문서 3절 저장소 위치 규칙) |
| **시드 heritage 내용** — 성덕대왕신종(주조 771년, 통일신라 · 국립경주박물관 소장) | 직접 작성(초안, `reviewed=false`) | 시드 `places[].heritage`에 사용(gj-museum). '공식 자료로 검수 필요' 표기를 시드·문서에 유지한다 — 소장처·연도는 공식 자료 확인 전까지 확정 서술하지 않는다 |
| tools/ 시연 스크립트 3종·routes/gyeongju-demo.json | 직접 작성 | 참가팀 스크립트를 보고 베끼지 않고 03 문서 명세대로 작성 |
| `letter_demo.ogg` (음성 편지 예시 오디오) | **미준비** | 파일이 아직 없다(DEV_LOG Q14). 발주자가 합성음성 또는 직접 녹음으로 준비하면 `android/app/src/main/assets/seed/letter_demo.ogg`로 넣는 순간 T9-2 재생 포트가 활성화된다. 넣을 때 이 표에 출처·제작 방식을 먼저 기록한다. 고인의 목소리를 AI로 만들거나 흉내 내지 않는다(00 문서 11-2) |
| 이후 추가되는 사진·일러스트 | (미정) | 공공누리 1유형 또는 직접 제작만 사용, 이 표에 추가 |

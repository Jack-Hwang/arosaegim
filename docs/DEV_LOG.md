# DEV_LOG

> 작업 로그. 최신이 위. 스모크 테스트 결과표(U1~U16 및 D1·D2)를 맨 위에 유지한다.
> 날짜 시각은 로컬(KST) 기준.

## 2026-11-14 (새벽) — 11번 문서(SDD·명세·품질 통합) 수행 통합 보고

- 한 일: 11번 문서의 산출물 A~J를 4+5 병렬 작업으로 수행해 통합. 번호 안내: 이 섹션의 F101~F106은 이 섹션 전용이다 — 아래쪽 12번 문서 섹션의 F91~F92와 번호가 겹치므로 섹션 소속으로 구분한다(2026-11-11 선례와 같다). 같은 P0 항목을 12번 문서 작업과 병렬로 수행한 부분(ac 정본·ADR·SPEC_TRACE·tools·가족 코드 값 정리)은 작업 트리에서 합쳤고 최종 상태 기준으로 전부 검증했다.
- 통합 검증: **앱 단위 시험 197/197(25파일, 실패 0)**·서버 pytest **87/87**·assembleDebug·assembleRelease 성공·APK 40,484,425바이트 갱신. `tools/spec_check.py` 오류 0(수용 기준 129건·시험 이름 없음 0)·`eval_retrieval` 게이트 통과·드리프트는 커밋 후 HEAD 측정과 문서(197·25·40,484,425)가 맞아 통과 예정. 초기 1건 실패(ApiClientHttpContractTest SocketTimeout)는 병렬 편집 중 일시적 소켓 타임아웃으로 재실행 통과.
- 확인된 사실(F-번호):
  - **F101 PIN 거절 표시(관문 ⑤)**: 서버가 코드+PIN을 실어 보낸 요청의 401·403으로 거절하면 세션 메모리의 PIN을 치워 재전송을 멈춘다(큐는 보존, 새 PIN 입력으로 재개). 배선: VisitQueue.flush(가장 좁은 지점)→FamilySessionStore.markPinRejected→TripSession 안내 한 줄. 참고: 현재 방문 전송은 familyCode 미연결이라 서버가 먼저 family_code_required로 거절 — 잠금 횟수와 무관하며 가족 코드 연동 시 안전장치가 자동으로 활성화된다. ParkedScreen·DiagnosticsViewModel의 조회 경로 거절 표시 연결은 후속 과제.
  - **F102 수용 기준 정본**: 10기능(F10 화면 이동·메뉴 포함)·129건 — [코드 확인] 71·(설계) 33·(계획) 12·[확인] 7·[일부 확인] 5·[실패] 1. 시험 연결(unit+server) 66건·에뮬레이터 실측 근거 12건·none 10. F10은 정본만 있고 서술 문서는 (계획).
  - **F103 근거 검색 체계**: 도구 5종(표준 라이브러리만)·골든셋 42건 — Hit@5 1.000·MRR 0.818·nDCG@5 0.748(게이트 통과).
  - **F104 ADR 재작성**: 14건 전부 채택(기각한 대안 각 1~2건). ADR-14 미성년 보호자 동의는 서버 코드·시험으로 실존 확인(403 거부). ADR-06 제목만 사실에 맞게 보정("도착 판정과 화면 거리 표시에만").
  - **F105 관문 ③·④ 판단**: 포인트 일일 합계 명시 상수 불필요(건당 1,000,000원·하루 5건의 구조적 이중 상한으로 최대 100,000점/일이 닫힘 — 규칙 변경 없음). 전송 재시도 예산 상수 불구현(flush 호출자가 도착 1곳뿐 — 공회전 구조적으로 없음, 동결 전 새 동작 회피).
  - **F106 저장소 공개 전 정리**: README 시험 수 불일치·Collaborator 초대 항목·깨진 문서 참조·실제 발급 코드 값(예시 가짜로 교체) 등 22파일 정리. 남는 검색 1종은 서버 응답 모델 식별자(HeritageBriefOut)의 부분 일치뿐 — API 계약이라 유지.
- 보고(11번 문서 §8 형식):
  - ① 최종 커밋: 이 섹션을 포함한 커밋(푸시 후 origin/main에서 해시 확인).
  - ② 시험 수: 앱 197/197(25파일)·서버 87/87(9파일).
  - ③ 만든 문서·도구: docs/spec/(헌장·F1~F9 서술·ac 정본 10파일)·docs/SPEC_TRACE.md(도구 생성)·docs/ADR.md(재작성)·docs/QUALITY.md·docs/AI_SCOPE.md·docs/SPEC_INDEX.md·docs/TRACEABILITY.md(정리)·tools/(명세 점검·사실 대장·드리프트·근거 검색·검색 평가 5종).
  - ④ 못 한 것과 이유: 아래 "이어서 완료" 블록에서 기획 검증 최종본(ac_final) 적용(F107)·F10 서술 문서(F108)·PIN 거절 조회 경로 연결(F109)이 완료됐다. 남은 것: E1 전체 경로·U6 청취·Q27 확정 실측(에뮬레이터 GUI 세션).
  - ⑤ **기획서 반영 요청:** 앱 시험 수 190→**197**(25파일)·APK **40,484,425바이트**·수용 기준 **129건(10기능 — F10 화면 이동 포함)**·상태 분포(위 F102)·헌장·품질 체계·AI 판정 별지 요약은 docs/spec/00_CHARTER.md·docs/QUALITY.md·docs/AI_SCOPE.md·docs/SPEC_INDEX.md 에서 인용 가능.

### 이어서 완료(2026-11-14 계속) — ac_final 적용·F10 서술·PIN 거절 조회 경로

- 확인된 사실(F-번호):
  - **F107 수용 기준 정본을 기획 검증 최종본(ac_final)으로 교체**: 129건→**136건**(F6 AC10·F9 AC07 내용 변경·F10 +7)·상태 분포 [코드 확인]72·(계획)19·(설계)32·[확인]7·[일부 확인]5·[실패]1·확인 방법 code 40·unit 40·server 27·none 17·emulator 12. ac_final 원칙 준수 — 정본 JSON을 한 글자도 바꾸지 않고, 틀린 곳은 기획서 반영 요청으로 알린다. Q31(기능 문서 수치 동기화) 해소.
  - **F108 F10 서술 문서 신설**(spec/F10_화면이동메뉴홈설정.md, 105줄): F10은 **(계획) 상태의 차기 기능**임을 문서 첫 줄에 명시 — [코드 확인] 4건(현재 동작)과 (계획) 19건(접수 뒤 구현용 수용 기준)을 구분해 구현된 것처럼 쓰지 않았다.
  - **F109 PIN 거절 표시를 조회(GET) 경로에 연결**: ParkedScreen의 8개 조회(자리 이름표·그때와 지금·설정·물려주기·지표)가 401·403·429(AuthException)로 거절되면 세션 PIN을 치워 재조회를 자동으로 멈춘다(isAuthFailure를 429 포함으로 확장 — ApiClient의 429→AuthException 매핑과 합류). 진단 화면 지표 조회도 동일 배선 + session_pin_rejected 안내 문구 전환. 시험 +9(ParkedAuthFailureTest — 401·403·429·우연 일치 방어 등 9케이스). 큐·방문 기록은 보존.
- 통합 검증(갱신): **앱 216/216(27파일)·서버 137/137(11파일 — 포인트 골든셋 파라미터 50건 포함)**·spec_check 오류 0(136건·시험 이름 없음 0)·eval 게이트 통과·드리프트 전 항목 일치(커밋 후 HEAD 기준).
- ⑤ **기획서 반영 요청(갱신):** 앱 시험 수 **216**(27파일)·서버 시험 **137**(골든셋 파라미터화 포함 — 소스 함수 88·케이스 50)·수용 기준 **136건**·APK 40,484,425바이트는 동일. F10은 접수 뒤 구현 계획 기능으로 별지에 (계획)으로 표기.

### 이어서 완료(2026-11-14 계속 2) — 13번 문서 T1(로그아웃·가족 전환 정리 일원화)

- 시작 시점 규정: 13번 문서는 "접수 메일 뒤(11/16~) 시작, 접수 전에는 커밋 금지 — 사용자가 앞당기면 **T1만**"이라 규정했고, 사용자의 '계속 구현' 지시로 **T1만 선수행**했다(T2~T6는 접수 메일 뒤로 남긴다).
- 확인된 사실(F-번호):
  - **F110 로그아웃·가족 전환 정리 일원화(13번 T1·현황 e 해소)**: `session/SignOutFlow.run()` 신설 — 고정 순서 ①가족 세션 해제 ②로컬 파일 삭제(SignOutCleanup) ③'나중에 정하기' 메모리 ④TripSession stop→resetCourse(스탬프·목표·거리·인원·로컬 방문 기록 초기화 — stop이 먼저라 resetCourse 끝의 감시 재시작은 걸리지 않는다) ⑤퀴즈 진행 ⑥코스 시작 상태 ⑦전송 큐 메모리. 한 단계 실패도 나머지를 계속하고 결과 줄을 돌려준다(멱등). FamilySessionStore는 Context 의존이라 JVM 시험 불가 → 최소 인터페이스 `FamilySessionCleaner`로 추출(AppContainer가 위임 조립). `VisitQueue.clearMemoryState()` 추가(메모리만 — 파일은 ② 담당). 진단 화면 `signOutFromCar()`는 SignOutFlow 실행 한 번으로 재배선. 시험 +4(`SignOutFlowTest` — 접속 상태 전체 초기화·부분 실패 지속·둘러보기 정리·멱등).
  - **F111 서버 429(잠금) 대응 시험 확장(12번 문서 P2 합류분 재확인)**: ApiClient의 401·403·429→AuthException 매핑으로 PIN 잠금 429도 앱의 재전송 중단·한 줄 안내 경로에 합류한다(F101·F109 경로와 같다) — 이번 T1 재배선으로 정차 화면 8개 조회·진단 지표·방문 전송 세 경로 모두 동일 거절 동작을 공유한다.
- 통합 검증(갱신): **앱 220/220(28파일)·서버 137/137**·spec_check 오류 0(136건)·완료 명령 ② `signOut(` 호출처가 SignOutFlow 조립(AppContainer 위임)·FamilySessionStore 정의뿐.
- ⑤ **기획서 반영 요청(갱신):** 앱 시험 수 216→**220**(27→**28파일** — SignOutFlowTest 신설)·APK 40,484,425바이트 동일. 13번 문서의 F10 화면(메뉴·홈·설정)은 (계획) 유지 — T2~T6는 접수 후 수행 예정.

### 이어서 완료(2026-11-14 계속 3) — 13번 문서 T2~T5 병렬 구현(사용자 '다 병렬적으로' 지시 선수행)

- 확인된 사실(F-번호):
  - **F112 T2 메뉴 구현**: 머리글 trailing에 ☰ 메뉴 버튼(76dp 이상·`menu_label`)·`MenuPanel`(오른쪽 패널 — 폭 27%·최소 240dp·닫힌 채 시작·애니메이션 없음·가족 블록은 마스킹 코드)·`MenuPolicy` 순수 정책(주행(D)=버튼 숨김·가로 창=본문 축소·세로(1:1) 창=겹침+왼쪽 어둡게·세션 없음=버튼 없음)·패널 열림 상태는 프로세스 수명 홀더로 복원. `MenuPanelRow`의 onClick을 마지막 파라미터로 정렬(후행 람다 바인딩). 시험 11건.
  - **F113 T3 홈 구현**: `HomeContent.resolve`(이어서 하기·지난 방문·첫 기록 모드 — 값 없으면 줄만 숨김)·`HomeGate`(세션+정차+앱 열 때 홈 먼저·**도착 직후는 스텝1 우선(AC20)**·주행 시 닫힘)·`HomeScreen`. ModeRouter 무변경. 시험 16건(게이트 5 포함).
  - **F114 T4 설정·가족 전환 구현**: `ui/settings/` 신설 — SettingsScreen(가족[마스킹 코드·가족 전환·로그아웃]·표시[prefs 스위치 — 서버 값 우선·실패 시 기기 임시값]·개인정보 안내)·FamilySwitchConfirmScreen(지워지는 것/그대로 남아요·확인)·FamilyCodeMask(앞2·뒤2 마스킹)·SettingsState(서버 값 우선 판정·토글 반전·keptHiddenPlaceIds 유지). ParkedScreen에 라우트(설정↔가족 전환) 렌더링 배선 — [로그아웃하고 바꾸기]는 SignOutFlow 실행 뒤 시작 화면(REORDER_TO_FRONT). PartyFoldLocalStore.writeAllHidden 추가. 시험 10건(FamilyCodeMask 5·SettingsState 5).
  - **F115 T5 확인 과제 결과**: 시스템 프로필 = 운전자 2개(user 0·10, 실행 중)·pm get-max-users = 10,000 지원·**프로필별 앱 데이터 분리 확인**(user 0의 files/ 없음·user 10의 files/는 profileInstalled만 — family_session도 프로필별로 따로 저장된다 [일부 확인: 세션 값 생성 후 재확인 필요]). 세로 창(1:1)은 MenuPolicy OVERLAY 분기 구현·시험 완료 — 화면 캡처 실측은 GUI 세션.
  - **F116 f10 수용 기준 정본 갱신**: 구현 반영으로 (계획) 19건 중 16건을 [코드 확인]으로 올림(시험 refs 실존 확인)·(계획) 3건 유지(둘러보기 문구 가림·세로 창 캡처 실측·시스템 프로필 실측)·AC11 문장을 구현에 맞게 정정(진행 중 코스가 있으면 이어서 하기 카드가 먼저) — **기획서 반영 요청:** 별지의 F10 표·건수(136건 내 F10 [코드 확인] 20·(계획) 3)를 갱신된 정본에서 다시 만든다.
- 통합 검증(갱신): **앱 258/258(32파일)·서버 137/137**·spec_check 오류 0(136건·시험 이름 없음 0)·eval 게이트 통과·strings 122→**170**(시안 38~43 문구 48건 추가 — 시안 글자와 한 글자도 일치)·T6 문구 등록 완료·야간은 AroColors 다크 토큰 자동 대응·로터리 미지원 선언 유지. APK 40,635,757바이트 갱신.
- ⑤ **기획서 반영 요청(갱신):** 앱 시험 수 220→**258**(28→**32파일**)·strings 122→**170**·APK **40,635,757바이트**·수용 기준 f10 [코드 확인] 20·(계획) 3(위 F116)·F10 화면(메뉴·홈·설정·가족 전환)은 **구현·단위 시험까지 완료 — 에뮬레이터 화면 실측 전**으로 표기.

---

## 2026-11-13 (오전) — D실측(연료·EV 배터리) 완료(진단 화면 실측)

- 한 일: ①07문서 3절 D(연료·배터리 신호 실측 전용 스모크)를 진단 화면에서 실행 ②U10 관찰 중의 연속 도착 재확인 ③서버 DB에서 E1 전송 상태 대조
- 결과/증거: `.pleos-download/d1_result.png`·`d2_result.png`·`u15_result.png`·`u16_result.png`(진단 판정 화면), `launched_drawer3.png`(스탬프 2/3·다음 첨성대)
- 확인된 사실(F-번호):
  - **F76 D실측 4항목 완료(07문서 3절 약속 이행 — capability 확인만, 값 미읽음)**: D1 연료 capability **FAIL**(`Fuel.checkFuelPercentCapabilities` → `FAILED: ERROR_NOT_SUPPORTED_PROPERTY` — 에뮬레이터 VHAL이 연료 속성 미지원)·D2 EV 배터리 capability **PASS**(`EvBattery.checkEvBatteryStateOfChargeCapabilities` → **READ**)·U15 연료 지원(Vehicle 직접) **FAIL**(동일 사유)·U16 EV 배터리 지원 **PASS**(확인함). 판정은 08:09~08:11 실측. → 기획서 반영 요청: "그때는 휘발유차, 오늘은 전기차" 문장의 연료 쪽은 이 에뮬레이터에서 불가(EV 쪽만 가능) — 기획서 담당 소유.
  - **F77 연속 도착 1→2 독립 관찰**: U10 관찰 중 geo fix 이동 경로가 대릉원을 지나가자 도착이 자동 발행돼 스탬프 2/3·다음 목표 첨성대로 진행 — 같은 아래쪽 (아침) 섹션 F74(별도 실측 기록)와 동일 사실의 독립 재관찰로, 재시작·수동 개입 없는 자동 진행을 다시 확인. F68(1건)의 연장 실측.
  - **F78 서버 DB visits 0건 대조**: 기존 설치 릴리스 APK(네트워크 보안 설정 이전 빌드)의 방문이 서버에 1건도 없었다 — 아래쪽 (아침) 섹션 F71(릴리스 클리어텍스트 차단)과 정확히 일치하는 서버 쪽 대조 기록. 가족 세션이 없는 앱은 방문을 큐에 보류한다(설계대로 — 00문서 10.6). E1 전체 재실측(코드·PIN 입력→출발→도착→서버 행)은 Q28의 진입점 문제와 함께 GUI 세션 항목으로 남는다.
  - **F79 진단 화면 진입 경로 확정**: 디버그 빌드는 런처=진단 화면이지만, 기어 D 상태에서는 시스템이 비(DO) 창 전환을 제한해 시작/정차 화면이 계속 최상단을 덮는다(아래쪽 (아침) 섹션 Q26의 DO 잔존과 같은 맥락). 기어 P 복구(콘솔 REST `PUT /vehicle/controls {speed:0,brake:100}` → `PUT /vehicle/gear?gear=Park`) 후 `am start --activity-reorder-to-front`로 진입이 안정화됐다. 진단 화면 ⑤ 차량 세션 카드의 '가족 코드 입력 화면' 버튼이 StartActivity의 유일한 adb 진입로다(StartActivity는 exported=false — Q28의 일반 흐름 진입점 보완과 별개). 릴리스 빌드는 진단 화면이 즉시 시작 화면으로 넘어가므로(설계) 진단 실측은 반드시 디버그 APK로 한다(서명이 달라 `install -r`로 덮어쓸 수 없음 — `uninstall` 후 재설치, 앱 데이터 초기화; 아래쪽 F75의 pm clear --user 10 참고).
- Open Questions(Q-번호):
  - **Q29** E1 전체 경로(가족 코드·PIN 입력 → 출발 → geo fix 도착 → 서버 visits 행·스탬프 반영) 실측 — GUI 세션. 테스트 가족은 세션마다 서버에서 새로 발급해 쓰고, 코드·PIN 값은 문서에 기록하지 않는다. (Q18 정리 — 제출 전 삭제까지 완료: DB 0건) Q28(가족 코드 진입점 보완) 선행.
  - U11(좌석 4석에서의 문장 표시)·U13(주행거리 문장)·U6(음성 청취) 유지 — GUI 세션.
- 다음: 미커밋 배치(네트워크 보안 설정·FuelBatteryProbe·스탬프 복원·마커·U9 lastRouted)는 이후 커밋(327158a, 2026-11-13 08:15)에 포함됐다. 본 섹션은 DEV_LOG만 커밋

---

## 2026-11-13 (오후) — 미구현 요구 전수 조사·작업 단위 구현(Q26·Q27·Q20·09문서 7번·요구 E항목 문서화)

- 한 일: 요구 문서 갱신(11-12 20:57 이후 없음)을 확인하고 남은 구현 과제를 개발 기록·감사·요구 후보에서 전수 추려 4개 작업 단위로 구현해 통합. 통합 검증: 앱 단위 테스트는 그 시점 기준 179건(163+7+9, 실패 0 — 이후 신규 시험으로 190건까지 증가)·백엔드 pytest 87/87(+1)·assembleDebug·assembleRelease 성공, `apk/arosaegim_아로새김_release.apk` 갱신(당시 40,483,945 B)
- 확인된 사실(F-번호):
  - **F81 Q26 해소 — 기어 P 지속 시 주행 해제 타임아웃**: DrivingStateMerger에 정차 유지 해제 규칙 추가 — 보조(기어)가 false(P)로 확인된 지속 시간이 5초(`PARK_RELEASE_HOLD_MS`, 2초 폴링 3회) 이상이면 1차(DO)가 주행을 주장해도 PARKED로 판정. 근거: 기어 P에서 차가 움직일 수 없다는 물리 사실(DO 잔존은 오류 — Q26 실증). 시계(`nowMs`)·보류 시간 주입으로 순수 유지, CombinedDrivingState는 무변경. 테스트 +7(기존 7 유지)
  - **F82 Q27 완화 — 좌석 집계 부분 합산 차단**: 집계를 순수 함수 `countOccupied`로 추출하고 **응답률 50% 미달(`MIN_RESPONSE_RATIO=0.5`)이면 null**(F73의 "주입 3명→표시 1명"은 capability 일부 목록·부분 응답의 부분 합산 경로와 일치 — 확정 실측은 여전히 사용자 Virtual Console 세션 필요). `lastSeatDiagnostics`(요청/성공/실패 좌석 수 한 줄 — 표시 전용·저장 없음)를 진단 U11 상세에 연결. VisitQueue(E④)는 flush 호출자가 TripSession enqueue 직후 1회뿐임을 확인 — 공회전 폴링 없음(설계 요건 충족), KDoc에 전송 시점·재시도 예산 절 추가. 테스트 +9
  - **F83 Q20 부분 해소 — ParkedScreen 분리**: 1094줄 → ParkedScreen 596 + ParkedSteps 143 + ParkedRecordStep 219 + ParkedServerParsing 200(순수 이동 — git diff로 바이트 대조, private→internal 5개 최소 확장만). ParkedScreen 컴저블 하나가 상태를 클로저로 캡처해 596줄이 순수 이동 한계 — 이하 정리는 상태 끌어올림 리팩터가 필요(별도 과제)
  - **F84 09문서 7번 해소**: `assets/seed/then_placeholder.png` 1×1 순녹색 → 960×720 중립 자리표시(배경 #F0EDE8·'사진 자리' 안내, 13,246바이트) — ThenPhoto 코드 무변경, 에셋 교체만
  - **F85 요구 E항목 직접 수행(문서·가드 확인)**: E③ 포인트 가드 **이미 구현 확인**(services/points.py: 건당 MAX_AMOUNT_KRW=1,000,000·가족당 하루 5건 daily_limit — 테스트 존재)·E⑤ PIN 잠금 **이미 구현 확인**(security.py: 5회 실패 10분 — locked_until 미래 시각 검증 테스트 +1)·E④ 확인(위 F82)·E① `docs/TRACEABILITY.md`(요구↔구현↔시험 표 3개)·E② `docs/ADR.md`(실제 결정 14건, 코드 근거 경로 포함)·E⑥ `tools/`(숫자 자동 대조 도구 + facts.json — d4eb823 기준 9/9 OK)
- Open Questions(Q-번호):
  - **Q30(신규)**: `tools/spec_drift_check.py`는 HEAD 기준 측정 — 이번 커밋 후 F-TEST-APP(179)·F-TEST-SERVER(87)·F-APK-BYTES는 문서의 숫자는 이 도구로 코드에서 센 값과 대조하며, 문서가 옛 값을 유지하는 동안에는 잠정 FAIL이 정상이다(HEAD가 바뀌면 문서 숫자를 재대조한다). 저장소 내 README·ARCHITECTURE·TRACEABILITY는 이미 179/87로 갱신함
  - Q27 확정 실측·U11 문장 표시·U6 음성 — 사용자 Virtual Console 세션(테스트 가족은 세션에서 새로 발급 — 값은 문서에 기록하지 않는다)
  - Q14(letter_demo 오디오 — 발주자 준비 시 assets/audio 자동 활성)·Q18(테스트 가족 정리 — 완료: DB 0건)·Q20 잔여(ParkedScreen 596줄 상태 끌어올림)
- 다음: 커밋·푸시
- 확인된 사실(추가):
  - **F80 Q28 수정 — 정차 자동 전환의 세션 게이트**: 새 설치에서 첫 상태 수집(PARKED)이 정차 화면을 곧바로 최상단으로 올려 시작 화면(가족 코드 입력)의 진입점이 사라졌다(위 (아침) 섹션 Q28). 수정: ModeRouter에 `sessionExists` 게이트 — 정차로의 자동 전환은 세션(가족 코드·둘러보기)이 있을 때만; 주행 화면은 DO 안전 화면이므로 세션과 무관하게 항상 허용. 순수 판정 `ModeRouter.resolveTarget` 분리 + 단위 테스트 4케이스(`ModeRouterResolveTest`), `FamilySessionStore.hasSession()` 신설, 진단 U9의 `forceBringTo`는 게이트 우회(개발자 명시 전환 보호). ArosaegimApp에서 `container.familySessionStore.hasSession()` 배선. 실측 검증(08:30): 수정분 debug APK 재설치 후 콜드 스타트 → 6초 경과 시 `topResumedActivity`가 여전히 런처(진단 화면)이고 ParkedActivity가 뜨지 않았다(수정 전에는 몇 초 안에 정차 화면이 덮였다) — `.pleos-download/q28_verify.png`. 앱 테스트 163/163(신규 4케이스 포함)·assembleDebug·assembleRelease 성공, `apk/arosaegim_아로새김_release.apk` 갱신.

---

## 2026-11-10 (심야) — Pleos 에뮬레이터 CLI 구축 + 실기동 첫 스모크

- 한 일:
  - **에뮬레이터를 CLI로 끝까지 구축해 실제 부팅·앱 실행까지 완료** — Android Studio 없이 가능함을 확인
  - 백엔드(T8+T10)·웹(T9+T10·진행 중)·앱 동기화(T7+T8)·차량 세션(T9-1)·문서(T12) 작업 단위 구현 통합
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
  - **F32** 시드가 재변경됨(제휴처 `p-demo-stay` 추가, 총 4개) — 요구 문서·android assets·backend seed를 동기화(sha1 19dbf8ab…)하고 테스트 단언(3→4) 갱신
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

## 2026-11-10 (야간) — 요구 문서 갱신 반영 + T5·T6 작업 단위 구현 완료

- 한 일:
  - 갱신 요구 반영(모델·시드·테마)과 T5(주행 화면+도착 스탬프)·T6(정차 화면)을 4개 작업 단위로 구현 후 통합
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

## 2026-11-10 (야간) — 요구 문서 갱신 반영(요구 문서 대폭 수정)

- 한 일: 요구 문서의 00~03 문서가 20:55에 대폭 갱신되고 04_UI_GUIDE.md가 신설되어, 차이를 분석하고 P0 범위를 코드에 반영 시작
- 결과/증거: 아래 F19~F23. 갱신 작업은 도메인·시드 저장소 담당과 테마 담당 2개 작업 단위로 병렬 진행
- 확인된 사실(F-번호):
  - **F19** 도메인 모델 변경(00문서 4절): `QuizItem.reviewed`, `Place.coordVerified`, `Place.narrationReviewed` 필드가 모델로 이동. ThenNow는 10.4에 따라 `label` 표시용 필드를 둔다.
  - **F20** 시드 구조 확장: `stamp_books`(스탬프북 정의 + 완성 배지), `partners_demo`(제휴처·적립률 1%/2%), `then_now_demo.label`("1990년대 중반") 신설. `years_ago`는 35→32로 변경됐고 시드 주석에 "연도는 1994~1996 사이로 발주자 확인 필요(임시값)" — **사용자 확인 대상**.
  - **F21** 신규 작업(T9-1 가족 계승·차량 세션 = P1, T10 스탬프북·맛집·포인트 = P1+)과 섹션 10 보완사항(가족 코드 PIN, 보호자 동의, '관리 권한 이전' 용어, 영수증 입력은 모바일 웹 전용) 추가. 기존 "T10 혜택 미리보기(P2)"는 T10으로 흡수됨.
  - **F22** UI 방침 대체: 04_UI_GUIDE에 따라 **밝은 테마 기본 + 주·야간 모두 지원**, 색은 토큰 구조(AroColors light/dark), 포인트 색은 인주 하나, 버튼 높이 76dp 이상, Page Navigation 헤더, 가로·세로 레이아웃 모두 지원. 화면 기준은 화면 시안 이미지 01~06이다.
  - **F23** 03문서 5-1 단위 테스트 목록 확장: ThenNowResolver에 "여러 과거 → 가장 오래된 것", "label 우선 연도 표시" 규칙, StampBook(진행률·배지·중복 무시) 추가. 포인트 규칙은 서버 쪽(P1+). 01문서는 U1~U10 표 변경 없음, Gleo Interface 절(6-1, P2 선택)만 추가.
  - **F24** 01문서 6-1: Gleo Interface는 App Schema(assets/schema.json) 선언 → Intent 호출 → Callback 응답 방식(function calling). LLM SDK는 릴리스 노트에서 제공 종료 안내. P2 선택 과제로 지금은 구현하지 않는다.
- Open Questions(Q-번호):
  - **Q9** `then_now_demo`의 실제 연도(1994~1996 어디로 할지) — 시드 years_ago=32는 임시값. 사용자가 정하면 label·years_ago를 함께 확정.
  - **Q10** 04문서 [그림] 값(타이포 크기·버튼 크기·색 토큰 실값)은 D1~D6 확인 목록대로 에뮬레이터·원문 이미지로 재확인 필요.
  - **Q11** 앱 카테고리(00문서 10.2): 주행 화면의 공식 허용 카테고리가 미확정 — ARCHITECTURE.md 작성 시 후보(Maps/Navigation 또는 Music/Audio 성격)와 근거를 적어야 한다.
- 다음: P0 반영(모델·시드·StampBook·테마) 완료 후 재빌드·재테스트 → T5/T6에서 04문서 화면 명세 적용

## 2026-11-10 (심야 2차) — 요구 문서 3차 갱신(11~13절) 반영 착수

- 한 일: 요구 문서 재갱신(00: 37.6KB·01·02·03·04·05_INSTALL_EMULATOR 신설·seed 23:04)을 읽고 델타를 4개 작업 단위로 구현 시작
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

- 한 일: 4개 작업(앱 코어·앱 UI·백엔드 10/11절·웹) 통합 + 릴리스 APK 서명 빌드
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

---

## 2026-11-12 — E2E 감사(사용자) 수정 통합

- 한 일: 사용자 E2E 감사(08절, _e2e 스냅샷 실측)의 지적을 6개 작업 단위로 병렬 수정·통합
- 결과/증거:
  - **E1 수정**: postVisit 계약을 서버와 일치 — 스네이크 케이스 필드(place_id·course_id·visited_at·vehicle_label), X-Family-Code+X-Family-Pin 헤더, 응답 visit_id(정수)/stamp_no 파싱, party_size·trip_km·course_run_id 추가(start마다 UUID 발급), wireJson(null 생략). **계약 테스트 신설**(VisitPayloadContractTest 4케이스·실측 응답 파싱 케이스)
  - **E2 수정**: '그때와 지금'이 서버 past(연도·메모·사진 URL·인원·차량)를 렌더링 — 사진은 /uploads 로드(실패 시 글만), 우선순위 서버 past > 시드 예시('예시' 태그) > 첫 방문 문구
  - **E3 수정**: ModeRouter intent에 FLAG_ACTIVITY_NEW_TASK 추가 + 실패 시 진단 '마지막 오류' 기록
  - **E4 수정**: Narrator 3단 체인 — 1·2단(합성) 실패 시 **AssetAudioNarrator(사전 제작 오디오)** 재시도. 매핑: audio/arrival_<id>.ogg·narration_<id>.ogg. 현재 파일 미준비(Q14)라 동작 불변
  - **E5 수정**: start() 시점 첫 목표에도 경로 요청(테스트 기대값 갱신). **A7**: routeBecameReady(SwitchableRouteService.switched) 전환 시 진행 중 목표 재요청 — AppContainer에 배선 완료
  - **서버 S1~S3**: 구성원 없는 heir 500→400, 마커 상한 10 통일(등록·수정), **대릉원·첨성대 heritage 시드 보강**(천마총 650·첨성대 632 — reviewed=false·학설 문구)
  - **웹 6건**: 타임라인 중복 렌더 원인 수정(+코스 동일 버그), 그때 연도 표시, 순번·거리 표시, 8자리 코드 문구, 제휴처 이름 매핑, 본인 후보 제외
  - **seat-role 갱신 엔드포인트** 추가 + FamilySessionStore PIN 휘발성(AppContainer 배선: VisitQueue·TripSession에 pinProvider 주입)
  - 테스트: **앱 138/138 통과**(감사 5건 포함 — SwitchableNarratorTest는 가짜 구조 보강으로 전환 검증 실질화·FamilyApiParsers HALF_UP 반올림은 이미 수정돼 있음 확인), **서버 pytest 76/76**(신규 3), 웹 node --check 10파일
  - **릴리스 APK 재빌드**·apksigner verify 통과, git push(커밋 2건)
- 확인된 사실(F-번호):
  - **F58~F60** 이전 항목 유지. 신규:
  - **F61** E1 원인: 앱 전송 필드 카멜케이스+PIN 헤더 누락+응답 파싱 불일치 — 서버는 규격대로 동작(사용자 직접 호출 확인). 계약 테스트로 재발 방지
  - **F62** E2 원인: ThenNowStep이 시드 예시 전용이었음 — 서버 past 우선 표기로 수정(예시는 폴백)
  - **F63** Connect-S 창 관리: force-stop 후 재실행 시 내비가 앱 창을 선점하며 am start 복귀가 지연될 수 있다 — GNB 앱 서랍(격자) 경로가 안전한 대안(실측)
  - **F64** 백엔드 감사 기준선은 73(요구 문서 51은 구시점), 현재 76. 앱은 138(감사 시점 129+보강)
- Open Questions(Q-번호):
  - **Q18 유지**(테스트 가족 정리) + **Q19**: 에뮬레이터 간헐 재부팅 원인(Pleos 하드웨어 서비스 SIGABRT — Android Studio 설치·리소스 경합 가능성, 재부팅 후 재현 여부 관찰)
- 다음:
  - 사용자 Virtual Console 세션(U7·U11·U13 완결·U6 청취) → DEV_LOG 표·기획서 최종 확정
  - E1 재실측: 앱에서 도착 1건 → 서버 타임라인 표시 확인(사용자 세션)

---

## 2026-11-12 (밤) — 요구 갱신(09문서·07문서) 통합 완료

- 한 일: 09_UI_FINDINGS(색 대비 3쌍·글자 크기·야간 정책)·07_NEXT_TASKS(B1·B3·B4·A1·A3)를 4개 작업 단위로 구현해 통합
- 결과/증거:
  - 앱: **단위 테스트 158/158 통과**(신규: PartyPrefs 13·HeirPinGuide 6·TripSession A3 2·SessionCredentials 등), assembleDebug·**assembleRelease 성공**, `apk/arosaegim_아로새김_release.apk` 갱신, 에뮬레이터 재설치·실행 확인(pid 생존)
  - 백엔드: **pytest 86/86 통과**(prefs 10 신규) — B4 prefs API(GET/PUT/POST /v1/families/me/prefs)·visit_ordinal(then-now·visits·타임라인·export)·family_total_trip_km(metrics+export) 착지
  - 웹: node --check 10파일 통과, 코스 목록 주소 문구 사용자화
  - git: 커밋 3건 푸시 완료(87c93e8·6c7a0a5·e547626)
- 확인된 사실(F-번호):
  - **F65** 색 대비: 라이트 textQuaternary #6C655E(4.91:1)·다크 stampPoint #DF7451(surfaceAccent 4.57:1)로 AA 엄격(4.5:1) 충족 — 09문서 계산값 채택. 32sp "○"는 큰 글자 3:1 기준 합격 유지
  - **F66** 야간 자동 전환: ThemeState.autoByClock + refreshByClock(19:00~07:00) — 호출부가 주기 호출하면 즉시 반영(미호출 시 수동 토글만 — 가짜 자동 금지)
  - **F67** TripSession.rewatchCurrentTarget(): 진단 관찰 중단 후에도 여정 감시 복구 — 테스트 2케이스로 증명
- Open Questions(Q-번호):
  - **Q20** ParkedScreen.kt가 ~1061줄로 300줄 규칙 초과 — 스텝 컴포저블 분리 정리 과제
  - Q13(TTS onReady)·Q14(오디오 파일)·Q18(테스트 가족 정리) 유지
- 다음:
  - 사용자 GUI 세션: U7(기어 D)·U11(Seat)·U13(Odometer)·U6(음성)·E1 재실측(도착→서버 타임라인)
  - DEV_LOG U 표 갱신 → 기획서 반영 요청 전달(기획서 담당 소유)

---

## 스모크 테스트 결과표 (U1~U16 및 D1·D2)

---

## 2026-11-11 — 스모크 전 항목 adb 자동 탭 실측 완료 (U4·U5·U8·U9·U13·U11·U6·U10)
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
  - **F58** 서버가 발급하는 가족 코드는 **8자리**(형식 예시: A3K7Q9M2) — FamilyCodeValidator를 6~8자리로 확장(테스트 갱신: 7자리 유효·9자리 무효).
  - **F59** PIN은 세션 메모리(SessionCredentials companion)에만 존재 — 앱 재시작 시 소실(재입력 설계). members/metrics 조회는 코드+PIN 헤더가 모두 있어야 하며 401/403은 AuthException으로 구분된다.
  - **F60** U-tap 실측 방법(input tap + 스크린샷 좌표 측정)으로 진단 화면의 카드를 직접 조작할 수 있다 — U4·U5·U8·U9·U11·U13·U10이 사용자 없이 실측됐다.
- Open Questions(Q-번호):
  - Q13~Q17 유지. **Q18**: 백엔드 DB의 라이브 검증용 테스트 가족은 정리 완료다(DB 0건 — 2026-11-13 재확인). 테스트 세션이 필요하면 그때 새로 발급한다.
- 다음:
  - 사용자 Virtual Console 세션(U7·U11·U13 완결)과 기획서 문장 최종 확정만 남음. 앱 80/80·백엔드 51/51·릴리스 APK 재빌드(apk/arosaegim_아로새김_release.apk, 39.7MB) 완료.

---

## 2026-11-11 (심야) — 07문서(기획서 v10) 통합: A1~A8·D·B1~B5 구현 + 콜드 스타트 P0 해소 + A4 실측

- 배경: 기획서의 고도화·가점 후보 조치를 작업 단위로 구현(안드로이드 A1~A3·A8·D스모크·B1~B4 / 백엔드 B1~B5 서버 / 웹 B4 되돌리기·B5 사진 이름표). 요구 원본은 건드리지 않고 맨 아래 "기획서 반영 요청"으로 기록한다.
- 확인된 사실(F-번호):
  - **F61 콜드 스타트 교착(P0) 발견·해소**: release APK 첫 실행이 `AppContainer.selectRouteService()`의 `runBlocking { withTimeoutOrNull(3s) … }`에서 영구 파킹(타임아웃 미발화) → 시스템이 "failed to complete startup"으로 강제 종료(dropbox data_app_anr 스택 3건, main park @selectRouteService — /data/anr 은 production 빌드라 비루트 읽기 불가, dumpsys dropbox로 확보). 아침 스모크 세션에도 동일 ANR 흔적 — 재시도 성공으로 마스킹돼 있었다. **수정**: 포트 선택을 동기 즉시 판정으로 바꾸고 늦은 초기화 성공 시 일방향 업그레이드(SwitchableRouteService 패턴을 Narrator로 일반화). **실측**: `-no-snapshot` 콜드 부팅 직후 첫 실행 2회 연속 `Status: ok, TotalTime 8.1~8.5s`, ANR 0.
  - **F62 A2 정차 자동 전환(기어 병합)**: `CombinedDrivingState`(1차 CarUxRestrictions + 기어 보조 2초 폴링, 어긋나면 더 제한적(주행) 우선). 실측: 앱 로그캣에서 `VENDOR_GEAR_SELECTION` 2초 주기 조회 확인(23:12~23:13 구간). D→P 자동 복귀의 시각 증명은 미확보(에뮬레이터 불안정 — Q19).
  - **F63 A3 진단 관찰 분리**: `FusedArrivalDetector.startObservation/stopObservation` 신설 — 관찰이 도착 이벤트를 만들지 않고 여정 감시(watch)와 수명주기 분리. 관찰을 꺼도 다음 장소 도착 판정 유지가 설계 목표(실측 대기).
  - **F64 A1 사전 제작 오디오**: `assets/voice/letter_demo.wav` 신설(Windows SAPI Microsoft Heami Desktop — 데모용 일반 합성 음성, 고인 목소리 합성 금지 원칙과 무관). 파일이 없으면 재생 생략(가짜 금지). Pleos TTS는 onReady 확인 후에만 fallback 사용(A1 계약 유지).
  - **F65 A8 로그아웃 삭제**: `SignOutCleanup`(visits.json·visit_queue.json 삭제, 결과 줄 반환) + `FamilySessionStore.signOut()`이 코드·PIN 모두 해제 + `HeirQuestionMemory.clearAll()`. 단위 테스트 4건으로 삭제 검증.
  - **F66 D1·D2 실측 전용 스모크**: 진단 화면에 Fuel.checkFuelPercentCapabilities·EvBattery.checkEvBatteryStateOfChargeCapabilities의 capability 성공/실패만 기록하는 카드 신설(값은 미읽음 — 3절 "실측만, 구현 보류" 준수). CAR_ENERGY는 매니페스트에 있어 실행 가능.
  - **F67 CAR_SEATS 권한 누락 발견·수정 — U11의 진짜 원인**: 매니페스트에 `pleos.car.permission.CAR_SEATS`가 없어 `checkSeatOccupancyCapabilities`가 로그캣에 "need Permission"으로 실패하고 있었다(01문서 231행 "Seat·Odometer 권한 CAR_SEATS·CAR_DRIVE"와 일치, U11 FAIL의 표면 원인은 null이었지만 근원은 권한 누락). 매니페스트에 추가(+pm grant) 후 재실측: **SEAT_OCCUPANCY capability 성공 + 좌석별 값 수신**(areaId 1/4/16/32/64, eventValue 1=점유·2=빈칸으로 보임 — 의미 대응은 재확인 권장). release APK 재빌드·apk/ 재복사(40.3MB) 완료.
  - **F68 A4 실측 — 기어 D 주행 상태의 도착 기록**: Virtual Console로 기어 D + 속도 40km/h(`/vehicle/controls?speed=40`)를 만든 뒤 gj-museum 반경(120m) 안으로 geo fix 진입 → **도착 판정·스탬프·다음 목표(대릉원) 경로 요청 발생 확인**(NaviHelper requestRoute → 내비 앱에 대릉원 검색 팝업 + 로그캣). 그 동안 앱 화면은 시스템이 가림("주행 중에는 사용할 수 없습니다" — A4의 '화면은 시스템이 가려도 된다' 조건과 일치). 증거: `.pleos-download/a4_36(주행중 가려짐)·a4_51·a4_52(도착 후 대릉원 팝업·클러스터 D·40km/h).png`. **주의**: 근접 알림 등록이 끝나기 전에 위치가 이미 반경 안에 있으면 ENTER가 발화하지 않는다(첫 시도 실패 원인 — 반경 밖에서 코스를 시작한 뒤 진입할 것).
  - **F69 Virtual Console 스크립트 조작 노하우**: 활성화 조건은 브라우저 세션(8088 페이지 로드 + 1초 하트비트). `503 {"error":"emulator_inactive"}`가 나오면 페이지를 새로 고침해 재활성화하고, 활성 윈도우(수 분) 안에 PUT을 몰아서 실행한다: `/vehicle/gear?gear=Drive|Park`, `/vehicle/seat?kind=occupied&area=driver|passenger|rear_left|rear_right&value=true|false`, `/vehicle/odometer?traveled_distance_km=N`, `/vehicle/controls?speed=N&accelerator=..&brake=..`. 무입성으로 수 분 경과하면 다시 비활성화된다. 이 방법으로 좌석 3명·주행거리 12500km·기어 D·속도 40 주입에 성공(200).
  - **F70 에뮬레이터 불안정 기록**: 콜드 부팅 인스턴스에서 system_server 사망 1회("Can't find service: activity"), QEMU 프로세스 사망 2회(exit 2816 — 유휴 무입 상태에서 발생). 재부팅 절차: taskkill qemu+adb → AVD lock 삭제 → `-no-snapshot` 기동(부팅 40~120초).
- 열린 질문(Q-번호):
  - **Q19** 기어 D→P 전환 시 CarUxRestrictions(DO)가 주행 유지를 보고하는가 — CombinedDrivingState의 "더 제한적 우선" 규칙이 정차 자동 복귀를 막지 않는지 실측 필요(A2 완결 기준).
  - **Q20** B1 heir POST가 차량 세션(PIN 없음)에서 항상 403으로 답하는지 — "스마트폰의 가족 관리에서 정해 주세요" 안내 경로 확인 대기.
  - **Q21** letter_demo.wav 말미 묵음(약 12초) 재생 체감 — 길면 트리밍.
- 테스트·산출물: 백엔드 pytest 76/76 통과. 앱은 android 정비 작업이 겹쳐 최종 수치는 다음 섹션에서 확정한다. 웹 node --check 10파일 통과.
- **기획서 반영 요청**(섹션 6 — 기획서 담당이 content.py 등에 적용):
  1. 본문의 단위 테스트·서버 테스트 수치를 최신 값으로(앱·서버 수치는 다음 섹션의 최종 확인값 사용).
  2. BOX5 '[미확인] 기어 D 상태의 도착 기록' → '[확인: 기어 D·40km/h 주행 상태에서 반경에 들어서면 도착·스탬프가 기록되고 다음 목표로 진행된다. 그동안 화면은 시스템이 가린다(F68)]' 제안.
  3. BOX3 '좌석 점유' — 근원이 권한 누락이었음이 확인됐고 수정으로 조회 성공(F67). '값을 바꿔 넣어 문장이 나오는 것' 실측 후 갱신.
  4. BOX7 APK 크기 39.6MB → 40.3MB(현재 빌드 기준, 최종 빌드 후 재확인).
  5. captions.json의 시안5 캡션 수정분은 시안 생성 스크립트(make_mockups.py)에도 반영 필요(재생성 시 덮인다).

---

## 스모크 테스트 결과표 (U1~U16 및 D1·D2)

| # | 확인 항목 | 결과 | 에뮬레이터 | SDK 버전 | 비고/스크린샷 |
|---|---|---|---|---|---|
| U1 | requestRoute poiId 빈 값 | **PASS(호출 접수)** — poiId=""·poiSubId=""·poiProvider=""로 호출, 예외 없음·Error 미수신 | v2.1.0 실측 | NaviHelper 3.0.5 | 내비 화면 경로 표시는 육안 병기 필요 — .pleos-download/u1_result2.png |
| U2 | NaviHelper 초기화·리스너 | **PASS** — initState: ok, initError 없음 | v2.1.0 실측 | 3.0.5 | AppContainer 선택 대기(3초)보다 늦음 → RouteService만 Fake 선택, U1/U2로 직접 커버(Q15) |
| U3 | FusedLocation 근접 알림 | **PASS — proximity 등록 + ENTER 수신 + 도착 이벤트 발행**. 모드 idle->proximity(폴백 아님) | v2.1.0 실측 | 1.1.3 | **반경 단위 미터 실측 확정**(00문서 10.5 해소) — .pleos-download/u3_enter.png |
| U4 | FusedLocation isSupported() | **PASS** — `FusedLocation.isSupported = true`, mode: proximity, lastError 없음 | v2.1.0 실측 | 1.1.3 | .pleos-download/u4_r.png |
| U5 | Vehicle capability/기어 | **PASS** — VIN capability AccessType=**READ**(확인만·값 미읽음 ✓규정), **기어 상태 GEAR_PARK 실수신**(클러스터 P와 일치), Odometer 값은 스코프 밖 | v2.1.0 실측 | 2.3.2 | .pleos-download/u5_r2.png — 진단 '기어 상태(Vehicle·Gear)' 행 |
| U6 | TTS ON_DEVICE speak | **FAIL(원인 2건 확정)** — ①AndroidTtsNarrator: Android TTS 초기화 미완료/실패(0ms — 에뮬레이터 TTS 엔진·한국어 데이터 부재 가능성) ②PleosTtsNarrator 재시도: onReady 미수신 상태(초기화는 성공) | v2.1.0 실측 | 3.3.1 | F54·Q13·Q16·Q17 — 초기화 후 여유 두고 재탭 권장 |
| U7 | 주행 규제 전환 | **부분 → 진전** — CarUxRestrictions 획득·등록 성공에 더해, A2 병합(CombinedDrivingState)로 기어 보조 2초 폴링이 실측 확인됨(F62 — VENDOR_GEAR_SELECTION 주기 조회 로그). 기어 D 진입 시 주행 모드 전환·화면 가려짐도 확인(F68). restrict 수신 세부는 여전히 Q19 | v2.1.0 실측 | 2.3.2 | .pleos-download/a4_36.png |
| U8 | 에뮬레이터→호스트 서버 | **PASS** — GET 10.0.2.2:8000/health → **200 {"ok":true}**(cleartext 포함)·재확인 | v2.1.0 실측 | - | 백엔드 uvicorn 구동 중 — .pleos-download/u8_r.png |
| U9 | Activity 전환 반복 | **PASS** — Driving↔Parked 3회 전환(REORDER_TO_FRONT) 완료·복귀·상태 유지 | v2.1.0 실측 | - | .pleos-download/u9_r.png |
| U10 | 도착 이벤트 관찰 | **관찰 중 — geo fix 3회 이동 후에도 onDestinationArrived 미발생**([REPORTED]와 일치 — 이것이 정상 결과). 관찰 카드 유지. **2026-11-13: 연속 도착 1→2 재확인(F77)** — 대릉원 통과 중 스탬프 2/3·다음 첨성대 자동 진행 | v2.1.0 실측 | 3.0.5 | 도착 판정 본역은 FusedLocation(U3 PASS) — 설계 정당화 근거 |
| U11 | 좌석 점유 → 인원(partySize) | **원인 발견·수정 → 조회 성공** — 근원은 매니페스트 CAR_SEATS 권한 누락("need Permission", F67). 추가 후 Virtual Console 좌석 3명 주입 재실측에서 SEAT_OCCUPANCY capability 성공·좌석별 값 수신. '문장 표시'까지의 화면 확인은 Q19과 함께 대기 | v2.1.0 실측 | 2.3.2 | .pleos-download/a4_51.png — u11_r3.png(구 실패 기록) |
| U12 | 문 상태(참고 — 사용 안 함) | **해당 없음(고정)** — 문장 안 함(로드맵)·좌석·수신음 실행하지 않음 | - | - | 진단 카드 고정 안내 렌더링 확인 |
| U13 | 주행거리계(odometerKm) | **PASS** — odometerKm()=**0.0 km 수신**(판정 PASS). '현재(트립) 거리' 채널로 보임 — **T5-1 계산에 적합**. 2026-11-11 심야: Virtual Console odometer 12500km 주입 성공(F69) — 클러스터에 반영 확인. 앱 화면 문장 반영 확인은 대기 | v2.1.0 실측 | 2.3.2 | .pleos-download/u13_r.png |
| U14 | 배터리(참고 — 사용 안 함) | **해당 없음(고정)** — 사용 안 함(로드맵) | - | - | 진단 카드 고정 안내 렌더링 확인 |
| D1 | 연료 capability 실측(07문서 3절 D — 실측 전용) | **FAIL — `ERROR_NOT_SUPPORTED_PROPERTY`**(에뮬레이터 VHAL이 연료 속성 미지원). capability 확인만·값 미읽음(약속 준수) | v2.1.0 실측 | 2.3.2 | .pleos-download/d1_result.png — F76, 기획서 반영 요청 |
| D2 | EV 배터리 capability 실측(실측 전용) | **PASS — capability 결과 `READ`**. 값 미읽음(약속 준수) | v2.1.0 실측 | 2.3.2 | .pleos-download/d2_result.png — F76 |
| U15 | 연료 지원(Vehicle 직접 접근 프로브) | **FAIL — D1과 동일 사유** | v2.1.0 실측 | 2.3.2 | .pleos-download/u15_result.png — F76 |
| U16 | EV 배터리 지원(EvBattery 직접 접근 프로브) | **PASS — 확인함** | v2.1.0 실측 | 2.3.2 | .pleos-download/u16_result.png — F76 |

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

## 2026-11-10 — T0~T4 작업 단위 구현·통합 빌드

- 한 일:
  - T1(스캐폴드)·T2(어댑터+Diagnostics)·T3(도메인+테스트)·T4(시드)를 4개 작업 단위로 구현 후 통합
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
  - T1~T4 구현(4개 작업 단위) → 통합 빌드(assembleDebug·test)
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

---

## 2026-11-13 (아침) — F71 릴리스 클리어텍스트 차단 발견·수정 + A2 실측 PASS + Q26 실증

- 번호 안내: 이 섹션의 F71~F75는 이 섹션 전용이다(위쪽 2026-11-12 섹션의 F61~F64, 중간 2026-11-11(심야) 섹션의 F61~F70과 번호가 겹치므로 섹션 소속으로 구분).
- 확인된 사실(F-번호):
  - **F71 릴리스 클리어텍스트 차단 발견·수정(P0)**: release APK에서 ApiClient의 http://10.0.2.2:8000 호출이 전부 클라이언트에서 차단 — uvicorn 접속 로그에 앱 요청 0건(가족 코드 이어 보기는 오프라인 저장이라 성공처럼 보였고, then-now 조회 실패→B4 접기 버튼·B2 숨김, 방문 큐 미전송까지 전부 이 하나로 설명됨). debug 변형 매니페스트만 usesCleartextTraffic=true였던 것이 원인. 수정: res/xml/network_security_config.xml 신설(10.0.2.2·localhost만 허용 — 릴리스 HTTPS 전제 유지) + 매니페스트 networkSecurityConfig 연결. 수정 후 재실측: POST /v1/visits가 서버에 도달 확인(401 — 아래 Q26; 도달 자체는 성공).
  - **F72 A2 실측 PASS(정차 화면 자동 전환)**: Virtual Console 기어 D(40km/h)→P 전환 시 손대지 않아도 ParkedActivity가 최상단 복귀 — 2회 확인(.pleos-download/a5_07_afterP_1.png·a5_15_afterP2.png). CombinedDrivingState 기어 2초 폴링이 실제 정차 전환을 만든다.
  - **F73 A5 실측(문장 표시 PASS·값 불일치)**: 좌석 3명 주입 후 도착 카드에 "오늘은 1명이 함께 왔어요. 그때는 5명이었어요." 표시 확인(.pleos-download/a5_11_thennow.png) — partySize 파이프라인 동작. 주입 3명 대비 1명은 Q27 과제.
  - **F74 A4 스탬프 카드 재확인**: 기어 D·40km/h 주행 중 반경 진입 → P 복귀 후 "국립경주박물관에 스탬프를 찍었어요 / 스탬프 1/3 / 다음: 대릉원(천마총)" 표시(a5_07_afterP_3.png). 대릉원 도착 2/3도 확인(a5_15_afterP2.png).
  - **F75 운영 기록 — 에뮬레이터 공유 충돌**: 사용자의 Android Studio Run이 같은 에뮬레이터에 debug 빌드를 설치·기동할 수 있다(2026-11-13 07:58 사례: app-debug.apk 49MB 설치 → 진단 화면 노출). 자동 실측 시작 전 pm path app.arosaegim + lastUpdateTime 확인 권장. 이 이미지의 앱 데이터 초기화는 pm clear --user 10이 필요(pm clear만으론 user 10 데이터 미삭제).
- 열린 질문(Q-번호):
  - **Q26 (Q19 후속·실증)**: D→P 직후 우리 앱 디스플레이의 DO(CarUxRestrictions)가 Requires DO? true로 잔존해 병합 규칙(더 제한적 우선)이 주행 화면을 유지한 사례 1회 — 기어 P↔D 재사이클로 해소. 코드 과제: 기어 P·속도 0 지속 시 주행 해제 타임아웃(미구현).
  - **Q27**: partySize 불일치(F73 — 주입 3 vs 표시 1) — SeatArea(ROW_1~3×L/C/R)와 VHAL areaId(1/4/16/32/64) 대응표·점유 값 의미(1/2) 스모크 확정 필요.
  - **Q28**: 새 설치 플로우에서 가족 코드 입력 화면(StartActivity) 진입점이 보이지 않음 — fresh install이 곧바로 ParkedActivity 코스 선택. 세션(PIN) 없이는 방문 동기화(401)·B2/B4 표시가 불가능하다. UI 진입점 확인·보완 필요. → **해소(2026-11-13 오전, 위 오전 섹션 F80)**: ModeRouter 세션 게이트 — 세션 없는 정차 상태에서는 자동 전환을 보류해 시작 화면을 유지. 남은 검증: 새 설치 후 시작 화면이 유지되는지 GUI 재확인.
- 테스트: 앱 ./gradlew test 159/159(네트워크 설정 포함 재빌드), 백엔드 pytest 76/76. release APK 재빌드·apk/ 재복사(40.45MB).

---

## 2026-11-13 (밤) — 12번 문서 §3 수행 1: P0-1 tools/ 저장소 내부화 완료

- 배경: 요구 문서 12번 §3(P0-1 → P0-5 순서 수행 지시). 로직 변경 없음 — 도구·문서·추적 설정만 고쳤다. F 번호는 F85 다음부터(F86~).
- 확인된 사실(F-번호):
  - **F86 P0-1 완료**: ① 저장소 밖 작업 폴더 경로 검색(tools 한정) **0건**(tools/README.md의 옛 문구와 커밋 번호·낡은 수치(163·86·d4eb823)를 지우고 "현재 값은 도구를 돌려 확인한다"로 교체, drift-results.json의 작업 폴더 경로 38건은 파일째 추적 제외). ② 숫자 대조 도구(spec_drift_check.py) 실행 **10개 사실 전부 OK·문서 일치 17곳** — 0매칭이던 F-ENDPOINTS(37)·F-ROUTERS(11)·F-MODELS(46)·F-STRINGS(121)·F-TESTFILES-APP(22)·F-TESTFILES-SERVER(9)는 README에 "현재 값" 문단을 새로 두고 규칙을 등록해 실제로 잡힌다. ARCHITECTURE 구성도의 "백엔드 라우터 10개"를 **11개로 갱신**(preferences 추가)하고 facts.json의 skip 규칙을 해제했다. ③ 실행 결과 파일(drift-results.json)을 **추적에서 제외**(git rm --cached·.gitignore에 tmp/·tools/drift-results.json 추가)하고 도구 기본 출력 경로를 으로 바꿨다(결과 파일에 문장 조각이 남지 않게). ④ tools/README.md 재작성(도구 2종 용법·표준 라이브러리 전용·읽기 전용 명시), Q30 문구를 "문서의 숫자는 이 도구로 코드에서 센 값과 대조한다"로 교체.
  - 참고: facts.json의 doc_patterns 저장소 내부화는 이미 선행 반영돼 있어 이번에는 0매칭 규칙 보강만 수행했다. README의 릴리스 APK 크기 표기(39.6MB→40.4MB)도 함께 정정했다.
- 시험: 해당 없음(도구·문서 변경 — 코드 미변경). 도구 자체 검증은 위 ②로 대신한다.

---

## 2026-11-13 (밤) — 12번 문서 §3 수행 2: P0-2 작업 방식 흔적·깨진 참조 제거 완료

- 확인된 사실(F-번호):
  - **F87 P0-2 완료**: ③ 작업 방식 흔적 검사(요구 문서 12 §7 명령 ③)의 결과를 다음과 같이 정리했다. (a) 신설 `docs/EMULATOR_SETUP.md` — 에뮬레이터 요구 사항·설치 순서·자주 막히는 곳·adb 명령 모음·수동 확인 목록·릴리스 체크리스트를 사실만 모아 한 문서로 만들었고(심사용 계정 초대 항목은 제외 — 비공개 제출 시 불필요), README·ARCHITECTURE의 에뮬레이터 구축 참조를 이 문서로 연결했다. (b) TRACEABILITY 머리말의 커밋 번호 제거("현재 값은 tools/spec_drift_check.py로 확인"), 표 1 제목을 "다음 과제 기능(A1~A8·B1~B5)"으로, 근거 줄을 "docs/DEV_LOG.md 의 F·Q 번호"로 교체. (c) ADR.md 전면 정비 — 내부 문서 번호(00문서 2-3절·10.6·9절·7절 B3·11-2, 02문서 11절)를 요구 내용+DEV_LOG F·Q 번호·코드 경로로 교체(ADR-001·003·005·007·010·011·013·014), ADR-005 상태 문구를 현재 사실로(F81 코드 반영·DrivingStateMergerTest 검증·실측 미완료), ADR-009에 "안내 문구는 서버 발급 형식(8자리), 검증은 구형 6자리 호환을 위해 6~8자리 허용" 한 줄 추가. (d) 코드 주석 5곳의 작업 방식 표현 제거(DiagnosticsScreen·ParkedScreen 3곳·ThenNowExtras — 주석 글만, 로직·식별자 무변경). (e) docs/DEV_LOG.md의 표현 20줄을 사실 보존 원칙으로 재표현(작업 방식 표현→일반 표현, 내부 문서 경로→일반 명칭 — 수치·링크·F/Q 무변경, UTF-8 무결성 검증 포함). (f) 작업 폴더 경로 표기 3곳 제거(ATTRIBUTION 화면 시안 출처 행·DEV_LOG F22·기획서 반영 요청 5호 — 경로 대신 일반 명칭).
  - **남는 검색 결과의 성격**: 명령 ③의 대소문자 무시 검사에서 서버 응답 모델 식별자 1종(backend/app/models.py 3줄·services/then_now.py 2줄)이 대소문자 무시 검사에서 남는다. API 스키마 이름이라 식별자 변경은 금지(요구 문서 12 §2·P0-2.1)이므로 예외로 남긴다 — 실제 작업 방식 문장은 0건이다.
  - **링크 검증**: docs/ 안 모든 상대 .md 링크가 실제 파일을 가리킨다(0건 끊김 — EMULATOR_SETUP.md 포함). UTF-8 무결성도 검증(DEV_LOG·models.py·then_now.py).
  - **README 보강**: "현재 값" 문단 아래에 코드 주석의 "NN문서 …" 표기가 요구 문서 번호이며 저장소에 포함되지 않는다는 안내 한 줄 추가(P0-2.4 — 코드 주석 자체는 유지).
- 시험: 해당 없음(문서·주석 변경 — 코드 미변경).

---

## 2026-11-13 (밤) — 12번 문서 §3 수행 3: P0-3 테스트 가족·PIN 실제 값 제거 완료

- 확인된 사실(F-번호):
  - **F88 P0-3 완료**: 실제 테스트 가족 코드 2종과 PIN 값 1종이 남아 있던 곳은 docs/ADR.md(ADR-009 맥락)·docs/DEV_LOG.md 5줄(Q29·남은 실측·Q14/Q18·F58·Q18)의 6곳이었고, 코드 파일(FamilySessionStore.kt KDoc·FamilySessionStoreTest·VisitQueueTest)은 선행 커밋에서 이미 예시 값으로 바뀌어 있었다. 남은 6곳을 명백한 예시 값(형식 예시: A3K7Q9M2)으로 교체하거나 "세션마다 새로 발급·값은 문서에 기록하지 않는다"로 재표현했다. 시험의 의미(8자리 형식 검증 등)는 그대로다.
  - **Q18 완료 재확인**: 서버 DB의 테스트 가족은 0건이다(직접 조회 — families·visits·members 모두 0). `git ls-files`에서 DB 파일은 추적되지 않으며 uploads/는 .gitkeep만 추적된다(내용 없음).
  - **시험 수 기록**: 앱 단위 테스트 **190/190**·서버 pytest **87/87** 통과. 앱 시험 수는 이 문서 작성 기준 179에서 190으로 늘어 있다(선행 커밋의 신규 시험 포함 — 이번 P0-3은 문서 변경이라 개수 무관). README·ARCHITECTURE·TRACEABILITY·facts.json의 숫자를 190으로 갱신 — **기획서 반영 요청:** 기획서의 테스트 수 표기를 179→190(앱)으로 맞춘다.

---

## 2026-11-13 (밤) — 12번 문서 §3 수행 4: P0-4 수용 기준 정본·점검 도구·추적표 완료

- 확인된 사실(F-번호):
  - **F89 P0-4 완료**: 수용 기준 정본을 `docs/spec/ac/`에 두고(기능 F1~F9 · 9파일 · **수용 기준 122건**), `tools/spec_check.py`(표준 라이브러리만 — 기존) 실행 결과 **오류 0건 · 시험 이름 '없음' 0건**(시험 이름 실존 자동 확인 138건·점검 제외 128건). `--write-trace`로 `docs/SPEC_TRACE.md`(기능별 표 + 실존 열 자동 점검)를 생성했고 `--counts`는 앱 190건(22파일)·서버 87건(9파일)을 출력한다.
  - **정본 경로 정리**: 수용 기준 초안이 두 곳에 있었다(요구 문서 11 첨부 ac/f1~f7 초안 vs docs/spec/ 루트의 야간 재검증판 f1~f9). 비교 결과 루트 판이 상위 집합이다(F1 11→14·F2 13→14·F3 10→11·F4 11→13·F5 11→13·F6 14→15·F7 15→16 — F77·F80 실측을 반영한 AC 신설 포함) therefore 루트 판을 `docs/spec/ac/` 정본으로 옮기고 f8_cashback·f9_offline(F8·F9 초안)도 함께 옮겼다. 요구 문서 11 첨부의 구형 초안은 저장소 밖이라 그대로 둔다.
  - **수용 기준 최종본 대기**: 요구 문서 12 §5가 말하는 기획서 검증을 마친 수용 기준 최종본(첨부)은 아직 미도착 — 도착하면 정본을 덮어쓰고 차이를 별도 F 번호로 보고한다.
  - **F9 유형 보고**: F9(오프라인·대체 구현)는 항상·선택 유형만 있고 사건·상태·원치 않는 상황이 없다(보고만 — 오류 아님). 시험이 없는 수용 기준은 P2 "시험 추가" 후보다.
- 시험: 신규 추가 없음(정본·도구·추적표 작업). 점검기 기준: 오류 0건.

---

## 2026-11-13 (밤) — 12번 문서 §3 수행 5: P0-5 ADR 기각한 대안 보강 완료

- 확인된 사실(F-번호):
  - **F90 P0-5 완료**: ADR-001~014 전체(14건)에 `- 기각한 대안:` 줄을 추가했다(번호·제목 무변경 — 기획서가 같은 번호로 인용). 실제로 검토·배제한 근거가 있는 것(ADR-001 Hilt 미사용·ADR-005 DO/기어 단독 판정 기각·ADR-006 NEW_TASK 누락·ADR-008 대기 시간 연장·ADR-010 평문 저장·ADR-012 카멜케이스 유지·ADR-014 SDK 직접 호출)은 DEV_LOG F·Q 번호와 함께 기록했고, 검토 기록이 없는 것(ADR-002 큐 비동기 소비·ADR-003 암호화 영속 저장·ADR-004 UUID 멱등키·ADR-007 음성 경로 없음·ADR-009 8자리 강제·ADR-011 제휴처별 상한·ADR-013 Room·ADR-014 선구현)은 "(검토 기록 없음) 대안으로 생각할 수 있는 것: …" 형식으로 구분해 거짓 이력을 만들지 않았다.
  - 상태 문구: ADR-005의 진행 문구는 F81 반영 사실로 교체돼 있다(위 P0-2.3 정비와 동일 판).
- 시험: 해당 없음(문서 변경).

---

## 2026-11-13 (밤) — 12번 문서 §3 수행 보고 (§6 형식)

1. **최종 커밋**: 3bea357(요구 문서 12 §3 P0-1~P0-5 일괄 — 이후 본 보고 커밋이 뒤따른다).
2. **시험 수**: 앱 단위 테스트는 당시 190/190(`./gradlew testDebugUnitTest`) · 서버 pytest 87/87. 이번 P0-1~P0-5는 문서·주석·도구 변경이라 시험 수를 바꾸지 않았다(190은 선행 커밋의 신규 시험 반영).
3. **만든·고친 문서와 도구**: tools(facts.json 규칙 보강·spec_drift_check.py 기본 출력 tmp/ 이동·README 재작성·spec_check.py 기존) / docs(EMULATOR_SETUP.md 신설·SPEC_TRACE.md 신설·spec/ac/ 정본 9파일·ADR.md 정비+기각한 대안 14건·TRACEABILITY 머리말·README 현재 값 문단+NN문서 안내·ATTRIBUTION 경로 표기 정리) / 코드 주석 6파일(작업 방식 표현 제거 — 로직·식별자 무변경).
4. **못 한 것과 이유**: (a) 명령 ③이 서버 응답 모델 식별자 1종 5줄을 대소문자 무시로 계속 잡는다 — 식별자 변경은 금지(P0-2.1)라 예외로 남긴다(실제 작업 방식 문장은 0건). (b) 기획서 검증을 마친 수용 기준 최종본·문서 원문 첨부가 미도착 — 도착 시 P0-4 절차대로 정본을 덮어쓰고 차이를 새 F 번호로 보고한다(F101로 완료). (c) 명령 ②는 커밋 전 기준이라 F-TEST-APP이 잠정 불일치로 나온다 — 본 커밋 이후 HEAD 측정값이 문서(190)와 일치해 통과한다. (d) P1-1 README 심사 안내·P1-2 문서 묶음은 미착수(12번 §4 — P1-2는 첨부 도착 전 쓰지 않는다). (e) 시연 영상 재촬영·U6 청취·Q27 확정 실측은 에뮬레이터 GUI 세션 과제로 남는다(사용자 세션).
5. **기획서 반영 요청**:
   - 기획서의 앱 테스트 수 표기를 179→190으로 갱신(시험 11건 추가 — F88 참조).
   - 기획서가 가족 코드 실측값(8자리 코드·PIN)을 인용하는 곳은 형식 예시(예: A3K7Q9M2)로 교체 — 실제 값은 문서·주석에서 모두 제거했다(F88).
   - "그때는 휘발유차" 문장용 연료 capability는 이 에뮬레이터에서 미지원(위 (아침) 섹션 F76 — EV 쪽만 가능)은 유지 확인.

---

## 2026-11-14 — 12번 문서 §4 수행 1: P1-2 문서 묶음 배치·P1-1 심사 안내 (F91)

- 확인된 사실(F-번호):
  - **F91 P1-2 배치 완료**: 별지 검증이 끝난 문서 원문을 배치 지도대로 저장소에 옮겼다. ① `docs/spec/00_CHARTER.md`·`docs/AI_SCOPE.md`·`docs/QUALITY.md` — 원문과 바이트 단위 동일(대조 확인 4건). ② `docs/QUALITY_EVAL.md` 신설(평가 지표 적용표 — 배치 지도의 선택지 중 별도 파일로 배치). ③ `docs/ADR.md`의 14건 `- 기각한 대안:` 줄을 원문으로 교체(ADR 번호·제목 무변경, '검토 근거 있음/검토 기록 없음' 구분 14건 유지). ④ P1-1로 README 맨 위에 '5분 확인' 절 추가(①APK 위치·크기 ②시험 명령·현재 개수 ③문서 지도 ④숫자 대조 명령 ⑤확인·미확인 요약 — 상태 어휘 그대로).
  - **배치 후 코드·시험 재확인**(배치 규칙): 원문이 인용한 수용 기준 번호 22종 전부 정본에 실존, 시험 이름 전부 실존, 코드 경로(routers/places.py·ui/parked/ParkedComponents.kt·web/js/screens/receipt.js·Haversine.distanceM·PleosVehicleInfoProvider.checkVinCapabilityOnly 등) 전부 실존, 시드 수치(코스 3곳·퀴즈 6문항·반경 120m·제휴처 4곳)·APK 40,483,945 B(당시) 일치.
  - **원문과 실제가 어긋난 값**(고치지 않고 요청만): TripSessionTest 14건→실제 16건·FamilySessionStoreTest 16건→실제 21건·VisitQueueTest 9건→실제 13건·앱 단위 시험 179건→197건(F92 신규 7건 포함). 서버 시험 87건·CombinedDrivingStateTest 3건·DrivingStateMergerTest 15건·SwitchableRouteServiceTest 8건·SwitchableNarratorTest 5건·ModeRouterResolveTest 4건·VisitPayloadContractTest 4건·SignOutCleanupTest 4건·ThenNowResolverTest 12건은 일치.
  - **SPEC_INDEX.md는 만들지 않았다**: 원문 제공이 없어 기획서 별지와 어긋날 위험을 피했다 — 원문이 도착하면 그때 배치한다.
  - **작업 트리 정리**: docs/ADR.md에 커밋되지 않은 재작성본(번호·제목 체계가 별지 인용과 어긋나는 14건)이 남아 있어 커밋 기준본으로 되돌린 뒤 원문을 반영했다(되돌린 본은 tmp/ 아래 백업 — 추적 제외).
  - **Q31(열린 질문)**: docs/spec/F1~F9 기능별 문서 9개가 수용 기준 정본(129건) 반영 이전의 건수(예: F1 14건)를 인용한다. 정본 수치로 맞출지, 목록에서 뺄지 사용자 확인이 필요하다 — 확인 전까지 커밋하지 않고 작업 트리에 둔다.
- **기획서 반영 요청**:
  - 위 어긋난 값 4종(TripSessionTest·FamilySessionStoreTest·VisitQueueTest·앱 단위 시험 수)을 원문 갱신 때 같은 값으로 올린다.
  - 색인(별지 목차 문서)의 원문이 만들어지면 전달을 부탁한다 — 도착 즉시 배치한다.

---

## 2026-11-14 — 12번 문서 §4 수행 2: P2 시험 추가·PIN 잠금(429) 처리·주석 (F92)

- 확인된 사실(F-번호):
  - **F92-① 시험 7건 추가(앱 190→197, 파일 22→25)**:
    - `ApiClientHttpContractTest` 2건 — 루프백 소켓 최소 응답기로 상태 코드→예외 계약을 실제 전송으로 검증(429→AuthException·404→IOException).
    - `DrivingScreenContractTest` 2건 — '주행 중 퀴즈 미노출' 보증 구조를 파일 계약으로 고정: 매니페스트에서 주행 화면만 distractionOptimized 선언(정차 화면은 미선언), 주행 화면 소스는 퀴즈 화면(ui.parked)을 참조하지 않음.
    - `QuizStateHolderTest` 3건 — 같은 장소 퀴즈 진행 보존(주행 복귀 이어풀기)·장소 변경 시 초기화·문항 수 감소 시 clamp 보정.
  - **F92-② PIN 잠금(429 family_locked) 처리 추가**(작은 코드 변경 — 시험 동반): 앱이 서버의 429를 IOException으로 흡수해 잠금 10분 동안 같은 조합을 되풀이 전송할 수 있었다. ApiClient 3개 호출 경로가 429도 AuthException으로 답하게 하여, 기존 거절 잠금(VisitQueueTest 11~13 — 같은 조합 재전송 멈춤·큐 보존)이 429에도 작동하고 TripSession의 한 줄 안내로 알린다. 주석·계약 설명(401·403 → 401·403·429)을 함께 맞췄다.
  - **F92-③ 근접 알림 실패→거리 계산 대체 전환(ARO-F1-AC02)은 JVM 단위 시험을 넣지 않았다**: FusedArrivalDetector가 차량 SDK(Context)를 직접 감싸 인스턴스 자체가 JVM에서 만들어지지 않는다. 기존 보증(StampEngineTest 호출자 경계 판정·HaversineTest·U3 에뮬레이터 실측)을 유지하고, 시험을 위한 분리를 강제하는 것은 코드 동결 직전의 위험이라 남겨 둔다.
  - **F92-④ ModeStateMachine 클래스 주석 명시**: "참고용 순수 모델 — 운영 코드는 호출하지 않고 실제 전환은 ModeRouter가 담당, 삭제하지 않는다"를 머리 주석에 추가(운영 코드 미호출 재확인).
- 시험 수: 앱 단위 시험 197/197(25파일)·서버 87/87 통과. README·ARCHITECTURE·TRACEABILITY의 현재 값 문장을 197·25파일로 갱신.
- **기획서 반영 요청**: 앱 시험 수 179→197·앱 시험 파일 22→25로 갱신하고, 신규 시험 이름 3개(ApiClientHttpContractTest·DrivingScreenContractTest·QuizStateHolderTest)를 확인 방법 목록에 올린다.

---

## 2026-11-14 — 12번 문서 §4 수행 보고 (§6 형식)

1. **최종 커밋**: 내용 커밋 df3f723(12번 §4 P1-1·P1-2·P2) — 뒤이어 통합 커밋 ad23058(수용 기준 정본·SPEC_INDEX·F1~F9 기능 문서)·f1dec60(과거 실측 문장형 정리 — 숫자 대조 전 항목 통과)와 본 보고 커밋이 이어진다.
2. **시험 수**: 앱 단위 시험 197/197(25파일) · 서버 pytest 87/87. P2에서 앱 시험 7건을 추가했고 그 외는 문서·주석 변경이다. 완료 확인 명령 ①~⑥ 전부 통과(③은 API 스키마 식별자 예외 — P0-2 보고와 같다), 숫자 대조 도구는 10개 사실 전부 일치·종료 코드 0.
3. **만든·고친 문서와 도구**: docs — 별지 원문 배치 4건(00_CHARTER·AI_SCOPE·QUALITY·QUALITY_EVAL — 바이트 동일 확인), ADR 기각한 대안 14건 원문 교체, README '5분 확인' 절, README·ARCHITECTURE·TRACEABILITY 현재 값 갱신, DEV_LOG F91~F92. 코드 — ApiClient의 429→AuthException 매핑 3경로(이번 유일한 동작 변경)·FamilySessionStore·VisitQueue·ModeStateMachine 주석. 시험 — 3개 클래스 7건 신설. 릴리스 APK는 현재 코드로 재빌드(40,484,425바이트 — README·사실 대장 갱신).
4. **못 한 것과 이유**: (a) 근접 알림 실패→거리 계산 대체 전환의 JVM 단위 시험 — 차량 SDK 직접 의존으로 불가(F92-③, 기존 보증 유지). (b) 옮긴 원문(docs/QUALITY.md 등)의 시험 수·APK 바이트는 원문 수정 금지 규칙에 따라 고치지 않고 기획서 반영 요청으로 남긴다. (c) F1~F9 기능 문서의 수용 기준 건수는 정본(129건) 반영 이전 값이다(Q31 — 통합 커밋에 포함됐으므로 정본 수치로 맞추는 일이 남는다). (d) 시연 영상 재촬영·U6 청취·정차 자동 전환 재실측은 에뮬레이터 GUI 세션 과제로 남는다.
5. **기획서 반영 요청**:
   - 앱 시험 수 179→197·앱 시험 파일 22→25·신규 시험 이름 3개(ApiClientHttpContractTest·DrivingScreenContractTest·QuizStateHolderTest) — F92.
   - TripSessionTest 14→16·FamilySessionStoreTest 16→21·VisitQueueTest 9→13 — F91.
   - docs/QUALITY.md 원문의 APK 바이트 수는 현재 값(40,484,425바이트)으로 — 재빌드 반영.
   - ApiClient 오류 계약을 인용하는 곳은 '401·403·429 → AuthException'으로 — PIN 잠금 429가 앱의 재전송 중단·한 줄 안내 경로에 합류했다(F92-②).

---

## 2026-11-14 — 12번 문서 §4 P2 추가 수행: 포인트 골든셋·포트 계약 시험·F1~F9 수치 동기화 (F93)

- 확인된 사실(F-번호):
  - **F93-① 포인트 계산 골든셋(P2 추가 ⑤)**: `backend/tests/test_points_golden.py` + `backend/tests/data/points_golden.json` — evaluate_receipt 순수 함수에 입력→기대 판정 50건 표(금액 경계 0·1·50·100만·100만 1원·빈 값, floor 계산 10건, 키·사진 중복과 검사 순서 8건, 방문 연계 4건, 확인 대기 5건, 하루 한도 7건, 비율 특이값 2건)를 매개변수화했다. 기대값은 services/points.py 머리 규칙 1~7과 검사 순서(금액→키 중복→사진 중복→방문→대기→한도→계산)에서 유도한 것이고 구현의 출력을 베껴 쓰지 않았다 — 50/50 통과로 규칙과 구현이 일치함을 확인했다. 서버 시험 87→137(10파일).
  - **F93-② 포트 계약 시험(P2 추가 ④)**: `FallbackPortContractTest` 10건 — 포트 5종(주행 상태·도착 감지·차량 정보·차량 신호 조회·음성 편지 재생)의 대체 구현이 계약(같은 입력→같은 형태의 출력, 실패하면 null·failure)을 지키는지 JVM으로 고정했다. 차량 연동 구현은 SDK(Context·콜백) 직접 의존이라 JVM 불가 — 그 절반은 에뮬레이터 실측(U1~U16)이 보증하고, 경로·음성의 전환 계약은 기존 SwitchableRouteServiceTest·SwitchableNarratorTest가 담당한다(시험 머리 주석에 명시). 앱 단위 시험 197→207(26파일).
  - **F93-③ 시험 보강 중 결함 1건 자체 발견**: 신설 도착 감지 시험이 SharedFlow 구독 전 발행(구독자 없으면 버려짐)으로 영구 대기해 첫 실행이 10분 넘게 걸렸다 — 구독을 먼저 걸고 한 번 넘긴 뒤 발행·5초 시간 초과 가드로 고쳤다(시험 코드만 고침 — 운영 코드 무관).
  - **F93-④ Q31 해소**: docs/spec/F1~F9 기능 문서의 수용 기준 건수는 통합 커밋(ad23058)에서 정본 기준으로 이미 갱신돼 있었다. 9개 문서의 상태 분포를 정본과 정밀 대조한 결과 F9 하나만 어긋났고([코드 확인] 7→8·(설계) 4→3 — AC07이 재시도 루프 없음 확인으로 전환된 반영) 고쳤다. 이제 9개 문서의 건수·상태 분포가 정본과 전부 일치한다.
- 시험 수: 앱 단위 시험 207/207(26파일)·서버 137/137(10파일) 통과. README·ARCHITECTURE·TRACEABILITY·사실 대장의 현재 값을 207·26·137·10으로 갱신.
- **기획서 반영 요청**: 앱 시험 197→207(26파일)·서버 87→137(10파일 — 골든셋 50건 추가)·신규 시험 이름 2개(test_points_golden·FallbackPortContractTest)를 확인 방법에 추가한다.

---

## 2026-11-14 — 릴리스 APK와 최종 코드 일치 재확인 (F94)

- 확인된 사실(F-번호):
  - **F94 APK↔코드 일치**: 현재 HEAD 코드로 `assembleRelease` 재빌드한 결과가 게시 APK(apk/arosaegim_아로새김_release.apk)와 **바이트 수(40,484,425)·SHA-256 해시까지 동일**했다(build 29ac986c97924671… = 게시본 29ac986c97924671…) — PIN 거절 조회 경로 연결분이 게시 APK에 이미 포함돼 있음이 확정됐다(00:52 빌드가 당시 작업 트리 편집을 이미 반영). apksigner verify 통과(CN=arosaegim). 빌드 재현성(같은 입력→같은 바이트)도 함께 확인했다.
  - 운영 소스가 이 커밋 뒤 또 바뀌면 동결 시각에 같은 절차로 최종 재빌드·apk/ 재복사·바이트 수 갱신(README·사실 대장)이 필요하다.
- 시험: 해당 없음(빌드·검증만 — 코드 미변경).

---

## 2026-11-14 — E1 전체 경로: 가족 코드 전송 연결 수정 + 에뮬레이터 실측 (F95)

- 확인된 사실(F-번호):
  - **F95-① 전송 경로 결함 확정·수정**: 에뮬레이터 전 경로 실측에서 코드+PIN 입력 → 연결 → 코스 출발 → 국립경주박물관 근접 도착·스탬프 1/3(화면 문구 확인)까지 동작했고, 서버 방문 기록은 0건이었다. 원인은 TripSession 의 enqueue 호출이 familyCode = null 을 하드코딩한 것(F101 후속 과제의 실체) — 세션 코드가 큐 아이템에 실리지 않아 서버가 family_code_required 로 거부하고 큐에만 남는다. 수정: TripSession 에 familyCodeProvider 공급자 추가(기본 null — 기존 호출부 호환), 조립기에서 { familySessionStore.familyCode.value } 를 실도록 연결.
  - **F95-② 시험 2건 추가**: TripSessionTest 17(세션 가족 코드가 큐 아이템에 실린다)·18(공급자가 null이면 코드도 null — 기존 동작 유지). 격리 검증 트리에서 전체 222/222 통과. 공유 작업 트리에는 진행 중 편집(홈·메뉴 화면)이 있어 컴파일이 미완 상태였으므로, 격리 복사 트리에서 진행 중 파일만 커밋 기준본으로 되돌린 뒤 검증했다(공유 트리는 건드리지 않음).
  - **F95-③ 실측 결과**: [확인] 코드+PIN 입력·연결 성공 문구 / 코스 출발·첫 목표 내비 설정 / 근접 ENTER·스탬프 1/3. [미확인] 수정본 APK 의 전송 구간 종단(서버 visits 적재) — 내비 안내 오버레이가 화면을 지속 덮어 앱 상태 열람이 불가했고 user 10 앱 데이터는 셸에서 읽을 수 없어 큐 상태를 직접 확인하지 못했다. 서버 접근성은 별도 확인: 에뮬레이터 내부에서 10.0.2.2:8000 으로 TCP 연결 성공(닫힌 포트 9999 는 refused 대조) — 네트워크 문제는 아니다.
  - **Q33(열린 질문)**: 첫 실측에서 로그인 직후 세션이 사라졌다(진단 화면 '현재 세션: 세션 없음' — 코드는 기기 설정값에 저장돼 유지돼야 한다). 크래시 기록은 0건이다. 재로그인 후에는 정상 유지됐다. 원인 미확인 — 다음 GUI 실측 세션에서 우선 확인할 것.
- 시험: 앱 격리 검증 222/222 · 서버 137/137(골든셋 포함). 서버 DB는 실측 종료 후 테스트 가족을 삭제해 families·visits·members 0건(제출 준비 상태)을 유지했다.
- **기획서 반영 요청**: E1 전체 경로 — 차량 앱→서버 방문 전송의 가족 코드 헤더 연결이 코드로 완료됐다(시험 17·18). 종단 실측은 위 [미확인] 대로 다음 GUI 세션 과제로 남는다.
- 운영 APK 주의: 이 수정은 게시 릴리스 APK(40,484,425바이트)에 아직 없다 — 동결 시점에 최종 재빌드·apk/ 재복사·바이트 갱신(F94 절차)으로 반영한다.

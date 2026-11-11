package app.arosaegim.ui.driving

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arosaegim.R
import app.arosaegim.ui.common.ThemeState
import app.arosaegim.ui.parked.DiagnosticsMetrics
import app.arosaegim.ui.session.StartActivity

/*
 * 진단 화면(Compose). 스크롤 목록 + 버튼만 사용하고 애니메이션은 없다(주행 규제 안전 규칙).
 * 본문 최소 18sp 로 통일했다 — 이 화면은 진단 전용(정보밀도 허용)으로 정차 중에만 쓰며
 * 주행 화면(UX 규제 대상)이 아니므로 긴 상세 목록을 그대로 보여 준다.
 *
 * 04문서 시안 11 — 항목 9개, 항목마다 '구현'/'검증' 꼬리표 2개:
 *   ① 어댑터 선택 ② 수동 제어 ③ 스모크 테스트(U1~U14) ④ 마지막 오류 ⑤ 차량 세션
 *   ⑥ 기어 상태(Vehicle·Gear) ⑦ 점수 지표(T12+) ⑧ 고정 진실 ⑨ 보조 신호 메모
 * '기어 상태' 행은 01문서 10절(차종·연식 미제공 정정)에 따라 기존 '차종·연식' 관련 행을 대체한다.
 */

@Composable
fun DiagnosticsScreen(vm: DiagnosticsViewModel) {
    val results by vm.results.collectAsState()
    val selectedPlaceId by vm.selectedPlaceId.collectAsState()
    val manualLog by vm.manualLog.collectAsState()
    val lastError by vm.lastError.collectAsState()
    val sessionStatus by vm.sessionStatus.collectAsState()
    val sessionResult by vm.sessionResult.collectAsState()
    val gearState by vm.gearState.collectAsState()
    val metrics by vm.metrics.collectAsState()
    // T12+ 지표 서버 값(2026-11-11): 출처('서버'/'로컬')·상태 안내 한 줄·서버 조회 오류 1줄.
    val metricSources by vm.metricSources.collectAsState()
    val metricsNote by vm.metricsNote.collectAsState()
    val serverLastError by vm.serverLastError.collectAsState()
    val context = LocalContext.current
    var showSignOutDialog by remember { mutableStateOf(false) }

    // T12+ 지표는 이 화면에 들어올 때마다 다시 읽는다(정차 화면이 쓴 카운터를 반영).
    LaunchedEffect(Unit) { vm.refreshMetrics() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 윈도우 다크 배경 비침 방지(주간 테마 기본)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("아로새김 진단 (U1~U14)", fontSize = 22.sp)

        // ① 어댑터 선택 — 포트마다 '구현'(선택된 어댑터/사유)과 '검증'(근거 테스트 결과) 두 줄.
        SectionHeader("① 어댑터 선택")
        vm.adapterRows.forEach { row ->
            val verified = row.verifyTestId != null &&
                results.any { it.id == row.verifyTestId && it.status == SmokeResult.Status.PASS }
            Text("• ${row.port}", fontSize = 18.sp)
            ImplVerifyTags(impl = row.impl, verified = verified, fallback = "실측 전")
        }

        // ② 수동 제어 — 선택된 제공자가 Manual(대체)일 때만 주행 토글이 실제로 반영된다.
        val manualActive = vm.drivingProviderName == "ManualDrivingState"
        SectionHeader("② 수동 제어 (주행 제공자: ${vm.drivingProviderName})")
        ImplVerifyTags(
            impl = if (manualActive) "ManualDrivingState (대체)" else "${vm.drivingProviderName} (Pleos/표준)",
            verified = results.any { it.id == "U7" && it.status == SmokeResult.Status.PASS },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.setManualDriving(true) },
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Text("DRIVING", fontSize = 18.sp)
            }
            Button(
                onClick = { vm.setManualDriving(false) },
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Text("PARKED", fontSize = 18.sp)
            }
        }
        Text("목표 장소 선택", fontSize = 18.sp)
        vm.places.forEach { place ->
            val marker = if (place.id == selectedPlaceId) "▶ " else "　 "
            Button(
                onClick = { vm.selectPlace(place.id) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("$marker${place.name}", fontSize = 18.sp)
            }
        }
        Button(
            onClick = { vm.manualArrive() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text("수동 도착 (source=manual)", fontSize = 18.sp)
        }
        manualLog.forEach { line ->
            Text(line, fontSize = 18.sp)
        }

        // ③ 스모크 테스트 — U1~U14 전부(01문서 8절). 카드마다 '구현'/'검증' 두 줄을 단다.
        SectionHeader("③ 스모크 테스트 (U1~U14)")
        results.forEach { result ->
            val impl = vm.smokeImplNotes[result.id] ?: "미지정"
            val verified = result.status == SmokeResult.Status.PASS
            TestCard(result, impl, verified) { vm.onTestClicked(result.id) }
        }

        // ④ 마지막 오류.
        SectionHeader("④ 마지막 오류")
        ImplVerifyTags(
            impl = "ViewModel lastError 감시(FAIL 발생 시 한 줄 기록)",
            verified = lastError == null,
            verifiedText = "확인함(오류 없음)",
            fallback = "오류 있음 — ③ 참조",
        )
        Text(lastError ?: "없음", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
        // 정차 화면(T9-3 이름표 조회 등)이 SharedPreferences 채널로 남긴 서버 조회 오류 1줄.
        Text("• 서버 조회: ${serverLastError ?: "오류 없음"}", fontSize = 18.sp)

        // ⑤ 차량 세션(T9-1) — 가족 코드 접속 상태와 '이 차에서 로그아웃'.
        // 릴리스 흐름은 이 세션(StartActivity)부터 시작한다(DiagnosticsActivity 리다이렉트).
        SectionHeader("⑤ 차량 세션 (T9-1)")
        ImplVerifyTags(
            impl = "FamilySessionStore (SharedPreferences \"family_session\")",
            verified = sessionStatus != "세션 없음",
            verifiedText = "확인함(세션 표시 중)",
            fallback = "실측 전(세션 미생성)",
        )
        Text("현재 세션: $sessionStatus", fontSize = 18.sp)
        Button(
            onClick = { context.startActivity(Intent(context, StartActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text("가족 코드 입력 화면", fontSize = 18.sp)
        }
        Button(
            onClick = { showSignOutDialog = true },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text("이 차에서 로그아웃", fontSize = 18.sp)
        }
        // 로그아웃 결과 한 줄(파일별 삭제 결과). 상태 갱신은 DiagnosticsActivity.onResume 에서도 한다.
        sessionResult.forEach { line ->
            Text(line, fontSize = 18.sp)
        }

        // ⑥ 기어 상태(Vehicle·Gear) — 01문서 10절: CarInfo 에 제조사·연식이 없어 기존 '차종·연식'
        // 행을 대체한다. Gear 는 주행 판정의 보조 신호이며 병합 로직은 주행 state 담당(병렬 작업).
        SectionHeader("⑥ 기어 상태 (Vehicle·Gear)")
        ImplVerifyTags(
            impl = "PleosVehicleInfoProvider.readGearStateOnly (표시 전용·저장 금지)",
            verified = gearVerified(gearState),
            verifiedText = "확인함(조회 성공)",
            fallback = "실측 전(조회 대기)",
        )
        Text("기어 상태: ${gearState ?: "조회 전"}", fontSize = 18.sp)
        Text("참고: 차종·연식은 CarInfo 미제공(01문서 10절) — 수동 프로필만 사용", fontSize = 18.sp)
        Button(
            onClick = { vm.queryGearState() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text("기어 상태 조회", fontSize = 18.sp)
        }

        // ⑦ 점수 지표(T12+) — 6개 지표 + 값 출처. 기본은 로컬 카운터, 세션(가족 코드·PIN)이 있을 때
        // [지표 새로고침]으로 서버 값을 가져오면 출처가 '서버'로 바뀐다. 실패 시 로컬 값을 유지한다.
        SectionHeader("⑦ 점수 지표 (T12+)")
        ImplVerifyTags(
            impl = "SharedPreferences \"diagnostics_metrics\" (로컬 카운터) + 서버 값 교체(getMetrics)",
            verified = metrics.isNotEmpty(),
            verifiedText = "확인함(표시 중)",
            fallback = "실측 전(카운터 미읽음)",
        )
        MetricLine(metrics, metricSources, DiagnosticsMetrics.KEY_FAMILY_CODES, "가족 코드 생성 수", "로컬 카운터")
        MetricLine(metrics, metricSources, DiagnosticsMetrics.KEY_THENNOW_VIEWS, "그때와 지금 열람 수", "로컬 카운터")
        MetricLine(metrics, metricSources, DiagnosticsMetrics.KEY_STAMPBOOK_COMPLETIONS, "스탬프북 완성(완주)", "로컬 카운터")
        MetricLine(metrics, metricSources, DiagnosticsMetrics.KEY_RECEIPTS_VERIFIED, "영수증 인증 건수", "서버 연동 전 — 0 고정")
        MetricLine(metrics, metricSources, DiagnosticsMetrics.KEY_REBOOK_GAP_DAYS, "재방문 간격(일)", "서버 연동 전 — 0 고정")
        MetricLine(metrics, metricSources, DiagnosticsMetrics.KEY_HANDOVER_DESIGNATED, "물려주기 지정 수", "서버 연동 전 — 0 고정")
        // [지표 새로고침] — 세션(가족 코드·PIN)이 있을 때만 서버를 호출한다(ViewModel 이 판정).
        Button(
            onClick = { vm.refreshMetricsFromServer() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text(stringResource(R.string.diag_metrics_refresh), fontSize = 18.sp)
        }
        // 상태 안내 한 줄(세션 없음·가져오기 실패). 성공하면 사라진다. 코드·PIN 값 자체는 표시하지 않는다.
        metricsNote?.let { note ->
            Text(note, fontSize = 18.sp)
        }

        // ⑧ 고정 진실 — 개인정보 규칙(00문서 2-2)상 항상 참이어야 하는 두 문장.
        SectionHeader("⑧ 고정 진실")
        ImplVerifyTags(
            impl = "상수 표시(해당 데이터를 저장하지 않음)",
            verified = true,
            verifiedText = "확인함(설계상 항상 참)",
            fallback = "실측 전",
        )
        Text("• 저장된 이동 경로: 0건", fontSize = 18.sp)
        Text("• VIN 저장: 없음", fontSize = 18.sp)

        // ⑨ 보조 신호 메모 — 기어 병합과 '사용 안 함' 범위 고정 항목의 안내(01문서 10절·00문서 11-2).
        SectionHeader("⑨ 보조 신호 메모")
        ImplVerifyTags(
            impl = "주행 state 담당(병렬 작업) — 이 화면은 안내 표시만",
            verified = false,
            verifiedText = "확인함",
            fallback = "실측 전(병합 후 재확인)",
        )
        Text("• Gear 보조: 병합 예정 — CarUxRestrictions 와 Gear 가 어긋나면 주행(더 제한적) 우선", fontSize = 18.sp)
        Text("• U12 문 · U14 배터리: 참고 — 사용 안 함(로드맵, 조회·수신 실행 없음)", fontSize = 18.sp)

        // ── 테마(개발) — 주간/야간 토큰 전환(00문서 12절 T6, 시안 10 다크 확인용 D5 도구).
        //
        // 구조상 제한(중요 — D5 실측 절차에 그대로 기록할 것):
        //  - 전 화면이 다크 토큰으로 즉시 바뀌려면 Activity setContent 가
        //      ArosaegimTheme(isDark = ThemeState.isDark) { … }
        //    처럼 ThemeState 를 구독해야 한다(권장 패턴 — Theme.kt 주석).
        //  - 호출부 Activity(DiagnosticsActivity·ParkedActivity·DrivingActivity·StartActivity)는
        //    별도 통합 작업 소유라 이번 판에 고정 값(isDark=false 주간)을 쓴다. 그래서 이 토글은
        //    ThemeState.isDark 값만 바꾸고, 실제 다크 토큰 적용은 앱 재시작 후(호출부가 권장 패턴으로
        //    바뀐 뒤 기준) 이뤄진다.
        //  - 예외: 아래 '현재' 줄은 ThemeState 를 직접 읽는 컴포저블이라 자기 자신은 즉시 다시 그린다.
        //    테마 토큰(aroColors)은 Activity 가 고정 값을 넘기므로 여전히 주간으로 그린다.
        SectionHeader("테마 (개발)")
        ImplVerifyTags(
            impl = "ThemeState.isDark (object 홀더 — ui/common/ThemeState.kt)",
            verified = true,
            verifiedText = "토글됨(토큰 적용은 재시작 후 — 호출부 구독 대기)",
            fallback = "실측 전",
        )
        Text(
            text = "현재: ${if (ThemeState.isDark) "야간" else "주간"} — " +
                "다크 토큰은 ArosaegimTheme(isDark = ThemeState.isDark) 로 호출부를 바꾸거나 재시작 후 적용",
            fontSize = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ThemeState.isDark = false },
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Text("[주간]", fontSize = 18.sp)
            }
            Button(
                onClick = { ThemeState.isDark = true },
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Text("[야간]", fontSize = 18.sp)
            }
        }
    }

    if (showSignOutDialog) {
        // 확인 대화상자 — '이 차에서 로그아웃'은 되돌릴 수 없는 삭제라 확인을 받는다.
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("이 차에서 로그아웃", fontSize = 22.sp) },
            text = {
                Text(
                    "가족 코드와 이 차의 방문 기록·전송 대기 큐를 삭제해요. " +
                        "기록 자체는 가족 쪽에 남아 다른 차에서 이어 볼 수 있어요.",
                    fontSize = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.signOutFromCar()
                    showSignOutDialog = false
                }) {
                    Text("로그아웃", fontSize = 18.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("취소", fontSize = 18.sp)
                }
            },
        )
    }
}

/** 기어 조회 결과가 성공 조회인지 판정한다(FAILED/NOT_INITIALIZED/UNKNOWN 은 실패로 본다). */
private fun gearVerified(gear: String?): Boolean = gear != null &&
    !gear.startsWith("FAILED") && gear != "NOT_INITIALIZED" && gear != "UNKNOWN"

/**
 * ⑦ 지표 한 줄 — "이름: 값 (출처)". 값이 아직 없으면 0 으로 표시한다.
 * 출처는 ViewModel 의 [sources](키 → "server"/"local")가 '서버'/'로컬 카운터'를 고르고,
 * 아직 서버 조회가 없던 키는 기존 문구([defaultSource])를 그대로 보여 준다.
 */
@Composable
private fun MetricLine(
    metrics: Map<String, Int>,
    sources: Map<String, String>,
    key: String,
    label: String,
    defaultSource: String,
) {
    val source = when (sources[key]) {
        "server" -> stringResource(R.string.diag_metrics_source_server)
        "local" -> stringResource(R.string.diag_metrics_source_local)
        else -> defaultSource
    }
    Text("• $label: ${metrics[key] ?: 0} (출처: $source)", fontSize = 18.sp)
}

/** 항목 머리글. */
@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
}

/**
 * 시안 11의 항목 꼬리표 2개 — '구현: …' / '검증: …' 두 줄.
 * @param verified    검증 결과(근거 스모크/상태 기반). true → [verifiedText], false → [fallback].
 */
@Composable
private fun ImplVerifyTags(
    impl: String,
    verified: Boolean,
    verifiedText: String = "확인함",
    fallback: String = "실측 전",
) {
    Text("구현: $impl", fontSize = 18.sp)
    Text(
        text = "검증: ${if (verified) verifiedText else fallback}",
        fontSize = 18.sp,
        color = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TestCard(result: SmokeResult, impl: String, verified: Boolean, onClick: () -> Unit) {
    val statusText = when (result.status) {
        SmokeResult.Status.IDLE -> "대기"
        SmokeResult.Status.RUNNING -> "실행 중…"
        SmokeResult.Status.PASS -> "PASS"
        SmokeResult.Status.FAIL -> "FAIL"
    }
    val statusColor = when (result.status) {
        SmokeResult.Status.PASS -> MaterialTheme.colorScheme.primary
        SmokeResult.Status.FAIL -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Card(onClick) 는 material3 실험 API 이므로 실험 옵트인 없이 clickable 을 직접 붙인다.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${result.id}. ${result.label}", fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(statusText, fontSize = 18.sp, color = statusColor)
            }
            // 시안 11: 항목마다 '구현'/'검증' 꼬리표를 따로 표시한다.
            ImplVerifyTags(impl = impl, verified = verified)
            result.detail.forEach { line ->
                Text(line, fontSize = 18.sp)
            }
            if (result.updatedAt.isNotEmpty()) {
                Text("갱신: ${result.updatedAt}", fontSize = 18.sp)
            }
        }
    }
}

package app.arosaegim.ui.session

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.data.FamilyCodeValidator
import app.arosaegim.data.FamilySessionStore
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.PageNavigationBar
import app.arosaegim.ui.parked.ParkedActivity

/*
 * 시작 화면(T9-1) — 가족 코드 접속 또는 '가족 없이 둘러보기'(00문서 T9-1·10.6).
 *
 * 안전 규칙(04문서):
 *  - 터치 영역 76dp 이상, 본문 24sp 계열, 애니메이션 없음.
 *  - 토스트 대신 화면 안에 안내 문구를 표시한다(차량 환경은 시선 고정 — 별도 팝업 금지).
 *
 * 개인정보 규칙(00문서 9절·02문서 9절):
 *  - 가족 코드는 '식별용'이므로 password 스타일로 가리지 않는다. 대신 이 화면은
 *    distractionOptimized 를 선언하지 않아 주행(DRIVING) 상태가 되면 시스템이 자동으로
 *    가린다 — 화면 가림은 코드 노출 방지의 실제 장치다.
 *  - PIN 은 이 화면에 없다. 서버 연동 시 [이어 보기] 처리 위치에서 PIN 입력 UI 를
 *    띄우고, PIN 은 일회성으로만 쓰고 절대 저장하지 않는다(아래 signIn 호출부 주석).
 *  - 코드·PIN 을 Log 로 남기지 않는다(이 패키지에는 Log 호출이 없다).
 */

/** 코드 입력 최대 길이 — FamilyCodeValidator 규칙(6자리 영숫자)과 같다. */
private const val CODE_MAX_LENGTH = 6

@Composable
fun StartScreen(store: FamilySessionStore) {
    val context = LocalContext.current

    var code by rememberSaveable { mutableStateOf("") }
    var invalidMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    // 접속 완료 상태: null=입력 전, true=가족 코드 접속, false=둘러보기.
    var connected by rememberSaveable { mutableStateOf<Boolean?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 윈도우 다크 배경 비침 방지(주간 테마 기본)
            .verticalScroll(rememberScrollState()),
    ) {
        // 상단 헤더 — 시작 화면이므로 Back 은 숨기고 앱 이름만 표시한다.
        PageNavigationBar(courseName = null)

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (connected == null) {
                Text(
                    text = stringResource(R.string.session_title),
                    style = AroTextStyles.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                )

                // 가족 코드 입력 — 큰 글씨(36sp), 숫자·영문 키패드 힌트(Ascii).
                // 코드는 식별용이라 password 로 가리지 않는다(위 개인정보 규칙 주석).
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        // 영숫자만 남기고 6자리로 잘라 둔다 — 검증 오류를 줄이는 입력 보조.
                        code = input.filter { it.isLetterOrDigit() }.take(CODE_MAX_LENGTH)
                        invalidMessage = null
                    },
                    label = { Text(stringResource(R.string.session_code_label)) },
                    placeholder = { Text(stringResource(R.string.session_code_hint)) },
                    singleLine = true,
                    textStyle = AroTextStyles.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = invalidMessage != null,
                    supportingText = {
                        invalidMessage?.let { resId ->
                            Text(
                                text = stringResource(resId),
                                style = AroTextStyles.label.copy(
                                    color = MaterialTheme.colorScheme.error,
                                ),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp),
                )

                // [이어 보기] — 형식만 검증한다. 서버 검증은 이번 범위에서 생략:
                // Sync 담당 큐 flush 가 실질 검증이다(T9-2 이후 FamilySessionStore 주석).
                LargePrimaryButton(
                    text = stringResource(R.string.session_connect),
                    enabled = code.length == CODE_MAX_LENGTH,
                ) {
                    if (FamilyCodeValidator.validate(code)) {
                        // PIN 자리(02문서 9절): 서버 연동 시 여기서 PIN 입력 UI 를 띄우고
                        // 서버에 일회성 전달한다. PIN 은 절대 저장하지 않는다(미구현).
                        store.signIn(code)
                        connected = true
                    } else {
                        invalidMessage = R.string.session_connect_invalid
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // [가족 없이 둘러보기] — 문구 주석: 데모/개인 모드. 기록은 이 차에만 저장된다
                // (가족 기록과 연결되지 않고, '이 차에서 로그아웃'으로 지운다).
                OutlinedButton(
                    onClick = { store.enterGuestMode(); connected = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                ) {
                    Text(
                        text = stringResource(R.string.session_guest),
                        style = AroTextStyles.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.session_guest_note),
                    style = AroTextStyles.label.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            } else {
                // 접속/둘러보기 완료 — 토스트 대신 화면 안내 문구(차량용 규칙).
                val doneRes = if (connected == true) {
                    R.string.session_connect_done
                } else {
                    R.string.session_guest_done
                }
                Text(
                    text = stringResource(doneRes),
                    style = AroTextStyles.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                )

                // 정차 화면(기록 화면)으로 진행. 이미 태스크에 있으면 최상단으로 당긴다.
                LargePrimaryButton(
                    text = stringResource(R.string.session_proceed_parked),
                ) {
                    context.startActivity(
                        Intent(context, ParkedActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    )
                }
            }
        }
    }
}

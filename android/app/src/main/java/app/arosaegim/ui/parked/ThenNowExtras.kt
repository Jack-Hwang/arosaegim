package app.arosaegim.ui.parked

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arosaegim.R
import app.arosaegim.domain.HeritageInfo
import app.arosaegim.domain.VoiceLetter
import app.arosaegim.ports.VoiceLetterPlayer
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.aroColors
import kotlinx.coroutines.launch

/*
 * 점수 보강(T6-1·T6-2·T9-2·T9-3)용 정차 화면 확장 컴포저블 모음(00문서 11절).
 * ParkedComponents.kt 가 ~300줄 규칙을 넘기 때문에 이 파일로 분리했다.
 *
 * 안전 규칙(04문서): 애니메이션·자동재생 없음. 인원·거리·편지는 "안 읽히면 숨김" 원칙 —
 * 값이 null 이면 컴포저블 전체를 그리지 않는다. 색은 MaterialTheme.aroColors 토큰만.
 *
 * 고정 API 주의(통합 담당 확인 사항): 아래 타입은 병렬 작업 중인 domain/·ports/·data/ 에서
 * 제공된다 — HeritageInfo / VoiceLetter / VoiceLetterPlayer / Place.heritage /
 * ThenNowDemo.partySize / SeedContent.voiceLetters. 이 파일은 그 시그니처를 그대로 쓴다.
 */

/**
 * T6-1: 유물×가족 타임라인 한 줄(정적 — 04문서 시안 06).
 * 예: "성덕대왕신종 · 771년 ─── 우리 가족 · 1994년"
 *
 * @param heritage  시드 places[].heritage(성덕대왕신종 771년 등). null 이면 이 줄을 숨긴다.
 * @param familyYear 우리 가족의 '그때' 연도(then_now_demo.year, 예: 1994). null 이면 유물 쪽만 표시.
 */
@Composable
internal fun HeritageTimelineLine(heritage: HeritageInfo?, familyYear: Int?, modifier: Modifier = Modifier) {
    if (heritage == null) return // heritage 없는 장소는 숨김(00문서 11절)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 유물 쪽: 이름 · 연도
            Text(
                text = "${heritage.name} · ${heritage.year}년",
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            // 중간 점선 — 정적 그리기(애니메이션 아님). 양쪽 텍스트 사이를 잇는 역할만 한다.
            DashedLine(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            // 가족 쪽: 연도를 못 읽으면 이쪽만 숨긴다.
            if (familyYear != null) {
                Text(
                    text = stringResource(R.string.parked_heritage_family, familyYear),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        // 유물 노트는 검수 전 초안일 수 있으므로 1줄만 보조로 표시한다(긴 메모 금지 규칙 준용).
        val note = heritage.note
        if (note != null && heritage.reviewed.not()) {
            Text(
                text = note,
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

/** 점선 한 줄(정적). Canvas 로 직선을 그리고 PathEffect 로만 점선을 만든다(애니메이션 없음). */
@Composable
private fun DashedLine(modifier: Modifier = Modifier) {
    val color = MaterialTheme.aroColors.textQuaternary
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
    }
}

/**
 * T6-2: 함께 온 인원 한 줄 — "오늘은 N명이 함께 왔어요. 그때는 M명이었어요."
 * 좌석 점유 신호(TripSignalProvider.partySize)를 인원 수로만 쓴다. 좌석별·누가 탔는지 표시 금지
 * (04문서 화면 4 금지 항목). N 이 null 이면 문장 전체를 숨긴다(안 읽히면 숨김 원칙).
 *
 * @param todayCount 좌석 점유에서 환산한 오늘 인원. null = 신호 없음 → 숨김.
 * @param thenCount  시드 party_size(예: 5). null 이면 비교 대상이 없어 문장을 숨긴다.
 */
@Composable
internal fun PartySizeLine(todayCount: Int?, thenCount: Int?, modifier: Modifier = Modifier) {
    if (todayCount == null || thenCount == null) return // 문장 숨김
    Text(
        text = stringResource(R.string.parked_party_line, todayCount, thenCount),
        modifier = modifier.fillMaxWidth(),
        style = AroTextStyles.bodySmall,
        color = MaterialTheme.aroColors.textPrimary,
        maxLines = 2,
    )
}

/**
 * T9-2: 음성 편지 카드(예시 1건 재생). 정차 화면에만 놓인다.
 * 항상 '예시' 표기를 붙인다. 재생 실패·재생 불가 시 "예시(음성 준비 중)" 텍스트 편지 안내.
 * 자동재생 금지 — 사용자가 버튼을 눌렀을 때만 play(letterId) 를 호출한다.
 *
 * @param letter 시드 voice_letter_demo(해당 장소 placeId 일치분). null 이면 카드를 숨긴다.
 * @param player VoiceLetterPlayer 어댑터. null 이면(조립 전) 텍스트 편지만 표시한다.
 */
@Composable
internal fun VoiceLetterCard(letter: VoiceLetter?, player: VoiceLetterPlayer?, modifier: Modifier = Modifier) {
    if (letter == null) return // 이 장소에 편지가 없으면 숨김
    val scope = rememberCoroutineScope()
    // 재생 상태: 화면에 남는 것은 텍스트 한 줄뿐(애니메이션·파형 움직임 없음 — 04문서 시안 07).
    var playing by remember(letter.id) { mutableStateOf(false) }
    var failed by remember(letter.id) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.parked_voice_letter_section),
                style = AroTextStyles.titleSmall,
                color = MaterialTheme.aroColors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            // 항상 '예시' 표기(00문서 11절 — 고인 목소리 재현 아님을 명시).
            SmallTag(stringResource(R.string.parked_voice_letter_example_tag))
        }
        // sealed_label("지난 방문에 남긴 한마디") — 편지 제목 줄.
        Text(
            text = letter.sealedLabel,
            style = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.aroColors.textSecondary,
            maxLines = 1,
        )
        // 텍스트 편지 본문 — 음성이 실패해도 이 텍스트가 내용을 전달한다.
        Text(
            text = letter.text,
            style = AroTextStyles.bodySmall,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 3,
        )
        val available = player != null && player.isAvailable
        if (available) {
            Button(
                onClick = {
                    if (playing) return@Button
                    playing = true
                    failed = false
                    val target = player ?: return@Button
                    scope.launch {
                        val result = target.play(letter.id)
                        playing = false
                        // 실패 시 안내 한 줄 — 주행 화면처럼 흐름을 끊지 않고 텍스트로만 알린다.
                        failed = result.isFailure
                    }
                },
                enabled = !playing,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(
                    text = if (playing) stringResource(R.string.parked_voice_letter_playing)
                    else stringResource(R.string.parked_voice_letter_play),
                    fontSize = 20.sp,
                )
            }
        }
        // 재생 불가(어댑터 없음) 또는 재생 실패 → "예시(음성 준비 중)" 안내.
        if (!available || failed) {
            Text(
                text = stringResource(R.string.parked_voice_letter_unavailable),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * T9-3: '오늘의 자리 이름표' — 운전석 / 조수석 길잡이 / 뒷좌석 탐험가.
 *
 * 값은 서버(members.seat_role) 실데이터다(2026-11-11 연결): seat_role("driver"|"navigator"|
 * "explorer") → nickname Map. 가족 코드·PIN 세션이 없거나(게스트·미연결·PIN 휘발 소실)
 * 조회 실패하면 호출부가 null/빈 Map 을 넘겨 카드 전체를 숨긴다(00문서 13절: 없으면 숨김).
 *
 * @param seatTags seat_role 코드 → 표시 이름. null·빈 Map = 표시 금지.
 */
@Composable
internal fun SeatNameTags(seatTags: Map<String, String>?, modifier: Modifier = Modifier) {
    if (seatTags.isNullOrEmpty()) return // 세션 없음/값 없음 = 표시 금지
    SectionCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.parked_seat_names_section),
            style = AroTextStyles.titleSmall,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 1,
        )
        seatTags.entries.take(4).forEach { (role, name) ->
            Text(
                text = "${seatRoleLabel(role)} — $name",
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/** members.seat_role 코드를 화면 라벨로 바꾼다. 모르는 코드는 그 값을 그대로 쓴다(방어). */
@Composable
private fun seatRoleLabel(role: String): String = when (role) {
    "driver" -> stringResource(R.string.seat_role_driver)
    "navigator" -> stringResource(R.string.seat_role_navigator)
    "explorer" -> stringResource(R.string.seat_role_explorer)
    else -> role
}

/**
 * T12+ Diagnostics 지표 로컬 카운터(SharedPreferences "diagnostics_metrics").
 *
 * 서버 연동 전까지는 값 출처가 전부 '로컬 카운터'다(진단 화면에 출처 표시). ParkedScreen 이
 * 쓰는 증가 지점과 DiagnosticsViewModel 이 읽는 표시 지점이 같은 파일을 공유하므로
 * 키를 여기서 한 곳으로 관리한다. ui.parked 와 ui.driving 양쪽에서 쓴다(internal, 모듈 범위).
 *
 * 기록 규칙: 좌표·코드·개인 식별값은 저장하지 않고 횟수만 센다(00문서 2-2 개인정보 규칙).
 */
object DiagnosticsMetrics {

    /** SharedPreferences 파일 이름 — 진단 화면과 정차 화면이 공유한다. */
    const val PREFS_NAME = "diagnostics_metrics"

    // 6개 지표 키(00문서 11절 T12+).
    const val KEY_FAMILY_CODES = "family_codes_created"       // 가족 코드 생성 수(시작 화면 연동 대기)
    const val KEY_THENNOW_VIEWS = "thennow_views"             // 그때와 지금 열람 수(스텝3 진입)
    const val KEY_RECEIPTS_VERIFIED = "receipts_verified"     // 영수증 인증 전환(서버 연동 전 0)
    const val KEY_REBOOK_GAP_DAYS = "rebook_gap_days"         // 재방문 간격(서버 연동 전 0)
    const val KEY_STAMPBOOK_COMPLETIONS = "stampbook_completions" // 스탬프북 완주(완주 시 +1)
    const val KEY_HANDOVER_DESIGNATED = "handover_designated" // 물려주기 지정 수(서버 연동 전 0)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 카운터 하나를 [by] 만큼 올린다. 실패는 조용히 흡수한다(지표가 기능을 막지 않게). */
    fun increment(context: Context, key: String, by: Int = 1) {
        runCatching {
            prefs(context).edit().putInt(key, prefs(context).getInt(key, 0) + by).apply()
        }
    }

    /** 키 하나를 고정값으로 쓴다(서버 값 반영·초기화 용도). */
    fun set(context: Context, key: String, value: Int) {
        runCatching { prefs(context).edit().putInt(key, value).apply() }
    }

    /** 전체 지표를 한 번에 읽는다. 없는 키는 0 으로 채워 돌려준다(진단 화면 표시용). */
    fun readAll(context: Context): Map<String, Int> {
        val p = prefs(context)
        return mapOf(
            KEY_FAMILY_CODES to p.getInt(KEY_FAMILY_CODES, 0),
            KEY_THENNOW_VIEWS to p.getInt(KEY_THENNOW_VIEWS, 0),
            KEY_RECEIPTS_VERIFIED to p.getInt(KEY_RECEIPTS_VERIFIED, 0),
            KEY_REBOOK_GAP_DAYS to p.getInt(KEY_REBOOK_GAP_DAYS, 0),
            KEY_STAMPBOOK_COMPLETIONS to p.getInt(KEY_STAMPBOOK_COMPLETIONS, 0),
            KEY_HANDOVER_DESIGNATED to p.getInt(KEY_HANDOVER_DESIGNATED, 0),
        )
    }

    // ── 서버 조회 오류 1줄 채널(2026-11-11) ──
    // 정차 화면(ParkedScreen, T9-3 이름표 조회)과 진단 화면(④ '마지막 오류')이 다른 Activity 라
    // 메모리 StateFlow 를 공유할 수 없다. 그래서 이 SharedPreferences 파일을 1줄짜리 전달 통로로
    // 쓴다: 정차 화면이 실패 원인 1줄을 기록하면 진단 화면이 refreshMetrics 때 읽어 ④에 표시한다.
    // 규칙: 문구에 가족 코드·PIN·좌표 등 식별값을 넣지 않는다(00문서 2-2).
    const val KEY_LAST_ERROR = "last_error"

    /** 서버 조회 실패 원인 1줄을 기록한다(진단 화면 ④ '마지막 오류' 표시용). */
    fun setLastError(context: Context, message: String) {
        runCatching { prefs(context).edit().putString(KEY_LAST_ERROR, message).apply() }
    }

    /** 성공 시(또는 세션 초기화 시) 기록을 지운다. */
    fun clearLastError(context: Context) {
        runCatching { prefs(context).edit().remove(KEY_LAST_ERROR).apply() }
    }

    /** 마지막 서버 조회 오류 1줄. 없으면 null — '오류 없음'으로 표시한다. */
    fun lastError(context: Context): String? =
        runCatching { prefs(context).getString(KEY_LAST_ERROR, null) }.getOrNull()
}

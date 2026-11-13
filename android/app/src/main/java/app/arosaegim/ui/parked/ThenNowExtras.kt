package app.arosaegim.ui.parked

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arosaegim.R
import app.arosaegim.domain.HeritageInfo
import app.arosaegim.domain.Place
import app.arosaegim.domain.VoiceLetter
import app.arosaegim.ports.VoiceLetterPlayer
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.aroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/*
 * 점수 보강(T6-1·T6-2·T9-2·T9-3)용 정차 화면 확장 컴포저블 모음(00문서 11절).
 * ParkedComponents.kt 가 ~300줄 규칙을 넘기 때문에 이 파일로 분리했다.
 *
 * 안전 규칙(04문서): 애니메이션·자동재생 없음. 인원·거리·편지는 "안 읽히면 숨김" 원칙 —
 * 값이 null 이면 컴포저블 전체를 그리지 않는다. 색은 MaterialTheme.aroColors 토큰만.
 *
 * 고정 API 주의(통합 시 확정): 아래 타입은 domain/·ports/·data/ 에서
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
 * B4(2026-11-11): 문장 옆 작은 '접어 두기' 버튼(정차 화면 전용). 누르면 [onFold] →
 * POST /v1/families/me/folded-places 가 호출되고 이 장소에서만 문장이 다시 나오지 않는다
 * (다른 장소 무영향 — 서버 folded_place_ids 가 보장). [comparisonFolded]=true(이 장소 접힘 또는
 * 가족 설정 fold_person_comparison_all)면 문장 자체를 그리지 않는다. then-now 조회 실패면
 * 호출부가 showFoldButton=false 로 넘겨 버튼만 숨기고 문장은 기존대로(계약).
 *
 * @param todayCount 좌석 점유에서 환산한 오늘 인원. null = 신호 없음 → 숨김.
 * @param thenCount  시드 party_size(예: 5). null 이면 비교 대상이 없어 문장을 숨긴다.
 * @param comparisonFolded 이 장소(또는 전역 설정)로 문장이 접혀 있으면 true → 문장·버튼 모두 숨김.
 * @param showFoldButton 접기 버튼 표시(then-now 조회 성공 + 전역 설정 아님). 기본 false —
 *                       상태를 못 읽은 기존 호출부는 버튼 없이 기존 동작 그대로다.
 * @param onFold     접기 버튼 처리(POST folded-places → 로컬 상태 갱신은 ParkedScreen 이 담당).
 */
@Composable
internal fun PartySizeLine(
    todayCount: Int?,
    thenCount: Int?,
    comparisonFolded: Boolean = false,
    showFoldButton: Boolean = false,
    onFold: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (todayCount == null || thenCount == null) return // 문장 숨김(안 읽히면 숨김)
    if (comparisonFolded) return // B4: 접힘 — 이 장소에서 문장 다시 안 나옴(전역 설정 포함)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.parked_party_line, todayCount, thenCount),
            modifier = Modifier.weight(1f),
            style = AroTextStyles.bodySmall,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 2,
        )
        if (showFoldButton && onFold != null) {
            // 작은 보조 버튼 — 정차 화면 전용. 주요 버튼(76dp+)이 아닌 보조 조작 칸(탭 내비와 같은 높이).
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFold() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.party_fold_button),
                    style = AroTextStyles.label,
                    color = MaterialTheme.aroColors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
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

// ── E2(2026-11-11): '그때와 지금' 서버 실데이터 표시(GET /v1/places/{id}/then-now) ──
// ParkedScreen 이 apiClient.getPlaceThenNow 원본 JSON 을 파싱해 [ServerThenNowInfo] 로 만들어
// 넘기면, 이 파일은 그 값을 그리는 일만 한다(조회·파싱은 ParkedScreen, 표시는 이 파일 — 역할 분리).

/**
 * 서버 then-now 응답에서 화면에 필요한 값만 뽑아 담는 표시 모델.
 * ParkedScreen.parseThenNowServerData 가 만들고 ThenNowStep 이 그린다. 어디에도 저장하지 않는다.
 *
 * @property past 서버의 과거 기록. null = 서버가 '과거 없음'(첫 방문)이라 답한 것 —
 *                예시(시드) 대신 첫 방문 문구를 보인다(02문서 4절 우선순위: 서버 past > 시드 예시 > 첫 방문).
 *                과거가 기억(사진)이면 [ThenNowServerPast.markerNames]에 사진 속 이름표가 함께 온다(B5).
 * @property nowPartySize 이번 방문 기록의 인원(now.party_size). 없으면 null → 줄 숨김(04문서 화면 4).
 * @property nowTripKm 이번 방문(코스) 주행거리(now.trip_km). 없으면 null → 줄 숨김.
 * @property visitOrdinal 이번 방문이 가족·장소 기준 몇 번째인지(1=첫 방문). 없으면 null.
 */
internal data class ServerThenNowInfo(
    val past: ThenNowServerPast?,
    val nowPartySize: Int?,
    val nowTripKm: Double?,
    val visitOrdinal: Int?,
)

/** 서버 과거 기록 표시값 묶음('그때' 카드 한 장분). 연도는 서버 year 가 진짜(00문서 10.4). */
internal data class ThenNowServerPast(
    val year: Int,
    val yearsAgo: Int,
    val note: String?,
    /** /uploads/... 상대경로 또는 절대 URL. null·빈값이면 사진 칸 없이 글만 표시한다. */
    val photoUrl: String?,
    val partySize: Int?,
    val vehicleLabel: String?,
    /** memory(가족이 남긴 기억) | visit(앱 자동 기록). 화면 표기에는 아직 쓰지 않고 보관만 한다. */
    val kind: String?,
    /**
     * B5(2026-11-12): 사진 속 이름표 — 가족이 웹에서 직접 붙인 이름만, 서버 응답 순서 그대로.
     * 빈 목록이면 '함께한 사람' 줄을 숨긴다(ThenMarkerNamesLine 계약 — 점·좌표는 차량에 그리지 않는다).
     */
    val markerNames: List<String> = emptyList(),
)

/**
 * 서버 사진·요청에 쓰는 서버 주소. ApiClient 의 baseUrl 은 private 라 읽을 수 없어 같은 기본값을
 * 상수로 둔다(에뮬레이터에서 호스트 머신 루프백). photo_url 이 /uploads/... 상대경로로 올 때만
 * 이 주소와 합치고, http(s) 로 시작하는 절대 URL 이면 그 값을 그대로 쓴다(방어).
 */
internal const val THEN_NOW_SERVER_BASE = "http://10.0.2.2:8000"

/** photo_url(상대경로 또는 절대 URL)을 이미지 로드용 절대 URL 로 바꾼다. */
internal fun resolveServerPhotoUrl(photoUrl: String): String {
    if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) return photoUrl
    val base = THEN_NOW_SERVER_BASE.trimEnd('/')
    return if (photoUrl.startsWith("/")) base + photoUrl else "$base/$photoUrl"
}

/**
 * E2: 서버 사진 한 장(photo_url — /uploads/... 상대경로)을 네트워크로 내려 그린다.
 * 이미지 라이브러리(Coil 등)를 쓰지 않는다 — ApiClient 와 같은 표준 HttpURLConnection.
 *  - 로드 중: 기존 '사진 자리' 박스(예시 경로의 then_placeholder 자리와 같은 모양).
 *  - 실패: 사진 칸을 통째로 그리지 않고 글만 남긴다(계약 — 조용히 흡수, Log 없음).
 *  - 서버가 5MB 를 넘는 업로드를 검증하므로, 클라이언트는 알려진 크기 10MB 초과만 거부한다(방어).
 * 애니메이션 없음.
 */
@Composable
internal fun ThenNetworkPhoto(photoUrl: String, modifier: Modifier = Modifier) {
    var bitmap by remember(photoUrl) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(photoUrl) { mutableStateOf(false) }
    LaunchedEffect(photoUrl) {
        val bmp = runCatching {
            withContext(Dispatchers.IO) { downloadBitmap(resolveServerPhotoUrl(photoUrl)) }
        }.getOrNull()
        if (bmp != null) bitmap = bmp else failed = true
    }
    val loaded = bitmap
    when {
        loaded != null -> Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        // 실패 확정 전(로드 중)은 자리표시자 박스 — 실패 확정 후엔 아무것도 그리지 않는다(글만).
        !failed -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.aroColors.surfaceLow),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.parked_photo_placeholder),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textQuaternary,
            )
        }
    }
}

/** HttpURLConnection 으로 이미지를 내려받아 디코딩한다. 실패는 null — 호출측 runCatching 이 흡수. */
private fun downloadBitmap(url: String): ImageBitmap? {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 3_000 // 조회 규정과 같은 3초(00문서 — 서버 조회 타임아웃).
        conn.readTimeout = 3_000
        conn.useCaches = true
        if (conn.responseCode !in 200..299) return null
        // 서버가 알려 주는 크기가 10MB 를 넘으면 읽지 않는다(모르면 -1 → 통과).
        if (conn.contentLengthLong > 10_000_000L) return null
        val bytes = conn.inputStream.use { it.readBytes() }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } finally {
        conn.disconnect()
    }
}

/**
 * B5(2026-11-12): 사진 속 이름표를 사진 아래 글자로만 보여 준다 —
 * 예: "함께한 사람: 할아버지 · 큰아버지".
 *  - 웹 '그때 기록하기'에서 가족이 직접 붙인 이름만 온다. 얼굴 자동 인식·라이브러리 없음,
 *    사진 위 점·이름표를 그리지 않으며 차량에서 입력·수정도 없다(07문서 B5 계약).
 *  - 이름이 하나도 없으면(서버 미제공·조회 실패·이름 전부 빈칸) 줄을 통째로 그리지 않는다
 *    ("안 읽히면 숨김" 원칙 — 빈 문구·크래시 금지).
 *  - 상한 10명(02문서 13절 — 파서가 이미 잘라 두지만 여기서 한 번 더 방어), 줄은 3줄 이내.
 */
@Composable
internal fun ThenMarkerNamesLine(names: List<String>, modifier: Modifier = Modifier) {
    val cleaned = names.filter { it.isNotBlank() }.take(10)
    if (cleaned.isEmpty()) return // 이름 없음 = 줄 숨김
    Text(
        text = stringResource(R.string.parked_then_marker_names, cleaned.joinToString(" · ")),
        modifier = modifier.fillMaxWidth(),
        style = AroTextStyles.bodySmall,
        color = MaterialTheme.aroColors.textPrimary,
        maxLines = 3,
    )
}

/**
 * '그때와 지금' 카드 아래 점선 보강 묶음(T6-1·T6-2·T9-2·T9-3) — 예시(시드) 경로와 서버 경로가
 * 같은 구성을 공유한다. 각 줄은 값이 없으면 스스로 숨는다("안 읽히면 숨김" 원칙).
 *
 * @param familyYear 우리 가족의 '그때' 연도(서버 past.year 또는 시드 demo.year). null 이면 유물 쪽만 표시.
 * @param thenCount  그때 인원(서버 party_size 또는 시드 partySize). null 이면 인원 문장을 숨긴다.
 * @param todayCount 좌석 점유에서 환산한 오늘 인원(TripSession). null 이면 인원 문장을 숨긴다.
 */
@Composable
internal fun ThenNowExtrasColumn(
    place: Place?,
    familyYear: Int?,
    thenCount: Int?,
    todayCount: Int?,
    comparisonFolded: Boolean,
    showFoldButton: Boolean,
    onFold: () -> Unit,
    voiceLetter: VoiceLetter?,
    voiceLetterPlayer: VoiceLetterPlayer?,
    seatTags: Map<String, String>?,
    seatAuthError: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ① 유물×가족 타임라인 한 줄(정적). heritage 가 파싱되지 않은 장소면 숨는다.
        HeritageTimelineLine(heritage = place?.heritage, familyYear = familyYear)
        // ② 인원 문장 — 인원 수만(좌석별·누가 탔는지 표시 금지). 옆 작은 '접어 두기' 버튼(B4).
        PartySizeLine(
            todayCount = todayCount,
            thenCount = thenCount,
            comparisonFolded = comparisonFolded,
            showFoldButton = showFoldButton,
            onFold = onFold,
        )
        // ③ 음성 편지(예시) — 정차 화면 전용. 주행 전환 시 ParkedActivity.onPause 에서 재생 중단.
        SectionCard { VoiceLetterCard(letter = voiceLetter, player = voiceLetterPlayer) }
        // ④ 자리 이름표(T9-3) — 세션 없음·조회 실패면 숨김, 401/403 만 작은 안내 한 줄.
        if (seatAuthError && seatTags.isNullOrEmpty()) {
            SectionCard {
                Text(
                    text = stringResource(R.string.parked_seat_names_auth_error),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textTertiary,
                    maxLines = 1,
                )
            }
        } else {
            SeatNameTags(seatTags = seatTags)
        }
    }
}

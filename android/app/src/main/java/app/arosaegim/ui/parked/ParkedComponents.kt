package app.arosaegim.ui.parked

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arosaegim.AppContainer
import app.arosaegim.R
import app.arosaegim.data.SeedContent
import app.arosaegim.data.ThenNowDemo
import app.arosaegim.domain.Place
import app.arosaegim.domain.StampBook
import app.arosaegim.domain.StampBookDef
import app.arosaegim.domain.ThenNowPlaceState
import app.arosaegim.domain.VoiceLetter
import app.arosaegim.ports.VoiceLetterPlayer
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.aroColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * 정차 화면 보조 컴포저블 모음.
 * ui/common 통합 예정: [ParkedPageNavigationBar] 는 04문서 공통 규격 "PageNavigationBar" 로,
 * [SmallTag] 는 "DemoModeTag" 로 ui/common 이 갱신되면 이 로컬 구현을 대체할 예정이다.
 * 안전 규칙: 색은 MaterialTheme.aroColors 토큰만, 애니메이션·효과음 없음.
 */

/** 상단 스텝 내비게이션(탭 아님 — 현재 스텝 위치 표시와 이동). ui/common PageNavigationBar 통합 예정. */
@Composable
internal fun ParkedPageNavigationBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.aroColors.backgroundSecondary, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, label ->
                val isSelected = i == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.aroColors.surfaceHigh else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = AroTextStyles.label,
                        color = if (isSelected) MaterialTheme.aroColors.textPrimary else MaterialTheme.aroColors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 꼬리표("데모 모드", "초안", "예시(시뮬레이션)"). ui/common DemoModeTag 통합 예정. */
@Composable
internal fun SmallTag(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.aroColors.surfaceLow, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = AroTextStyles.label,
            color = MaterialTheme.aroColors.textTertiary,
        )
    }
}

/** 정차 화면 기본 카드. 본문은 한 화면에 한 가지 일 — 카드 하나가 하나의 정보 덩어리다. */
@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.aroColors.surfaceBase,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/**
 * 보조 큰 버튼(높이 76dp — 04문서 접근성 하한). 주 버튼(LargePrimaryButton)과 같은 화면에
 * 쓸 때는 보조 동작(접기/펼치기, 기록 열람 등)에만 사용한다. 애니메이션 없음.
 */
@Composable
internal fun LargeSecondaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.aroColors.buttonBasic)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 1,
        )
    }
}

/** 스탬프 칸 표시(텍스트 심볼 자리표시자): 찍힘 ●(인주) / 안 찍힘 ○(비활성). */
@Composable
internal fun StampSlots(stampedCount: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total.coerceAtLeast(0)) { i ->
            Text(
                text = if (i < stampedCount) "●" else "○",
                style = TextStyle(fontSize = 32.sp),
                color = if (i < stampedCount) MaterialTheme.aroColors.stampPoint else MaterialTheme.aroColors.textQuaternary,
            )
        }
    }
}

/** 퀴즈 보기 큰 버튼(높이 76dp 이상 — 04문서 접근성). 채점 후 정답/오답 보기를 구분 표시한다. */
@Composable
internal fun QuizChoiceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    correct: Boolean = false,
    wrong: Boolean = false,
) {
    val aro = MaterialTheme.aroColors
    val border = when {
        correct -> BorderStroke(2.dp, aro.stampPoint)
        wrong -> BorderStroke(2.dp, aro.textQuaternary)
        else -> null
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (correct) aro.surfaceAccent else aro.buttonBasic)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
            color = if (correct) aro.stampPoint else aro.textPrimary,
            maxLines = 2,
        )
    }
}

/**
 * 스텝3: 그때와 지금 카드. 좌 '그때'(연대·사진 자리·메모 2줄·그때의 차) / 우 '지금'(오늘·도착 시각·지금의 차).
 * 좁은 창(세로)에서는 위아래로 쌓는다(04문서 공통·화면 4).
 *
 * E2(2026-11-11) — 표시 우선순위(02문서 4절): 서버 past > 시드 then_now_demo(예시) > 첫 방문 문구.
 *  - serverPast 가 있고 past 가 있으면: 서버 실데이터(연도·메모·사진·그때 인원·그때의 차)를 그리고
 *    '예시' 태그는 숨긴다. 사진은 photo_url 을 네트워크로 내려오고, 실패하면 글만 남긴다.
 *  - serverPast 가 있고 past 가 null 이면: 서버가 '과거 없음'이라 답한 것 — 첫 방문 문구.
 *  - serverPast 가 null(미조회·실패·세션 없음)이면: 기존 동작대로 시드 then_now_demo(예시 태그) 폴백.
 *
 * 점수 보강(00문서 11절) — 카드 아래에 차례로([ThenNowExtrasColumn] 공용):
 *  ① T6-1 유물×가족 타임라인 한 줄(정적, heritage 없는 장소는 숨김)
 *  ② T6-2 인원 문장(좌석 점유 신호가 없으면 숨김 — 인원 수만, 좌석별 표시 금지)
 *  ③ T9-2 음성 편지 카드(예시 태그, 정차 화면 전용 버튼)
 *  ④ T9-3 자리 이름표(서버 members.seat_role 실데이터 — 세션 없음·조회 실패면 숨김)
 */
@Composable
internal fun ThenNowStep(
    container: AppContainer,
    demo: ThenNowDemo?,
    place: Place?,
    isWide: Boolean,
    partySizeNow: Int?,
    voiceLetter: VoiceLetter?,
    voiceLetterPlayer: VoiceLetterPlayer?,
    // ── E2 서버 과거 기록(2026-11-11) ── ParkedScreen 이 스텝3 진입 때 apiClient.getThenNow 로
    // 조회·파싱한 값(ThenNowExtras.kt 의 ServerThenNowInfo). null = 미조회·실패·세션 없음 →
    // 예시(시드) 폴백으로 기존 동작을 유지한다. 서버 값이면 '예시' 태그를 숨긴다(계약).
    serverPast: ServerThenNowInfo? = null,
    // T9-3 서버 실데이터(2026-11-11): seat_role → 이름 Map. null·빈 Map = 이름표 카드 숨김.
    seatTags: Map<String, String>? = null,
    // 코드·PIN 불일치(401/403)일 때만 true — 이름표 대신 카드 안 작은 안내 한 줄을 보인다.
    seatAuthError: Boolean = false,
    // ── B2·B4 then-now 부가 상태(2026-11-11) ──
    // 서버 then-now 조회 성공분(parseThenNowPlaceState). null = 조회 실패·필드 없음 →
    // 첫 기록 문구·접기 버튼을 만들지 않고 기존 동작(빈 칸/숨김)을 유지한다(추측 금지).
    thenNowState: ThenNowPlaceState? = null,
    // B4: 인원 비교 문장 접힘(이 장소 folded 또는 가족 설정 fold_person_comparison_all).
    comparisonFolded: Boolean = false,
    // B4: 접기 버튼 표시 가능(then-now 조회 성공 + 전역 설정 아님). 조회 실패면 false(버튼만 숨김).
    showFoldButton: Boolean = false,
    // B4: 접기 버튼 처리 — ParkedScreen 이 POST folded-places 를 호출하고 상태를 갱신한다.
    onFoldComparison: () -> Unit = {},
) {
    // 도착 시각: TripSession 이 도착 갱신 시각을 주지 않으므로 이 화면 진입 시각으로 표시한다.
    val arrivalTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    val todayText = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date()) }
    // 지금의 차: 차량 프로필을 비동기로 읽고, 프로필이 없으면 '오늘의 차'.
    var nowVehicle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { nowVehicle = runCatching { container.vehicleInfo.read()?.model }.getOrNull() }
    val nowVehicleText = nowVehicle ?: stringResource(R.string.parked_now_vehicle_default)

    // '지금' 카드 — 예시·서버 경로 공용. 서버의 이번 방문 기록(now.party_size·now.trip_km)은
    // 있을 때만 한 줄씩 추가한다(04문서 화면 4 — 없으면 숨김). 예시 폴백 때는 자동으로 숨는다.
    val nowCard: @Composable () -> Unit = {
        SectionCard {
            Text(stringResource(R.string.parked_now_label), style = AroTextStyles.titleSmall, color = MaterialTheme.aroColors.textPrimary)
            Text(stringResource(R.string.parked_now_today, todayText), style = AroTextStyles.bodySmall, color = MaterialTheme.aroColors.textPrimary, maxLines = 1)
            Text(stringResource(R.string.parked_now_arrival, arrivalTime), style = AroTextStyles.bodySmall, color = MaterialTheme.aroColors.textPrimary, maxLines = 1)
            Text(
                text = stringResource(R.string.parked_now_vehicle, nowVehicleText),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textSecondary,
                maxLines = 2,
            )
            val nowParty = serverPast?.nowPartySize
            val nowTripKm = serverPast?.nowTripKm
            if (nowParty != null) {
                Text(
                    text = stringResource(R.string.parked_now_party, nowParty),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textPrimary,
                    maxLines = 1,
                )
            }
            if (nowTripKm != null) {
                Text(
                    text = stringResource(R.string.parked_trip_distance, nowTripKm),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
    // 넓은 창(가로)은 좌우, 좁은 창(세로)은 위아래(04문서 공통). thenCard 만 경로별로 바뀐다.
    val showPair: @Composable (@Composable () -> Unit) -> Unit = { thenCard ->
        if (isWide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { thenCard() }
                Box(Modifier.weight(1f)) { nowCard() }
            }
        } else {
            // 세로 창(04문서 시안 10): '그때와 지금' 카드를 위아래로 쌓는다.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                thenCard()
                nowCard()
            }
        }
    }

    // ── 1순위: 서버 실데이터(E2). ──
    val server = serverPast
    if (server != null) {
        val past = server.past
        if (past == null) {
            // 서버가 '과거 기록 없음'이라 답했다 — 예시 대신 첫 방문 문구(past null 계약).
            // visit_ordinal==1 은 '첫 기록' 문구(B2 — 서버 응답에서 우선 읽고, 없으면 B2 조회분).
            FirstVisitCard(
                firstRecord = (server.visitOrdinal ?: thenNowState?.visitOrdinal) == 1,
                todayPartyCount = partySizeNow,
            )
            return
        }
        // 연도 줄: 서버는 year 가 진짜 — "1994년 (32년 전)" 형식. years_ago 는 서버값 우선,
        // 없으면(파서가 현재 연도로 계산해 둔다) 그 값을 쓴다. 올해 기록이면 연도만 표시.
        val yearLabel = if (past.yearsAgo > 0) {
            stringResource(R.string.parked_then_server_year, past.year, past.yearsAgo)
        } else {
            "${past.year}년"
        }
        val thenCard: @Composable () -> Unit = {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.parked_then_label), style = AroTextStyles.titleSmall, color = MaterialTheme.aroColors.textPrimary, modifier = Modifier.weight(1f))
                    // 서버 실데이터 — '예시' 태그를 붙이지 않는다(계약: 서버 값이면 태그 숨김).
                }
                SmallTag(yearLabel)
                // 사진: photo_url 네트워크 로드. 경로 없음·실패면 칸 없이 아래 메모만(글만 — 계약).
                past.photoUrl?.let { ThenNetworkPhoto(photoUrl = it) }
                // 메모는 2줄로 자른다(04문서 화면 4 금지: 긴 메모 전체 표시).
                past.note?.let {
                    Text(it, style = AroTextStyles.bodySmall, color = MaterialTheme.aroColors.textPrimary, maxLines = 2)
                }
                // 그때 인원 — 인원 수만(누가 탔는지 표시 금지, 04문서 화면 4). 없으면 줄 숨김.
                past.partySize?.let {
                    Text(
                        text = stringResource(R.string.parked_then_party, it),
                        style = AroTextStyles.bodySmall,
                        color = MaterialTheme.aroColors.textPrimary,
                        maxLines = 1,
                    )
                }
                past.vehicleLabel?.let {
                    Text(
                        text = stringResource(R.string.parked_then_vehicle, it),
                        style = AroTextStyles.bodySmall,
                        color = MaterialTheme.aroColors.textSecondary,
                        maxLines = 2,
                    )
                }
            }
        }
        showPair(thenCard)
        ThenNowExtrasColumn(
            place = place,
            familyYear = past.year, // 타임라인의 '우리 가족' 연도도 서버 기록을 쓴다.
            thenCount = past.partySize,
            todayCount = partySizeNow,
            comparisonFolded = comparisonFolded,
            showFoldButton = showFoldButton,
            onFold = onFoldComparison,
            voiceLetter = voiceLetter,
            voiceLetterPlayer = voiceLetterPlayer,
            seatTags = seatTags,
            seatAuthError = seatAuthError,
        )
        return
    }

    // ── 2·3순위: 예시(시드) 폴백 / 첫 방문 — 서버 응답이 없을 때의 기존 동작(02문서 4절). ──
    if (demo == null || place == null || demo.placeId != place.id) {
        // 빈 상태(04문서): 과거 기록이 없으면 첫 방문 안내 한 줄.
        // B2(2026-11-11): 서버 then-now 가 visit_ordinal==1(옛 기록·지난 방문이 전혀 없음)을
        // 확인한 때만 빈 칸 대신 첫 기록 문구로 바꾼다. 서버 필드 없음·조회 실패 시 추측으로
        // 문구를 만들지 않고 기존 한 줄을 유지한다(계약). 인원은 '오늘은 N명이 함께 왔어요'까지만.
        FirstVisitCard(
            firstRecord = thenNowState != null && thenNowState.visitOrdinal == 1,
            todayPartyCount = partySizeNow,
        )
        return
    }
    // 예시(시드 then_now_demo) — 서버 응답이 없을 때만 본다. 카드에 '예시' 태그를 붙인다(계약).
    // 연도 표시 규칙(00문서 10.4): label("1990년대 중반") 우선, 없으면 현재 연도 − yearsAgo.
    // 절대 연도(demo.year, 예: 1994)가 시드에 있으면 타임라인의 '우리 가족' 쪽에 그 값을 쓴다.
    val pastYear = Calendar.getInstance().get(Calendar.YEAR) - demo.yearsAgo
    val thenCard: @Composable () -> Unit = {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.parked_then_label), style = AroTextStyles.titleSmall, color = MaterialTheme.aroColors.textPrimary, modifier = Modifier.weight(1f))
                SmallTag(stringResource(R.string.parked_thennow_example_tag))
            }
            SmallTag(demo.label ?: "${pastYear}년")
            ThenPhoto(demo.photoAsset)
            // 메모는 2줄로 자른다(04문서 화면 4 금지: 긴 메모 전체 표시).
            Text(demo.note, style = AroTextStyles.bodySmall, color = MaterialTheme.aroColors.textPrimary, maxLines = 2)
            Text(
                text = stringResource(R.string.parked_then_vehicle, demo.vehicleLabel),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textSecondary,
                maxLines = 2,
            )
        }
    }
    showPair(thenCard)
    ThenNowExtrasColumn(
        place = place,
        familyYear = demo.year,
        thenCount = demo.partySize,
        todayCount = partySizeNow,
        comparisonFolded = comparisonFolded,
        showFoldButton = showFoldButton,
        onFold = onFoldComparison,
        voiceLetter = voiceLetter,
        voiceLetterPlayer = voiceLetterPlayer,
        seatTags = seatTags,
        seatAuthError = seatAuthError,
    )
}

/**
 * 첫 방문 카드(공용). 서버(B2·E2)가 visit_ordinal==1 을 확인한 때만 '첫 기록' 문구로 바꾸고,
 * 그 외(미조회·실패·2번째 이상)는 기존 한 줄을 유지한다(추측 금지 — 값이 없으면 만들지 않는다).
 */
@Composable
private fun FirstVisitCard(firstRecord: Boolean, todayPartyCount: Int?) {
    if (firstRecord) {
        SectionCard { FirstRecordContent(todayPartyCount = todayPartyCount) }
    } else {
        SectionCard {
            Text(
                text = stringResource(R.string.parked_first_visit),
                style = AroTextStyles.bodyMedium,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 2,
            )
        }
    }
}

/** 그때 사진 자리. assets 의 photoAsset("assets/seed/...") 을 열어 표시하고, 실패 시 자리표시자 박스. */
@Composable
internal fun ThenPhoto(photoAsset: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photoAsset) {
        value = runCatching {
            context.assets.open(photoAsset.removePrefix("assets/")).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.aroColors.surfaceLow),
        contentAlignment = Alignment.Center,
    ) {
        val decoded = bitmap
        if (decoded != null) {
            Image(bitmap = decoded, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = stringResource(R.string.parked_photo_placeholder),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textQuaternary,
            )
        }
    }
}

/** 스탬프북 카드: 정의 → 순수 StampBook → 3칸·진행률·완성 배지. 스텝4와 완주 화면이 공용. */
@Composable
internal fun StampBookCard(def: StampBookDef?, stampedIds: Set<String>) {
    SectionCard {
        if (def == null) {
            Text(
                text = stringResource(R.string.parked_book_empty),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
            )
        } else {
            // StampBook 은 순수 도메인 상태. 찍힌 placeId 만 반영해 진행률·배지를 얻는다.
            val book = remember(def, stampedIds) {
                StampBook(def).also { b -> stampedIds.forEach { b.addStamp(it) } }
            }
            Text(def.title, style = AroTextStyles.titleSmall, color = MaterialTheme.aroColors.textPrimary, maxLines = 1)
            StampSlots(book.stampedPlaceIds.size, def.placeIds.size)
            Text(
                text = stringResource(R.string.parked_book_progress, book.progressPercent, book.stampedPlaceIds.size, def.placeIds.size),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textSecondary,
            )
            val badge = book.earnedBadge
            if (badge != null) {
                Text(
                    text = stringResource(R.string.parked_book_badge, badge),
                    style = AroTextStyles.bodyMedium,
                    color = MaterialTheme.aroColors.stampPoint,
                )
            }
        }
    }
}

/**
 * 스텝4: 스탬프북·제휴처(00문서 12절 T6 — 시안 08). 제휴처 카드를 맛집·가게·숙소 3종
 * 꼬리표로 나눠 최대 3장 보여 준다(04문서 카드 3장). 카드마다 '포인트 캐시백(예시)' 문구와
 * "현금이 아닌 포인트" 안내를 붙이고, 숙소 카드에는 체크인·체크아웃 영수증 안내 문구를 둔다.
 * "예시(시뮬레이션)" 꼬리표와 "영수증 인증은 스마트폰에서" 안내는 유지한다.
 * 입력란·결제 버튼은 절대 두지 않는다. 하단의 '오늘의 기록 보기' 버튼은 스텝5로 이동한다.
 *
 * @param onOpenRecord 스텝5(오늘의 기록)로 이동 — ParkedScreen 이 넘겨 준다.
 */
@Composable
internal fun BookStep(
    seed: SeedContent,
    place: Place?,
    stampedIds: Set<String>,
    isWide: Boolean,
    onOpenRecord: () -> Unit,
) {
    // lastArrival 이 속한 책, 없으면 첫 책.
    val bookDef = remember(place?.id) { pickBookDef(seed, place?.id) }
    // 근처 제휴처 최대 3장(04문서 시안 08: 맛집·가게·숙소 카드 3장).
    val partners = remember(place?.id) { seed.partnersDemo.filter { it.nearPlaceId == place?.id }.take(3) }
    val bookCard: @Composable () -> Unit = { StampBookCard(bookDef, stampedIds) }
    val partnerCard: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (partners.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.parked_partner_section),
                        style = AroTextStyles.titleSmall,
                        color = MaterialTheme.aroColors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    SmallTag(stringResource(R.string.parked_demo_simulation_tag))
                }
                partners.forEach { partner ->
                    SectionCard {
                        // 제휴처 꼬리표: 맛집 / 가게(shop·museum_shop) / 숙소(lodging) 3종(시안 08 카드 상단 태그).
                        SmallTag(partnerKindLabel(partner.kind))
                        Text(partner.name, style = AroTextStyles.bodyMedium, color = MaterialTheme.aroColors.textPrimary, maxLines = 2)
                        // 포인트 캐시백(예시) + "현금이 아닌 포인트" 안내(시안 08 하단 문구 — 포인트 문구 옆 배치).
                        Text(
                            text = stringResource(R.string.parked_partner_rate, ratePercentText(partner.pointRate)),
                            style = AroTextStyles.bodySmall,
                            color = MaterialTheme.aroColors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.parked_partner_point_notice),
                            style = AroTextStyles.label,
                            color = MaterialTheme.aroColors.textTertiary,
                            maxLines = 1,
                        )
                        // 숙소 카드 전용 안내(시안 08): 숙박 기간 안의 영수증도 인정한다는 문구.
                        if (partner.kind == "lodging") {
                            Text(
                                text = stringResource(R.string.parked_partner_lodging_notice),
                                style = AroTextStyles.label,
                                color = MaterialTheme.aroColors.textTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.parked_receipt_notice),
                        style = AroTextStyles.label,
                        color = MaterialTheme.aroColors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
                // '오늘의 기록 보기' 버튼 — 스텝5(오늘의 기록, 시안 09)로 이동한다.
                LargeSecondaryButton(text = stringResource(R.string.parked_partner_open_record)) {
                    onOpenRecord()
                }
            }
        }
    }
    if (isWide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { bookCard() }
            Box(Modifier.weight(1f)) { partnerCard() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            bookCard()
            partnerCard()
        }
    }
}

/** lastArrival 이 속한 스탬프북 정의, 없으면 첫 정의, 그마저 없으면 null. */
internal fun pickBookDef(seed: SeedContent, placeId: String?): StampBookDef? =
    seed.stampBooks.firstOrNull { placeId != null && placeId in it.placeIds } ?: seed.stampBooks.firstOrNull()

/** 제휴처 kind 값을 화면 라벨(3종 꼬리표)로 바꾼다: restaurant→맛집, shop·museum_shop→가게, lodging→숙소. */
@Composable
internal fun partnerKindLabel(kind: String): String = when (kind) {
    "restaurant" -> stringResource(R.string.parked_partner_kind_restaurant)
    "shop", "museum_shop" -> stringResource(R.string.parked_partner_kind_shop)
    "lodging" -> stringResource(R.string.parked_partner_kind_lodging)
    else -> stringResource(R.string.parked_partner_kind_default)
}

/** 적립률 표시(0.02 → "2%"). 소수 자릿수가 남으면 한 자리까지 표시한다(0.015 → "1.5%"). */
internal fun ratePercentText(rate: Double): String {
    val pct = rate * 100
    return if (pct == pct.toLong().toDouble()) {
        "${pct.toLong()}%"
    } else {
        String.format(Locale.US, "%.1f%%", pct)
    }
}

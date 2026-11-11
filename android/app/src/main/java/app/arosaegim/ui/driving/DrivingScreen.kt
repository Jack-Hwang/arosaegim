package app.arosaegim.ui.driving

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.domain.Haversine
import app.arosaegim.domain.Place
import app.arosaegim.pleos.LatestLocation
import app.arosaegim.ports.Narrator
import app.arosaegim.session.TripSession
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import androidx.compose.material3.MaterialTheme
import app.arosaegim.ui.common.aroColors
import app.arosaegim.ui.common.PageNavigationBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/*
 * 주행 화면(D) — T5.
 *
 * 안전 규칙(00문서 2-2 §6, 04문서 2절):
 *  - 퀴즈·입력·동영상·애니메이션 없음. 텍스트는 모두 maxLines 강제(3줄 초과 금지).
 *  - 화면 구성: 꼬리표 "주행 중" / 다음 장소명(큰 글씨, 한 줄) / 거리 한 줄(있으면) /
 *    스탬프 진행 한 줄 / 버튼 1개("이야기 다시 듣기") / 도착 시 하단 한 줄 알림 3초.
 *  - 도착 시 화면 전환·팝업 없음: TripSession.lastEventLine 한 줄만 바뀌고 음성이 낭독된다.
 *  - 본문은 24sp 이상(AroTextStyles.bodySmall 하한), 버튼은 76dp(LargePrimaryButton).
 *
 * 색은 MaterialTheme.aroColors 토큰만 쓴다(포인트 색 인주 하나 원칙).
 */

/** 거리 표시 갱신 주기 — 주행 중 시선 분산을 줄이기 위해 느리게(3초) 갱신한다. */
private const val DISTANCE_REFRESH_MS = 3_000L

/** 도착 한 줄 알림 노출 시간. 화면 전환 없이 잠깐 보이고 사라진다. */
private const val EVENT_LINE_VISIBLE_MS = 3_000L

/**
 * @param session        여행 세션(목표·진행·한 줄 알림 소스)
 * @param narrator       "이야기 다시 듣기" 재생용
 * @param latestLocation FusedArrivalDetector 의 최신 위치(거리 표시용, 저장 금지 값)
 */
@Composable
fun DrivingScreen(
    session: TripSession,
    narrator: Narrator,
    latestLocation: StateFlow<LatestLocation?>,
) {
    val target by session.currentTarget.collectAsState()
    val stampedCount by session.stampedCount.collectAsState()
    val eventLine by session.lastEventLine.collectAsState()
    val scope = rememberCoroutineScope()

    // 도착 한 줄 알림: lastEventLine 이 바뀌면 3초간 하단에 표시 후 사라진다.
    var visibleEventLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(eventLine) {
        if (eventLine != null) {
            visibleEventLine = eventLine
            delay(EVENT_LINE_VISIBLE_MS)
            visibleEventLine = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 윈도우 다크 배경 비침 방지(주간 테마 기본)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageNavigationBar(courseName = session.course.title)

        Row(verticalAlignment = Alignment.CenterVertically) {
            DrivingTag() // "주행 중" 꼬리표
        }

        Spacer(Modifier.height(8.dp))
        NextPlaceSection(target = target, latestLocation = latestLocation)
        Spacer(Modifier.weight(1f)) // 버튼·알림을 화면 하단에 붙인다(주행 중 손 위치 고려).

        // 스탬프 진행 표시("스탬프 1/3" 수준의 짧은 텍스트).
        Text(
            text = stringResource(R.string.driving_stamp_progress, stampedCount, session.course.places.size),
            maxLines = 1,
            style = AroTextStyles.bodySmall.copy(color = MaterialTheme.aroColors.textSecondary),
        )

        // 주행 중 유일한 버튼. LargePrimaryButton 이 76dp 높이를 보장한다.
        LargePrimaryButton(
            text = stringResource(R.string.driving_button_replay),
            enabled = target != null,
            onClick = {
                val place = target ?: return@LargePrimaryButton
                scope.launch {
                    // 재생 실패는 조용히 흡수한다(음성 실패가 주행 흐름을 막지 않는다).
                    narrator.speak(place.narration)
                }
            },
        )

        Spacer(Modifier.height(8.dp))
        EventLineNotice(visibleEventLine)
    }
}

/** 다음 목적지 영역: 라벨 + 장소명(큰 글씨, 한 줄) + 거리 한 줄(위치가 있을 때만). */
@Composable
private fun NextPlaceSection(
    target: Place?,
    latestLocation: StateFlow<LatestLocation?>,
) {
    Text(
        text = stringResource(R.string.driving_label_next),
        maxLines = 1,
        style = AroTextStyles.label.copy(color = MaterialTheme.aroColors.textTertiary),
    )
    // 완주면 장소명 대신 완주 문구를 큰 글씨로 보여 준다(추가 화면 전환 없음).
    Text(
        text = target?.name ?: stringResource(R.string.driving_completed_title),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = AroTextStyles.headline.copy(color = MaterialTheme.aroColors.textPrimary),
    )
    if (target != null) {
        DistanceLine(target = target, latestLocation = latestLocation)
    }
}

/**
 * 거리 한 줄("약 1.2km"). 최신 위치가 없으면 표시를 생략한다(안전하게 비워 둠).
 * 갱신은 3초에 한 번 — 위치 콜백 빈도에 화면이 좌우되지 않게 느리게 계산한다.
 */
@Composable
private fun DistanceLine(
    target: Place,
    latestLocation: StateFlow<LatestLocation?>,
) {
    // 포맷 문자열은 컴포저블 단계에서 읽어 코루틴 안에서는 값으로만 쓴다.
    val mFormat = stringResource(R.string.driving_distance_m)
    val kmFormat = stringResource(R.string.driving_distance_km)
    var distanceText by remember(target.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(target.id, mFormat, kmFormat) {
        while (true) {
            val loc = latestLocation.value
            distanceText = loc?.let {
                formatDistance(
                    mFormat,
                    kmFormat,
                    Haversine.distanceM(it.lat, it.lon, target.lat, target.lon),
                )
            }
            delay(DISTANCE_REFRESH_MS)
        }
    }
    distanceText?.let { text ->
        Text(
            text = text,
            maxLines = 1,
            style = AroTextStyles.bodySmall.copy(color = MaterialTheme.aroColors.textSecondary),
        )
    }
}

/** 거리 값 → 표시 문구. 1km 미만은 m, 이상은 소수 한 자리 km. */
private fun formatDistance(mFormat: String, kmFormat: String, meters: Double): String =
    if (meters < 1_000.0) {
        mFormat.format(meters.toInt())
    } else {
        kmFormat.format(meters / 1_000.0)
    }

/** 도착 한 줄 알림(하단). 화면 전환·팝업 없이 문장 한 줄만 표시한다. */
@Composable
private fun EventLineNotice(line: String?) {
    if (line == null) {
        Spacer(Modifier.height(28.dp)) // 알림이 없어도 버튼 아래 여백 높이를 유지한다(튐 방지).
        return
    }
    Text(
        text = line,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = AroTextStyles.bodySmall.copy(color = MaterialTheme.aroColors.stampPoint),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    )
}

/**
 * "주행 중" 꼬리표. ui/common 의 DemoModeTag 와 같은 스타일(테두리 박스·label 크기)이며
 * 문구만 다르다 — ui/common 이 텍스트 지정형으로 통합되면 이 컴포저블은 그쪽으로 교체한다.
 */
@Composable
private fun DrivingTag() {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.aroColors.textTertiary,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.driving_tag),
            style = AroTextStyles.label.copy(color = MaterialTheme.aroColors.textSecondary),
        )
    }
}

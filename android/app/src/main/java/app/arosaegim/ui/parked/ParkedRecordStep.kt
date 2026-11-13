package app.arosaegim.ui.parked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.data.SeedContent
import app.arosaegim.domain.Course
import app.arosaegim.domain.HeirCandidate
import app.arosaegim.domain.StampBookDef
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.aroColors

/*
 * 스텝5(오늘의 기록, 시안 09) 컴포저블 모음 — ParkedScreen.kt 에서 분리했다(Q20 순수 이동).
 * 서버 조회·정책 판정은 ParkedScreen 이 담당하고, 이 파일은 넘겨 받은 값을 그리는 일만 한다.
 */

/** 요약 타일 한 칸의 값·라벨(시안 09의 "4명 / 함께 온 인원" 구성). */
private data class RecordTile(val value: String, val label: String)

/**
 * 스텝5: 오늘의 기록(00문서 12절 T6 — 시안 09). 요약 타일 4개(스탬프 N/N · 함께 온 인원 ·
 * 함께 달린 거리 · 방문 장소 수) + "오늘의 기록이 저장됐어요." + [처음으로] 버튼.
 * 기존 완주 화면의 요소도 이 화면으로 이동 정리했다: 코스 완주 배지(StampBookCard)·
 * 거리(타일로)·혜택 미리보기(offers_demo). 타일은 "안 읽히면 숨김" 원칙 — partySize·tripKm
 * 신호가 없으면 해당 타일 자체를 그리지 않는다(04문서 화면 4).
 *
 * B1(2026-11-11): 완주 시 가족 배지 다음에 이어받을 사람 질문 카드가 1회 나타난다.
 * 질문 여부는 HeirQuestionPolicy 판정(ParkedScreen) — 정하지 않아도 배지·완주 기록은 유지된다.
 * B3(2026-11-11): metrics.family_total_trip_km 를 "우리 가족이 함께 달린 거리 N km"로 표시.
 * 누적값 없음·0 이면 줄을 숨긴다(FamilyTotalKmLine).
 *
 * @param isCompleted 완주 여부. true 면 '코스 완주!' 제목과 배지(스탬프북 카드)를 함께 보인다.
 * @param familyTotalKmLabel 가족 누적 거리 표시 문자열("1,240"). null = 없음/0 → 줄 숨김.
 * @param heirCard*  이어받을 사람 질문 카드 값(ParkedScreen 이 정책 판정·서버 호출을 담당).
 * @param onBackHome  [처음으로] 버튼 — 세션 resetCourse 후 스텝0(코스 선택)으로 돌아간다.
 */
@Composable
internal fun RecordContent(
    course: Course,
    seed: SeedContent,
    bookDef: StampBookDef?,
    stampedIds: Set<String>,
    stampedCount: Int,
    stampTotal: Int,
    partySize: Int?,
    tripKm: Double?,
    isCompleted: Boolean,
    familyTotalKmLabel: String?,
    heirCardVisible: Boolean,
    heirCardCandidates: List<HeirCandidate>,
    heirCardBusy: Boolean,
    heirCardDesignatedName: String?,
    heirCardOwnerGuide: Boolean,
    heirCardFailed: Boolean,
    heirCardOnPick: (HeirCandidate) -> Unit,
    heirCardOnDefer: () -> Unit,
    modifier: Modifier = Modifier,
    onBackHome: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 코스 제목 + 방문 순서 한 줄(시안 09: 제목 아래 "박물관 → 대릉원 → 첨성대" 구성).
        Text(
            text = course.title,
            style = AroTextStyles.titleSmall,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = course.places.joinToString(" → ") { it.name },
            style = AroTextStyles.bodySmall,
            color = MaterialTheme.aroColors.textSecondary,
            maxLines = 2,
        )
        if (isCompleted) {
            // 코스 완주(기존 완주 화면 요소 이동) — 배지는 아래 스탬프북 카드가 함께 보여 준다.
            Text(
                text = stringResource(R.string.parked_course_complete),
                style = AroTextStyles.headline,
                color = MaterialTheme.aroColors.stampPoint,
            )
            StampBookCard(bookDef, stampedIds)
            // B1: 가족 배지 다음 — 이어받을 사람 질문 카드(1회). 거절해도 배지는 그대로다.
            HeirQuestionCard(
                visible = heirCardVisible,
                candidates = heirCardCandidates,
                busy = heirCardBusy,
                designatedName = heirCardDesignatedName,
                ownerGuide = heirCardOwnerGuide,
                failed = heirCardFailed,
                onPick = heirCardOnPick,
                onDefer = heirCardOnDefer,
            )
        }
        Text(
            text = stringResource(R.string.parked_record_saved),
            style = AroTextStyles.bodyMedium,
            color = MaterialTheme.aroColors.textPrimary,
        )
        // B3: 우리 가족이 함께 달린 거리 누적(0·없음이면 줄 숨김).
        FamilyTotalKmLine(totalKmLabel = familyTotalKmLabel)

        // 요약 타일 4개(시안 09): 값이 없는 타일(인원·거리)은 통째로 숨긴다.
        val tiles = buildList {
            add(
                RecordTile(
                    value = stringResource(R.string.parked_record_stamps_value, stampedCount, stampTotal),
                    label = stringResource(R.string.parked_record_tile_stamps),
                ),
            )
            if (partySize != null) {
                add(
                    RecordTile(
                        value = stringResource(R.string.parked_record_party_value, partySize),
                        label = stringResource(R.string.parked_record_tile_party),
                    ),
                )
            }
            if (tripKm != null) {
                add(
                    RecordTile(
                        value = stringResource(R.string.parked_record_distance_value, tripKm),
                        label = stringResource(R.string.parked_record_tile_distance),
                    ),
                )
            }
            add(
                RecordTile(
                    value = stringResource(R.string.parked_record_places_value, stampedCount),
                    label = stringResource(R.string.parked_record_tile_places),
                ),
            )
        }
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTiles.forEach { tile ->
                    RecordTileBox(tile, Modifier.weight(1f))
                }
                // 홀수 개일 때 빈 칸을 채워 타일 폭이 흐트러지지 않게 한다.
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // 혜택 미리보기(기존 완주 화면 요소 이동): 시드 offers_demo 전부 simulated=true 이므로
        // '예시' 표기와 함께만 나열한다(00문서 ParkedActivity 완주 절).
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.parked_offers_section),
                    style = AroTextStyles.titleSmall,
                    color = MaterialTheme.aroColors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SmallTag(stringResource(R.string.parked_demo_simulation_tag))
            }
            seed.offersDemo.take(2).forEach { offer ->
                SectionCard {
                    Text(offer.title, style = AroTextStyles.bodyMedium, color = MaterialTheme.aroColors.textPrimary, maxLines = 2)
                    Text(
                        text = offer.condition,
                        style = AroTextStyles.label,
                        color = MaterialTheme.aroColors.textTertiary,
                    )
                }
            }
        }

        // [처음으로] — 코스 세션을 초기화하고 스텝0(코스 선택)으로 돌아간다.
        LargePrimaryButton(text = stringResource(R.string.parked_record_back_home)) {
            onBackHome()
        }
    }
}

/** 스텝5 요약 타일 한 칸(시안 09의 값 크게·라벨 아래 구성). 배경은 보조 배경 토큰. */
@Composable
private fun RecordTileBox(tile: RecordTile, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.aroColors.backgroundSecondary,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = tile.value,
                style = AroTextStyles.titleMedium,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = tile.label,
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

package app.arosaegim.ui.parked

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.arosaegim.R
import app.arosaegim.domain.Place
import app.arosaegim.domain.QuizItem
import app.arosaegim.ui.common.AroTextStyles
import app.arosaegim.ui.common.LargePrimaryButton
import app.arosaegim.ui.common.aroColors

/*
 * 정차 화면 세로 스텝 중 스텝1(스탬프 카드)·스텝2(퀴즈) 컴포저블 모음.
 * ParkedScreen.kt 가 300줄 규칙을 넘겨 분리했다(Q20 — 본문 그대로의 순수 이동).
 * 스텝3(그때와 지금)·스텝4(스탬프북·제휴처)는 ParkedComponents.kt, 스텝5(오늘의 기록)는
 * ParkedRecordStep.kt 에 있다. 화면 껍데기(스텝 선택·본문 분기)는 ParkedScreen.kt 다.
 */

/** 스텝1: 스탬프 획득 카드. 도착 장소·스탬프 아이콘 자리·진행(스탬프 N/3). 도착 전엔 시작 안내. */
@Composable
internal fun StampStep(lastArrival: Place?, currentTarget: Place?, stampedCount: Int, stampTotal: Int) {
    SectionCard {
        if (lastArrival == null) {
            // 빈 상태: 아직 도착 기록이 없다 — 코스 시작 안내 + 첫 목표.
            Text(
                text = stringResource(R.string.parked_course_start),
                style = AroTextStyles.titleMedium,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 1,
            )
            val target = currentTarget
            if (target != null) {
                Text(
                    text = stringResource(R.string.parked_first_target, target.name),
                    style = AroTextStyles.bodySmall,
                    color = MaterialTheme.aroColors.textSecondary,
                    maxLines = 3,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.parked_stamp_done, lastArrival.name),
                style = AroTextStyles.titleMedium,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 2,
            )
            StampSlots(stampedCount, stampTotal)
            Text(
                text = stringResource(R.string.parked_stamp_progress, stampedCount, stampTotal),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textSecondary,
            )
        }
    }
}

/**
 * 스텝2: 퀴즈. 문제 1개씩, 보기 choices 큰 버튼, 채점 후 해설 1~2줄 + "다음".
 * 진행(1/2) 표시. reviewed=false 면 '초안' 꼬리표. 점수·타이머 없음(04문서 화면 3).
 * 진행 상태는 QuizStateHolder(싱글톤)에 보존되어 주행 복귀 시 이어서 보인다.
 */
@Composable
internal fun QuizStep(place: Place?, quizzes: List<QuizItem>) {
    if (place == null || quizzes.isEmpty()) {
        SectionCard {
            Text(
                text = stringResource(R.string.parked_quiz_empty),
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
            )
        }
        return
    }
    val index = QuizStateHolder.quizIndex.coerceIn(0, quizzes.size - 1)
    val item = quizzes[index]
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.parked_quiz_progress, index + 1, quizzes.size),
                style = AroTextStyles.label,
                color = MaterialTheme.aroColors.textSecondary,
            )
            Spacer(Modifier.width(8.dp))
            if (!item.reviewed) SmallTag(stringResource(R.string.parked_quiz_draft_tag))
        }
        // 질문(Title)
        Text(
            text = item.q,
            style = AroTextStyles.titleMedium,
            color = MaterialTheme.aroColors.textPrimary,
            maxLines = 3,
        )
        // 보기 큰 버튼(높이 76dp 이상). 미응답일 때만 누를 수 있고, 누르면 즉시 채점한다.
        item.choices.forEachIndexed { i, choice ->
            QuizChoiceButton(
                text = choice,
                enabled = QuizStateHolder.answered == null,
                correct = QuizStateHolder.answered != null && i == item.answer,
                wrong = QuizStateHolder.answered == false && QuizStateHolder.chosen == i,
                onClick = {
                    QuizStateHolder.chosen = i
                    QuizStateHolder.answered = (i == item.answer) // 채점만. 점수·타이머 없음.
                },
            )
        }
        if (QuizStateHolder.answered != null) {
            val correct = QuizStateHolder.answered == true
            Text(
                text = if (correct) {
                    stringResource(R.string.parked_quiz_correct)
                } else {
                    stringResource(R.string.parked_quiz_wrong, item.choices[item.answer.coerceIn(item.choices.indices)])
                },
                style = AroTextStyles.bodySmall,
                color = if (correct) MaterialTheme.aroColors.informativeActive else MaterialTheme.aroColors.textSecondary,
            )
            // 해설 1~2줄(04문서 화면 3 — maxLines=2 로 강제).
            Text(
                text = item.explain,
                style = AroTextStyles.bodySmall,
                color = MaterialTheme.aroColors.textPrimary,
                maxLines = 2,
            )
            LargePrimaryButton(text = stringResource(R.string.parked_quiz_next)) {
                if (QuizStateHolder.quizIndex + 1 < quizzes.size) {
                    QuizStateHolder.quizIndex += 1 // 다음 문제
                    QuizStateHolder.answered = null
                    QuizStateHolder.chosen = null
                } else {
                    QuizStateHolder.stepIndex = 2 // 마지막 문항 후: 그때와 지금 스텝으로
                }
            }
        }
    }
}

package app.arosaegim.ui.parked

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * 퀴즈 진행 상태 보존 홀더 (00문서 5절: "주행 시작 시 2~5번 화면은 즉시 숨기고
 * 퀴즈 진행 상태는 보존").
 *
 * 주행(DRIVING) 중에는 ParkedActivity 가 distractionOptimized 를 선언하지 않았기 때문에
 * 시스템이 이 화면을 자동으로 가린다. 이때 시스템은 Activity 인스턴스를 파괴할 수 있으므로
 * Compose remember(= Activity 수명)에 퀴즈 진행을 두면 정차 복귀 시 처음부터 다시 풀게 된다.
 * 그래서 진행 상태를 프로세스 수명 동안 살아 있는 싱글톤 객체에 둔다. 정차(PARKED) 복귀 시
 * ModeRouter 가 이 Activity 를 REORDER_TO_FRONT 로 앞으로 가져오면, Activity 가 새로
 * 생성되어도 이 값을 읽어 이어서 보여 준다.
 *
 * 구현 노트: "일반 var + 알림용 mutableStateOf" 설계. 값 자체는 단순 상태이며,
 * mutableStateOf 위임은 화면이 이 값 변화를 감지해 다시 그리도록 하는 알림 장치일 뿐이다.
 * 점수·타이머·효과음은 없다(04문서 화면 3 금지 항목) — 채점(정답/오답)만 한다.
 */
object QuizStateHolder {

    /** 정차 화면에서 보고 있는 스텝(0=스탬프, 1=퀴즈, 2=그때와 지금, 3=스탬프북·맛집). */
    var stepIndex by mutableStateOf(0)

    /** 퀴즈를 진행 중인 장소 id. null = 아직 시작 전. */
    var quizPlaceId: String? by mutableStateOf<String?>(null)

    /** 현재 문항 인덱스(0부터). 문제는 한 번에 하나씩만 보여 준다. */
    var quizIndex by mutableStateOf(0)

    /** 마지막 문항 채점 결과(null=아직 답하지 않음, true=정답, false=오답). */
    var answered: Boolean? by mutableStateOf<Boolean?>(null)

    /** 마지막 문항에서 고른 보기 인덱스(채점 후 올바른 보기 강조에 사용). */
    var chosen: Int? by mutableStateOf<Int?>(null)

    /**
     * 장소의 퀴즈를 시작한다. 같은 장소면 진행을 그대로 보존한다(주행 복귀 이어풀기).
     * 장소가 바뀌었을 때만 문항 인덱스와 답안을 초기화한다.
     */
    fun beginQuiz(placeId: String) {
        if (quizPlaceId != placeId) {
            quizPlaceId = placeId
            quizIndex = 0
            answered = null
            chosen = null
        }
    }

    /** 보고 있던 문항이 존재하지 않게 됐을 때(씨드 교체 등) 인덱스를 목록 안으로 보정한다. */
    fun clamp(questionCount: Int) {
        if (questionCount <= 0) {
            quizIndex = 0
        } else if (quizIndex >= questionCount) {
            quizIndex = questionCount - 1
        }
    }

    /** 코스를 새로 시작할 때 전체 초기화. resetCourse 후 호출하는 용도다. */
    fun reset() {
        stepIndex = 0
        quizPlaceId = null
        quizIndex = 0
        answered = null
        chosen = null
    }
}

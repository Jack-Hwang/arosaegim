package app.arosaegim.ui.parked

/*
 * 홈 내용 판정(F10 T3, 2026-11-14) — 정차 화면의 홈이 무엇을 보여 줄지를 정하는 순수 함수.
 *
 * 계약(요구 문서 F10 — 시험은 HomeContentTest):
 *  ① 이어서 하기 카드 — 진행 중 코스가 있을 때만(스탬프 수·다음 목표로 판정).
 *  ② 지난 방문 카드 — 마지막 방문 기록이 있을 때만.
 *  ③ 첫 기록 모드 — 이어서 하기 카드도 지난 방문 카드도 없을 때.
 *
 * 세션 여부도 입력이다 — 세션이 없으면 홈 자체를 보여 주지 않으므로 모든 카드를 만들지 않는다.
 * 값은 기기에 있는 것만 읽는다: 거리 한 줄 등 서버·센서 값을 못 읽으면 해당 줄만 null 로
 * 숨기고 오류 문구는 만들지 않는다(AC23 — 서버 응답 없음도 카드는 그대로다).
 * 이 파일은 Compose·Android 를 쓰지 않는다 — JVM 단위 시험 대상.
 */

/** 홈 판정 입력 — TripSession·VisitStore 에서 읽은 값만 전달한다. */
data class HomeInputs(
    /** 가족 세션(코드 접속 또는 둘러보기)이 있는지 — 없으면 홈을 보여 주지 않는다. */
    val hasSession: Boolean,
    /** 진행 중(또는 마지막) 코스 이름. */
    val courseName: String,
    /** 찍힌 스탬프 수(TripSession.stampedCount). */
    val stampedCount: Int,
    /** 코스 전체 스탬프 수(코스 장소 수). */
    val stampTotal: Int,
    /** 다음 목표 장소 이름(TripSession.currentTarget — 없으면 null, 완주·빈 코스). */
    val currentTargetName: String?,
    /** 코스 완주 여부(TripSession.isCompleted). */
    val completed: Boolean,
    /** 마지막 방문 기록의 장소 이름(있을 때만 — 기록이 없으면 null). */
    val lastVisitPlaceName: String?,
    /** 거리 한 줄(TripSession.tripKm 을 호출부가 포맷한 값 — 없으면 null, 줄 숨김). */
    val distanceText: String?,
)

/** ① 이어서 하기 카드 — 진행 중 코스가 있을 때만 만든다. */
data class HomeResume(
    val courseName: String,
    val stampCount: Int,
    val stampTotal: Int,
    /** 다음 목표가 없으면(이상 상태·빈 코스) 이 줄만 숨긴다. */
    val nextPlaceName: String?,
    /** 거리 값을 못 읽으면 이 줄만 숨긴다(오류 문구 금지 — AC23). */
    val distanceText: String?,
)

/** ② 지난 방문 카드 — 마지막 방문 기록이 있을 때만 만든다. */
data class HomePastVisit(
    val placeName: String,
)

/** 홈 화면 모델 — 카드는 null(숨김)과 존재로만 구별한다. */
data class HomeModel(
    val resume: HomeResume?,
    val pastVisit: HomePastVisit?,
    /** ③ 첫 기록 모드 — 보여 줄 카드가 하나도 없을 때(true 면 첫 기록 안내를 그린다). */
    val firstRecord: Boolean,
)

object HomeContent {

    /**
     * 홈에 보여 줄 내용을 정한다(순수 함수 — Compose 상태·Android 의존 없음).
     *
     * 진행 중 판정: 완주 전이면서 스탬프가 하나라도 찍혔거나 다음 목표가 있을 때.
     * 완주 직후에는 이어서 하기 카드를 만들지 않는다(이어 갈 목표가 없다) — 기록이 있으면
     * 지난 방문 카드가 남는다. 둘 다 없으면 첫 기록 모드다.
     */
    fun resolve(inputs: HomeInputs): HomeModel {
        if (!inputs.hasSession) {
            // 세션 없음 — 시작 화면 규칙(가족 코드·PIN)과 같은 방향이다. 카드를 만들지 않는다.
            return HomeModel(resume = null, pastVisit = null, firstRecord = false)
        }
        val courseInProgress =
            !inputs.completed && (inputs.stampedCount > 0 || inputs.currentTargetName != null)
        val resume = if (courseInProgress) {
            HomeResume(
                courseName = inputs.courseName,
                stampCount = inputs.stampedCount,
                stampTotal = inputs.stampTotal,
                nextPlaceName = inputs.currentTargetName,
                distanceText = inputs.distanceText,
            )
        } else {
            null
        }
        val pastVisit = inputs.lastVisitPlaceName?.let { HomePastVisit(placeName = it) }
        return HomeModel(
            resume = resume,
            pastVisit = pastVisit,
            firstRecord = resume == null && pastVisit == null,
        )
    }
}

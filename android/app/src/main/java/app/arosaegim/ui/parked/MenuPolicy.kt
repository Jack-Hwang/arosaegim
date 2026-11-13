package app.arosaegim.ui.parked

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * 메뉴 판정(F10 T2, 2026-11-14) — [☰ 메뉴] 버튼·패널의 규칙을 순수 Kotlin 으로 모은다.
 *
 * 규칙(F10 수용 기준 — 시험은 MenuPolicyTest):
 *  ⒜ 주행(D)이면 버튼을 숨기고 패널을 닫는다(AC07 — 주행 최소 정보 원칙).
 *  ⒝ 정차 + 가로 창이면 패널을 열 때 본문 폭이 패널 폭만큼 줄어든다(오른쪽에 붙임).
 *  ⒞ 세로(1:1) 창이면 패널이 본문 위에 겹치고 왼쪽이 어둡다(AC17 — 본문 폭 그대로).
 *  ⒟ 세션 없음·시작 화면이면 버튼이 없다(AC05 — 시작 화면은 패널 대신 가족 코드·PIN 안내).
 *
 * 패널 폭은 앱 창 폭의 약 27%(시안 276/1006)이고 최소 240dp 다.
 * 가족 코드 화면 가림(AC18)도 메뉴 표시 규칙이라 여기에 둔다 — 앞 2자리·뒤 2자리만 남긴다.
 *
 * 이 파일의 판정 함수는 전부 순수하다(Compose 상태 읽기 없음 — JVM 단위 시험 대상).
 * 열림 상태 홀더([MenuPanelState])만 mutableStateOf 로 화면 갱신을 알린다(QuizStateHolder 와
 * 같은 방식의 프로세스 수명 object — 화면 이동·창 크기 변경에서 열림 여부가 복원된다).
 */

/** 메뉴 패널이 놓이는 방식. */
enum class MenuPanelMode {
    /** 닫힘 — 패널을 그리지 않는다. */
    CLOSED,

    /** 가로 창(⒝) — 본문 폭이 패널 폭만큼 줄어들고 패널이 오른쪽에 붙는다. */
    BODY_SHRINK,

    /** 세로(1:1) 창(⒞) — 패널이 본문 위에 겹치고 왼쪽이 어둡다. */
    OVERLAY,
}

/** 패널 항목 4개(AC06·AC08 — 홈·코스 선택·그때와 지금·설정, 모두 한글로 통일). */
enum class MenuPanelItem { HOME, COURSE, THENNOW, SETTINGS }

/** 메뉴 '코스 선택' 항목이 향하는 곳. */
enum class CourseSelectMode {
    /** 진행 중 코스가 없다 — 기존 스텝0(코스 선택) 판정에 맡긴다. */
    STEP0_FRESH,

    /** 진행 중 코스가 있다 — 진행·퀴즈를 지우지 않고 코스 목록(스텝0 화면)만 연다. */
    COURSE_LIST_ONLY,
}

/** 메뉴 판정 입력 — 창 크기는 dp 정수(시험에서 방향별 값을 넣는다). */
data class MenuPolicyInputs(
    val driving: Boolean,
    val hasSession: Boolean,
    val onStartScreen: Boolean,
    val windowWidthDp: Int,
    val windowHeightDp: Int,
    val panelRequestedOpen: Boolean,
)

/** 메뉴 판정 출력 — 버튼 표시·패널 모드·패널 폭. */
data class MenuPolicyOutputs(
    val showMenuButton: Boolean,
    val panelMode: MenuPanelMode,
    val panelWidthDp: Int,
)

object MenuPolicy {

    /** 패널 최소 폭(시안 하한). */
    const val MIN_PANEL_WIDTH_DP = 240

    /** 패널 폭 비율 — 시안 276/1006 ≈ 27%. */
    private const val PANEL_WIDTH_PERCENT = 27

    /**
     * 메뉴 버튼·패널 판정(순수 함수). 규칙 ⒜~⒟ 은 모두 이 한 곳에서 적용된다 —
     * 버튼이 숨는 조건에서는 열림 요청도 함께 무시해 패널이 강제로 닫힌다.
     */
    fun resolve(inputs: MenuPolicyInputs): MenuPolicyOutputs {
        // ⒟ 시작 화면·세션 없음, ⒜ 주행 — 어느 쪽이든 버튼도 패널도 없다.
        val usable = !inputs.driving && inputs.hasSession && !inputs.onStartScreen
        val open = usable && inputs.panelRequestedOpen
        // ⒝ 가로 창(너비가 높이보다 크다)만 본문 축소다. 세로·1:1 은 겹침(⒞)이다.
        val wideWindow = inputs.windowWidthDp > inputs.windowHeightDp
        val mode = when {
            !open -> MenuPanelMode.CLOSED
            wideWindow -> MenuPanelMode.BODY_SHRINK
            else -> MenuPanelMode.OVERLAY
        }
        return MenuPolicyOutputs(
            showMenuButton = usable,
            panelMode = mode,
            panelWidthDp = panelWidthDp(inputs.windowWidthDp),
        )
    }

    /** 패널 폭 = 창 폭의 27%, 최소 240dp. 닫혀 있어도 계산값은 같다(호출부가 무시하면 그만이다). */
    fun panelWidthDp(windowWidthDp: Int): Int =
        (windowWidthDp * PANEL_WIDTH_PERCENT / 100).coerceAtLeast(MIN_PANEL_WIDTH_DP)

    /**
     * 메뉴 '코스 선택' 행동 판정(순수 함수). 진행 중 코스(스탬프·다음 목표가 하나라도 있음)가
     * 있으면 진행을 지우지 않는다 — 코스 목록(스텝0 화면)만 연다.
     */
    fun courseSelectMode(courseListNeeded: Boolean): CourseSelectMode =
        if (courseListNeeded) CourseSelectMode.COURSE_LIST_ONLY else CourseSelectMode.STEP0_FRESH

    /**
     * 가족 코드 화면 가림(AC18, 순수 함수) — 앞 2자리·뒤 2자리만 남기고 가운데를 가린다.
     * 예: "A37K9Q2M"(8자리) → "A3••••2M". 코드가 없으면 null(호출부는 그 줄을 숨긴다).
     * 4자리 이하(규격 밖 값)는 전부 가려 값이 드러나지 않게 한다.
     */
    fun maskFamilyCode(code: String?): String? {
        val trimmed = code?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.length <= 4) return "•".repeat(trimmed.length)
        val hidden = trimmed.length - 4
        return trimmed.take(2) + "•".repeat(hidden) + trimmed.takeLast(2)
    }
}

/**
 * 메뉴 패널 열림 상태 홀더(F10 T2) — QuizStateHolder 와 같은 방식의 프로세스 수명 object.
 *
 * 주행 전환 때 ParkedActivity 가 시스템에 의해 가려지고 재생성될 수 있어도 열림 여부가
 * 이 홀더에 남아 화면 이동·창 크기 변경에서 복원된다. 주행으로 바뀌면 ParkedScreen 이
 * close() 로 닫는다(⒜ — 열어 둔 채 주행 전환 시 패널·버튼이 함께 감춰진다, AC07).
 */
object MenuPanelState {

    /** 패널이 열려 있는지. 닫힌 채로 시작한다(기본값 false). */
    var isOpen by mutableStateOf(false)
        private set

    /** 패널을 연다 — 메뉴 버튼 처리용. */
    fun open() {
        isOpen = true
    }

    /** 패널을 닫는다 — 닫기 버튼·항목 선택·주행 전환 처리용. */
    fun close() {
        isOpen = false
    }
}

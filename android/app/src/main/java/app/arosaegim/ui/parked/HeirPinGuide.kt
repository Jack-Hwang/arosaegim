package app.arosaegim.ui.parked

/**
 * B1: PIN 이 없을 때의 '이어받을 사람' 안내 전환 판정(시안 19 — 2026-11-12).
 *
 * 차량은 PIN 을 저장하지 않는다(00문서 10.6 — 메모리 전용). 프로세스가 끝나면 PIN 은 사라지고,
 * 그때는 POST /v1/families/me/heir 를 부를 수 없다. 계약(glm 07문서 2절 B1)에 따라 이때
 * 구성원 버튼을 통째로 숨기지 않고 '스마트폰의 가족 관리에서 정해 주세요' 안내 카드로
 * 전환한다(00문서 T9-3 방식 — 조회를 생략하되 안내 한 줄은 남긴다).
 *
 * 알려진 한계(주석으로 남긴다): PIN 이 없으면 GET heir 로 '이미 정해짐'을 확인할 수 없으므로,
 * 이미 지정된 가족에서도 이 안내가 한 번 보일 수 있다. 안내 자체는 웹 가족 관리로 안내하는
 * 한 줄이라 해가 없다 — 추측으로 질문 버튼을 만들지는 않는다(구성원 목록도 못 읽는다).
 *
 * 순수 Kotlin — JVM 단위 테스트 대상(HeirPinGuideTest). 질문 카드 본 판정은
 * app.arosaegim.domain.HeirQuestionPolicy(PIN 이 있을 때)가 담당한다.
 */
fun shouldShowHeirPinGuide(
    familyConnected: Boolean,
    hasPin: Boolean,
    courseCompleted: Boolean,
    driving: Boolean,
    deferredThisCourse: Boolean,
): Boolean {
    if (!familyConnected) return false // 가족 연결 자체가 없으면 배지도 없다 — 숨김(없으면 숨김).
    if (hasPin) return false // PIN 이 있으면 정상 경로(HeirQuestionPolicy) — 안내가 아니라 질문.
    if (!courseCompleted) return false // 코스 3/3 완주 직후에만.
    if (driving) return false // 주행 중에는 아무것도 띄우지 않는다(공통 규칙).
    if (deferredThisCourse) return false // 이 코스에서 '나중에 정하기'를 눌렀으면 다시 보지 않는다.
    return true
}

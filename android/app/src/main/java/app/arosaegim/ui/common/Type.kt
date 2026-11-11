package app.arosaegim.ui.common

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * 아로새김 글자 위계 (04_UI_GUIDE.md 3절).
 *
 * 규칙:
 *  - 위계는 Headline / Title / Body / Label 네 단계다.
 *    [설계] 장소명 = Headline, 질문·카드 제목 = Title, 설명 = Body, 꼬리표·보조 = Label.
 *  - 서체는 시스템 기본(Pleos 차량용은 Asta Sans). 장식이 강한 서체는 쓰지 않는다.
 *  - 본문은 Body Small(24sp)보다 작게 쓰지 않는다. 최소 폰트는 12sp이지만
 *    본문 용도에서는 24sp 가 하한이다.
 *
 * ⚠ 임시값 경고: 아래 크기·줄 높이는 가이드 [그림]에서 읽은 대략값
 *   (Title Large·Medium·Small ≈ 40·36·32 / Body Large·Medium·Small ≈ 30·28·24,
 *   줄 높이 38·36·34)에 기반한 임시값이다. D2 확인 과제(가이드 Typography 이미지
 *   또는 Figma 실측)로 재확인 후 수정한다.
 */

/** 아로새김 텍스트 스타일 묶음. 색은 포함하지 않는다 — 호출부가 토큰 색과 조합해 쓴다. */
object AroTextStyles {
    /** 장소명 등 화면의 가장 큰 제목. */
    val headline = TextStyle(
        fontSize = 44.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 질문·카드 제목. */
    val titleLarge = TextStyle(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val titleMedium = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val titleSmall = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 본문 설명. Body Small(24sp)이 본문 하한. */
    val bodyLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Normal,
    )
    val bodyMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
    )
    val bodySmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 꼬리표·보조 안내("데모 모드", "예시(시뮬레이션)" 등). */
    val label = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
    )
}

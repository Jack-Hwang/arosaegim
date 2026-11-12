package app.arosaegim.ui.common

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * 아로새김 색 토큰 (04_UI_GUIDE.md 4절).
 *
 * 규칙:
 *  - Pleos Connect System Color 토큰 이름(Background/Surface/Button/Text/Informative)을 따른다.
 *    16진수 색은 이 파일에만 존재하며 화면 코드에서 직접 쓰지 않는다.
 *  - 주간(Light)/야간(Dark) 두 벌을 모두 제공한다(00_BRIEF 5절 "다크 고대비 고정"은 폐기).
 *  - 포인트 색은 스탬프 인주색(stampPoint) 하나뿐이다. 과한 색 사용 금지.
 *  - regulation 색은 주행 규제·경고 전용이다. 장식(꾸밈)에는 절대 쓰지 않는다.
 *  - 본문·아이콘 대비는 4.5:1 이상(04문서 2절). 아래 주석의 명암비는 WCAG 상대 휘도 공식으로
 *    backgroundPrimary 위에서 계산한 값이며, 실제 값은 D4(가이드 Colors 이미지)로 재확인한다.
 *    (2026-11-12 대비 점검 반영 — 09문서 색 대비 절. 계산기: 제출용/_work/spec/contrast_calc.py.
 *    ① 라이트 textQuaternary #756E66→#6C655E — surfaceLow 위 4.30:1 미달을 4.91:1로.
 *    ② 다크 stampPoint #D8552B→#DF7451 — surfaceAccent 위 3.55:1·surfaceBase 위 4.33:1 미달을
 *    AA 엄격(4.5:1 무조건) 기준으로 해소. 라이트 stampPoint #B3401C는 이미 합격이라 그대로다.)
 *
 * 안전 규칙: 주행 중 가독성을 위해 테마를 동적으로 섞거나 Material You 동적 컬러를 쓰지 않고,
 * 아래 고정 토큰만 사용한다.
 */

/** 아로새김 색 토큰 묶음. 테마(주간/야간)마다 값이 다르므로 코드에서는 토큰 이름만 참조한다. */
@Immutable
data class AroColors(
    // --- Background ---
    val backgroundPrimary: Color,   // 화면 기본 배경
    val backgroundSecondary: Color, // 보조 배경(구역 구분)
    val backgroundPopup: Color,     // 팝업·토스트 배경

    // --- Surface ---
    val surfaceBase: Color,   // 카드·패널 기본 표면
    val surfaceLow: Color,    // 낮은 강조 표면
    val surfaceHigh: Color,   // 높은 강조 표면
    val surfaceAccent: Color, // 강조 표면(인주 틴트 — 스탬프·완료 영역)

    // --- Text ---
    val textPrimary: Color,   // 본문·장소명 (대비 최우선)
    val textSecondary: Color, // 보조 설명
    val textTertiary: Color,  // 꼬리표·비활성 안내
    val textQuaternary: Color, // 가장 약한 텍스트(플레이스홀더 등)

    // --- Button ---
    val buttonBasic: Color,   // 기본 버튼 배경
    val buttonActive: Color,  // 눌림·활성 상태 배경
    val buttonFilled: Color,  // 채운(CTA) 버튼 배경

    // --- Informative ---
    val informativeActive: Color,   // 초록 — 성공/정상
    val informativePositive: Color, // 파랑 — 정보
    val informativeNegative: Color, // 빨강 — 오류

    // --- 아로새김 전용 ---
    val stampPoint: Color,  // 인주색 — 앱의 유일한 포인트 색(스탬프·완료 배지 전용)
    val regulation: Color,  // 주행 규제·경고 전용(장식 금지)
)

/*
 * 주간 테마. 배경은 웜화이트(#F7F5F2) 계열.
 * 대비비는 backgroundPrimary(#F7F5F2, 상대 휘도 ≈0.915) 기준:
 *  - textPrimary #221F1C      ≈ 15.1:1
 *  - textSecondary #55504A    ≈  7.3:1
 *  - textTertiary #6E675F     ≈  5.1:1
 *  - textQuaternary #6C655E   ≈  5.3:1 (2026-11-12 대비 점검으로 #756E66에서 조정 —
 *                                 surfaceLow 위에서 4.91:1, surfaceBase 위에서 5.59:1)
 *  - stampPoint #B3401C       ≈  5.3:1
 *  - regulation #B3261E       ≈  6.0:1
 *  - informativeActive #2E7D32   ≈ 4.7:1
 *  - informativePositive #1A5FB4 ≈ 5.8:1
 *  - informativeNegative #BA1A1A ≈ 5.9:1
 */
val LightAroColors: AroColors = AroColors(
    backgroundPrimary = Color(0xFFF7F5F2),
    backgroundSecondary = Color(0xFFEFEBE6),
    backgroundPopup = Color(0xFFFFFFFF),
    surfaceBase = Color(0xFFFDFCFA),
    surfaceLow = Color(0xFFF0EDE8),
    surfaceHigh = Color(0xFFFFFFFF),
    surfaceAccent = Color(0xFFFCEBE3), // 인주 틴트
    textPrimary = Color(0xFF221F1C),
    textSecondary = Color(0xFF55504A),
    textTertiary = Color(0xFF6E675F),
    // 2026-11-12 대비 점검(09문서 색대비 1번): #756E66은 surfaceLow(#F0EDE8) 위 4.30:1로
    // 20sp 라벨(사진 자리 문구)의 4.5:1에 미달 → 한 단계 어둡게. surfaceLow 4.91:1·surfaceBase 5.59:1.
    textQuaternary = Color(0xFF6C655E),
    buttonBasic = Color(0xFFE9E5DF),
    buttonActive = Color(0xFFDCD6CE),
    buttonFilled = Color(0xFF2A2723), // 잉크 채움 — 흰 텍스트와 약 14.9:1
    informativeActive = Color(0xFF2E7D32),
    informativePositive = Color(0xFF1A5FB4),
    informativeNegative = Color(0xFFBA1A1A),
    stampPoint = Color(0xFFB3401C), // 인주(주간) — backgroundPrimary 위 5.26:1로 합격(2026-11-12 점검에서 유지)
    regulation = Color(0xFFB3261E), // 진한 경고색 — 규제 전용
)

/*
 * 야간 테마. 배경은 오프블랙(#101014).
 * 대비비는 backgroundPrimary(#101014, 상대 휘도 ≈0.0053) 기준:
 *  - textPrimary #F2F1EE      ≈ 17.0:1
 *  - textSecondary #B9B6B0    ≈  9.4:1
 *  - textTertiary #8F8C86     ≈  5.7:1
 *  - textQuaternary #827F79   ≈  4.8:1
 *  - stampPoint #DF7451       ≈  6.1:1 (2026-11-12 대비 점검으로 #D8552B에서 조정 —
 *                                 surfaceBase 위 5.57:1, surfaceAccent 위 4.57:1)
 *  - regulation #FF6E5E       ≈  6.9:1
 *  - informativeActive #81C784   ≈ 9.4:1
 *  - informativePositive #8AB4F8 ≈ 9.0:1
 *  - informativeNegative #FFB4AB ≈ 11.2:1
 */
val DarkAroColors: AroColors = AroColors(
    backgroundPrimary = Color(0xFF101014),
    backgroundSecondary = Color(0xFF17171C),
    backgroundPopup = Color(0xFF1F1F26),
    surfaceBase = Color(0xFF1A1A20),
    surfaceLow = Color(0xFF14141A),
    surfaceHigh = Color(0xFF23232B),
    surfaceAccent = Color(0xFF3A2620), // 인주 틴트(어두운 톤)
    textPrimary = Color(0xFFF2F1EE),
    textSecondary = Color(0xFFB9B6B0),
    textTertiary = Color(0xFF8F8C86),
    textQuaternary = Color(0xFF827F79),
    buttonBasic = Color(0xFF2A2A33),
    buttonActive = Color(0xFF3A3A45),
    buttonFilled = Color(0xFFF2F1EE), // 밝은 잉크 — 다크 텍스트와 약 17:1
    informativeActive = Color(0xFF81C784),
    informativePositive = Color(0xFF8AB4F8),
    informativeNegative = Color(0xFFFFB4AB),
    // 2026-11-12 대비 점검(09문서 색대비 2·3번): #D8552B은 surfaceBase 위 4.33:1·surfaceAccent 위
    // 3.55:1로 미달(SemiBold를 "굵게"로 보지 않는 보수적 판정). AA 엄격(4.5:1 무조건) 채택으로
    // 한 단계 밝게 — surfaceAccent 4.57:1·surfaceBase 5.57:1·backgroundPrimary 6.11:1로 세 배경 모두 합격.
    // (기존 backgroundPrimary 위 4.75:1도 6.11:1로 여유가 생긴다. 인주색조가 밝아져 시각 확인은 D4·D5로.)
    stampPoint = Color(0xFFDF7451), // 인주(야간 — 채도·명도 조절)
    regulation = Color(0xFFFF6E5E), // 야간에서 밝은 경고색 — 규제 전용
)

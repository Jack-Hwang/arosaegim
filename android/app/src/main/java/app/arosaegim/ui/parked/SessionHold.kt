package app.arosaegim.ui.parked

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.arosaegim.data.FamilySessionStore

/*
 * 정차 화면용 차량 세션 저장소 홀더(T9-1·T9-3, 2026-11-11).
 *
 * StartActivity 와 DiagnosticsViewModel 이 FamilySessionStore 를 AppContainer 밖에서
 * 직접 만드는 기존 패턴을 그대로 따른다(AppContainer 는 병렬 수정 중이라 건드리지 않기 위함 —
 * 각 파일 주석 참조). SharedPreferences("family_session") 를 공유하므로 인스턴스가 여러 개여도
 * 저장 상태(가족 코드·게스트 플래그)는 서로 일치한다(FamilySessionStore 주석의 인스턴스화 규약).
 *
 * PIN 규칙(00문서 10.6): PIN 은 이 저장소의 메모리 전용 API(setPin/pinOrNull)로만 다뤄지고
 * 어디에도 저장되지 않는다. 프로세스가 끝나면 PIN 은 사라지고, 정차 화면·진단 화면은
 * pinOrNull() 이 null 이면 서버 조회를 하지 않고 해당 값을 숨긴다(00문서 13절: 없으면 숨김).
 */
@Composable
internal fun rememberFamilyStore(context: Context): FamilySessionStore =
    remember { FamilySessionStore(context.applicationContext) }

package app.arosaegim.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 로컬 방문 기록 한 건.
 *
 * @property placeId        방문한 장소 id(시드의 place id).
 * @property courseId       어느 코스에서 찍었는지.
 * @property visitedAtEpochMs 방문(스탬프) 시각.
 * @property source         도착 출처: "fused" | "navi" | "manual"(00문서 4절 ArrivalEvent 규약).
 * @property vehicleLabel   당시 차종 표시용 라벨(선택). VIN 은 규정상 저장하지 않는다(00문서 9절).
 */
@Serializable
data class LocalVisit(
    val placeId: String,
    val courseId: String,
    val visitedAtEpochMs: Long,
    val source: String,
    val vehicleLabel: String? = null,
)

/**
 * 로컬 방문 기록 저장소 — filesDir/visits.json 파일 하나에 JSON 배열을 유지한다.
 *
 * 설계 의도(간단·설명 가능 우선):
 *  - DB 를 두지 않고 파일 하나로 관리한다. 방문 기록은 코스당 최대 장소 수 건으로 매우 작다.
 *  - 모든 파일 실패(IOException 등)는 흡수한다. 기록 실패로 주행 흐름이 깨지면 안 되므로
 *    크래시 대신 빈 목록/조용한 실패로 본다.
 *  - 좌표는 절대 저장하지 않는다. 위치는 도착 판정에만 쓰고 버린다(00문서 2-2 §7:
 *    "위치는 도착 판정에만 쓰고 저장하지 않는다"). LocalVisit 에도 좌표 필드가 없다.
 *
 * open class 인 이유: 단위 테스트(TripSessionTest)에서 파일을 실제로 쓰지 않고
 * append 를 가로채는 하위 클래스로 대체하기 위함이다(기능 동일, 저장만 가짜).
 */
open class VisitStore {

    /** visits.json 이 놓이는 파일. filesDir 아래 앱 전용 영역이다(기본 생성자). */
    private val file: File

    /** 앱에서 쓰는 표준 생성자 — filesDir/visits.json 을 가리킨다. */
    constructor(context: Context) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    /** 파일을 직접 지정하는 생성자 — 단위 테스트가 Context 없이 임시 파일로 대체할 때 쓴다. */
    constructor(file: File) {
        this.file = file
    }

    /** 읽기-수정-쓰기 경쟁 방지용 잠금(세션 콜백·진단 화면이 같이 접근할 수 있다). */
    private val lock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true // 사람이 열어 확인 가능하게(발주자가 코드·파일을 직접 설명한다).
    }

    /** 방문 기록 한 건을 추가한다. 실패해도 예외를 던지지 않는다. */
    open fun append(visit: LocalVisit) {
        synchronized(lock) {
            try {
                val current = readAllInternal()
                val updated = current + visit
                file.writeText(json.encodeToString(updated))
            } catch (t: Throwable) {
                // 기록 실패는 주행 흐름을 막지 않는다(00문서 2-3 §16: 핵심 흐름 보호).
            }
        }
    }

    /** 전체 방문 기록을 읽는다. 파일이 없거나 깨졌으면 빈 목록. */
    open fun readAll(): List<LocalVisit> {
        synchronized(lock) {
            return try {
                readAllInternal()
            } catch (t: Throwable) {
                // 깨진 JSON 은 데이터 손실로 간주하고 빈 목록으로 복구한다(크래시 금지).
                emptyList()
            }
        }
    }

    /** 방문 기록 전체 삭제 — '이 차에서 로그아웃'(T9-1)과 데모 초기화용. */
    open fun clear() {
        synchronized(lock) {
            try {
                if (!file.exists()) return
                file.delete()
            } catch (t: Throwable) {
                // 삭제 실패도 흡수한다. 다음 append 가 배열을 다시 만든다.
            }
        }
    }

    /**
     * 실제 파일 읽기/파싱. lock 밖에서 부르지 않는다.
     * append 내부 재사용용이며 readAll 과 달리 실패를 그대로 전파한다.
     */
    private fun readAllInternal(): List<LocalVisit> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(text)
    }

    private companion object {
        const val FILE_NAME = "visits.json"
    }
}

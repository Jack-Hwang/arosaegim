package app.arosaegim.fallback

import app.arosaegim.domain.VehicleInfo
import app.arosaegim.ports.VehicleInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 수동 프로필 차량 정보 — 차종·연식을 앱 설정 값(데모는 고정값)으로 답한다.
 *
 * 이유: Pleos CarInfo 는 VIN/CRN 조회만 제공(AAR 확인)하고 VIN 은 규정상 읽지 않는다.
 * 차량 식별이 꼭 필요한 화면은 이 수동 프로필을 쓰고, 값이 없으면 표시를 조용히 숨긴다.
 */
class ProfileVehicleInfo(
    private val model: String?,
    private val modelYear: Int?,
) : VehicleInfoProvider {

    /** model 이 없으면 정보 없음(null)으로 간주한다. 값은 화면 표시 전용이며 저장·전송하지 않는다. */
    override suspend fun read(): VehicleInfo? = withContext(Dispatchers.Default) {
        model?.let { VehicleInfo(model = it, modelYear = modelYear) }
    }
}

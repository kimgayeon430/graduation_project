package smu.ai.graduation_project.ui.screens

import android.net.Uri
import com.google.firebase.firestore.GeoPoint
import smu.ai.graduation_project.domain.LocationVerification
import smu.ai.graduation_project.domain.MissionRewardPolicy

/**
 * [MissionPerformScreen] 이 그리는 데 필요한 모든 상태.
 * 화면은 이 상태를 표시하고 [MissionPerformViewModel] 의 함수를 호출하는 역할만 한다.
 */
data class MissionPerformUiState(
    val missionTitle: String = "미션",
    val missionCategory: String = "투어",
    val missionPoints: Int = 0,
    val missionLocation: GeoPoint? = null,
    val missionDocId: String? = null,
    /** 미션 대표 이미지 임베딩. 있으면 사진 인증에서 촬영본과의 유사도를 결합한다(6.7). */
    val missionReferenceEmbedding: List<Float> = emptyList(),

    val isVerifying: Boolean = false,
    val isUploading: Boolean = false,
    val locationVerified: Boolean = false,
    val missionCompleted: Boolean = false,
    val stage1RewardGranted: Boolean = false,
    val stage2RewardGranted: Boolean = false,

    val verificationText: String = "아직 위치 인증을 하지 않았습니다.",

    val capturedPhotoUri: Uri? = null,
    val photoUrl: String? = null,
    val uploadError: String? = null,

    /** 한 번만 소비되는 일회성 이벤트. */
    val toastMessage: String? = null,
    val navigateBack: Boolean = false
) {
    val allowedRadiusMeters: Float get() = LocationVerification.DEFAULT_ALLOWED_RADIUS_METERS.toFloat()
    val stage1Reward: Int get() = MissionRewardPolicy.stage1Reward(missionPoints)
    val stage2Reward: Int get() = MissionRewardPolicy.stage2Reward(missionPoints)
}

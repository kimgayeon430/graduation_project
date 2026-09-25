package smu.ai.graduation_project.ui.screens

import android.net.Uri
import com.google.firebase.firestore.GeoPoint
import smu.ai.graduation_project.domain.LocationVerification
import smu.ai.graduation_project.domain.MissionRewardPolicy
import smu.ai.graduation_project.domain.PhotoVerification

/** 사진 인증 결과 화면(전체 화면 오버레이)에 표시할 판정. */
enum class PhotoVerdictUi { PASS, REVIEW, REJECT }

/**
 * AI 사진 판정이 끝난 뒤 전체 화면으로 보여줄 결과. [MissionPerformUiState.photoResult] 가
 * null 이 아니면 화면이 이 값을 바탕으로 결과 오버레이를 띄운다 — 화면 회전에도
 * [MissionPerformViewModel] 이 들고 있는 상태라 그대로 복원된다.
 */
data class PhotoResultUi(
    val verdict: PhotoVerdictUi,
    /** 표시할 사진. [Uri](로컬 촬영본) 또는 업로드된 URL 문자열. */
    val displayPhoto: Any?,
    /** 미션이 요구한 카테고리(코드값, 예: "투어"). */
    val missionCategory: String,
    /** AI 가 예측한 카테고리(코드값). 모델을 못 불러왔으면 빈 문자열. */
    val predictedLabel: String,
    /** 0..1. 표시할 만한 값이 없으면(모델 미가동 등) null. */
    val confidence: Double?,
    val pointsGranted: Int,
    /** [PhotoVerdictUi.REJECT] 일 때만 값이 있다. */
    val rejectReasonCode: PhotoVerification.RejectReasonCode?,
    /** 모델 버전. "분석 정보" 접이식 영역에만 노출한다. */
    val modelVersion: String
)

/**
 * PASS 판정 직후 짧게(1~1.5초) 보여주는 성취 연출에 필요한 값. 이 화면을 지나야
 * [PhotoResultUi] 결과 화면(이미 준비돼 있음)이 이어서 보인다.
 */
data class CelebrationUi(
    val missionTitle: String,
    val pointsGranted: Int,
    /** 이번 주(월요일 0시~) 완료한 미션 수. 조회 실패 시 0. */
    val weeklyCompleted: Int,
    val weeklyGoal: Int
)

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
    /** 미션 대표 이미지(들) 임베딩. 있으면 사진 인증에서 촬영본과의 유사도(최대값)를 결합한다(6.7, 6.7.8). */
    val missionReferenceEmbeddings: List<List<Float>> = emptyList(),

    val isVerifying: Boolean = false,
    val isUploading: Boolean = false,
    val locationVerified: Boolean = false,
    val missionCompleted: Boolean = false,
    val stage1RewardGranted: Boolean = false,
    val stage2RewardGranted: Boolean = false,

    /** 빈 문자열이면 화면이 [smu.ai.graduation_project.R.string.perform_status_not_verified_yet] 를 대신 표시한다. */
    val verificationText: String = "",

    val capturedPhotoUri: Uri? = null,
    val photoUrl: String? = null,
    val uploadError: String? = null,

    /** AI 판정이 끝난 뒤 보여줄 전체 화면 결과. null 이 아니면 화면이 오버레이를 띄운다. */
    val photoResult: PhotoResultUi? = null,
    /** PASS 일 때만 값이 있다. null 이 아니면 [photoResult] 보다 먼저 이 연출을 보여준다. */
    val celebration: CelebrationUi? = null,

    /** 한 번만 소비되는 일회성 이벤트. */
    val toastMessage: String? = null,
    val navigateBack: Boolean = false
) {
    val allowedRadiusMeters: Float get() = LocationVerification.DEFAULT_ALLOWED_RADIUS_METERS.toFloat()
    val stage1Reward: Int get() = MissionRewardPolicy.stage1Reward(missionPoints)
    val stage2Reward: Int get() = MissionRewardPolicy.stage2Reward(missionPoints)
}

package smu.ai.graduation_project.ui.screens

import android.location.Location
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import smu.ai.graduation_project.data.FakePhotoVerifier
import smu.ai.graduation_project.data.FirebaseMissionRepository
import smu.ai.graduation_project.data.MissionRepository
import smu.ai.graduation_project.data.PhotoVerifier
import smu.ai.graduation_project.domain.LocationVerification
import smu.ai.graduation_project.domain.MissionCompletion
import smu.ai.graduation_project.domain.PhotoVerificationConfig

/**
 * 미션 수행 화면의 상태 보유 + Firebase 조회·위치 인증·사진 업로드·포인트 지급 흐름 조정.
 * Android 프레임워크 의존(위치 권한/GPS 획득, 카메라 실행)은 화면에 남기고,
 * 그 결과값(Location, 촬영 Uri)만 이 ViewModel 로 전달된다.
 */
class MissionPerformViewModel(
    private val repository: MissionRepository,
    private val photoVerifier: PhotoVerifier,
    private val photoVerificationConfig: PhotoVerificationConfig
) : ViewModel() {

    /**
     * 프로덕션 기본값. 아직 사진 인증 모델(`assets/photo_verifier.onnx`)이 없어
     * [FakePhotoVerifier] 가 항상 null 을 돌려주며, 모델이 없을 때는 통과시킨다.
     * `OnnxPhotoVerifier` 연결 시 이 두 인자를 실제 구현·기본 설정([PhotoVerificationConfig.DEFAULT])으로 교체한다.
     */
    constructor() : this(
        FirebaseMissionRepository(),
        FakePhotoVerifier(),
        PhotoVerificationConfig(passWhenModelUnavailable = true)
    )

    var uiState by mutableStateOf(MissionPerformUiState())
        private set

    private var missionId: String = ""
    private var uid: String? = null

    /** 화면 진입 시 미션 정보 + 사용자 진행 상태를 불러온다. */
    fun loadData(missionId: String, uid: String?) {
        this.missionId = missionId
        this.uid = uid

        repository.loadMissionInfo(
            missionId = missionId,
            onResult = { info ->
                uiState = uiState.copy(
                    missionTitle = info.title,
                    missionCategory = info.category,
                    missionPoints = info.points,
                    missionLocation = info.location
                )
            },
            onError = {}
        )

        if (uid != null) {
            repository.loadUserMissionState(
                missionId = missionId,
                uid = uid,
                onResult = { state ->
                    val completed = MissionCompletion.isCompleted(state.status)
                    uiState = uiState.copy(
                        missionDocId = state.docId,
                        locationVerified = state.locationVerified,
                        missionCompleted = completed,
                        stage1RewardGranted = state.stage1RewardGranted,
                        stage2RewardGranted = state.stage2RewardGranted,
                        photoUrl = state.photoUrl,
                        verificationText = when {
                            completed -> "위치 인증 완료 · 미션 완료 상태입니다."
                            state.locationVerified -> "위치 인증 완료: %.5f, %.5f".format(
                                state.verifiedLatitude ?: 0.0,
                                state.verifiedLongitude ?: 0.0
                            )
                            else -> uiState.verificationText
                        }
                    )
                },
                onError = {}
            )
        }
    }

    // ---- 1단계: 위치 인증 --------------------------------------------------

    /** 화면에서 GPS 획득을 시작했음을 알린다(스피너 표시). */
    fun onLocationRequestStarted() {
        uiState = uiState.copy(isVerifying = true)
    }

    /** 화면에서 받아온 현재 위치로 1단계 인증을 처리한다. */
    fun onLocationResult(location: Location?) {
        if (uid == null) {
            finishVerify("로그인이 필요합니다.")
            return
        }
        if (location == null) {
            finishVerify("현재 위치를 가져오지 못했습니다.")
            return
        }
        val target = uiState.missionLocation
        if (target == null) {
            finishVerify("미션 위치 정보가 없습니다.")
            return
        }

        val verification = LocationVerification.verify(
            currentLat = location.latitude,
            currentLon = location.longitude,
            targetLat = target.latitude,
            targetLon = target.longitude,
            allowedRadiusMeters = uiState.allowedRadiusMeters.toDouble()
        )
        val distanceMeters = verification.distanceMeters
        val isNearEnough = verification.isWithinRadius

        uiState = uiState.copy(
            locationVerified = isNearEnough,
            verificationText = if (isNearEnough) {
                "위치 인증 완료 · 목표 지점까지 %.0fm".format(distanceMeters)
            } else {
                "현재 위치가 인증 범위를 벗어났습니다 · %.0fm 떨어져 있어요".format(distanceMeters)
            }
        )

        val docId = uiState.missionDocId
        if (docId == null) {
            finishVerify("미션 진행 정보가 없습니다.")
            return
        }

        repository.verifyLocation(
            userMissionDocId = docId,
            uid = uid!!,
            isNearEnough = isNearEnough,
            latitude = location.latitude,
            longitude = location.longitude,
            distanceMeters = distanceMeters,
            missionPoints = uiState.missionPoints,
            onResult = { result ->
                uiState = uiState.copy(
                    isVerifying = false,
                    stage1RewardGranted = if (isNearEnough) true else uiState.stage1RewardGranted,
                    toastMessage = when {
                        !isNearEnough -> "미션 위치 근처에서 다시 시도해주세요."
                        result.rewardGranted > 0 -> "위치 인증 완료. ${result.rewardGranted}P가 지급되었습니다."
                        else -> "위치 인증이 완료되었습니다."
                    }
                )
            },
            onError = {
                uiState = uiState.copy(
                    isVerifying = false,
                    toastMessage = "위치 인증 처리에 실패했습니다."
                )
            }
        )
    }

    private fun finishVerify(message: String) {
        uiState = uiState.copy(isVerifying = false, toastMessage = message)
    }

    // ---- 2단계: 사진 인증 --------------------------------------------------

    fun onPhotoCaptured(uri: Uri) {
        uiState = uiState.copy(capturedPhotoUri = uri, uploadError = null)
    }

    fun onPhotoCaptureCancelled() {
        uiState = uiState.copy(toastMessage = "사진 촬영이 취소되었습니다.")
    }

    /**
     * @param photoBytes 화면에서 촬영본 Uri 를 읽어 넘긴 이미지 바이트. 읽지 못했으면 null.
     */
    fun uploadPhotoAndComplete(photoBytes: ByteArray?) {
        val state = uiState
        if (uid == null) {
            emitToast("로그인이 필요합니다.")
            return
        }
        val docId = state.missionDocId
        if (docId == null) {
            emitToast("미션 진행 정보가 없습니다.")
            return
        }
        if (!state.locationVerified) {
            emitToast("먼저 위치 인증을 완료해주세요.")
            return
        }
        if (state.capturedPhotoUri == null) {
            emitToast("먼저 사진을 촬영해주세요.")
            return
        }
        if (photoBytes == null || photoBytes.isEmpty()) {
            emitToast("사진을 읽지 못했습니다. 다시 촬영해주세요.")
            return
        }
        if (state.missionCompleted || state.isUploading) return

        uiState = state.copy(isUploading = true, uploadError = null)

        repository.uploadPhotoAndComplete(
            missionId = missionId,
            missionCategory = state.missionCategory,
            userMissionDocId = docId,
            uid = uid!!,
            photoBytes = photoBytes,
            missionPoints = state.missionPoints,
            photoVerifier = photoVerifier,
            photoVerificationConfig = photoVerificationConfig,
            onResult = { result ->
                uiState = uiState.copy(
                    isUploading = false,
                    missionCompleted = true,
                    stage2RewardGranted = true,
                    photoUrl = result.photoUrl,
                    verificationText = if (result.needsReview) {
                        "사진 업로드 완료 · 관리자 검수 후 최종 확정됩니다."
                    } else {
                        "위치 인증 완료 · 미션이 완료되었습니다."
                    },
                    toastMessage = when {
                        result.alreadyCompleted -> "이미 완료 처리된 미션입니다."
                        result.needsReview && result.rewardGranted > 0 ->
                            "사진 업로드 완료. ${result.rewardGranted}P 지급 · 관리자 검수 예정입니다."
                        result.rewardGranted > 0 -> "사진 인증 완료. ${result.rewardGranted}P가 지급되었습니다."
                        else -> "사진 인증 완료."
                    },
                    navigateBack = true
                )
            },
            onError = { error ->
                val exception = error as? MissionRepository.MissionCompleteException
                uiState = when (exception?.stage) {
                    MissionRepository.MissionCompleteException.Stage.VERIFY -> uiState.copy(
                        isUploading = false,
                        uploadError = exception?.reason
                            ?: "사진이 미션과 맞지 않아요. 미션 장소·대상을 다시 촬영해 주세요.",
                        toastMessage = "사진이 미션과 맞지 않아 인증하지 못했어요."
                    )
                    MissionRepository.MissionCompleteException.Stage.FINALIZE -> uiState.copy(
                        isUploading = false,
                        uploadError = "완료 처리에 실패했습니다. 다시 시도해주세요.",
                        toastMessage = "완료 처리에 실패했습니다."
                    )
                    else -> uiState.copy(
                        isUploading = false,
                        uploadError = "사진 업로드에 실패했습니다. 네트워크를 확인하고 다시 시도해주세요.",
                        toastMessage = "사진 업로드에 실패했습니다."
                    )
                }
            }
        )
    }

    // ---- 일회성 이벤트 소비 ----------------------------------------------

    private fun emitToast(message: String) {
        uiState = uiState.copy(toastMessage = message)
    }

    fun onToastShown() {
        uiState = uiState.copy(toastMessage = null)
    }

    fun onNavigateHandled() {
        uiState = uiState.copy(navigateBack = false)
    }
}

package smu.ai.graduation_project.ui.screens

import android.app.Application
import android.location.Location
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.FirebaseMissionRepository
import smu.ai.graduation_project.data.MissionRepository
import smu.ai.graduation_project.data.OnnxPhotoVerifier
import smu.ai.graduation_project.data.PhotoVerifier
import smu.ai.graduation_project.data.getLocalizedString
import smu.ai.graduation_project.domain.LocationVerification
import smu.ai.graduation_project.domain.MissionCompletion
import smu.ai.graduation_project.domain.PhotoVerificationConfig

/**
 * 미션 수행 화면의 상태 보유 + Firebase 조회·위치 인증·사진 업로드·포인트 지급 흐름 조정.
 * Android 프레임워크 의존(위치 권한/GPS 획득, 카메라 실행)은 화면에 남기고,
 * 그 결과값(Location, 촬영 Uri)만 이 ViewModel 로 전달된다.
 */
class MissionPerformViewModel(
    application: Application,
    private val repository: MissionRepository,
    private val photoVerifier: PhotoVerifier,
    private val photoVerificationConfig: PhotoVerificationConfig
) : AndroidViewModel(application) {

    /**
     * 프로덕션 기본값. 사진은 [OnnxPhotoVerifier] 로 `assets/photo_verifier.onnx`
     * (`mobilevit-small-fullft-1`) 를 써 온디바이스 판정한다. 임계값은
     * [PhotoVerificationConfig.DEFAULT] (`ml/thresholds.json` 에서 선정).
     * 모델 로드에 실패하면 판정을 `NEEDS_REVIEW` 로 보내 관리자 검수를 거친다.
     */
    constructor(application: Application) : this(
        application,
        FirebaseMissionRepository(),
        OnnxPhotoVerifier(application),
        PhotoVerificationConfig.DEFAULT
    )

    var uiState by mutableStateOf(MissionPerformUiState())
        private set

    private var missionId: String = ""
    private var uid: String? = null

    private fun str(resId: Int, vararg args: Any): String =
        getApplication<Application>().getLocalizedString(resId, *args)

    /** 화면 진입 시 미션 정보 + 사용자 진행 상태를 불러온다. */
    fun loadData(missionId: String, uid: String?) {
        this.missionId = missionId
        this.uid = uid

        // 사진 인증 임베더 모델(런타임 다운로드)을 미리 받아 둔다. 위치 인증·촬영을 하는 동안 끝난다.
        Thread { runCatching { photoVerifier.prefetch() } }.start()

        repository.loadMissionInfo(
            missionId = missionId,
            onResult = { info ->
                uiState = uiState.copy(
                    missionTitle = info.title,
                    missionCategory = info.category,
                    missionPoints = info.points,
                    missionLocation = info.location,
                    missionReferenceEmbeddings = info.photoEmbeddings
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
                            completed -> str(R.string.perform_state_completed_status)
                            state.locationVerified -> str(
                                R.string.perform_state_verified_coords,
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
            finishVerify(str(R.string.toast_login_required))
            return
        }
        if (location == null) {
            finishVerify(str(R.string.perform_error_no_location))
            return
        }
        val target = uiState.missionLocation
        if (target == null) {
            finishVerify(str(R.string.perform_error_no_mission_location))
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
                str(R.string.perform_state_verified_distance, distanceMeters)
            } else {
                str(R.string.perform_state_out_of_range, distanceMeters)
            }
        )

        val docId = uiState.missionDocId
        if (docId == null) {
            finishVerify(str(R.string.perform_error_no_progress))
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
                        !isNearEnough -> str(R.string.perform_toast_retry_near_location)
                        result.rewardGranted > 0 -> str(R.string.perform_toast_location_verified_reward, result.rewardGranted)
                        else -> str(R.string.perform_toast_location_verified)
                    }
                )
            },
            onError = {
                uiState = uiState.copy(
                    isVerifying = false,
                    toastMessage = str(R.string.perform_toast_location_verify_failed)
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
        uiState = uiState.copy(toastMessage = str(R.string.perform_toast_photo_cancelled))
    }

    /**
     * @param photoBytes 화면에서 촬영본 Uri 를 읽어 넘긴 이미지 바이트. 읽지 못했으면 null.
     */
    fun uploadPhotoAndComplete(photoBytes: ByteArray?) {
        val state = uiState
        if (uid == null) {
            emitToast(str(R.string.toast_login_required))
            return
        }
        val docId = state.missionDocId
        if (docId == null) {
            emitToast(str(R.string.perform_error_no_progress))
            return
        }
        if (!state.locationVerified) {
            emitToast(str(R.string.toast_verify_location_first))
            return
        }
        if (state.capturedPhotoUri == null) {
            emitToast(str(R.string.perform_toast_take_photo_first))
            return
        }
        if (photoBytes == null || photoBytes.isEmpty()) {
            emitToast(str(R.string.perform_toast_photo_read_failed))
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
            referenceEmbeddings = state.missionReferenceEmbeddings
                .filter { it.isNotEmpty() }.map { it.toFloatArray() },
            onResult = { result ->
                uiState = uiState.copy(
                    isUploading = false,
                    missionCompleted = true,
                    stage2RewardGranted = true,
                    photoUrl = result.photoUrl,
                    verificationText = if (result.needsReview) {
                        str(R.string.perform_state_photo_needs_review)
                    } else {
                        str(R.string.perform_state_mission_completed)
                    },
                    toastMessage = when {
                        result.alreadyCompleted -> str(R.string.perform_toast_already_completed)
                        result.needsReview && result.rewardGranted > 0 ->
                            str(R.string.perform_toast_photo_uploaded_review, result.rewardGranted)
                        result.rewardGranted > 0 -> str(R.string.perform_toast_photo_verified_reward, result.rewardGranted)
                        else -> str(R.string.perform_toast_photo_verified)
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
                            ?: str(R.string.perform_error_photo_mismatch),
                        toastMessage = str(R.string.perform_toast_photo_mismatch)
                    )
                    MissionRepository.MissionCompleteException.Stage.FINALIZE -> uiState.copy(
                        isUploading = false,
                        uploadError = str(R.string.perform_error_finalize_failed),
                        toastMessage = str(R.string.perform_toast_finalize_failed)
                    )
                    else -> uiState.copy(
                        isUploading = false,
                        uploadError = str(R.string.perform_error_upload_failed),
                        toastMessage = str(R.string.perform_toast_upload_failed)
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

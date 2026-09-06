package smu.ai.graduation_project.data

import android.net.Uri
import com.google.firebase.firestore.GeoPoint

/**
 * 미션 수행 화면에서 필요한 데이터 접근을 추상화한다.
 * 기존 Firestore 컬렉션(`missions`, `user_missions`, `users`)과 필드 구조는 그대로 유지한다.
 */
interface MissionRepository {

    /** `missions/{missionId}` 문서에서 읽어온 미션 기본 정보. */
    data class MissionInfo(
        val title: String,
        val points: Int,
        val location: GeoPoint?
    )

    /** 현재 사용자의 `user_missions` 진행 상태. */
    data class UserMissionState(
        val docId: String?,
        val status: String,
        val locationVerified: Boolean,
        val stage1RewardGranted: Boolean,
        val stage2RewardGranted: Boolean,
        val verifiedLatitude: Double?,
        val verifiedLongitude: Double?,
        val photoUrl: String?
    )

    /** 1단계(위치 인증) 트랜잭션 결과. */
    data class LocationVerifyResult(val rewardGranted: Int)

    /** 2단계(사진 인증) 완료 트랜잭션 결과. */
    data class CompleteResult(
        val rewardGranted: Int,
        val alreadyCompleted: Boolean,
        val photoUrl: String
    )

    /** 사진 인증 완료 과정에서 어느 단계가 실패했는지 구분하기 위한 예외. */
    class MissionCompleteException(
        val stage: Stage,
        cause: Throwable?
    ) : Exception(cause) {
        enum class Stage { UPLOAD, FINALIZE }
    }

    fun loadMissionInfo(
        missionId: String,
        onResult: (MissionInfo) -> Unit,
        onError: (Exception) -> Unit
    )

    fun loadUserMissionState(
        missionId: String,
        uid: String,
        onResult: (UserMissionState) -> Unit,
        onError: (Exception) -> Unit
    )

    /**
     * 1단계 위치 인증. `user_missions` 문서를 갱신하고, 범위 안이면서 아직 지급 전이면
     * `users/{uid}.points` 를 [stage1Reward] 만큼 올린다. (트랜잭션으로 중복 지급 방지)
     */
    fun verifyLocation(
        userMissionDocId: String,
        uid: String,
        isNearEnough: Boolean,
        latitude: Double,
        longitude: Double,
        distanceMeters: Float,
        stage1Reward: Int,
        onResult: (LocationVerifyResult) -> Unit,
        onError: (Exception) -> Unit
    )

    /**
     * 2단계 사진 인증. 사진을 Storage 에 업로드하고, 성공 후에만 트랜잭션으로 미션을 완료 처리하며
     * 아직 지급 전이면 `users/{uid}.points` 를 [stage2Reward] 만큼 올린다.
     */
    fun uploadPhotoAndComplete(
        missionId: String,
        userMissionDocId: String,
        uid: String,
        photoUri: Uri,
        stage2Reward: Int,
        onResult: (CompleteResult) -> Unit,
        onError: (Exception) -> Unit
    )
}

package smu.ai.graduation_project.data

import com.google.firebase.firestore.GeoPoint
import smu.ai.graduation_project.domain.PhotoVerificationConfig

/**
 * 미션 수행 화면에서 필요한 데이터 접근을 추상화한다.
 * 기존 Firestore 컬렉션(`missions`, `user_missions`, `users`)과 필드 구조는 그대로 유지한다.
 */
interface MissionRepository {

    /** `missions/{missionId}` 문서에서 읽어온 미션 기본 정보. */
    data class MissionInfo(
        val title: String,
        /** 사진 인증 판정에서 기대 카테고리로 쓰인다. */
        val category: String,
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
        val photoUrl: String,
        /** 자동 사진 판정이 애매해 관리자 검수 대기(`photoNeedsReview`)로 완료됐는지. */
        val needsReview: Boolean
    )

    /** 사진 인증 완료 과정에서 어느 단계가 실패/거부됐는지 구분하기 위한 예외. */
    class MissionCompleteException(
        val stage: Stage,
        cause: Throwable? = null,
        /** VERIFY 단계일 때 사용자에게 보여줄 사유. */
        val reason: String? = null
    ) : Exception(cause) {
        /**
         * VERIFY = 사진이 미션과 맞지 않아 업로드 전 거부,
         * UPLOAD = Supabase Storage 업로드 실패,
         * FINALIZE = 업로드 후 Firestore 완료 처리 실패.
         */
        enum class Stage { VERIFY, UPLOAD, FINALIZE }
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
     * `users/{uid}.points` 를 1단계 보상만큼 올린다. (트랜잭션으로 중복 지급 방지)
     * 보상 계산은 [smu.ai.graduation_project.domain.MissionRewardPolicy] 에 위임한다.
     */
    fun verifyLocation(
        userMissionDocId: String,
        uid: String,
        isNearEnough: Boolean,
        latitude: Double,
        longitude: Double,
        distanceMeters: Double,
        missionPoints: Int,
        onResult: (LocationVerifyResult) -> Unit,
        onError: (Exception) -> Unit
    )

    /**
     * 2단계 사진 인증. 순서는 다음과 같다.
     *
     *  1. [photoVerifier] 로 사진을 온디바이스 분류하고 [smu.ai.graduation_project.domain.PhotoVerification]
     *     규칙으로 판정한다. `REJECT` 면 업로드하지 않고 [MissionCompleteException] (`Stage.VERIFY`) 로 끝낸다.
     *  2. `PASS` / `NEEDS_REVIEW` 면 사진을 Supabase Storage 에 업로드한다.
     *  3. 업로드 성공 후에만 트랜잭션으로 미션을 완료 처리하고, 아직 지급 전이면 2단계 보상을 지급하며
     *     판정 결과(`photoVerified` / `photoNeedsReview` / `photoVerifyScore` / `photoVerifyLabel` / `photoVerifyModelVersion`)를 저장한다.
     *
     * `NEEDS_REVIEW` 도 미션은 완료되고 포인트도 지급되며, 관리자 검수용 플래그만 남는다.
     * 완료 판정/보상 계산은 [smu.ai.graduation_project.domain.MissionCompletion] 에 위임한다.
     */
    fun uploadPhotoAndComplete(
        missionId: String,
        missionCategory: String,
        userMissionDocId: String,
        uid: String,
        photoBytes: ByteArray,
        missionPoints: Int,
        photoVerifier: PhotoVerifier,
        photoVerificationConfig: PhotoVerificationConfig = PhotoVerificationConfig.DEFAULT,
        onResult: (CompleteResult) -> Unit,
        onError: (Exception) -> Unit
    )
}

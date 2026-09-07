package smu.ai.graduation_project.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.domain.MissionCompletion
import smu.ai.graduation_project.domain.MissionRewardPolicy
import smu.ai.graduation_project.domain.PhotoVerificationConfig
import java.util.concurrent.Executors

/**
 * [MissionRepository] 의 구현. 미션/진행 상태 데이터는 Firestore, 사진 파일은 Supabase Storage 를 쓴다.
 * 읽고 쓰는 Firestore 컬렉션·필드 구조는 이전과 동일하다.
 */
class FirebaseMissionRepository : MissionRepository {

    private val db = Firebase.firestore
    private val uploadExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun loadMissionInfo(
        missionId: String,
        onResult: (MissionRepository.MissionInfo) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("missions").document(missionId).get()
            .addOnSuccessListener { doc ->
                onResult(
                    MissionRepository.MissionInfo(
                        title = doc.getString("title") ?: "미션",
                        category = doc.getString("category") ?: "투어",
                        points = doc.getLong("points")?.toInt() ?: 0,
                        location = doc.getGeoPoint("location")
                    )
                )
            }
            .addOnFailureListener(onError)
    }

    override fun loadUserMissionState(
        missionId: String,
        uid: String,
        onResult: (MissionRepository.UserMissionState) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("user_missions")
            .whereEqualTo("userId", uid)
            .whereEqualTo("missionId", missionId)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                onResult(
                    MissionRepository.UserMissionState(
                        docId = doc?.id,
                        status = doc?.getString("status").orEmpty(),
                        locationVerified = doc?.getBoolean("locationVerified") == true,
                        stage1RewardGranted = doc?.getBoolean("stage1RewardGranted") == true,
                        stage2RewardGranted = doc?.getBoolean("stage2RewardGranted") == true,
                        verifiedLatitude = doc?.getDouble("verifiedLatitude"),
                        verifiedLongitude = doc?.getDouble("verifiedLongitude"),
                        photoUrl = doc?.getString("photoUrl")
                    )
                )
            }
            .addOnFailureListener(onError)
    }

    override fun verifyLocation(
        userMissionDocId: String,
        uid: String,
        isNearEnough: Boolean,
        latitude: Double,
        longitude: Double,
        distanceMeters: Double,
        missionPoints: Int,
        onResult: (MissionRepository.LocationVerifyResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userMissionRef = db.collection("user_missions").document(userMissionDocId)
        val userRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            val userMissionSnapshot = transaction.get(userMissionRef)
            val alreadyRewarded = userMissionSnapshot.getBoolean("stage1RewardGranted") == true
            val rewardToGrant = MissionRewardPolicy.stage1RewardToGrant(
                missionPoints = missionPoints,
                isNearEnough = isNearEnough,
                alreadyGranted = alreadyRewarded
            )
            transaction.update(
                userMissionRef,
                mapOf(
                    "locationVerified" to isNearEnough,
                    "verifiedLatitude" to latitude,
                    "verifiedLongitude" to longitude,
                    "distanceToTargetMeters" to distanceMeters,
                    "progress" to if (isNearEnough) 0.5f else 0f,
                    "status" to MissionCompletion.STATUS_IN_PROGRESS,
                    "stage1RewardGranted" to (alreadyRewarded || isNearEnough),
                    "stage1RewardPoints" to MissionRewardPolicy.stage1Reward(missionPoints)
                )
            )
            if (rewardToGrant > 0) {
                transaction.set(
                    userRef,
                    mapOf("points" to FieldValue.increment(rewardToGrant.toLong())),
                    SetOptions.merge()
                )
            }
            rewardToGrant
        }.addOnSuccessListener { rewardToGrant ->
            onResult(MissionRepository.LocationVerifyResult(rewardToGrant))
        }.addOnFailureListener(onError)
    }

    override fun uploadPhotoAndComplete(
        missionId: String,
        missionCategory: String,
        userMissionDocId: String,
        uid: String,
        photoBytes: ByteArray,
        missionPoints: Int,
        photoVerifier: PhotoVerifier,
        photoVerificationConfig: PhotoVerificationConfig,
        onResult: (MissionRepository.CompleteResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Supabase Storage 키는 ASCII 일부 문자만 허용한다. missionId 가 한글 제목인 경우가 있어
        // (`InvalidKey` 400) 안전한 문자로 변환하고, 서로 다른 제목이 같은 폴더로 뭉치지 않도록 해시를 앞에 붙인다.
        val missionKey = Integer.toHexString(missionId.hashCode()) +
            "_" + missionId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        val storagePath = "$missionKey/${uid}_${System.currentTimeMillis()}.jpg"
        val userMissionRef = db.collection("user_missions").document(userMissionDocId)
        val userRef = db.collection("users").document(uid)
        val missionRef = db.collection("missions").document(missionId)

        // 모델 추론·업로드는 무거운 호출이므로 백그라운드 스레드에서 수행한다.
        uploadExecutor.execute {
            // 0) 온디바이스 모델로 사진을 1차 판정한다. (업로드 전)
            val decision = PhotoGate.decide(photoBytes, missionCategory, photoVerifier, photoVerificationConfig)
            if (decision is PhotoGate.Decision.Reject) {
                mainHandler.post {
                    onError(
                        MissionRepository.MissionCompleteException(
                            MissionRepository.MissionCompleteException.Stage.VERIFY,
                            reason = decision.reason
                        )
                    )
                }
                return@execute
            }
            val proceed = decision as PhotoGate.Decision.Proceed
            val needsReview = proceed.needsReview

            // 1) Supabase Storage 업로드
            val photoUrl = try {
                SupabaseStorage.upload(storagePath, photoBytes)
            } catch (e: Exception) {
                mainHandler.post {
                    onError(
                        MissionRepository.MissionCompleteException(
                            MissionRepository.MissionCompleteException.Stage.UPLOAD, e
                        )
                    )
                }
                return@execute
            }

            // 2) 사진 업로드 성공 후에만 Firestore 트랜잭션으로 완료 처리 + 2단계 포인트 지급 + 판정 결과 기록.
            //    (runTransaction 의 성공/실패 콜백은 기본적으로 메인 스레드에서 실행된다.)
            db.runTransaction { transaction ->
                    val missionSnapshot = transaction.get(userMissionRef)
                    val status = missionSnapshot.getString("status").orEmpty()
                    val alreadyCompleted = MissionCompletion.isCompleted(status)
                    val alreadyRewarded = missionSnapshot.getBoolean("stage2RewardGranted") == true
                    // 이 콜백은 업로드 성공 후에만 실행되므로 uploadSucceeded = true
                    val outcome = MissionCompletion.resolve(
                        currentStatus = status,
                        missionPoints = missionPoints,
                        stage2AlreadyGranted = alreadyRewarded,
                        uploadSucceeded = true
                    )
                    transaction.update(
                        userMissionRef,
                        mapOf(
                            "status" to outcome.newStatus,
                            "progress" to 1f,
                            "locationVerified" to true,
                            "photoUrl" to photoUrl,
                            "photoStoragePath" to storagePath,
                            "photoVerified" to !needsReview,
                            "photoNeedsReview" to needsReview,
                            "photoVerifyScore" to proceed.matchScore,
                            "photoVerifyLabel" to proceed.topLabel,
                            "photoVerifyModelVersion" to proceed.modelVersion,
                            "photoUploadedAt" to FieldValue.serverTimestamp(),
                            "stage2RewardGranted" to true,
                            "stage2RewardPoints" to MissionRewardPolicy.stage2Reward(missionPoints),
                            "completedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    if (outcome.pointsToGrant > 0) {
                        transaction.set(
                            userRef,
                            mapOf("points" to FieldValue.increment(outcome.pointsToGrant.toLong())),
                            SetOptions.merge()
                        )
                    }
                    // 이 사용자가 처음 완료할 때만 미션 인기도(completionCount) 를 올린다.
                    if (outcome.countTowardPopularity) {
                        transaction.set(
                            missionRef,
                            mapOf("completionCount" to FieldValue.increment(1L)),
                            SetOptions.merge()
                        )
                    }
                    Triple(outcome.pointsToGrant, alreadyCompleted, photoUrl)
            }.addOnSuccessListener { result ->
                onResult(
                    MissionRepository.CompleteResult(
                        rewardGranted = result.first,
                        alreadyCompleted = result.second,
                        photoUrl = result.third,
                        needsReview = needsReview
                    )
                )
            }.addOnFailureListener { e ->
                // 사진은 올라갔지만 완료 처리 실패 → 재시도 가능 (포인트 중복 지급은 트랜잭션이 방지)
                onError(
                    MissionRepository.MissionCompleteException(
                        MissionRepository.MissionCompleteException.Stage.FINALIZE, e
                    )
                )
            }
        }
    }
}

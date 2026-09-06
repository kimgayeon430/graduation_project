package smu.ai.graduation_project.data

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage

/**
 * [MissionRepository] 의 Firebase(Firestore + Storage) 구현.
 * 기존 [MissionPerformScreen] 에 인라인으로 있던 조회/트랜잭션/업로드 로직을 그대로 옮긴 것으로,
 * 읽고 쓰는 컬렉션·필드·값은 리팩터링 전과 동일하다.
 */
class FirebaseMissionRepository : MissionRepository {

    private val db = Firebase.firestore
    private val storage = Firebase.storage

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
        distanceMeters: Float,
        stage1Reward: Int,
        onResult: (MissionRepository.LocationVerifyResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userMissionRef = db.collection("user_missions").document(userMissionDocId)
        val userRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            val userMissionSnapshot = transaction.get(userMissionRef)
            val alreadyRewarded = userMissionSnapshot.getBoolean("stage1RewardGranted") == true
            val rewardToGrant = if (isNearEnough && !alreadyRewarded) stage1Reward else 0
            transaction.update(
                userMissionRef,
                mapOf(
                    "locationVerified" to isNearEnough,
                    "verifiedLatitude" to latitude,
                    "verifiedLongitude" to longitude,
                    "distanceToTargetMeters" to distanceMeters,
                    "progress" to if (isNearEnough) 0.5f else 0f,
                    "status" to "In Progress",
                    "stage1RewardGranted" to (alreadyRewarded || isNearEnough),
                    "stage1RewardPoints" to stage1Reward
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
        userMissionDocId: String,
        uid: String,
        photoUri: Uri,
        stage2Reward: Int,
        onResult: (MissionRepository.CompleteResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val storagePath = "mission_photos/$missionId/${uid}_${System.currentTimeMillis()}.jpg"
        val storageRef = storage.reference.child(storagePath)
        val userMissionRef = db.collection("user_missions").document(userMissionDocId)
        val userRef = db.collection("users").document(uid)

        storageRef.putFile(photoUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: RuntimeException("사진 업로드 실패")
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                // 사진 업로드 성공 후에만 Firestore 트랜잭션으로 완료 처리 + 2단계 포인트 지급
                db.runTransaction { transaction ->
                    val missionSnapshot = transaction.get(userMissionRef)
                    val status = missionSnapshot.getString("status").orEmpty()
                    val alreadyCompleted = status.contains("완료") || status.equals("Completed", true)
                    val alreadyRewarded = missionSnapshot.getBoolean("stage2RewardGranted") == true
                    val rewardToGrant = if (!alreadyRewarded) stage2Reward else 0
                    transaction.update(
                        userMissionRef,
                        mapOf(
                            "status" to "Completed",
                            "progress" to 1f,
                            "locationVerified" to true,
                            "photoUrl" to downloadUri.toString(),
                            "photoStoragePath" to storagePath,
                            "photoVerified" to true,
                            "photoUploadedAt" to FieldValue.serverTimestamp(),
                            "stage2RewardGranted" to true,
                            "stage2RewardPoints" to stage2Reward,
                            "completedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    if (!alreadyCompleted && rewardToGrant > 0) {
                        transaction.set(
                            userRef,
                            mapOf("points" to FieldValue.increment(rewardToGrant.toLong())),
                            SetOptions.merge()
                        )
                    }
                    Triple(rewardToGrant, alreadyCompleted, downloadUri.toString())
                }.addOnSuccessListener { result ->
                    onResult(
                        MissionRepository.CompleteResult(
                            rewardGranted = result.first,
                            alreadyCompleted = result.second,
                            photoUrl = result.third
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
            .addOnFailureListener { e ->
                onError(
                    MissionRepository.MissionCompleteException(
                        MissionRepository.MissionCompleteException.Stage.UPLOAD, e
                    )
                )
            }
    }
}

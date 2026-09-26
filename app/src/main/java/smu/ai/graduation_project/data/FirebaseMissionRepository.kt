package smu.ai.graduation_project.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.domain.MissionCompletion
import smu.ai.graduation_project.domain.MissionRewardPolicy
import smu.ai.graduation_project.domain.PhotoVerificationConfig
import smu.ai.graduation_project.domain.WeekBoundary
import java.util.concurrent.Executors

/** [FirebaseMissionRepository.uploadPhotoAndComplete] 트랜잭션의 반환값. */
private data class CompletionTxResult(
    val pointsToGrant: Int,
    val alreadyCompleted: Boolean,
    val photoUrl: String,
    /** 처음 완료할 때만 값이 있다(재제출/검수 대기는 null). */
    val delta: MissionRewardCounters.Delta?
)

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
                        title = doc.localizedString("title", LanguagePreference.current, "미션"),
                        category = doc.getString("category") ?: "투어",
                        points = doc.getLong("points")?.toInt() ?: 0,
                        location = doc.getGeoPoint("location"),
                        photoEmbeddings = parsePhotoEmbeddings(doc)
                    )
                )
            }
            .addOnFailureListener(onError)
    }

    /**
     * `missions/{id}.photoEmbeddings`(배열, 신규)와 `photoEmbedding`(단일, 레거시)을 합쳐 반환한다.
     * 두 필드가 다 있으면 둘 다 참조로 쓴다(최대 유사도라 손해 볼 게 없다). 6.7.8.
     *
     * Firestore 는 **배열의 배열을 지원하지 않는다**("Nested arrays are not allowed"). 그래서
     * `photoEmbeddings` 는 배열의 원소가 각각 `{"v": [...]}` 맵인 "배열의 맵" 구조로 저장한다
     * (배열→맵→배열은 허용). `ml/embed_missions.py` 가 쓰는 형식과 맞춘다.
     */
    private fun parsePhotoEmbeddings(doc: com.google.firebase.firestore.DocumentSnapshot): List<List<Float>> {
        val many = (doc.get("photoEmbeddings") as? List<*>)
            ?.mapNotNull { row ->
                val vec = (row as? Map<*, *>)?.get("v") as? List<*>
                vec?.mapNotNull { (it as? Number)?.toFloat() }?.takeIf { it.isNotEmpty() }
            }
            .orEmpty()
        val single = (doc.get("photoEmbedding") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toFloat() }
            ?.takeIf { it.isNotEmpty() }
        return if (single != null) many + listOf(single) else many
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
            val updates = mutableMapOf<String, Any>(
                "locationVerified" to isNearEnough,
                "verifiedLatitude" to latitude,
                "verifiedLongitude" to longitude,
                "distanceToTargetMeters" to distanceMeters,
                "progress" to if (isNearEnough) 0.5f else 0f,
                "status" to MissionCompletion.STATUS_IN_PROGRESS,
                "stage1RewardGranted" to (alreadyRewarded || isNearEnough),
                "stage1RewardPoints" to MissionRewardPolicy.stage1Reward(missionPoints)
            )
            // 포인트 내역(마이페이지)에서 1단계 보상 시점을 보여주기 위해 지급될 때만 기록한다.
            if (rewardToGrant > 0) {
                updates["stage1VerifiedAt"] = FieldValue.serverTimestamp()
            }
            transaction.update(userMissionRef, updates)
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
        referenceEmbeddings: List<FloatArray>,
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
            //    이 자체가 예기치 않게 실패하면(REJECT 판정이 아니라 진짜 오류) UPLOAD/FINALIZE 와
            //    구분되는 ANALYZE 단계로 보고한다 — 안 그러면 콜백이 아예 안 불려 화면이 멈춘다.
            val decision = try {
                PhotoGate.decide(
                    photoBytes, missionCategory, photoVerifier, photoVerificationConfig, referenceEmbeddings
                )
            } catch (e: Exception) {
                Log.e("PhotoVerify", "ANALYZE 단계 실패: mission=$missionId", e)
                mainHandler.post {
                    onError(
                        MissionRepository.MissionCompleteException(
                            MissionRepository.MissionCompleteException.Stage.ANALYZE, e
                        )
                    )
                }
                return@execute
            }
            // 유사도 임계값 보정용 진단 로그. REJECT 는 Firestore 에 아무것도 안 남기므로
            // 여기서만 관측 가능하다(보고서 6.7.6). `adb logcat -s PhotoVerify:*`
            Log.i(
                "PhotoVerify",
                "mission=$missionId cat=$missionCategory ref=${if (referenceEmbeddings.isNotEmpty()) "y(${referenceEmbeddings.size}x${referenceEmbeddings[0].size})" else "n"} " +
                    "match=${"%.3f".format(decision.matchScore)} invalid=${"%.3f".format(decision.invalidScore)} " +
                    "sim=${decision.similarity?.let { "%.3f".format(it) } ?: "null"} " +
                    "-> ${decision::class.simpleName}${if (decision is PhotoGate.Decision.Proceed && decision.needsReview) "(review)" else ""}"
            )
            if (decision is PhotoGate.Decision.Reject) {
                mainHandler.post {
                    onError(
                        MissionRepository.MissionCompleteException(
                            MissionRepository.MissionCompleteException.Stage.VERIFY,
                            reason = decision.reason,
                            topLabel = decision.topLabel,
                            matchScore = decision.matchScore,
                            invalidScore = decision.invalidScore,
                            similarity = decision.similarity,
                            rejectReasonCode = decision.reasonCode
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
                Log.e("PhotoVerify", "UPLOAD 단계 실패: mission=$missionId path=$storagePath", e)
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
                    // 이 콜백은 업로드 성공 후에만 실행되므로 uploadSucceeded = true.
                    // needsReview 면 관리자가 승인하기 전까지 Completed 전환·포인트 지급을 보류한다
                    // (AdminPhotoReviewScreen.approve() 가 MissionCompletion.resolveApproval() 로 마무리).
                    val outcome = MissionCompletion.resolve(
                        currentStatus = status,
                        missionPoints = missionPoints,
                        stage2AlreadyGranted = alreadyRewarded,
                        uploadSucceeded = true,
                        needsReview = needsReview
                    )
                    // Firestore 트랜잭션은 모든 읽기가 모든 쓰기보다 먼저 실행돼야 하므로
                    // (안 그러면 트랜잭션 자체가 실패) 다른 문서에 쓰기 전에 이 읽기부터 끝낸다.
                    val rewardState = if (outcome.countTowardPopularity) {
                        MissionRewardCounters.read(transaction, userRef)
                    } else null
                    transaction.update(
                        userMissionRef,
                        mapOf(
                            "status" to outcome.newStatus,
                            "progress" to if (needsReview) 0.9f else 1f,
                            "locationVerified" to true,
                            "photoUrl" to photoUrl,
                            "photoStoragePath" to storagePath,
                            "photoVerified" to !needsReview,
                            "photoNeedsReview" to needsReview,
                            "photoVerifyScore" to proceed.matchScore,
                            "photoVerifyLabel" to proceed.topLabel,
                            "photoVerifyModelVersion" to proceed.modelVersion,
                            "photoVerifySimilarity" to (proceed.similarity ?: FieldValue.delete()),
                            "photoUploadedAt" to FieldValue.serverTimestamp(),
                            "stage2RewardGranted" to outcome.markCompleted,
                            "stage2RewardPoints" to MissionRewardPolicy.stage2Reward(missionPoints)
                        ) + if (outcome.markCompleted) {
                            mapOf("completedAt" to FieldValue.serverTimestamp())
                        } else {
                            emptyMap()
                        }
                    )
                    // 이 사용자가 처음 완료할 때만: 포인트 지급 + 인기도(completionCount) + 배지/레벨용
                    // 누적 카운터를 같은 트랜잭션 안에서 원자적으로 올린다. 재제출/검수 대기는 여기 안 온다.
                    val delta = if (outcome.countTowardPopularity) {
                        transaction.set(
                            missionRef,
                            mapOf("completionCount" to FieldValue.increment(1L)),
                            SetOptions.merge()
                        )
                        MissionRewardCounters.applyCompletion(
                            transaction, userRef, rewardState!!, missionCategory, outcome.pointsToGrant
                        )
                    } else null
                    CompletionTxResult(outcome.pointsToGrant, alreadyCompleted, photoUrl, delta)
            }.addOnSuccessListener { result ->
                onResult(
                    MissionRepository.CompleteResult(
                        rewardGranted = result.pointsToGrant,
                        alreadyCompleted = result.alreadyCompleted,
                        photoUrl = result.photoUrl,
                        needsReview = needsReview,
                        matchScore = proceed.matchScore,
                        topLabel = proceed.topLabel,
                        modelVersion = proceed.modelVersion,
                        similarity = proceed.similarity,
                        totalPointsAfter = result.delta?.totalPointsAfter ?: 0,
                        weeklyCompletedAfter = result.delta?.weeklyCompletedAfter ?: 0,
                        newlyUnlockedBadges = result.delta?.newlyUnlockedBadges ?: emptyList()
                    )
                )
            }.addOnFailureListener { e ->
                // 사진은 올라갔지만 완료 처리 실패 → 재시도 가능 (포인트 중복 지급은 트랜잭션이 방지)
                Log.e("PhotoVerify", "FINALIZE 단계 실패: mission=$missionId docId=$userMissionDocId", e)
                onError(
                    MissionRepository.MissionCompleteException(
                        MissionRepository.MissionCompleteException.Stage.FINALIZE, e
                    )
                )
            }
        }
    }

    override fun countMissionsCompletedThisWeek(uid: String, onResult: (Int) -> Unit) {
        db.collection("user_missions")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val startOfWeek = WeekBoundary.startOfThisWeekMillis()
                val count = snapshot.documents.count { doc ->
                    val status = doc.getString("status").orEmpty()
                    val completedAt = doc.getTimestamp("completedAt")
                    MissionCompletion.isCompleted(status) &&
                        completedAt != null &&
                        completedAt.toDate().time >= startOfWeek
                }
                onResult(count)
            }
            .addOnFailureListener { onResult(0) }
    }
}

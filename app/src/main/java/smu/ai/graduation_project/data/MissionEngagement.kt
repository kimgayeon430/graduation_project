package smu.ai.graduation_project.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * 미션 좋아요/찜 토글. `mission_likes`/`mission_bookmarks` 문서 ID를 `"${uid}_${missionId}"`로 고정해
 * 중복 반응을 구조적으로 막는다(존재하면 이미 좋아요/찜한 것, 삭제가 곧 취소).
 *
 * 문서 생성/삭제와 `missions.{likeCount|bookmarkCount}` 증감을 하나의 배치로 묶어, 카운터가
 * 반응 문서 존재 여부와 항상 같이 움직이게 한다. (`firestore.rules`의 `onlyChanged(['likeCount', ...])`가
 * 이 증감 쓰기를 허용한다 — 기존 `completionCount`와 같은 신뢰 모델.)
 */
object MissionEngagement {

    private fun docId(uid: String, missionId: String) = "${uid}_$missionId"

    fun setLiked(
        db: FirebaseFirestore,
        uid: String,
        missionId: String,
        liked: Boolean,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) = toggle(db, "mission_likes", "likeCount", uid, missionId, liked, onComplete, onError)

    fun setBookmarked(
        db: FirebaseFirestore,
        uid: String,
        missionId: String,
        bookmarked: Boolean,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) = toggle(db, "mission_bookmarks", "bookmarkCount", uid, missionId, bookmarked, onComplete, onError)

    private fun toggle(
        db: FirebaseFirestore,
        collection: String,
        counterField: String,
        uid: String,
        missionId: String,
        active: Boolean,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val engagementRef = db.collection(collection).document(docId(uid, missionId))
        val missionRef = db.collection("missions").document(missionId)
        val batch = db.batch()
        if (active) {
            batch.set(engagementRef, mapOf("userId" to uid, "missionId" to missionId, "createdAt" to FieldValue.serverTimestamp()))
            batch.set(missionRef, mapOf(counterField to FieldValue.increment(1L)), SetOptions.merge())
        } else {
            batch.delete(engagementRef)
            batch.set(missionRef, mapOf(counterField to FieldValue.increment(-1L)), SetOptions.merge())
        }
        batch.commit().addOnSuccessListener { onComplete() }.addOnFailureListener(onError)
    }
}

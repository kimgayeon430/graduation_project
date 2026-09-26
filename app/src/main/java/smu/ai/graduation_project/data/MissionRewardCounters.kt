package smu.ai.graduation_project.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import smu.ai.graduation_project.domain.BadgeId
import smu.ai.graduation_project.domain.BadgeUnlockEvaluator
import java.util.Calendar

/**
 * 미션이 **처음으로** 완료될 때(= `MissionCompletion.Outcome.countTowardPopularity == true`)만
 * 호출한다. `users/{uid}` 문서에 누적/카테고리별/주간 완료 카운터를 원자적으로 올리고, 그 결과로
 * 새로 조건을 충족한 배지가 있으면 같은 트랜잭션 안에서 함께 기록한다.
 *
 * 기존 스키마를 깨지 않도록 전부 optional 필드로 추가한다 — 없던 사용자는 0/빈 값으로 취급된다:
 * `completedMissionsTotal`(Long), `completedByCategory`(Map<String,Long>),
 * `completedByWeek`(Map<String,Long>), `badges`(List<Map<String,Any?>>).
 *
 * Firestore 트랜잭션은 쿼리를 지원하지 않아(문서 단건 get 만 가능) "이번 주/이 카테고리 완료 수"를
 * 매번 `user_missions` 쿼리로 셀 수 없다 — 그래서 `users` 문서에 카운터를 얹어 두고 여기서
 * `FieldValue.increment` 로 원자적으로 올리는 방식을 택했다(완료 트랜잭션을 깨지 않는 선에서
 * 배지 중복 지급을 막을 수 있는 현실적인 방법).
 */
object MissionRewardCounters {

    private val KNOWN_CATEGORIES = setOf("투어", "맛집", "체험", "쇼핑")

    data class Delta(
        val totalPointsAfter: Int,
        val weeklyCompletedAfter: Int,
        val newlyUnlockedBadges: List<BadgeId>
    )

    fun applyCompletion(
        transaction: Transaction,
        userRef: DocumentReference,
        missionCategory: String,
        pointsToGrant: Int
    ): Delta {
        val snapshot = transaction.get(userRef)
        val pointsBefore = snapshot.getLong("points") ?: 0L
        val totalBefore = snapshot.getLong("completedMissionsTotal") ?: 0L
        val weekKey = currentWeekKey()

        @Suppress("UNCHECKED_CAST")
        val byCategory = snapshot.get("completedByCategory") as? Map<String, Number> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val byWeek = snapshot.get("completedByWeek") as? Map<String, Number> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val existingBadges = snapshot.get("badges") as? List<Map<String, Any?>> ?: emptyList()
        val existingBadgeIds = existingBadges.mapNotNull { it["badgeId"] as? String }

        val categoryKnown = missionCategory in KNOWN_CATEGORIES
        val categoryBefore = byCategory[missionCategory]?.toInt() ?: 0
        val categoryAfter = if (categoryKnown) categoryBefore + 1 else categoryBefore
        val weekBefore = byWeek[weekKey]?.toInt() ?: 0
        val weekAfter = weekBefore + 1
        val totalAfter = totalBefore + 1

        val newlyUnlocked = BadgeUnlockEvaluator.evaluate(
            totalBefore = totalBefore.toInt(),
            totalAfter = totalAfter.toInt(),
            weeklyBefore = weekBefore,
            weeklyAfter = weekAfter,
            categoryBefore = categoryBefore,
            categoryAfter = categoryAfter,
            categoryConditionApplicable = categoryKnown
        ).filter { it.id !in existingBadgeIds }

        val newBadgeEntries = newlyUnlocked.map { badge ->
            mapOf(
                "badgeId" to badge.id,
                "unlockedAt" to Timestamp.now(),
                "relatedCategory" to if (badge == BadgeId.TASTE_DISCOVERY) missionCategory else null,
                "relatedWeek" to if (badge == BadgeId.WEEKLY_EXPLORER) weekKey else null,
                "isNew" to true
            )
        }

        val updates = mutableMapOf<String, Any>(
            "points" to FieldValue.increment(pointsToGrant.toLong()),
            "completedMissionsTotal" to FieldValue.increment(1L),
            "completedByWeek" to mapOf(weekKey to FieldValue.increment(1L))
        )
        if (categoryKnown) {
            updates["completedByCategory"] = mapOf(missionCategory to FieldValue.increment(1L))
        }
        if (newBadgeEntries.isNotEmpty()) {
            updates["badges"] = existingBadges + newBadgeEntries
        }
        transaction.set(userRef, updates, SetOptions.merge())

        return Delta(
            totalPointsAfter = (pointsBefore + pointsToGrant).toInt(),
            weeklyCompletedAfter = weekAfter,
            newlyUnlockedBadges = newlyUnlocked
        )
    }

    /**
     * 이번 주 월요일 0시(기기 로컬 시각) epoch millis 를 키로 쓴다 — 연도 경계의 ISO 주차 계산
     * 같은 미묘함 없이, [FirebaseMissionRepository.countMissionsCompletedThisWeek] 가 쓰는 "이번 주"
     * 정의와 정확히 같은 경계를 공유한다.
     */
    private fun currentWeekKey(): String {
        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis.toString()
    }
}

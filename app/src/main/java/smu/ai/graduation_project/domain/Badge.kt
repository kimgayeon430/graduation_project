package smu.ai.graduation_project.domain

/** 배지 3종. `id` 는 Firestore `users.badges[].badgeId` 에 저장되는 안정적인 문자열 키다. */
enum class BadgeId(val id: String) {
    /** 누적 완료 미션 수가 처음 1개 이상. */
    FIRST_STEP("first_step"),
    /** 동일한 주(월요일 0시~)에 완료한 미션 수가 처음 5개 이상. */
    WEEKLY_EXPLORER("weekly_explorer"),
    /** 같은 카테고리의 완료 미션 수가 처음 3개 이상. */
    TASTE_DISCOVERY("taste_discovery");

    companion object {
        fun fromId(id: String): BadgeId? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 배지 unlock 조건 판정. 순수 Kotlin — before/after 카운터 쌍을 받아 이번에 "처음으로" 문턱을
 * 넘은 배지만 돌려준다(트랜잭션 안에서 원자적으로 올린 카운터와 함께 쓰도록 설계됐다).
 *
 * 호출 쪽(예: [smu.ai.graduation_project.data.MissionRewardCounters])에서 이미 획득한 배지 id 는
 * 한 번 더 걸러내는 게 안전하다 — 이 함수 자체는 "이번 완료로 문턱을 넘었는가"만 본다.
 */
object BadgeUnlockEvaluator {

    const val FIRST_STEP_THRESHOLD = 1
    const val WEEKLY_EXPLORER_THRESHOLD = 5
    const val TASTE_DISCOVERY_THRESHOLD = 3

    fun evaluate(
        totalBefore: Int,
        totalAfter: Int,
        weeklyBefore: Int,
        weeklyAfter: Int,
        categoryBefore: Int,
        categoryAfter: Int,
        /** 미션 카테고리를 모르는 경우(예: 삭제된 미션) false 로 넘기면 취향 발견 배지는 평가하지 않는다. */
        categoryConditionApplicable: Boolean = true
    ): List<BadgeId> {
        val unlocked = mutableListOf<BadgeId>()
        if (totalBefore < FIRST_STEP_THRESHOLD && totalAfter >= FIRST_STEP_THRESHOLD) {
            unlocked += BadgeId.FIRST_STEP
        }
        if (weeklyBefore < WEEKLY_EXPLORER_THRESHOLD && weeklyAfter >= WEEKLY_EXPLORER_THRESHOLD) {
            unlocked += BadgeId.WEEKLY_EXPLORER
        }
        if (categoryConditionApplicable &&
            categoryBefore < TASTE_DISCOVERY_THRESHOLD &&
            categoryAfter >= TASTE_DISCOVERY_THRESHOLD
        ) {
            unlocked += BadgeId.TASTE_DISCOVERY
        }
        return unlocked
    }
}

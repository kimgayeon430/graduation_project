package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 배지 3종 unlock 조건 판정 (요구사항 5). */
class BadgeUnlockEvaluatorTest {

    private fun evaluate(
        totalBefore: Int = 5, totalAfter: Int = 5,
        weeklyBefore: Int = 0, weeklyAfter: Int = 0,
        categoryBefore: Int = 0, categoryAfter: Int = 0,
        categoryConditionApplicable: Boolean = true
    ) = BadgeUnlockEvaluator.evaluate(
        totalBefore, totalAfter, weeklyBefore, weeklyAfter, categoryBefore, categoryAfter, categoryConditionApplicable
    )

    // 1. 첫 발자국: 누적 완료 수가 처음 1개 이상이 된 순간에만
    @Test
    fun firstStepUnlocksOnlyWhenCrossingOneForTheFirstTime() {
        assertEquals(listOf(BadgeId.FIRST_STEP), evaluate(totalBefore = 0, totalAfter = 1))
        assertTrue(evaluate(totalBefore = 1, totalAfter = 2).isEmpty())
        assertTrue(evaluate(totalBefore = 0, totalAfter = 0).isEmpty())
    }

    // 2. 주간 탐험가: 이번 주 완료 수가 처음 5개 이상이 된 순간에만
    @Test
    fun weeklyExplorerUnlocksOnlyWhenCrossingFiveForTheFirstTime() {
        assertEquals(listOf(BadgeId.WEEKLY_EXPLORER), evaluate(weeklyBefore = 4, weeklyAfter = 5))
        assertTrue(evaluate(weeklyBefore = 5, weeklyAfter = 6).isEmpty())
        assertTrue(evaluate(weeklyBefore = 0, weeklyAfter = 3).isEmpty())
    }

    // 3. 취향 발견: 같은 카테고리 완료 수가 처음 3개 이상이 된 순간에만
    @Test
    fun tasteDiscoveryUnlocksOnlyWhenCrossingThreeForTheFirstTime() {
        assertEquals(listOf(BadgeId.TASTE_DISCOVERY), evaluate(categoryBefore = 2, categoryAfter = 3))
        assertTrue(evaluate(categoryBefore = 3, categoryAfter = 4).isEmpty())
    }

    // 미션이 삭제되는 등 카테고리를 모르면(categoryConditionApplicable = false) 취향 발견은 평가하지 않는다.
    @Test
    fun tasteDiscoveryIsSkippedWhenCategoryUnknown() {
        assertTrue(
            evaluate(categoryBefore = 2, categoryAfter = 3, categoryConditionApplicable = false).isEmpty()
        )
    }

    // 여러 배지를 한 번의 완료로 동시에 얻을 수 있다 (예: 취향 발견 + 주간 탐험가).
    @Test
    fun multipleBadgesCanUnlockAtOnce() {
        val unlocked = evaluate(
            totalBefore = 2, totalAfter = 3,
            weeklyBefore = 4, weeklyAfter = 5,
            categoryBefore = 2, categoryAfter = 3
        )
        assertEquals(setOf(BadgeId.WEEKLY_EXPLORER, BadgeId.TASTE_DISCOVERY), unlocked.toSet())
    }

    @Test
    fun noBadgesWhenNothingCrossesAThreshold() {
        assertTrue(evaluate().isEmpty())
    }
}

package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import smu.ai.graduation_project.model.Mission

/** 미션 추천 기본 점수 계산 규칙. */
class MissionScorerTest {

    private fun mission(category: String, points: Int = 100) =
        Mission(id = "m-$category-$points", title = "미션", category = category, points = points)

    // 명시적 선호 카테고리는 점수와 근거가 모두 붙는다
    @Test
    fun explicitPreferenceAddsScoreAndReason() {
        val context = RecommendationContext(preferredCategories = setOf("맛집"))
        val preferred = MissionScorer.score(mission("맛집"), context)
        val notPreferred = MissionScorer.score(mission("쇼핑"), context)

        assertTrue(preferred.score > notPreferred.score)
        assertTrue(preferred.reasons.any { it.contains("취향") })
        assertFalse(notPreferred.reasons.any { it.contains("취향") })
    }

    // 완료 이력이 많은 카테고리는 암묵적 취향으로 가산된다
    @Test
    fun implicitAffinityFromHistory() {
        val context = RecommendationContext(
            completedCountByCategory = mapOf("맛집" to 3, "투어" to 1)
        )
        val food = MissionScorer.score(mission("맛집"), context)
        val tour = MissionScorer.score(mission("투어"), context)
        val none = MissionScorer.score(mission("쇼핑"), context)

        assertTrue(food.score > tour.score)
        assertTrue(tour.score > none.score)
        assertTrue(food.reasons.contains("자주 하는 유형"))
        assertFalse(none.reasons.contains("자주 하는 유형"))
    }

    // 난이도 적합도: 레벨 기대 포인트에 가까울수록 점수가 높다
    @Test
    fun difficultyFitPeaksNearExpectedPoints() {
        val context = RecommendationContext(userLevel = 2) // 기대 포인트 = 50 + 2*50 = 150
        val onTarget = MissionScorer.score(mission("투어", points = 150), context)
        val farOff = MissionScorer.score(mission("투어", points = 500), context)

        assertTrue(onTarget.score > farOff.score)
        assertTrue(onTarget.reasons.contains("지금 레벨에 적당"))
        assertFalse(farOff.reasons.contains("지금 레벨에 적당"))
    }

    @Test
    fun expectedPointsGrowsWithLevel() {
        assertEquals(100, MissionScorer.expectedPoints(1))
        assertEquals(150, MissionScorer.expectedPoints(2))
        assertEquals(100, MissionScorer.expectedPoints(0)) // 레벨은 최소 1로 취급
    }

    // 같은 입력이면 항상 같은 결과 (설명 가능 · 재현 가능)
    @Test
    fun scoringIsDeterministic() {
        val context = RecommendationContext(
            preferredCategories = setOf("체험"),
            completedCountByCategory = mapOf("체험" to 2),
            userLevel = 3
        )
        val a = MissionScorer.score(mission("체험", 200), context)
        val b = MissionScorer.score(mission("체험", 200), context)
        assertEquals(a.score, b.score, 0.0)
        assertEquals(a.reasons, b.reasons)
    }
}

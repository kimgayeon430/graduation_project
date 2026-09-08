package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import smu.ai.graduation_project.model.Mission

/** 하이브리드(학습 re-ranker) 추천. */
class MissionRecommenderRerankedTest {

    private fun mission(id: String, category: String, points: Int = 100) =
        Mission(id = id, title = id, category = category, points = points)

    private val missions = listOf(
        mission("tour", "투어"),
        mission("food", "맛집"),
        mission("shop", "쇼핑"),
        mission("exp", "체험"),
    )

    @Test
    fun nullModelBehavesLikeRuleBased() {
        val ctx = RecommendationContext(preferredCategories = setOf("맛집"))
        val ruled = MissionRecommender.recommendScored(missions, ctx, emptyList(), limit = 3)
        val reranked = MissionRecommender.recommendReranked(missions, ctx, emptyList(), model = null, limit = 3)
        assertEquals(ruled.map { it.mission.id }, reranked.map { it.mission.id })
    }

    @Test
    fun blendZeroKeepsRuleOrder() {
        val ctx = RecommendationContext(
            preferredCategories = setOf("맛집"),
            completedCountByCategory = mapOf("투어" to 2),
        )
        val model = LearnedReranker.Model(
            weights = doubleArrayOf(-9.0, -9.0, -9.0, -9.0, -9.0), bias = 0.0, blend = 0.0,
        )
        val ruled = MissionRecommender.recommendScored(missions, ctx, emptyList(), limit = 4)
        val reranked = MissionRecommender.recommendReranked(missions, ctx, emptyList(), model, limit = 4)
        // blend=0 이면 학습 가중치가 아무리 이상해도 규칙 순서와 같아야 한다
        assertEquals(ruled.map { it.mission.id }, reranked.map { it.mission.id })
    }

    @Test
    fun learnedSignalCanChangeTopPick() {
        // 규칙상 아무 신호 없음 → 입력 순서(tour 먼저). 학습 모델이 "맛집 명시취향" 을 강하게 선호.
        val ctx = RecommendationContext(preferredCategories = setOf("맛집"))
        val model = LearnedReranker.Model(
            weights = doubleArrayOf(10.0, 0.0, 0.0, 0.0, 0.0), bias = -5.0, blend = 1.0,
        )
        val top = MissionRecommender.recommendReranked(missions, ctx, emptyList(), model, limit = 1)
        assertEquals("food", top.single().mission.id)
    }

    @Test
    fun reasonsFromRulesArePreserved() {
        val ctx = RecommendationContext(preferredCategories = setOf("맛집"))
        val model = LearnedReranker.Model(weights = DoubleArray(5), bias = 0.0, blend = 0.5)
        val reranked = MissionRecommender.recommendReranked(missions, ctx, emptyList(), model, limit = 4)
        val food = reranked.first { it.mission.id == "food" }
        assertEquals(true, food.reasons.any { it.contains("취향") })
    }

    @Test
    fun completedMissionsExcluded() {
        val ctx = RecommendationContext()
        val model = LearnedReranker.Model(weights = DoubleArray(5), bias = 0.0, blend = 0.5)
        val reranked = MissionRecommender.recommendReranked(missions, ctx, listOf("tour", "food"), model, limit = 4)
        assertEquals(setOf("shop", "exp"), reranked.map { it.mission.id }.toSet())
    }
}

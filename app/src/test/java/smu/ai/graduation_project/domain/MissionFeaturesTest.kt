package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import smu.ai.graduation_project.model.Mission

/** 미션 추천 신호(feature) 계산. */
class MissionFeaturesTest {

    private fun mission(id: String, category: String, points: Int = 100) =
        Mission(id = id, title = "미션", category = category, points = points)

    @Test
    fun signalsAreZeroToOne() {
        val ctx = RecommendationContext(
            preferredCategories = setOf("맛집"),
            completedCountByCategory = mapOf("맛집" to 3, "투어" to 1),
            userLevel = 2,
            distanceMetersByMissionId = mapOf("m1" to 2_000.0),
            completionCountByMissionId = mapOf("m1" to 5),
        )
        val s = MissionFeatures.of(mission("m1", "맛집", points = 150), ctx, maxCompletionCount = 10)

        for (v in s.asVector()) {
            assert(v in 0.0..1.0) { "신호가 0..1 범위를 벗어남: $v" }
        }
        assertEquals(5, s.asVector().size)
        assertEquals(MissionFeatures.NAMES.size, s.asVector().size)
    }

    @Test
    fun explicitPrefIsBinary() {
        val ctx = RecommendationContext(preferredCategories = setOf("맛집"))
        assertEquals(1.0, MissionFeatures.of(mission("a", "맛집"), ctx).explicitPref, 0.0)
        assertEquals(0.0, MissionFeatures.of(mission("b", "쇼핑"), ctx).explicitPref, 0.0)
    }

    @Test
    fun proximityIsZeroWhenLocationUnknown() {
        val ctx = RecommendationContext()  // 거리 정보 없음
        assertEquals(0.0, MissionFeatures.of(mission("a", "투어"), ctx).proximity, 0.0)
    }

    @Test
    fun nearerMissionHasHigherProximity() {
        val ctx = RecommendationContext(
            distanceMetersByMissionId = mapOf("near" to 500.0, "far" to 12_000.0)
        )
        val near = MissionFeatures.of(mission("near", "투어"), ctx).proximity
        val far = MissionFeatures.of(mission("far", "투어"), ctx).proximity
        assertEquals(1.0, near, 1e-9)
        assert(far < near)
    }

    @Test
    fun popularityIsRelativeToMax() {
        val ctx = RecommendationContext(completionCountByMissionId = mapOf("m1" to 4))
        assertEquals(0.5, MissionFeatures.of(mission("m1", "투어"), ctx, maxCompletionCount = 8).popularity, 1e-9)
        assertEquals(0.0, MissionFeatures.of(mission("m1", "투어"), ctx, maxCompletionCount = 0).popularity, 0.0)
    }

    @Test
    fun affinityMatchesCompletionRatio() {
        val ctx = RecommendationContext(completedCountByCategory = mapOf("맛집" to 3, "투어" to 1))
        assertEquals(0.75, MissionFeatures.of(mission("a", "맛집"), ctx).implicitAffinity, 1e-9)
    }
}

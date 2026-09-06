package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import smu.ai.graduation_project.model.Mission

/** 점수 기반 추천(recommendScored)의 정렬 · 다양성 · 안정성 규칙. */
class MissionRecommenderScoredTest {

    private fun mission(id: String, category: String, points: Int = 100) =
        Mission(id = id, title = "미션 $id", category = category, points = points)

    // 선호 카테고리 미션이 가장 앞에 온다
    @Test
    fun preferredCategoryRanksFirst() {
        val missions = listOf(
            mission("t1", "투어"),
            mission("f1", "맛집"),
            mission("s1", "쇼핑")
        )
        val result = MissionRecommender.recommendScored(
            missions = missions,
            context = RecommendationContext(preferredCategories = setOf("맛집")),
            completedMissionIds = emptyList(),
            limit = 3
        )
        assertEquals("f1", result.first().mission.id)
        assertTrue(result.first().reasons.any { it.contains("취향") })
    }

    // 이미 완료한 미션은 추천에서 제외된다
    @Test
    fun completedMissionsAreExcluded() {
        val missions = listOf(mission("t1", "투어"), mission("t2", "투어"))
        val result = MissionRecommender.recommendScored(
            missions = missions,
            context = RecommendationContext(preferredCategories = setOf("투어")),
            completedMissionIds = listOf("t1"),
            limit = 3
        )
        assertEquals(1, result.size)
        assertEquals("t2", result.first().mission.id)
    }

    // 다양성 감점: 선호가 한쪽에 쏠려도 목록이 한 카테고리로만 채워지지 않는다
    @Test
    fun diversityPenaltyBreaksUpSingleCategory() {
        val missions = listOf(
            mission("t1", "투어"),
            mission("t2", "투어"),
            mission("t3", "투어"),
            mission("f1", "맛집")
        )
        val result = MissionRecommender.recommendScored(
            missions = missions,
            context = RecommendationContext(preferredCategories = setOf("투어")),
            completedMissionIds = emptyList(),
            limit = 3
        )
        assertEquals(3, result.size)
        assertEquals("투어", result[0].mission.category)
        assertEquals("투어", result[1].mission.category)
        assertEquals("맛집", result[2].mission.category) // 세 번째는 감점으로 다른 카테고리에 밀림
    }

    // 다른 카테고리 대안이 없으면 감점이 있어도 계속 채운다
    @Test
    fun fillsFromSameCategoryWhenNoAlternative() {
        val missions = listOf(
            mission("t1", "투어"),
            mission("t2", "투어"),
            mission("t3", "투어")
        )
        val result = MissionRecommender.recommendScored(
            missions = missions,
            context = RecommendationContext(preferredCategories = setOf("투어")),
            completedMissionIds = emptyList(),
            limit = 3
        )
        assertEquals(3, result.size)
    }

    @Test
    fun respectsLimitAndSkipsBlankIds() {
        val missions = listOf(
            mission("t1", "투어"),
            mission("f1", "맛집"),
            Mission(id = "", title = "x", category = "투어", points = 100)
        )
        val result = MissionRecommender.recommendScored(
            missions = missions,
            context = RecommendationContext(),
            completedMissionIds = emptyList(),
            limit = 2
        )
        assertEquals(2, result.size)
        assertFalse(result.any { it.mission.id.isBlank() })
    }

    // 신호가 전혀 없으면(동점) 입력 순서를 유지한다
    @Test
    fun stableOrderOnTies() {
        val missions = listOf(
            mission("a", "투어"),
            mission("b", "맛집"),
            mission("c", "체험")
        )
        val result = MissionRecommender.recommendScored(
            missions = missions,
            context = RecommendationContext(),
            completedMissionIds = emptyList(),
            limit = 3
        )
        assertEquals(listOf("a", "b", "c"), result.map { it.mission.id })
    }

    @Test
    fun returnsEmptyWhenNoCandidates() {
        assertTrue(
            MissionRecommender.recommendScored(
                missions = emptyList(),
                context = RecommendationContext(),
                completedMissionIds = emptyList(),
                limit = 3
            ).isEmpty()
        )
    }
}

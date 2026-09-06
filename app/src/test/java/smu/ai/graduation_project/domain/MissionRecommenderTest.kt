package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import smu.ai.graduation_project.model.Mission

/** 규칙 기반 미션 추천. */
class MissionRecommenderTest {

    private fun mission(id: String, category: String) =
        Mission(id = id, title = "미션 $id", category = category)

    private val all = listOf(
        mission("t1", "투어"),
        mission("f1", "맛집"),
        mission("e1", "체험"),
        mission("t2", "투어"),
        mission("s1", "쇼핑")
    )

    // 선호 카테고리를 우선 표시
    @Test
    fun preferredCategoriesComeFirst() {
        val result = MissionRecommender.recommend(
            missions = all,
            preferences = listOf("맛집"),
            completedMissionIds = emptyList(),
            limit = 5
        )
        assertEquals("f1", result.first().id)
        assertEquals(all.size, result.size)
    }

    // 이미 완료한 미션은 추천에서 제외
    @Test
    fun completedMissionsAreExcluded() {
        val result = MissionRecommender.recommend(
            missions = all,
            preferences = listOf("투어"),
            completedMissionIds = listOf("t1"),
            limit = 5
        )
        assertFalse(result.any { it.id == "t1" })
        assertEquals("t2", result.first().id)
    }

    // 추천할 미션이 부족하면 다른 카테고리로 채우기
    @Test
    fun fillsFromOtherCategoriesWhenPreferredInsufficient() {
        val result = MissionRecommender.recommend(
            missions = all,
            preferences = listOf("쇼핑"), // 후보는 s1 하나뿐
            completedMissionIds = emptyList(),
            limit = 3
        )
        assertEquals(3, result.size)
        assertEquals("s1", result[0].id) // 선호 카테고리 먼저
        assertTrue(result.drop(1).none { it.category == "쇼핑" }) // 나머지는 다른 카테고리
    }

    // 추천 결과가 없는 경우 (모두 완료 / 미션 없음)
    @Test
    fun returnsEmptyWhenAllCompletedOrNoMissions() {
        assertTrue(
            MissionRecommender.recommend(all, listOf("투어"), all.map { it.id }, 5).isEmpty()
        )
        assertTrue(
            MissionRecommender.recommend(emptyList(), listOf("투어"), emptyList(), 5).isEmpty()
        )
    }

    @Test
    fun respectsLimitAndSkipsBlankIds() {
        val withBlank = all + Mission(id = "", title = "x", category = "투어")
        val result = MissionRecommender.recommend(withBlank, emptyList(), emptyList(), limit = 2)
        assertEquals(2, result.size)
        assertFalse(result.any { it.id.isBlank() })
    }

    @Test
    fun noPreferencesStillExcludesCompletedAndKeepsInputOrder() {
        val result = MissionRecommender.recommend(all, emptyList(), listOf("t1"), 5)
        assertEquals(listOf("f1", "e1", "t2", "s1"), result.map { it.id })
    }
}

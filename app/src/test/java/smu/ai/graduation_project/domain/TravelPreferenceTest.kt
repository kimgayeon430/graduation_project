package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 여행 취향 선택 규칙. */
class TravelPreferenceTest {

    @Test
    fun allHasFourCategoriesInDisplayOrder() {
        assertEquals(listOf("투어", "맛집", "체험", "쇼핑"), TravelPreference.ALL)
    }

    // 최소 1개를 선택해야 완료 가능
    @Test
    fun canCompleteRequiresAtLeastOneSelection() {
        assertFalse(TravelPreference.canComplete(emptyList()))
        assertTrue(TravelPreference.canComplete(listOf("투어")))
        assertTrue(TravelPreference.canComplete(listOf("투어", "맛집")))
    }

    @Test
    fun normalizeKeepsOnlyKnownCategoriesInCanonicalOrder() {
        assertEquals(
            listOf("투어", "맛집", "쇼핑"),
            TravelPreference.normalize(listOf("쇼핑", "투어", "맛집"))
        )
        assertEquals(listOf("체험"), TravelPreference.normalize(listOf("체험", "미지의값")))
        assertEquals(emptyList<String>(), TravelPreference.normalize(listOf("없음")))
    }
}

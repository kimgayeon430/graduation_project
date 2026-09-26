package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 여행 레벨 정책: 누적 포인트 → 레벨/구간 진행률 계산 (요구사항 4). */
class TravelLevelPolicyTest {

    @Test
    fun zeroPointsIsLevel1() {
        val progress = TravelLevelPolicy.progressFor(0)
        assertEquals(TravelLevel.SEEDLING, progress.level)
        assertEquals(1, progress.level.number)
    }

    // 명세 예시: 1,000P 면 Lv.2(500P) 구간에서 50% 진행, 다음 레벨까지 500P
    @Test
    fun exampleAt1000PointsMatchesSpec() {
        val progress = TravelLevelPolicy.progressFor(1000)
        assertEquals(TravelLevel.NEIGHBORHOOD_EXPLORER, progress.level)
        assertEquals(500, progress.levelStartPoints)
        assertEquals(1500, progress.nextLevelPoints)
        assertEquals(0.5f, progress.progressRatio, 0.0001f)
        assertEquals(500, progress.pointsToNextLevel)
    }

    @Test
    fun levelBoundariesAreInclusiveOfMinPoints() {
        assertEquals(TravelLevel.SEEDLING, TravelLevelPolicy.progressFor(499).level)
        assertEquals(TravelLevel.NEIGHBORHOOD_EXPLORER, TravelLevelPolicy.progressFor(500).level)
        assertEquals(TravelLevel.NEIGHBORHOOD_EXPLORER, TravelLevelPolicy.progressFor(1499).level)
        assertEquals(TravelLevel.CITY_TRAVELER, TravelLevelPolicy.progressFor(1500).level)
    }

    @Test
    fun maxLevelHasFullProgressAndNoNextLevel() {
        val progress = TravelLevelPolicy.progressFor(5000)
        assertEquals(TravelLevel.MASTER_TRAVELER, progress.level)
        assertTrue(progress.isMaxLevel)
        assertEquals(1f, progress.progressRatio, 0.0001f)
        assertNull(progress.nextLevelPoints)
        assertNull(progress.pointsToNextLevel)
    }

    @Test
    fun wellBeyondMaxLevelStaysAtMaxLevel() {
        val progress = TravelLevelPolicy.progressFor(999_999)
        assertEquals(TravelLevel.MASTER_TRAVELER, progress.level)
        assertTrue(progress.isMaxLevel)
    }

    @Test
    fun progressResetsAtStartOfEachLevel() {
        val justLeveled = TravelLevelPolicy.progressFor(1500)
        assertFalse(justLeveled.isMaxLevel)
        assertEquals(0f, justLeveled.progressRatio, 0.0001f)
        assertEquals(1500, justLeveled.pointsToNextLevel)
    }
}

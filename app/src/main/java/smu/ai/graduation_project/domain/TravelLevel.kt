package smu.ai.graduation_project.domain

/**
 * 여행 레벨 정책. 누적 포인트에서 계산하며 Firestore 에 별도 필드로 중복 저장하지 않는다
 * (기존 `users.level` 문자열 필드는 가입 시 한 번만 쓰이고 갱신되지 않아 신뢰할 수 없다).
 */
enum class TravelLevel(val number: Int, val minPoints: Int) {
    SEEDLING(1, 0),
    NEIGHBORHOOD_EXPLORER(2, 500),
    CITY_TRAVELER(3, 1500),
    HIDDEN_GEM_COLLECTOR(4, 3000),
    MASTER_TRAVELER(5, 5000);

    companion object {
        private val byMinPointsDesc = entries.sortedByDescending { it.minPoints }

        fun forPoints(points: Int): TravelLevel = byMinPointsDesc.first { points >= it.minPoints }
    }
}

/**
 * 특정 시점의 레벨 진행 상태. 진행률은 전체 5,000P 기준이 아니라 **현재 레벨 구간** 기준이다.
 * 예: 1,000P 면 Lv.2(500P) 구간 안이므로 (1000-500)/(1500-500) = 50%.
 */
data class LevelProgress(
    val level: TravelLevel,
    val points: Int,
    val levelStartPoints: Int,
    /** 최고 레벨이면 null. */
    val nextLevelPoints: Int?,
    /** 0f~1f. 최고 레벨이면 1f. */
    val progressRatio: Float,
    /** 최고 레벨이면 null. */
    val pointsToNextLevel: Int?
) {
    val isMaxLevel: Boolean get() = nextLevelPoints == null
}

object TravelLevelPolicy {

    fun progressFor(points: Int): LevelProgress {
        val level = TravelLevel.forPoints(points)
        val next = TravelLevel.entries.filter { it.minPoints > level.minPoints }.minByOrNull { it.minPoints }
        if (next == null) {
            return LevelProgress(
                level = level,
                points = points,
                levelStartPoints = level.minPoints,
                nextLevelPoints = null,
                progressRatio = 1f,
                pointsToNextLevel = null
            )
        }
        val span = (next.minPoints - level.minPoints).coerceAtLeast(1)
        val progressed = (points - level.minPoints).coerceIn(0, span)
        return LevelProgress(
            level = level,
            points = points,
            levelStartPoints = level.minPoints,
            nextLevelPoints = next.minPoints,
            progressRatio = progressed.toFloat() / span,
            pointsToNextLevel = (next.minPoints - points).coerceAtLeast(0)
        )
    }
}

package smu.ai.graduation_project.domain

/**
 * 추천 점수 계산에 필요한 사용자 맥락.
 *
 * Firebase 스냅샷에서 값만 채워 넣는 순수 데이터 홀더이며, 계산 로직은 갖지 않는다.
 */
data class RecommendationContext(
    /** 회원가입 때 고른 명시적 취향 카테고리. */
    val preferredCategories: Set<String> = emptySet(),
    /** 완료한 미션을 카테고리별로 센 값 (암묵적 취향 신호). */
    val completedCountByCategory: Map<String, Int> = emptyMap(),
    /** 사용자 레벨 (기본 1). 난이도 적합도 계산에 사용. */
    val userLevel: Int = 1
) {
    /** 지금까지 완료한 전체 미션 수. */
    val totalCompleted: Int get() = completedCountByCategory.values.sum()

    /** 해당 카테고리를 완료한 비율 (0.0 ~ 1.0). 완료 이력이 없으면 0. */
    fun affinity(category: String): Double {
        if (totalCompleted <= 0) return 0.0
        return (completedCountByCategory[category] ?: 0).toDouble() / totalCompleted
    }
}

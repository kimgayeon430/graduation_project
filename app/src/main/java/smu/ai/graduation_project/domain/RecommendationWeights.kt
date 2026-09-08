package smu.ai.graduation_project.domain

/**
 * 추천 점수 가중치.
 *
 * 각 신호가 최종 점수에 얼마나 기여하는지 한곳에 모아 두어, 추천 결과를 설명하고
 * 튜닝(오프라인 평가 후 값 조정)할 수 있게 한다.
 */
data class RecommendationWeights(
    /** 미션 카테고리가 사용자의 명시적 선호에 포함될 때 가산치. */
    val explicitPreference: Double = 3.0,
    /** 사용자가 그 카테고리 미션을 완료한 비율에 곱하는 가산치. */
    val implicitAffinity: Double = 2.0,
    /** 미션 난이도(포인트대)가 사용자 레벨에 맞을수록 주는 가산치. */
    val difficultyFit: Double = 1.0,
    /** 미션이 사용자 현재 위치에 가까울수록 주는 가산치. */
    val proximity: Double = 1.5,
    /** 다른 사용자가 많이 완료한 미션에 주는 가산치. */
    val popularity: Double = 1.0,
    /** 지금 시각이 미션 카테고리의 활동 시간대에 맞을수록 주는 가산치. */
    val timeOfDayFit: Double = 1.0,
    /** 추천 목록에 같은 카테고리가 하나 쌓일 때마다 빼는 감점치 (다양성). */
    val diversityPenalty: Double = 2.0
) {
    companion object {
        val DEFAULT = RecommendationWeights()
    }
}

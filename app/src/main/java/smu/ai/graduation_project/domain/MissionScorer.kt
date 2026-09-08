package smu.ai.graduation_project.domain

import smu.ai.graduation_project.model.Mission

/**
 * 미션 1건의 추천 기본 점수를 설명 가능한 규칙으로 계산한다. (학습 모델 없음)
 *
 * 신호 5개는 [MissionFeatures] 에서 계산하고, 여기서는 [RecommendationWeights] 로 가중합 + 근거 문구를 붙인다.
 * 기본 점수 = 명시적 취향 + 암묵적 취향 + 난이도 적합도 + 거리 근접도 + 인기도
 *
 * 다양성 감점은 추천 목록을 만드는 순서에 의존하므로 여기서 계산하지 않고
 * [MissionRecommender] 의 그리디 선택 단계에서 적용한다.
 */
object MissionScorer {

    /** 점수와 함께 "왜 추천됐는지" 근거를 담는다. */
    data class Scored(
        val mission: Mission,
        val score: Double,
        val reasons: List<String>
    )

    /** 난이도 적합도 근거 문구를 붙이는 최소 적합도. */
    private const val DIFFICULTY_REASON_THRESHOLD = 0.6

    /** "가까운 미션" / "인기 미션" 근거를 붙이는 최소 정규화값. */
    private const val SIGNAL_REASON_THRESHOLD = 0.5

    /**
     * 사용자 레벨에 "적당한" 미션 포인트 중심값.
     * 레벨이 오를수록 더 높은 포인트(어려운) 미션을 선호하도록 한다.
     */
    fun expectedPoints(level: Int): Int = 50 + level.coerceAtLeast(1) * 50

    fun score(
        mission: Mission,
        context: RecommendationContext,
        weights: RecommendationWeights = RecommendationWeights.DEFAULT,
        /** 후보 중 최대 완료 횟수. 0 이면 인기도 신호를 쓰지 않는다. */
        maxCompletionCount: Int = 0
    ): Scored {
        val f = MissionFeatures.of(mission, context, maxCompletionCount)
        val reasons = mutableListOf<String>()
        var score = 0.0

        if (f.explicitPref > 0.0) {
            score += weights.explicitPreference
            reasons += "${mission.category} 취향"
        }
        if (f.implicitAffinity > 0.0) {
            score += weights.implicitAffinity * f.implicitAffinity
            reasons += "자주 하는 유형"
        }
        score += weights.difficultyFit * f.difficultyFit
        if (f.difficultyFit >= DIFFICULTY_REASON_THRESHOLD) reasons += "지금 레벨에 적당"
        if (f.proximity > 0.0) {
            score += weights.proximity * f.proximity
            if (f.proximity >= SIGNAL_REASON_THRESHOLD) reasons += "가까운 미션"
        }
        if (f.popularity > 0.0) {
            score += weights.popularity * f.popularity
            if (f.popularity >= SIGNAL_REASON_THRESHOLD) reasons += "인기 미션"
        }

        return Scored(mission, score, reasons)
    }
}

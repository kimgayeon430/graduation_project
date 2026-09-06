package smu.ai.graduation_project.domain

import smu.ai.graduation_project.model.Mission
import kotlin.math.abs

/**
 * 미션 1건의 추천 기본 점수를 설명 가능한 규칙으로 계산한다. (AI 모델 없음)
 *
 * 기본 점수 = 명시적 취향 + 암묵적 취향 + 난이도 적합도
 *  - 명시적 취향: 미션 카테고리가 사용자 선호에 포함되면 가산
 *  - 암묵적 취향: 사용자가 그 카테고리 미션을 완료한 비율만큼 가산
 *  - 난이도 적합도: 미션 포인트대가 사용자 레벨 기대치에 가까울수록 가산
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

    /**
     * 사용자 레벨에 "적당한" 미션 포인트 중심값.
     * 레벨이 오를수록 더 높은 포인트(어려운) 미션을 선호하도록 한다.
     */
    fun expectedPoints(level: Int): Int = 50 + level.coerceAtLeast(1) * 50

    fun score(
        mission: Mission,
        context: RecommendationContext,
        weights: RecommendationWeights = RecommendationWeights.DEFAULT
    ): Scored {
        val reasons = mutableListOf<String>()
        var score = 0.0

        // 1. 명시적 취향
        if (mission.category in context.preferredCategories) {
            score += weights.explicitPreference
            reasons += "${mission.category} 취향"
        }

        // 2. 암묵적 취향 (완료 이력 비율)
        val affinity = context.affinity(mission.category)
        if (affinity > 0.0) {
            score += weights.implicitAffinity * affinity
            reasons += "자주 하는 유형"
        }

        // 3. 난이도 적합도: |미션 포인트 - 기대 포인트| 가 작을수록 1.0 에 가깝다
        val expected = expectedPoints(context.userLevel)
        val fit = (1.0 - abs(mission.points - expected).toDouble() / expected).coerceIn(0.0, 1.0)
        score += weights.difficultyFit * fit
        if (fit >= DIFFICULTY_REASON_THRESHOLD) reasons += "지금 레벨에 적당"

        return Scored(mission, score, reasons)
    }
}

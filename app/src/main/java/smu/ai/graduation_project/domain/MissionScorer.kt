package smu.ai.graduation_project.domain

import smu.ai.graduation_project.model.Mission
import kotlin.math.abs

/**
 * 미션 1건의 추천 기본 점수를 설명 가능한 규칙으로 계산한다. (AI 모델 없음)
 *
 * 기본 점수 = 명시적 취향 + 암묵적 취향 + 난이도 적합도 + 거리 근접도 + 인기도
 *  - 명시적 취향: 미션 카테고리가 사용자 선호에 포함되면 가산
 *  - 암묵적 취향: 사용자가 그 카테고리 미션을 완료한 비율만큼 가산
 *  - 난이도 적합도: 미션 포인트대가 사용자 레벨 기대치에 가까울수록 가산
 *  - 거리 근접도: 미션 목표 지점이 사용자 현재 위치에 가까울수록 가산 (위치를 알 때만)
 *  - 인기도: 다른 사용자의 완료 횟수가 많을수록 가산 (후보 중 최대값 대비 상대값)
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

    /** 이 거리(m) 이내면 근접도 만점. */
    private const val NEAR_METERS = 1_000.0

    /** 이 거리(m) 이상이면 근접도 0. */
    private const val FAR_METERS = 15_000.0

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

        // 4. 거리 근접도 (사용자 위치와 미션 좌표를 모두 알 때만)
        val distance = context.distanceMeters(mission.id)
        if (distance != null) {
            val proximity = ((FAR_METERS - distance) / (FAR_METERS - NEAR_METERS)).coerceIn(0.0, 1.0)
            if (proximity > 0.0) {
                score += weights.proximity * proximity
                if (proximity >= SIGNAL_REASON_THRESHOLD) reasons += "가까운 미션"
            }
        }

        // 5. 인기도 (후보 중 최대 완료 횟수 대비 상대값)
        if (maxCompletionCount > 0) {
            val popularity = context.completionCount(mission.id).toDouble() / maxCompletionCount
            if (popularity > 0.0) {
                score += weights.popularity * popularity
                if (popularity >= SIGNAL_REASON_THRESHOLD) reasons += "인기 미션"
            }
        }

        return Scored(mission, score, reasons)
    }
}

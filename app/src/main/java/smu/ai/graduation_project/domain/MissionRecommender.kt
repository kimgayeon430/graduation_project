package smu.ai.graduation_project.domain

import smu.ai.graduation_project.model.Mission

/**
 * 규칙 기반(설명 가능한) 미션 추천. AI 모델을 쓰지 않고 현재 데이터만으로 계산한다.
 */
object MissionRecommender {

    /**
     * 단순 버전: 선호 카테고리 미션을 앞에, 나머지를 뒤에 두고 [limit] 개를 반환한다.
     *
     * 규칙:
     *  1. id 가 비었거나 이미 완료한 미션은 후보에서 제외한다.
     *  2. 사용자 선호 카테고리에 속한 미션을 먼저 배치한다.
     *  3. 자리가 남으면 나머지 카테고리 미션으로 채운다.
     *  4. 같은 그룹 안에서는 입력 순서를 유지한다(결과가 안정적).
     */
    fun recommend(
        missions: List<Mission>,
        preferences: Collection<String>,
        completedMissionIds: Collection<String>,
        limit: Int = 5
    ): List<Mission> {
        if (limit <= 0) return emptyList()

        val completed = completedMissionIds.toHashSet()
        val preferred = preferences.toHashSet()

        val candidates = missions.filter { it.id.isNotBlank() && it.id !in completed }
        val (inPreferred, others) = candidates.partition { it.category in preferred }

        return (inPreferred + others).take(limit)
    }

    /**
     * 점수 기반 버전: [MissionScorer] 로 후보마다 기본 점수를 매기고,
     * 같은 카테고리가 쌓일수록 감점하며 [limit] 개를 그리디로 고른다.
     *
     * 규칙:
     *  1. id 가 비었거나 이미 완료한 미션은 후보에서 제외한다.
     *  2. 각 후보의 기본 점수를 계산한다(명시적 취향 · 암묵적 취향 · 난이도 적합도 · 거리 · 인기도).
     *  3. "현재 점수 = 기본 점수 − 다양성 감점 × 이미 뽑힌 같은 카테고리 수" 가
     *     가장 높은 후보를 한 개씩 뽑는다.
     *  4. 점수가 같으면 입력 순서가 앞선 후보를 뽑는다(결과가 안정적).
     */
    fun recommendScored(
        missions: List<Mission>,
        context: RecommendationContext,
        completedMissionIds: Collection<String>,
        limit: Int = 3,
        weights: RecommendationWeights = RecommendationWeights.DEFAULT
    ): List<MissionScorer.Scored> {
        if (limit <= 0) return emptyList()

        val completed = completedMissionIds.toHashSet()
        val candidates = missions.filter { it.id.isNotBlank() && it.id !in completed }
        if (candidates.isEmpty()) return emptyList()

        val maxCompletionCount = candidates.maxOfOrNull { context.completionCount(it.id) } ?: 0
        val remaining = candidates
            .map { MissionScorer.score(it, context, weights, maxCompletionCount) }
            .toMutableList()
        val pickedCategoryCount = HashMap<String, Int>()
        val picked = ArrayList<MissionScorer.Scored>(minOf(limit, remaining.size))

        while (picked.size < limit && remaining.isNotEmpty()) {
            fun currentScore(s: MissionScorer.Scored): Double =
                s.score - weights.diversityPenalty * (pickedCategoryCount[s.mission.category] ?: 0)

            // maxByOrNull 은 최댓값이 여럿이면 앞선 원소를 반환 → 입력 순서 기준으로 안정적
            val best = remaining.maxByOrNull(::currentScore) ?: break
            val penalty = weights.diversityPenalty * (pickedCategoryCount[best.mission.category] ?: 0)

            picked += best.copy(score = best.score - penalty)
            pickedCategoryCount[best.mission.category] = (pickedCategoryCount[best.mission.category] ?: 0) + 1
            remaining.remove(best)
        }

        return picked
    }
}

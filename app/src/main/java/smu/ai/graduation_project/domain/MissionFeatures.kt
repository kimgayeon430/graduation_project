package smu.ai.graduation_project.domain

import smu.ai.graduation_project.model.Mission
import kotlin.math.abs

/**
 * 미션 1건의 추천 신호(feature)를 계산한다. 순수 Kotlin, 모델 비의존.
 *
 * [MissionScorer] 의 규칙 기반 점수와 [LearnedReranker] 의 학습된 점수가 **같은 신호**를
 * 쓰도록 계산을 여기 한곳에 모은다. 각 신호는 0~1 로 정규화된다.
 *
 * `NAMES` 의 순서는 학습 데이터·모델 가중치·추론이 모두 공유하므로 바꾸지 말 것.
 */
object MissionFeatures {

    /** feature 벡터 순서. `ml/reco/` 학습 스크립트와 일치해야 한다. */
    val NAMES: List<String> = listOf(
        "explicit_pref", "implicit_affinity", "difficulty_fit", "proximity", "popularity",
        "time_of_day_fit",
    )

    /** 근접도 만점 거리(m). */
    private const val NEAR_METERS = 1_000.0

    /** 근접도 0 거리(m). */
    private const val FAR_METERS = 15_000.0

    /**
     * 카테고리별 "하기 좋은" 시간대(0~23시, 양끝 포함). `ml/reco/features.py` 와 동일하게 유지.
     * 표에 없는 카테고리는 시간대 신호를 쓰지 않는다(0).
     */
    private val TIME_WINDOWS: Map<String, List<IntRange>> = mapOf(
        "맛집" to listOf(11..14, 17..21),   // 점심·저녁
        "투어" to listOf(9..17),            // 낮
        "체험" to listOf(10..18),           // 낮
        "쇼핑" to listOf(13..21),           // 오후~저녁
    )

    /** 활동 시간대에서 이만큼(시간) 벗어나면 시간대 적합도가 0 이 된다. */
    private const val TIME_DECAY_HOURS = 4.0

    /**
     * 현재 시각([hour], 0~23)이 미션 [category] 의 활동 시간대에 얼마나 맞는지 (0~1).
     * 시간대 안이면 1, 밖이면 가장 가까운 경계까지의 시간 차로 선형 감소(24시 순환).
     */
    fun timeOfDayFit(category: String, hour: Int): Double {
        val windows = TIME_WINDOWS[category] ?: return 0.0
        if (windows.any { hour in it }) return 1.0
        val nearestEdgeDist = windows
            .flatMap { listOf(it.first, it.last) }
            .minOf { edge -> abs(hour - edge).let { d -> minOf(d, 24 - d) } }
        return (1.0 - nearestEdgeDist / TIME_DECAY_HOURS).coerceIn(0.0, 1.0)
    }

    data class Signals(
        /** 미션 카테고리가 명시적 취향에 포함되면 1, 아니면 0. */
        val explicitPref: Double,
        /** 그 카테고리를 완료한 비율 (0~1). */
        val implicitAffinity: Double,
        /** 미션 포인트대가 사용자 레벨 기대치에 얼마나 맞는지 (0~1). */
        val difficultyFit: Double,
        /** 사용자 현재 위치로부터의 근접도 (0~1). 위치를 모르면 0. */
        val proximity: Double,
        /** 다른 사용자 완료 횟수 기반 인기도 (0~1). 신호 없으면 0. */
        val popularity: Double,
        /** 현재 시각이 미션 카테고리의 활동 시간대에 맞는 정도 (0~1). 시각을 모르면 0. */
        val timeOfDayFit: Double,
    ) {
        fun asVector(): DoubleArray =
            doubleArrayOf(explicitPref, implicitAffinity, difficultyFit, proximity, popularity, timeOfDayFit)
    }

    /**
     * @param maxCompletionCount 후보 중 최대 완료 횟수. 0 이면 인기도 신호를 쓰지 않는다.
     */
    fun of(
        mission: Mission,
        context: RecommendationContext,
        maxCompletionCount: Int = 0,
    ): Signals {
        val explicit = if (mission.category in context.preferredCategories) 1.0 else 0.0

        val affinity = context.affinity(mission.category).coerceIn(0.0, 1.0)

        val expected = MissionScorer.expectedPoints(context.userLevel)
        val fit = (1.0 - abs(mission.points - expected).toDouble() / expected).coerceIn(0.0, 1.0)

        val distance = context.distanceMeters(mission.id)
        val proximity = if (distance == null) 0.0
        else ((FAR_METERS - distance) / (FAR_METERS - NEAR_METERS)).coerceIn(0.0, 1.0)

        val popularity = if (maxCompletionCount <= 0) 0.0
        else (context.completionCount(mission.id).toDouble() / maxCompletionCount).coerceIn(0.0, 1.0)

        val timeFit = context.currentHour?.let { timeOfDayFit(mission.category, it) } ?: 0.0

        return Signals(explicit, affinity, fit, proximity, popularity, timeFit)
    }
}

package smu.ai.graduation_project.domain

import smu.ai.graduation_project.model.Mission

/**
 * 규칙 기반(설명 가능한) 미션 추천. AI 모델을 쓰지 않고 현재 데이터만으로 계산한다.
 *
 * 규칙:
 *  1. id 가 비었거나 이미 완료한 미션은 후보에서 제외한다.
 *  2. 사용자 선호 카테고리에 속한 미션을 먼저 배치한다.
 *  3. 자리가 남으면 나머지 카테고리 미션으로 채운다.
 *  4. 같은 그룹 안에서는 입력 순서를 유지한다(결과가 안정적).
 *
 * 선호 카테고리가 비어 있으면 완료 제외만 적용한 상태로 앞에서부터 [limit] 개를 반환한다.
 */
object MissionRecommender {

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
}

package smu.ai.graduation_project.domain

/**
 * 미션 포인트 보상 계산 규칙. 순수 Kotlin (Firebase 비의존).
 *
 * - 1단계(위치 인증) 보상: 미션 포인트, 단 최대 [MAX_STAGE1_REWARD].
 * - 2단계(사진 인증) 보상: 남은 포인트(= 전체 - 1단계).
 */
object MissionRewardPolicy {

    const val MAX_STAGE1_REWARD = 100

    fun stage1Reward(missionPoints: Int): Int =
        minOf(MAX_STAGE1_REWARD, missionPoints.coerceAtLeast(0))

    fun stage2Reward(missionPoints: Int): Int =
        (missionPoints.coerceAtLeast(0) - stage1Reward(missionPoints)).coerceAtLeast(0)

    /**
     * 1단계에서 실제로 지급할 포인트.
     * 반경 밖이거나([isNearEnough] == false) 이미 지급됐으면([alreadyGranted]) 0.
     */
    fun stage1RewardToGrant(
        missionPoints: Int,
        isNearEnough: Boolean,
        alreadyGranted: Boolean
    ): Int = if (isNearEnough && !alreadyGranted) stage1Reward(missionPoints) else 0

    /**
     * 2단계에서 실제로 지급할 포인트.
     * 이미 완료됐거나([alreadyCompleted]) 이미 지급됐으면([alreadyGranted]) 0.
     */
    fun stage2RewardToGrant(
        missionPoints: Int,
        alreadyCompleted: Boolean,
        alreadyGranted: Boolean
    ): Int = if (!alreadyCompleted && !alreadyGranted) stage2Reward(missionPoints) else 0
}

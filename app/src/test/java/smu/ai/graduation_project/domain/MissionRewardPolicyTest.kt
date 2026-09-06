package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 포인트 보상 계산 규칙 (필수 테스트 3, 4, 5). */
class MissionRewardPolicyTest {

    // 3. 미션 포인트가 100 이하일 때 보상 계산 → 전액 1단계, 2단계는 0
    @Test
    fun stage1_takesAllRewardWhenPointsAtOrBelow100() {
        assertEquals(80, MissionRewardPolicy.stage1Reward(80))
        assertEquals(0, MissionRewardPolicy.stage2Reward(80))

        assertEquals(100, MissionRewardPolicy.stage1Reward(100))
        assertEquals(0, MissionRewardPolicy.stage2Reward(100))
    }

    // 4. 미션 포인트가 100을 초과할 때 1·2단계 보상 계산 → 1단계 100 상한, 나머지는 2단계
    @Test
    fun rewardIsSplitWhenPointsAbove100() {
        assertEquals(100, MissionRewardPolicy.stage1Reward(250))
        assertEquals(150, MissionRewardPolicy.stage2Reward(250))

        // 1단계 + 2단계 = 전체 포인트
        assertEquals(300, MissionRewardPolicy.stage1Reward(300) + MissionRewardPolicy.stage2Reward(300))
    }

    // 5. 이미 지급된 보상은 다시 지급하지 않음
    @Test
    fun stage1RewardIsNotGrantedTwice() {
        assertEquals(
            100,
            MissionRewardPolicy.stage1RewardToGrant(250, isNearEnough = true, alreadyGranted = false)
        )
        assertEquals(
            0,
            MissionRewardPolicy.stage1RewardToGrant(250, isNearEnough = true, alreadyGranted = true)
        )
    }

    @Test
    fun stage1RewardIsZeroWhenOutOfRange() {
        assertEquals(
            0,
            MissionRewardPolicy.stage1RewardToGrant(250, isNearEnough = false, alreadyGranted = false)
        )
    }

    @Test
    fun stage2RewardIsNotGrantedTwiceOrAfterCompletion() {
        assertEquals(
            150,
            MissionRewardPolicy.stage2RewardToGrant(250, alreadyCompleted = false, alreadyGranted = false)
        )
        assertEquals(
            0,
            MissionRewardPolicy.stage2RewardToGrant(250, alreadyCompleted = false, alreadyGranted = true)
        )
        assertEquals(
            0,
            MissionRewardPolicy.stage2RewardToGrant(250, alreadyCompleted = true, alreadyGranted = false)
        )
    }
}

package smu.ai.graduation_project.domain

/**
 * 사진 인증(2단계) → 미션 완료 처리 규칙. 순수 Kotlin (Firebase 비의존).
 */
object MissionCompletion {

    const val STATUS_IN_PROGRESS = "In Progress"
    const val STATUS_COMPLETED = "Completed"

    data class Outcome(
        /** 완료 처리 후 저장할 상태. 업로드 실패 시 기존 상태 그대로. */
        val newStatus: String,
        /** users.points 에 더할 2단계 보상. 중복이면 0. */
        val pointsToGrant: Int,
        /** user_missions 를 완료로 표시할지 여부. */
        val markCompleted: Boolean,
        /** missions.completionCount 를 1 올릴지 여부(이 사용자가 처음 완료할 때만 true). */
        val countTowardPopularity: Boolean
    )

    fun isCompleted(status: String): Boolean =
        status.contains("완료") || status.equals(STATUS_COMPLETED, ignoreCase = true)

    /**
     * 사진 인증 완료를 시도할 수 있는 상태인지.
     * 위치 인증이 끝났고, 아직 완료 전이며, 촬영한 사진이 있어야 한다.
     */
    fun canAttemptPhotoStage(
        locationVerified: Boolean,
        missionCompleted: Boolean,
        hasPhoto: Boolean
    ): Boolean = locationVerified && !missionCompleted && hasPhoto

    /**
     * 사진 업로드 결과에 따른 완료 처리 결과를 계산한다.
     *
     * - [uploadSucceeded] == false 이면 상태를 바꾸지 않고(=Completed 로 승격하지 않음) 포인트도 0.
     * - [needsReview] == true 면(자동 판정이 애매해 관리자 검수 필요) 업로드는 성공했어도
     *   상태를 In Progress 로 유지하고 포인트도 지급하지 않는다. 관리자가 [resolveApproval] 로
     *   승인해야 비로소 Completed 로 전환되고 2단계 보상이 지급된다.
     * - 업로드 성공 + 검수 불필요일 때만 즉시 Completed 로 전환하며, 아직 지급 전일 때만 2단계 보상을 지급한다.
     */
    fun resolve(
        currentStatus: String,
        missionPoints: Int,
        stage2AlreadyGranted: Boolean,
        uploadSucceeded: Boolean,
        needsReview: Boolean = false
    ): Outcome {
        if (!uploadSucceeded) {
            return Outcome(
                newStatus = currentStatus,
                pointsToGrant = 0,
                markCompleted = false,
                countTowardPopularity = false
            )
        }
        if (needsReview) {
            return Outcome(
                newStatus = STATUS_IN_PROGRESS,
                pointsToGrant = 0,
                markCompleted = false,
                countTowardPopularity = false
            )
        }
        val alreadyCompleted = isCompleted(currentStatus)
        val points = MissionRewardPolicy.stage2RewardToGrant(
            missionPoints = missionPoints,
            alreadyCompleted = alreadyCompleted,
            alreadyGranted = stage2AlreadyGranted
        )
        return Outcome(
            newStatus = STATUS_COMPLETED,
            pointsToGrant = points,
            markCompleted = true,
            countTowardPopularity = !alreadyCompleted
        )
    }

    /**
     * 관리자가 승인 대기(`photoNeedsReview`) 건을 승인할 때의 완료 처리 결과.
     * 이미 완료됐거나, 검수 대상이 아니거나([needsReview] == false), 이미 지급됐으면
     * 아무 것도 하지 않는 결과([markCompleted] == false, [pointsToGrant] == 0)를 반환한다
     * (중복 승인·중복 지급 방지).
     */
    fun resolveApproval(
        currentStatus: String,
        stage2Points: Int,
        stage2AlreadyGranted: Boolean,
        needsReview: Boolean
    ): Outcome {
        if (!needsReview || isCompleted(currentStatus) || stage2AlreadyGranted) {
            return Outcome(
                newStatus = currentStatus,
                pointsToGrant = 0,
                markCompleted = false,
                countTowardPopularity = false
            )
        }
        return Outcome(
            newStatus = STATUS_COMPLETED,
            pointsToGrant = stage2Points,
            markCompleted = true,
            countTowardPopularity = true
        )
    }
}

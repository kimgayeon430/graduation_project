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
        val markCompleted: Boolean
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
     * - 업로드 성공 시에만 Completed 로 전환하며, 아직 지급 전일 때만 2단계 보상을 지급한다.
     */
    fun resolve(
        currentStatus: String,
        missionPoints: Int,
        stage2AlreadyGranted: Boolean,
        uploadSucceeded: Boolean
    ): Outcome {
        if (!uploadSucceeded) {
            return Outcome(newStatus = currentStatus, pointsToGrant = 0, markCompleted = false)
        }
        val alreadyCompleted = isCompleted(currentStatus)
        val points = MissionRewardPolicy.stage2RewardToGrant(
            missionPoints = missionPoints,
            alreadyCompleted = alreadyCompleted,
            alreadyGranted = stage2AlreadyGranted
        )
        return Outcome(newStatus = STATUS_COMPLETED, pointsToGrant = points, markCompleted = true)
    }
}

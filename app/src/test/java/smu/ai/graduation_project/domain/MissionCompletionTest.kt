package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 사진 인증 → 미션 완료 처리 규칙 (필수 테스트 6, 7, 8). */
class MissionCompletionTest {

    // 6. 위치 인증 전에는 사진 인증 완료 불가
    @Test
    fun cannotAttemptPhotoStageBeforeLocationVerified() {
        assertFalse(
            MissionCompletion.canAttemptPhotoStage(
                locationVerified = false, missionCompleted = false, hasPhoto = true
            )
        )
    }

    @Test
    fun cannotAttemptPhotoStageWithoutPhoto() {
        assertFalse(
            MissionCompletion.canAttemptPhotoStage(
                locationVerified = true, missionCompleted = false, hasPhoto = false
            )
        )
    }

    @Test
    fun canAttemptPhotoStageWhenLocationVerifiedAndPhotoPresent() {
        assertTrue(
            MissionCompletion.canAttemptPhotoStage(
                locationVerified = true, missionCompleted = false, hasPhoto = true
            )
        )
    }

    // 7. 사진 업로드 실패 시 미션이 Completed 로 바뀌지 않음
    @Test
    fun uploadFailureDoesNotCompleteMission() {
        val outcome = MissionCompletion.resolve(
            currentStatus = "In Progress",
            missionPoints = 250,
            stage2AlreadyGranted = false,
            uploadSucceeded = false
        )
        assertEquals("In Progress", outcome.newStatus)
        assertFalse(outcome.markCompleted)
        assertEquals(0, outcome.pointsToGrant)
        assertFalse(outcome.countTowardPopularity)
    }

    // 처음 완료할 때만 인기도(completionCount) 를 올린다
    @Test
    fun popularityCountsOnlyOnFirstCompletion() {
        val first = MissionCompletion.resolve(
            currentStatus = "In Progress",
            missionPoints = 250,
            stage2AlreadyGranted = false,
            uploadSucceeded = true
        )
        assertTrue(first.countTowardPopularity)

        val again = MissionCompletion.resolve(
            currentStatus = "Completed",
            missionPoints = 250,
            stage2AlreadyGranted = true,
            uploadSucceeded = true
        )
        assertFalse(again.countTowardPopularity)
    }

    // 8. 완료 요청을 반복해도 포인트가 중복 지급되지 않음
    @Test
    fun repeatedCompletionGrantsStage2PointsOnlyOnce() {
        var status = "In Progress"
        var stage2Granted = false
        var totalGranted = 0

        repeat(3) {
            val outcome = MissionCompletion.resolve(
                currentStatus = status,
                missionPoints = 250,
                stage2AlreadyGranted = stage2Granted,
                uploadSucceeded = true
            )
            totalGranted += outcome.pointsToGrant
            // 서버 트랜잭션이 상태를 갱신한 것처럼 다음 반복에 반영
            if (outcome.markCompleted) {
                status = outcome.newStatus
                stage2Granted = true
            }
        }

        assertEquals(150, totalGranted) // 250 - min(100, 250) = 150, 단 한 번만
        assertEquals("Completed", status)
    }

    @Test
    fun alreadyCompletedMissionGrantsNothing() {
        val outcome = MissionCompletion.resolve(
            currentStatus = "Completed",
            missionPoints = 250,
            stage2AlreadyGranted = false,
            uploadSucceeded = true
        )
        assertEquals(0, outcome.pointsToGrant)
        assertTrue(outcome.markCompleted)
    }
}

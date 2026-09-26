package smu.ai.graduation_project.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 사용자 제안 미션 검수 상태 판정 (사용자 미션 제안/검수 기능). */
class MissionReviewStatusTest {

    @Test
    fun approvedAndLegacyMissingFieldAreVisible() {
        assertTrue(MissionReviewStatus.isPubliclyVisible(MissionReviewStatus.APPROVED))
        assertTrue(MissionReviewStatus.isPubliclyVisible(null))
        assertTrue(MissionReviewStatus.isPubliclyVisible(""))
    }

    @Test
    fun pendingChangesRequestedAndRejectedAreNotVisible() {
        assertFalse(MissionReviewStatus.isPubliclyVisible(MissionReviewStatus.PENDING))
        assertFalse(MissionReviewStatus.isPubliclyVisible(MissionReviewStatus.CHANGES_REQUESTED))
        assertFalse(MissionReviewStatus.isPubliclyVisible(MissionReviewStatus.REJECTED))
    }

    @Test
    fun onlyChangesRequestedCanBeResubmitted() {
        assertTrue(MissionReviewStatus.canResubmit(MissionReviewStatus.CHANGES_REQUESTED))
        assertFalse(MissionReviewStatus.canResubmit(MissionReviewStatus.REJECTED))
        assertFalse(MissionReviewStatus.canResubmit(MissionReviewStatus.APPROVED))
        assertFalse(MissionReviewStatus.canResubmit(MissionReviewStatus.PENDING))
        assertFalse(MissionReviewStatus.canResubmit(null))
    }
}

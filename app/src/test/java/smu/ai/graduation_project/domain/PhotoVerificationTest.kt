package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 사진 인증(2단계) 판정 규칙. */
class PhotoVerificationTest {

    private fun classification(vararg pairs: Pair<String, Double>) =
        PhotoVerification.Classification(pairs.toMap())

    @Test
    fun highCategoryScorePasses() {
        val result = PhotoVerification.verify(
            missionCategory = "맛집",
            classification = classification("맛집" to 0.90, PhotoVerification.INVALID_LABEL to 0.02)
        )
        assertEquals(PhotoVerification.Verdict.PASS, result.verdict)
    }

    @Test
    fun highInvalidScoreIsRejected() {
        val result = PhotoVerification.verify(
            missionCategory = "맛집",
            classification = classification("맛집" to 0.20, PhotoVerification.INVALID_LABEL to 0.75)
        )
        assertEquals(PhotoVerification.Verdict.REJECT, result.verdict)
    }

    @Test
    fun lowCategoryScoreIsRejected() {
        val result = PhotoVerification.verify(
            missionCategory = "체험",
            classification = classification(
                "체험" to 0.10, "투어" to 0.50, PhotoVerification.INVALID_LABEL to 0.10
            )
        )
        assertEquals(PhotoVerification.Verdict.REJECT, result.verdict)
    }

    @Test
    fun midCategoryScoreNeedsReview() {
        val result = PhotoVerification.verify(
            missionCategory = "쇼핑",
            classification = classification("쇼핑" to 0.50, PhotoVerification.INVALID_LABEL to 0.10)
        )
        assertEquals(PhotoVerification.Verdict.NEEDS_REVIEW, result.verdict)
    }

    @Test
    fun nullClassificationNeedsReviewByDefault() {
        val result = PhotoVerification.verify(missionCategory = "투어", classification = null)
        assertEquals(PhotoVerification.Verdict.NEEDS_REVIEW, result.verdict)
    }

    @Test
    fun nullClassificationPassesWhenConfigured() {
        val result = PhotoVerification.verify(
            missionCategory = "투어",
            classification = null,
            config = PhotoVerificationConfig(passWhenModelUnavailable = true)
        )
        assertEquals(PhotoVerification.Verdict.PASS, result.verdict)
    }

    @Test
    fun boundaryScoreEqualToAutoPassThresholdPasses() {
        val config = PhotoVerificationConfig(autoPassThreshold = 0.70)
        val result = PhotoVerification.verify(
            missionCategory = "투어",
            classification = classification("투어" to 0.70, PhotoVerification.INVALID_LABEL to 0.0),
            config = config
        )
        assertEquals(PhotoVerification.Verdict.PASS, result.verdict)
    }
}

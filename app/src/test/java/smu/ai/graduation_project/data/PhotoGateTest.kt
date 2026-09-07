package smu.ai.graduation_project.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import smu.ai.graduation_project.domain.PhotoVerification
import smu.ai.graduation_project.domain.PhotoVerificationConfig

/** 업로드 전 사진 판정 게이트. ([PhotoGate]) */
class PhotoGateTest {

    private val bytes = ByteArray(4) { 1 }

    private fun classification(vararg pairs: Pair<String, Double>) =
        PhotoVerification.Classification(pairs.toMap())

    private fun verifier(
        fixed: PhotoVerification.Classification?,
        version: String = "test-1"
    ) = FakePhotoVerifier(fixed, version)

    @Test
    fun highCategoryScoreProceedsWithoutReview() {
        val decision = PhotoGate.decide(
            bytes, "맛집",
            verifier(classification("맛집" to 0.92, PhotoVerification.INVALID_LABEL to 0.01))
        )
        assertEquals(
            PhotoGate.Decision.Proceed(
                needsReview = false, matchScore = 0.92, topLabel = "맛집", modelVersion = "test-1"
            ),
            decision
        )
    }

    @Test
    fun highInvalidScoreIsRejected() {
        val decision = PhotoGate.decide(
            bytes, "맛집",
            verifier(classification("맛집" to 0.2, PhotoVerification.INVALID_LABEL to 0.8))
        )
        assertTrue(decision is PhotoGate.Decision.Reject)
    }

    @Test
    fun midCategoryScoreProceedsWithReview() {
        val decision = PhotoGate.decide(
            bytes, "쇼핑",
            verifier(classification("쇼핑" to 0.5, PhotoVerification.INVALID_LABEL to 0.1))
        )
        assertEquals(true, (decision as PhotoGate.Decision.Proceed).needsReview)
    }

    @Test
    fun missingModelNeedsReviewByDefault() {
        val decision = PhotoGate.decide(bytes, "투어", verifier(null))
        assertEquals(true, (decision as PhotoGate.Decision.Proceed).needsReview)
    }

    @Test
    fun missingModelPassesWhenConfigured() {
        val decision = PhotoGate.decide(
            bytes, "투어", verifier(null),
            PhotoVerificationConfig(passWhenModelUnavailable = true)
        )
        assertEquals(false, (decision as PhotoGate.Decision.Proceed).needsReview)
    }

    @Test
    fun classifierExceptionIsTreatedAsMissingModel() {
        val throwing = object : PhotoVerifier {
            override val modelVersion = "boom"
            override fun classify(photoBytes: ByteArray): PhotoVerification.Classification =
                throw IllegalStateException("model failed to load")
        }
        val decision = PhotoGate.decide(bytes, "투어", throwing)
        assertEquals(true, (decision as PhotoGate.Decision.Proceed).needsReview)
        assertEquals("", decision.topLabel)
    }
}

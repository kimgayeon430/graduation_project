package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 사진 인증(2단계) 판정 규칙. */
class PhotoVerificationTest {

    private fun classification(vararg pairs: Pair<String, Double>) =
        PhotoVerification.Classification(pairs.toMap())

    private fun classification(embedding: FloatArray, vararg pairs: Pair<String, Double>) =
        PhotoVerification.Classification(pairs.toMap(), embedding)

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

    // ---- 참조 이미지 유사도 결합 (보고서 6.7.5) ----

    /** cos 유사도가 목표가 되도록 방향을 맞춘 2D 벡터. `ref` 와의 각도로 유사도를 만든다. */
    private val ref = floatArrayOf(1f, 0f)
    private val simHigh = floatArrayOf(1f, 0f)          // cos = 1.0
    private val simMid = floatArrayOf(0.6f, 0.8f)       // cos = 0.6  (rescue..suspect 사이)
    private val simLow = floatArrayOf(0f, 1f)           // cos = 0.0

    @Test
    fun highCategoryHighSimilarityPasses() {
        val r = PhotoVerification.verify(
            "투어", classification(simHigh, "투어" to 0.90, PhotoVerification.INVALID_LABEL to 0.02),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.PASS, r.verdict)
        assertEquals(1.0, r.similarity!!, 1e-6)
    }

    @Test
    fun highCategoryLowSimilarityIsDowngradedToReview() {
        // 유형은 확실한데 대표 이미지와 안 닮음 → 같은 유형의 다른 대상 의심.
        val r = PhotoVerification.verify(
            "투어", classification(simLow, "투어" to 0.90, PhotoVerification.INVALID_LABEL to 0.02),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.NEEDS_REVIEW, r.verdict)
    }

    @Test
    fun lowCategoryHighSimilarityIsRescuedToReview() {
        // 카테고리 점수는 낮지만 대표 이미지와 닮아 즉시 거절하지 않는다 (약한 클래스 구제).
        val r = PhotoVerification.verify(
            "체험", classification(simHigh, "체험" to 0.10, "투어" to 0.5, PhotoVerification.INVALID_LABEL to 0.05),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.NEEDS_REVIEW, r.verdict)
    }

    @Test
    fun midCategoryHighSimilarityIsUpgradedToPass() {
        val r = PhotoVerification.verify(
            "쇼핑", classification(simHigh, "쇼핑" to 0.50, PhotoVerification.INVALID_LABEL to 0.10),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.PASS, r.verdict)
    }

    @Test
    fun midCategoryMidSimilarityStaysReview() {
        val r = PhotoVerification.verify(
            "쇼핑", classification(simMid, "쇼핑" to 0.50, PhotoVerification.INVALID_LABEL to 0.10),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.NEEDS_REVIEW, r.verdict)
    }

    @Test
    fun lowCategoryLowSimilarityIsRejected() {
        val r = PhotoVerification.verify(
            "체험", classification(simLow, "체험" to 0.10, "투어" to 0.5, PhotoVerification.INVALID_LABEL to 0.05),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.REJECT, r.verdict)
    }

    @Test
    fun invalidRejectShortCircuitsBeforeSimilarity() {
        // 참조 이미지를 화면에 띄워 재촬영하면 유사도는 최대에 가깝다. 무효가 먼저 잘라야 한다.
        val r = PhotoVerification.verify(
            "맛집", classification(simHigh, "맛집" to 0.20, PhotoVerification.INVALID_LABEL to 0.80),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.REJECT, r.verdict)
        assertNull("무효 단락 시 유사도는 계산하지 않는다", r.similarity)
    }

    @Test
    fun noReferenceEmbeddingKeepsCategoryOnlyVerdict() {
        val r = PhotoVerification.verify(
            "체험", classification(simHigh, "체험" to 0.10, PhotoVerification.INVALID_LABEL to 0.05),
            referenceEmbedding = null
        )
        assertEquals(PhotoVerification.Verdict.REJECT, r.verdict)
        assertNull(r.similarity)
    }

    @Test
    fun missingCapturedEmbeddingSkipsSimilarity() {
        val r = PhotoVerification.verify(
            "체험", classification("체험" to 0.10, PhotoVerification.INVALID_LABEL to 0.05),
            referenceEmbedding = ref
        )
        assertEquals(PhotoVerification.Verdict.REJECT, r.verdict)
        assertNull(r.similarity)
    }

    @Test
    fun cosineHandlesNullAndMismatchedLengths() {
        assertNull(PhotoEmbedding.cosineOrNull(null, ref))
        assertNull(PhotoEmbedding.cosineOrNull(floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 2f)))
        assertEquals(1.0, PhotoEmbedding.cosineOrNull(floatArrayOf(2f, 0f), floatArrayOf(5f, 0f))!!, 1e-9)
        assertEquals(0.0, PhotoEmbedding.cosineOrNull(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f))!!, 1e-9)
    }

    @Test
    fun l2NormalizedHasUnitNorm() {
        val n = PhotoEmbedding.l2Normalized(floatArrayOf(3f, 4f))
        val norm = kotlin.math.sqrt((n[0] * n[0] + n[1] * n[1]).toDouble())
        assertTrue(kotlin.math.abs(norm - 1.0) < 1e-6)
    }
}

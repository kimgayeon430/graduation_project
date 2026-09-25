package smu.ai.graduation_project.data

import smu.ai.graduation_project.domain.PhotoVerification
import smu.ai.graduation_project.domain.PhotoVerificationConfig

/**
 * 사진 업로드 전 "이 사진을 올려도 되는가"를 결정한다.
 *
 * [PhotoVerifier] 추론 + [PhotoVerification] 판정 규칙을 묶어, Firebase·Android 에 의존하지 않는
 * 순수 로직으로 뽑아 냈다. ([FirebaseMissionRepository] 가 이 결과에 따라 업로드/거부한다.)
 *
 * **모델 추론이 무거우므로 백그라운드 스레드에서 호출하라.**
 */
object PhotoGate {

    sealed interface Decision {
        /** 판정 근거 점수. 임계값 보정·로그용. */
        val matchScore: Double
        val invalidScore: Double
        val similarity: Double?

        /** 업로드하지 않고 재촬영을 요청한다. */
        data class Reject(
            val reason: String,
            override val matchScore: Double = 0.0,
            override val invalidScore: Double = 0.0,
            override val similarity: Double? = null,
            /** 모델이 낸 최상위 라벨(REJECT 결과 화면에 "AI가 예측한 카테고리"로 보여준다). */
            val topLabel: String = "",
            val reasonCode: PhotoVerification.RejectReasonCode = PhotoVerification.RejectReasonCode.LOW_CONFIDENCE,
        ) : Decision

        /** 업로드를 진행한다. [needsReview] 면 완료는 하되 관리자 검수 큐에 올린다. */
        data class Proceed(
            val needsReview: Boolean,
            override val matchScore: Double,
            val topLabel: String,
            val modelVersion: String,
            override val invalidScore: Double = 0.0,
            /** 미션 대표 이미지와의 코사인 유사도. 참조 임베딩·임베더가 없으면 null. */
            override val similarity: Double? = null,
        ) : Decision
    }

    /**
     * @param referenceEmbeddings 미션 대표 이미지(들) 임베딩(`missions/{id}.photoEmbeddings`). 비어 있으면
     *                            유사도 결합을 건너뛴다. 여러 장이면 최대 유사도를 쓴다(6.7.8).
     */
    fun decide(
        photoBytes: ByteArray,
        missionCategory: String,
        verifier: PhotoVerifier,
        config: PhotoVerificationConfig = PhotoVerificationConfig.DEFAULT,
        referenceEmbeddings: List<FloatArray> = emptyList(),
    ): Decision {
        val classification = try {
            verifier.classify(photoBytes)
        } catch (_: Exception) {
            null
        }
        val result = PhotoVerification.verify(missionCategory, classification, referenceEmbeddings, config)
        return when (result.verdict) {
            PhotoVerification.Verdict.REJECT -> Decision.Reject(
                reason = result.reason,
                matchScore = result.matchScore,
                invalidScore = result.invalidScore,
                similarity = result.similarity,
                topLabel = classification?.topLabel ?: "",
                reasonCode = result.rejectReasonCode ?: PhotoVerification.RejectReasonCode.LOW_CONFIDENCE,
            )
            PhotoVerification.Verdict.NEEDS_REVIEW, PhotoVerification.Verdict.PASS -> Decision.Proceed(
                needsReview = result.verdict == PhotoVerification.Verdict.NEEDS_REVIEW,
                matchScore = result.matchScore,
                topLabel = classification?.topLabel ?: "",
                modelVersion = verifier.modelVersion,
                invalidScore = result.invalidScore,
                similarity = result.similarity,
            )
        }
    }
}

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
        /** 업로드하지 않고 재촬영을 요청한다. */
        data class Reject(val reason: String) : Decision

        /** 업로드를 진행한다. [needsReview] 면 완료는 하되 관리자 검수 큐에 올린다. */
        data class Proceed(
            val needsReview: Boolean,
            val matchScore: Double,
            val topLabel: String,
            val modelVersion: String,
            /** 미션 대표 이미지와의 코사인 유사도. 참조 임베딩·임베더가 없으면 null. */
            val similarity: Double? = null,
        ) : Decision
    }

    /**
     * @param referenceEmbedding 미션 대표 이미지 임베딩(`missions/{id}.photoEmbedding`). 없으면 유사도 결합을 건너뛴다.
     */
    fun decide(
        photoBytes: ByteArray,
        missionCategory: String,
        verifier: PhotoVerifier,
        config: PhotoVerificationConfig = PhotoVerificationConfig.DEFAULT,
        referenceEmbedding: FloatArray? = null,
    ): Decision {
        val classification = try {
            verifier.classify(photoBytes)
        } catch (_: Exception) {
            null
        }
        val result = PhotoVerification.verify(missionCategory, classification, referenceEmbedding, config)
        return when (result.verdict) {
            PhotoVerification.Verdict.REJECT -> Decision.Reject(result.reason)
            PhotoVerification.Verdict.NEEDS_REVIEW, PhotoVerification.Verdict.PASS -> Decision.Proceed(
                needsReview = result.verdict == PhotoVerification.Verdict.NEEDS_REVIEW,
                matchScore = result.matchScore,
                topLabel = classification?.topLabel ?: "",
                modelVersion = verifier.modelVersion,
                similarity = result.similarity,
            )
        }
    }
}

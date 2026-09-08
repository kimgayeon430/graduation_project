package smu.ai.graduation_project.domain

import kotlin.math.exp

/**
 * 완료 로그로 학습한 로지스틱 회귀로 미션 추천 점수를 다시 매긴다. 순수 Kotlin.
 *
 * `ml/reco/train_reranker.py` 가 [MissionFeatures] 신호 벡터에 대한 가중치를 학습해
 * `assets/reranker.json` 으로 내보낸다. 앱은 그 모델로 "사용자가 이 미션을 완료할 확률"을
 * 추정하고, 규칙 점수와 섞어([Model.blend]) 최종 순위를 만든다. (규칙 점수는 설명·콜드스타트용으로 유지)
 *
 * 모델이 없으면 [MissionRecommender] 가 규칙 기반([MissionRecommender.recommendScored])으로 폴백한다.
 */
object LearnedReranker {

    /**
     * @param weights [MissionFeatures.NAMES] 순서의 로지스틱 가중치. 길이가 신호 수와 같아야 한다.
     * @param bias 로지스틱 절편.
     * @param blend λ. 0 이면 규칙 점수만, 1 이면 학습 점수만. (0~1)
     * @param diversityPenalty 블렌드 점수([0,1] 스케일)에서 같은 카테고리가 하나 쌓일 때마다 빼는 값.
     */
    data class Model(
        val weights: DoubleArray,
        val bias: Double,
        val blend: Double,
        val diversityPenalty: Double = 0.15,
        val version: String = "reranker",
    ) {
        init {
            require(weights.size == MissionFeatures.NAMES.size) {
                "가중치 길이(${weights.size}) 가 신호 수(${MissionFeatures.NAMES.size}) 와 다릅니다."
            }
            require(blend in 0.0..1.0) { "blend 는 0..1 범위여야 합니다." }
        }

        override fun equals(other: Any?): Boolean =
            other is Model && weights.contentEquals(other.weights) && bias == other.bias &&
                blend == other.blend && diversityPenalty == other.diversityPenalty && version == other.version

        override fun hashCode(): Int =
            weights.contentHashCode() * 31 + bias.hashCode()
    }

    /** 학습된 완료 확률 sigmoid(w·x + b). */
    fun probability(signals: MissionFeatures.Signals, model: Model): Double {
        val x = signals.asVector()
        var z = model.bias
        for (i in x.indices) z += model.weights[i] * x[i]
        return 1.0 / (1.0 + exp(-z))
    }
}

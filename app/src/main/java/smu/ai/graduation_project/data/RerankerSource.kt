package smu.ai.graduation_project.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import smu.ai.graduation_project.domain.LearnedReranker
import smu.ai.graduation_project.domain.MissionFeatures

/**
 * `assets/reranker.json` (= `ml/reco/train_reranker.py` 산출물) 을 읽어 [LearnedReranker.Model] 로 만든다.
 *
 * 파일이 없거나 형식/신호 순서가 맞지 않으면 null 을 돌려주고, 추천은 규칙 기반으로 폴백한다.
 */
object RerankerSource {

    private const val TAG = "RerankerSource"

    fun load(context: Context, asset: String = "reranker.json"): LearnedReranker.Model? = runCatching {
        val json = JSONObject(context.assets.open(asset).use { it.reader().readText() })

        // 신호 순서가 앱과 다르면 가중치를 잘못 곱하게 되므로 거부한다.
        val names = json.optJSONArray("feature_names")
        if (names != null) {
            val jsonNames = (0 until names.length()).map { names.getString(it) }
            require(jsonNames == MissionFeatures.NAMES) {
                "feature_names 불일치: $jsonNames vs ${MissionFeatures.NAMES}"
            }
        }

        val w = json.getJSONArray("weights")
        LearnedReranker.Model(
            weights = DoubleArray(w.length()) { w.getDouble(it) },
            bias = json.getDouble("bias"),
            blend = json.optDouble("blend", 0.5),
            diversityPenalty = json.optDouble("diversity_penalty", 0.15),
            version = json.optString("version", "reranker"),
        )
    }.onFailure { Log.i(TAG, "reranker 모델 미탑재/무효 → 규칙 기반 추천: ${it.message}") }.getOrNull()
}

package smu.ai.graduation_project.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import smu.ai.graduation_project.domain.LearnedReranker
import smu.ai.graduation_project.domain.MissionFeatures

/**
 * `assets/reranker.json` 이 **실제로 모델로 로드되는지** 확인하는 계측 테스트.
 *
 * [smu.ai.graduation_project.domain.LearnedRerankerTest] 는 손으로 만든 [LearnedReranker.Model] 로
 * 수식만 검증하므로, 배포되는 에셋이 실제로 파싱·검증을 통과하는지는 여기서만 확인된다.
 *
 * [RerankerSource.load] 는 실패를 `runCatching` 으로 삼키고 null 을 돌려주며, 그러면 추천이 조용히
 * 규칙 기반으로 폴백한다. 앱은 정상으로 보이고 추천 품질만 떨어지므로 눈으로는 알아챌 수 없다.
 * 재학습한 모델을 갈아끼울 때 신호 순서나 개수가 어긋나는 사고를 여기서 잡는다.
 * ([MissionFeatures.NAMES] 순서는 학습·가중치·추론이 공유한다)
 */
@RunWith(AndroidJUnit4::class)
class RerankerSourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedAssetLoadsAsModel() {
        val model = RerankerSource.load(context)

        assertNotNull(
            "assets/reranker.json 로드 실패 → 추천이 규칙 기반으로 폴백한다. " +
                "feature_names 가 ${MissionFeatures.NAMES} 와 순서까지 같은지 확인하라.",
            model,
        )
        assertEquals(
            "가중치 개수가 신호 수와 다르다",
            MissionFeatures.NAMES.size,
            model!!.weights.size,
        )
        assertTrue("blend 가 0..1 밖: ${model.blend}", model.blend in 0.0..1.0)
        assertTrue("가중치에 NaN/무한대가 있다", model.weights.all { it.isFinite() })
        assertTrue("bias 가 유한하지 않다", model.bias.isFinite())
    }

    @Test
    fun shippedAssetIsTheExpectedTrainedVersion() {
        // 모델을 재학습해 교체했다면 이 값을 함께 갱신하라. (train_reranker.py 의 version)
        assertEquals("reranker-lr-sim-3", RerankerSource.load(context)?.version)
    }

    @Test
    fun loadedModelProducesUsableProbability() {
        val model = requireNotNull(RerankerSource.load(context))

        val signals = MissionFeatures.Signals(
            explicitPref = 1.0,
            implicitAffinity = 0.5,
            difficultyFit = 0.8,
            proximity = 0.9,
            popularity = 0.3,
            timeOfDayFit = 1.0,
        )
        val p = LearnedReranker.probability(signals, model)

        assertTrue("확률이 0..1 밖: $p", p in 0.0..1.0)
    }

    @Test
    fun missingAssetFallsBackToNullInsteadOfCrashing() {
        assertNull(RerankerSource.load(context, asset = "no_such_reranker.json"))
    }
}

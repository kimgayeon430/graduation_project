package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 학습된 로지스틱 re-ranker. */
class LearnedRerankerTest {

    private fun signals(
        explicit: Double = 0.0, affinity: Double = 0.0, fit: Double = 0.0,
        proximity: Double = 0.0, popularity: Double = 0.0,
    ) = MissionFeatures.Signals(explicit, affinity, fit, proximity, popularity)

    @Test
    fun probabilityIsSigmoidOfLinearScore() {
        val model = LearnedReranker.Model(
            weights = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0), bias = 0.0, blend = 0.5,
        )
        // z = 0 → sigmoid(0) = 0.5
        assertEquals(0.5, LearnedReranker.probability(signals(), model), 1e-9)
    }

    @Test
    fun higherWeightedSignalRaisesProbability() {
        val model = LearnedReranker.Model(
            weights = doubleArrayOf(2.0, 0.0, 0.0, 0.0, 0.0), bias = -1.0, blend = 0.5,
        )
        val low = LearnedReranker.probability(signals(explicit = 0.0), model)
        val high = LearnedReranker.probability(signals(explicit = 1.0), model)
        assertTrue(high > low)
        assertTrue(low in 0.0..1.0 && high in 0.0..1.0)
    }

    @Test
    fun rejectsWrongWeightLength() {
        assertThrows(IllegalArgumentException::class.java) {
            LearnedReranker.Model(weights = doubleArrayOf(1.0, 2.0), bias = 0.0, blend = 0.5)
        }
    }

    @Test
    fun rejectsBlendOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            LearnedReranker.Model(weights = DoubleArray(5), bias = 0.0, blend = 1.5)
        }
    }
}

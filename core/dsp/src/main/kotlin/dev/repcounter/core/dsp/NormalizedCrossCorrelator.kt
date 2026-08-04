package dev.repcounter.core.dsp

import kotlin.math.sqrt

/**
 * Normalized cross-correlation [CrossCorrelator] over a bounded lag search window - spec §8.1
 * step 5 (`ANKLE_Y_L` vs `ANKLE_Y_R`, lag ~= 0 for `BOTH_FEET`, lag ~= half period for
 * `ALTERNATING`). Positive [LagResult.lagMs] means `b` is delayed relative to `a`.
 */
class NormalizedCrossCorrelator : CrossCorrelator {
    override fun correlate(
        a: List<TimedSample>,
        b: List<TimedSample>,
        maxLagMs: Long,
    ): LagResult {
        require(a.size >= MIN_SAMPLES_FOR_CORRELATION && b.size >= MIN_SAMPLES_FOR_CORRELATION) {
            "both inputs need at least $MIN_SAMPLES_FOR_CORRELATION samples"
        }
        val periodMs = (a[1].tMs - a[0].tMs).coerceAtLeast(1)
        val maxLagSamples = (maxLagMs / periodMs).toInt().coerceAtLeast(0)

        val av = a.map { it.value }
        val bv = b.map { it.value }
        val n = minOf(av.size, bv.size)
        val aMean = av.average().toFloat()
        val bMean = bv.average().toFloat()

        var bestLag = 0
        var bestCorrelation = -Float.MAX_VALUE
        for (lag in -maxLagSamples..maxLagSamples) {
            var crossSum = 0.0
            var aNormSum = 0.0
            var bNormSum = 0.0
            var overlap = 0
            for (i in 0 until n) {
                val j = i + lag
                if (j !in 0 until n) continue
                val aCentered = av[i] - aMean
                val bCentered = bv[j] - bMean
                crossSum += aCentered * bCentered
                aNormSum += aCentered * aCentered
                bNormSum += bCentered * bCentered
                overlap++
            }
            if (overlap == 0) continue
            val denominator = sqrt(aNormSum * bNormSum)
            val correlation = if (denominator > NORMALIZATION_EPSILON) (crossSum / denominator).toFloat() else 0f
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        return LagResult(lagMs = bestLag * periodMs, correlation = bestCorrelation)
    }

    private companion object {
        const val MIN_SAMPLES_FOR_CORRELATION = 2
        const val NORMALIZATION_EPSILON = 1e-9
    }
}

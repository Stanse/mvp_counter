package dev.repcounter.core.dsp

import kotlin.math.roundToInt

/**
 * Autocorrelation-based [CadenceEstimator] over a sliding window - spec §8.1 step 2. More robust
 * than counting raw peaks: a single dropped/noisy sample can't skip a beat, since the estimate
 * comes from how well the whole window matches a shifted copy of itself, not from any one
 * threshold crossing. Search is restricted to `[minHz, maxHz]` (plausible jump-rope/rep cadence)
 * so it can't lock onto sub-harmonics or noise outside that range.
 */
class AutocorrelationCadenceEstimator(
    private val sampleRateHz: Float,
    windowSeconds: Float = DEFAULT_WINDOW_SECONDS,
    private val minHz: Float = DEFAULT_MIN_HZ,
    private val maxHz: Float = DEFAULT_MAX_HZ,
) : CadenceEstimator {
    init {
        require(sampleRateHz > 0f) { "sampleRateHz must be positive, was $sampleRateHz" }
        require(0f < minHz && minHz < maxHz) { "require 0 < minHz < maxHz, was $minHz, $maxHz" }
    }

    private val window = FloatRingBuffer((windowSeconds * sampleRateHz).roundToInt().coerceAtLeast(2))

    // Early returns keep each "not enough signal yet" guard (warmup, near-silent window,
    // degenerate lag range, no positive-lag peak) a single readable line.
    @Suppress("ReturnCount")
    override fun update(sample: TimedSample): CadenceEstimate? {
        window.push(sample.value)
        if (!window.isFull) return null

        val data = window.toFloatArray()
        val mean = data.average().toFloat()
        val centered = FloatArray(data.size) { data[it] - mean }
        val energy = centered.sumOf { (it * it).toDouble() }
        if (energy < ENERGY_EPSILON) return null

        val minLag = (sampleRateHz / maxHz).roundToInt().coerceAtLeast(1)
        val maxLag = (sampleRateHz / minHz).roundToInt().coerceAtMost(centered.size - 1)
        if (minLag >= maxLag) return null

        var bestLag = -1
        var bestCorrelation = -Float.MAX_VALUE
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until centered.size - lag) {
                sum += centered[i] * centered[i + lag]
            }
            val normalized = (sum / energy).toFloat()
            if (normalized > bestCorrelation) {
                bestCorrelation = normalized
                bestLag = lag
            }
        }
        if (bestLag <= 0) return null

        return CadenceEstimate(hz = sampleRateHz / bestLag, confidence = bestCorrelation.coerceIn(0f, 1f))
    }

    override fun reset() = window.clear()

    private companion object {
        const val DEFAULT_WINDOW_SECONDS = 3f
        const val DEFAULT_MIN_HZ = 0.5f
        const val DEFAULT_MAX_HZ = 6f
        const val ENERGY_EPSILON = 1e-6
    }
}

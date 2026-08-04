package dev.repcounter.core.dsp

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * FSM peak/cycle detector with hysteresis - spec §8.1 step 3: threshold `k * RMS` measured over
 * a trailing window, so it adapts to signal amplitude instead of a fixed magic number. One
 * rise-above-`k*RMS`-then-fall-below-`k*RMS*hysteresis` cycle is one [Peak], reported at the
 * cycle's maximum sample. [minRefractoryMs] rejects double-counting bounce right after a peak;
 * the real jump-rope refractory (`0.6 / f0`) is computed by the caller from a [CadenceEstimator]
 * and passed in here, since this class knows nothing about cadence itself.
 */
class HysteresisPeakDetector(
    sampleRateHz: Float,
    private val kThreshold: Float = DEFAULT_K_THRESHOLD,
    minRefractoryMs: Long = DEFAULT_MIN_REFRACTORY_MS,
    rmsWindowMs: Long = DEFAULT_RMS_WINDOW_MS,
) : PeakDetector {
    init {
        require(sampleRateHz > 0f) { "sampleRateHz must be positive, was $sampleRateHz" }
        require(kThreshold > 0f) { "kThreshold must be positive, was $kThreshold" }
    }

    /**
     * Mutable so a caller like `JumpRopeAnalyzer` can re-derive it from a live
     * [CadenceEstimator] (spec §8.1 step 3: `refractory = 0.6 / f0`) without losing this
     * detector's RMS-window state, which reconstructing a new instance would.
     */
    var minRefractoryMs: Long = minRefractoryMs
        set(value) {
            require(value >= 0L) { "minRefractoryMs must not be negative, was $value" }
            field = value
        }

    private val window =
        FloatRingBuffer((rmsWindowMs / MILLIS_PER_SECOND * sampleRateHz).roundToInt().coerceAtLeast(1))
    private var sumOfSquares = 0.0

    private var aboveThreshold = false
    private var cycleMaxValue = 0f
    private var cycleMaxTMs = 0L
    private var lastEmittedTMs: Long? = null

    // Early returns keep each FSM guard (warmup, threshold-not-crossed, no-completed-cycle,
    // refractory) as a single readable line instead of nested ifs around one exit point.
    @Suppress("ReturnCount")
    override fun process(sample: TimedSample): Peak? {
        val evicted = window.push(sample.value)
        sumOfSquares += sample.value.toDouble() * sample.value
        if (evicted != null) sumOfSquares -= evicted.toDouble() * evicted

        if (!window.isFull) return null

        val rms = sqrt(sumOfSquares / window.size).toFloat()
        val highThreshold = kThreshold * rms
        val lowThreshold = highThreshold * HYSTERESIS_RATIO

        if (!aboveThreshold) {
            if (sample.value > highThreshold) {
                aboveThreshold = true
                cycleMaxValue = sample.value
                cycleMaxTMs = sample.tMs
            }
            return null
        }

        if (sample.value > cycleMaxValue) {
            cycleMaxValue = sample.value
            cycleMaxTMs = sample.tMs
        }
        if (sample.value >= lowThreshold) return null

        aboveThreshold = false
        val last = lastEmittedTMs
        if (last != null && cycleMaxTMs - last < minRefractoryMs) return null
        lastEmittedTMs = cycleMaxTMs
        return Peak(cycleMaxTMs, cycleMaxValue)
    }

    override fun reset() {
        window.clear()
        sumOfSquares = 0.0
        aboveThreshold = false
        lastEmittedTMs = null
    }

    companion object {
        const val DEFAULT_K_THRESHOLD = 1f
        const val DEFAULT_MIN_REFRACTORY_MS = 150L
        const val DEFAULT_RMS_WINDOW_MS = 1000L
        private const val HYSTERESIS_RATIO = 0.3f
        private const val MILLIS_PER_SECOND = 1000f
    }
}

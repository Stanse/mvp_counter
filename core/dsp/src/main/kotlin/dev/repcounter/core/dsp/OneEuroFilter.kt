package dev.repcounter.core.dsp

import kotlin.math.PI
import kotlin.math.abs

/**
 * One-Euro filter (Casiez, Roussel & Vogel 2012) for angular signals (`KNEE_ANGLE_*`,
 * `ELBOW_ANGLE_*`) - spec §7's alternative to the Butterworth bandpass for signals that are slow
 * and jittery rather than oscillatory, where a fixed passband would either lag real movement or
 * pass tracking jitter through. Cutoff frequency adapts to the signal's own speed: slow segments
 * get smoothed harder, fast segments get smoothed less to avoid lag.
 */
class OneEuroFilter(
    private val minCutoffHz: Float = DEFAULT_MIN_CUTOFF_HZ,
    private val speedCoefficient: Float = DEFAULT_SPEED_COEFFICIENT,
    private val derivativeCutoffHz: Float = DEFAULT_DERIVATIVE_CUTOFF_HZ,
) : StreamingFilter {
    init {
        require(minCutoffHz > 0f) { "minCutoffHz must be positive, was $minCutoffHz" }
        require(derivativeCutoffHz > 0f) { "derivativeCutoffHz must be positive, was $derivativeCutoffHz" }
    }

    private var initialized = false
    private var prevValue = 0f
    private var prevDerivative = 0f
    private var prevTMs = 0L

    override fun apply(sample: TimedSample): Float {
        if (!initialized) {
            initialized = true
            prevValue = sample.value
            prevDerivative = 0f
            prevTMs = sample.tMs
            return sample.value
        }

        val dtSeconds = ((sample.tMs - prevTMs).coerceAtLeast(1L) / MILLIS_PER_SECOND)
        val derivative = (sample.value - prevValue) / dtSeconds
        val smoothedDerivative = lowPass(derivative, prevDerivative, alpha(derivativeCutoffHz, dtSeconds))
        val cutoff = minCutoffHz + speedCoefficient * abs(smoothedDerivative)
        val smoothedValue = lowPass(sample.value, prevValue, alpha(cutoff, dtSeconds))

        prevValue = smoothedValue
        prevDerivative = smoothedDerivative
        prevTMs = sample.tMs
        return smoothedValue
    }

    override fun reset() {
        initialized = false
    }

    private fun alpha(
        cutoffHz: Float,
        dtSeconds: Float,
    ): Float {
        val timeConstant = 1f / (2f * PI.toFloat() * cutoffHz)
        return 1f / (1f + timeConstant / dtSeconds)
    }

    private fun lowPass(
        current: Float,
        previous: Float,
        alpha: Float,
    ): Float = alpha * current + (1f - alpha) * previous

    private companion object {
        const val DEFAULT_MIN_CUTOFF_HZ = 1f
        const val DEFAULT_SPEED_COEFFICIENT = 0.02f
        const val DEFAULT_DERIVATIVE_CUTOFF_HZ = 1f
        const val MILLIS_PER_SECOND = 1000f
    }
}

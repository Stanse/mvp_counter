package dev.repcounter.core.dsp

import kotlin.math.cos
import kotlin.math.sin

/** Coefficients for a canonical direct-form-II-transposed biquad, normalized so `a0 = 1`. */
internal data class BiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float,
)

/**
 * Second-order Butterworth (maximally flat, no passband ripple) lowpass/highpass section
 * coefficients, RBJ audio-EQ-cookbook formulas. [ButterworthBandpassFilter] cascades one of
 * each to build a wideband bandpass out of two well-understood, individually-testable sections.
 */
internal object ButterworthDesign {
    /** Q for a single 2nd-order Butterworth section (`1/sqrt(2)`, no peaking). */
    const val SECTION_Q = 0.7071068f

    fun lowpass(
        sampleRateHz: Float,
        cutoffHz: Float,
    ): BiquadCoefficients = design(sampleRateHz, cutoffHz, isHighpass = false)

    fun highpass(
        sampleRateHz: Float,
        cutoffHz: Float,
    ): BiquadCoefficients = design(sampleRateHz, cutoffHz, isHighpass = true)

    private fun design(
        sampleRateHz: Float,
        cutoffHz: Float,
        isHighpass: Boolean,
    ): BiquadCoefficients {
        val omega = 2.0 * Math.PI * cutoffHz / sampleRateHz
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val alpha = sinOmega / (2.0 * SECTION_Q)

        val (b0, b1, b2) =
            if (isHighpass) {
                Triple((1.0 + cosOmega) / 2.0, -(1.0 + cosOmega), (1.0 + cosOmega) / 2.0)
            } else {
                Triple((1.0 - cosOmega) / 2.0, 1.0 - cosOmega, (1.0 - cosOmega) / 2.0)
            }
        val a0 = 1.0 + alpha
        val a1 = -(2.0 * cosOmega)
        val a2 = 1.0 - alpha

        return BiquadCoefficients(
            b0 = (b0 / a0).toFloat(),
            b1 = (b1 / a0).toFloat(),
            b2 = (b2 / a0).toFloat(),
            a1 = (a1 / a0).toFloat(),
            a2 = (a2 / a0).toFloat(),
        )
    }
}

/** Stateful direct-form-II-transposed biquad section - two floats of state, one multiply-add each. */
internal class Biquad(
    private val coefficients: BiquadCoefficients,
) {
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = coefficients.b0 * x + z1
        z1 = coefficients.b1 * x - coefficients.a1 * y + z2
        z2 = coefficients.b2 * x - coefficients.a2 * y
        return y
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}

package dev.repcounter.core.dsp

import kotlin.math.roundToLong

/**
 * Linear-interpolation [Resampler]. Assumes [TimedSample.tMs] in the input is monotonically
 * non-decreasing. Output starts at `input.first().tMs` and steps at a fixed period, so it never
 * shifts phase relative to the input regardless of which source samples were dropped.
 */
class LinearResampler : Resampler {
    override fun resample(
        input: List<TimedSample>,
        outputHz: Float,
    ): List<TimedSample> {
        require(outputHz > 0f) { "outputHz must be positive, was $outputHz" }
        if (input.size < 2) return input

        val periodMs = MILLIS_PER_SECOND / outputHz
        val startMs = input.first().tMs
        val endMs = input.last().tMs

        val output = mutableListOf<TimedSample>()
        var sourceIndex = 0
        var outputTMs = startMs.toDouble()
        while (outputTMs <= endMs.toDouble()) {
            val targetMs = outputTMs.roundToLong()
            while (sourceIndex < input.size - 2 && input[sourceIndex + 1].tMs < targetMs) {
                sourceIndex++
            }
            val left = input[sourceIndex]
            val right = input[(sourceIndex + 1).coerceAtMost(input.size - 1)]
            val value =
                if (right.tMs == left.tMs) {
                    left.value
                } else {
                    val fraction = (targetMs - left.tMs).toFloat() / (right.tMs - left.tMs).toFloat()
                    left.value + (right.value - left.value) * fraction
                }
            output += TimedSample(targetMs, value)
            outputTMs += periodMs
        }
        return output
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000f
    }
}

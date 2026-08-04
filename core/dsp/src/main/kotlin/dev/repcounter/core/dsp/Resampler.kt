package dev.repcounter.core.dsp

/**
 * Converts a non-uniform, monotonically-increasing sample series onto a fixed-rate grid via
 * linear interpolation. Required before any [StreamingFilter]/[CadenceEstimator] because both
 * assume a uniform sample period; also the mechanism that absorbs dropped camera frames.
 *
 * Implemented in M2 (`:core:dsp`); called from the signal pipeline off the camera thread.
 */
interface Resampler {
    /** Resamples [input] onto a fixed grid at [outputHz], starting at `input.first().tMs`. */
    fun resample(
        input: List<TimedSample>,
        outputHz: Float,
    ): List<TimedSample>
}

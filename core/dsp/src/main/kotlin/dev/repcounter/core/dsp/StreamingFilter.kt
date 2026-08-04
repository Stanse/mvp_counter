package dev.repcounter.core.dsp

/**
 * A causal, per-sample digital filter (Butterworth bandpass for oscillatory signals, One-Euro
 * for angular ones). Stateful; call [reset] between sessions/gaps rather than reconstructing.
 *
 * Implemented in M2 (`:core:dsp`); driven by the resampled 50 Hz signal stream.
 */
interface StreamingFilter {
    /** Filters one already-resampled sample and returns the filtered value. */
    fun apply(sample: TimedSample): Float

    fun reset()
}

package dev.repcounter.core.dsp

/** One detected peak in a streaming series. */
data class Peak(
    val tMs: Long,
    val amplitude: Float,
)

/**
 * Streaming FSM peak/cycle detector with hysteresis: threshold `k * RMS` over a running window,
 * refractory period derived from the current cadence estimate. One rise-then-fall cycle is one
 * [Peak]. See spec §8.1 step 3 for the jump-rope tuning (`refractory = 0.6 / f0`).
 *
 * Implemented in M2 (`:core:dsp`).
 */
interface PeakDetector {
    /** Feeds one detrended, filtered sample; returns a [Peak] if one completed at this sample. */
    fun process(sample: TimedSample): Peak?

    fun reset()
}

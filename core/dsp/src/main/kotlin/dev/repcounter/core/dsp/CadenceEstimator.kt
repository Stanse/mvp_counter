package dev.repcounter.core.dsp

/** A cadence estimate; `confidence` reflects how sharp the autocorrelation/Goertzel peak is. */
data class CadenceEstimate(
    val hz: Float,
    val confidence: Float,
)

/**
 * Estimates the dominant frequency of a signal over a sliding window (autocorrelation or
 * Goertzel, per spec §8.1 step 2). More robust than counting raw peaks and used to derive the
 * [PeakDetector] refractory period adaptively.
 *
 * Implemented in M2 (`:core:dsp`).
 */
interface CadenceEstimator {
    /** Feeds one sample; returns an updated estimate once enough history has accumulated. */
    fun update(sample: TimedSample): CadenceEstimate?

    fun reset()
}

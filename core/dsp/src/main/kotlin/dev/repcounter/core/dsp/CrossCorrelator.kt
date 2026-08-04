package dev.repcounter.core.dsp

/** Best-matching lag of `b` relative to `a`, and the normalized correlation at that lag (`-1..1`). */
data class LagResult(
    val lagMs: Long,
    val correlation: Float,
)

/**
 * Cross-correlates two equal-rate signal windows to find the lag that best aligns them. Used by
 * `TechniqueClassifier` (`:analysis:jumprope`) on `ANKLE_Y_L`/`ANKLE_Y_R` to tell `BOTH_FEET`
 * (lag ~= 0) from `ALTERNATING` (lag ~= half period) apart. See spec §8.1 step 5.
 *
 * Implemented in M2 (`:core:dsp`).
 */
interface CrossCorrelator {
    /** Both inputs must be sampled on the same uniform grid. [maxLagMs] bounds the search window. */
    fun correlate(
        a: List<TimedSample>,
        b: List<TimedSample>,
        maxLagMs: Long,
    ): LagResult
}

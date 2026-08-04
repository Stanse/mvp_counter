package dev.repcounter.core.model

/**
 * One resampled instant of the signal layer: named scalar values at [tMs].
 *
 * Produced by `SignalExtractor` at the source frame rate, then by `Resampler`/`FilterBank`
 * (`:core:dsp`) at a fixed 50 Hz grid before reaching `ExerciseAnalyzer` (`:analysis:*`).
 * Missing entries mean the active [dev.repcounter.core.model.LandmarkSchema] can't compute
 * that [SignalId] — see [LandmarkSchema.indexOf].
 */
data class SignalFrame(
    val tMs: Long,
    val values: Map<SignalId, Float>,
)

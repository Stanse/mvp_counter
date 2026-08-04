package dev.repcounter.core.model

/**
 * Output of `ExerciseAnalyzer.process()` (`:analysis:*`), consumed by `SessionAggregator`
 * (`:data`/`:feature:workout`) to drive counters, technique segments and UI state.
 */
sealed interface AnalyzerEvent {
    /** One counted repetition. */
    data class Rep(
        val index: Int,
        val tMs: Long,
        val confidence: Float,
        /** Free-form numeric metadata, e.g. `"amplitude"`, `"durationMs"`. */
        val meta: Map<String, Float> = emptyMap(),
    ) : AnalyzerEvent

    /** The active technique changed, e.g. jump rope `BOTH_FEET` -> `ALTERNATING`. */
    data class TechniqueChanged(
        val technique: String,
        val tMs: Long,
    ) : AnalyzerEvent

    /** Cadence estimate update, in Hz. */
    data class CadenceUpdated(
        val hz: Float,
        val tMs: Long,
    ) : AnalyzerEvent

    /** A capture-quality problem was detected; the count should not advance while active. */
    data class QualityIssue(
        val kind: QualityKind,
        val tMs: Long,
    ) : AnalyzerEvent
}

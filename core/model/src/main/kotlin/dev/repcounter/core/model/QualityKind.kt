package dev.repcounter.core.model

/** Human-actionable capture-quality problems emitted as [AnalyzerEvent.QualityIssue]. */
enum class QualityKind {
    NO_PERSON,
    LOW_CONFIDENCE,
    FEET_OUT_OF_FRAME,
    LOW_FRAMERATE,
    TOO_FAR,
    TOO_CLOSE,
}

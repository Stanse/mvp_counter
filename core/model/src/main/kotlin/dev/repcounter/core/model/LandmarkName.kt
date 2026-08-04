package dev.repcounter.core.model

/**
 * Semantic body-point identifiers used by [dev.repcounter.core.model.LandmarkSchema] to
 * decouple `SignalExtractor` (in `:signals`) from the index layout of any specific pose model.
 */
enum class LandmarkName {
    NOSE,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
    LEFT_FOOT_INDEX,
    RIGHT_FOOT_INDEX,
}

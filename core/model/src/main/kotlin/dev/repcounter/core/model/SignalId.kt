package dev.repcounter.core.model

/**
 * Identifiers for the named one-dimensional time series `SignalExtractor` (`:signals`) computes
 * from a normalized skeleton. Exercise analyzers (`:analysis:*`) declare which of these they
 * need via `ExerciseDescriptor.requiredSignals` and never see landmarks directly.
 */
enum class SignalId {
    HIP_Y,
    SHOULDER_Y,
    ANKLE_Y_L,
    ANKLE_Y_R,
    KNEE_ANGLE_L,
    KNEE_ANGLE_R,
    KNEE_ANGLE_MEAN,
    ELBOW_ANGLE_L,
    ELBOW_ANGLE_R,
    ELBOW_ANGLE_MEAN,
    TORSO_TILT,
}

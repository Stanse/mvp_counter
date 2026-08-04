package dev.repcounter.core.model

import kotlinx.serialization.Serializable

/**
 * A single body keypoint as reported by a [dev.repcounter.pose.api.PoseDetector].
 *
 * Coordinates in [dev.repcounter.core.model.PoseFrame.landmarks] are normalized to `0..1`
 * with `y` growing downward (image space). Coordinates in
 * [dev.repcounter.core.model.PoseFrame.world] are metric, origin at the hip center.
 */
@Serializable
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
)

package dev.repcounter.core.model

import kotlinx.serialization.Serializable

/**
 * One frame with a recognized pose.
 *
 * Produced by `PoseDetector.detect()` (`:pose:api`) on the pose-inference dispatcher, consumed
 * by `PoseNormalizer`/`SignalExtractor` (`:signals`) and recorded verbatim by `TraceRecorder`
 * (`:capture`) for replay tests.
 */
@Serializable
data class PoseFrame(
    /** Timestamp from the source frame (`ImageProxy.imageInfo.timestamp` in production, or the
     * recorded trace timestamp in replay). Never wall-clock time — see §7.2 of the spec. */
    val tMs: Long,
    /** Normalized `0..1` image-space landmarks, `y` growing downward. */
    val landmarks: List<Landmark>,
    /** Metric landmarks with origin at the hip center, or `null` if the model doesn't provide them. */
    val world: List<Landmark>?,
    /** Aggregate `0..1` confidence over key landmarks. */
    val quality: Float,
)

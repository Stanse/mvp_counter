package dev.repcounter.signals

import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.LandmarkName
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.core.model.PoseFrame
import kotlin.math.hypot

/**
 * Scale-normalizes a raw [PoseFrame]'s image-space landmarks by torso length (so a signal like
 * `HIP_Y` reads the same whether the camera is 1.5 m or 3 m away) and holds each landmark's last
 * known-good position across frames where its `visibility` drops too low to trust - spec §7's
 * "сглаживание пропавших точек". Stateful; call [reset] between sessions/gaps.
 *
 * Caller: the signal pipeline (off the camera thread), one frame at a time, in frame order.
 */
interface PoseNormalizer {
    fun normalize(
        frame: PoseFrame,
        schema: LandmarkSchema,
    ): PoseFrame

    fun reset()
}

/**
 * Deliberately does **not** translate landmarks so the hip center sits at `(0, 0)`, even though
 * spec §7's pipeline diagram says "центр таза -> 0". Every [dev.repcounter.core.model.SignalId]
 * either needs the (translation-invariant) angle between two limb segments, or - per §8.1's own
 * worked formula, "`HIP_Y` (среднее по бёдрам, нормализовано на длину торса)" - the *scale*-only
 * normalized vertical position. Subtracting the hip center every frame would make `HIP_Y`
 * identically zero, which breaks the periodic detector it feeds. See `docs/DECISIONS.md`.
 */
class ScaleOnlyPoseNormalizer(
    private val minVisibility: Float = DEFAULT_MIN_VISIBILITY,
) : PoseNormalizer {
    private var lastGood: List<Landmark>? = null

    override fun normalize(
        frame: PoseFrame,
        schema: LandmarkSchema,
    ): PoseFrame {
        val held = holdMissing(frame.landmarks)
        lastGood = held

        val torsoLength = torsoLength(held, schema) ?: return frame.copy(landmarks = held)
        val scaled =
            held.map { landmark ->
                landmark.copy(
                    x = landmark.x / torsoLength,
                    y = landmark.y / torsoLength,
                    z = landmark.z / torsoLength,
                )
            }
        return frame.copy(landmarks = scaled)
    }

    override fun reset() {
        lastGood = null
    }

    private fun holdMissing(current: List<Landmark>): List<Landmark> {
        val previous = lastGood
        return current.mapIndexed { index, landmark ->
            if (landmark.visibility < minVisibility) {
                previous?.getOrNull(index) ?: landmark
            } else {
                landmark
            }
        }
    }

    @Suppress("ReturnCount")
    private fun torsoLength(
        landmarks: List<Landmark>,
        schema: LandmarkSchema,
    ): Float? {
        val hipCenter = midpoint(landmarks, schema, LandmarkName.LEFT_HIP, LandmarkName.RIGHT_HIP) ?: return null
        val shoulderCenter =
            midpoint(landmarks, schema, LandmarkName.LEFT_SHOULDER, LandmarkName.RIGHT_SHOULDER) ?: return null
        val length = hypot((shoulderCenter.x - hipCenter.x).toDouble(), (shoulderCenter.y - hipCenter.y).toDouble())
        return length.toFloat().coerceAtLeast(MIN_TORSO_LENGTH)
    }

    @Suppress("ReturnCount")
    private fun midpoint(
        landmarks: List<Landmark>,
        schema: LandmarkSchema,
        left: LandmarkName,
        right: LandmarkName,
    ): Landmark? {
        val a = schema.indexOf(left)?.let { landmarks.getOrNull(it) } ?: return null
        val b = schema.indexOf(right)?.let { landmarks.getOrNull(it) } ?: return null
        return Landmark(
            x = (a.x + b.x) / MIDPOINT_DIVISOR,
            y = (a.y + b.y) / MIDPOINT_DIVISOR,
            z = (a.z + b.z) / MIDPOINT_DIVISOR,
            visibility = minOf(a.visibility, b.visibility),
        )
    }

    private companion object {
        const val DEFAULT_MIN_VISIBILITY = 0.5f

        // A torso length below this (near-degenerate detection, landmarks collapsed to a point)
        // would blow up the scale division; treat it as this floor instead of dividing by ~0.
        const val MIN_TORSO_LENGTH = 0.01f
        const val MIDPOINT_DIVISOR = 2f
    }
}

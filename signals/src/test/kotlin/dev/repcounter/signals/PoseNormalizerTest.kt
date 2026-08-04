package dev.repcounter.signals

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.LandmarkName
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.core.model.PoseFrame
import org.junit.jupiter.api.Test

class PoseNormalizerTest {
    private val schema = LandmarkSchema.BLAZEPOSE_33

    @Test
    fun `scales every landmark by the torso length`() {
        val landmarks = blazePoseLandmarks(hipY = 0.6f, shoulderY = 0.2f)
        val frame = PoseFrame(tMs = 0, landmarks = landmarks, world = null, quality = 1f)
        val normalizer = ScaleOnlyPoseNormalizer()

        val normalized = normalizer.normalize(frame, schema)

        // Torso length is |0.6 - 0.2| = 0.4 (hips and shoulders both centered on x = 0.5).
        val hipIndex = schema.indexOf(LandmarkName.LEFT_HIP)!!
        assertThat(normalized.landmarks[hipIndex].y).isWithin(1e-4f).of(0.6f / 0.4f)
    }

    @Test
    fun `holds the last known-good position for a landmark that drops below the visibility floor`() {
        val goodFrame =
            PoseFrame(
                tMs = 0,
                landmarks =
                    blazePoseLandmarks(
                        hipY = 0.6f,
                        shoulderY = 0.2f,
                        rightAnkleY = 0.9f,
                        rightAnkleVisibility = 1f,
                    ),
                world = null,
                quality = 1f,
            )
        val droppedFrame =
            PoseFrame(
                tMs = 33,
                landmarks =
                    blazePoseLandmarks(
                        hipY = 0.6f,
                        shoulderY = 0.2f,
                        rightAnkleY = 0.1f,
                        rightAnkleVisibility = 0.05f,
                    ),
                world = null,
                quality = 0.4f,
            )
        val normalizer = ScaleOnlyPoseNormalizer()
        normalizer.normalize(goodFrame, schema)

        val normalized = normalizer.normalize(droppedFrame, schema)

        val ankleIndex = schema.indexOf(LandmarkName.RIGHT_ANKLE)!!
        // Held at the last known-good raw y (0.9), then scaled by this frame's torso length (0.4).
        assertThat(normalized.landmarks[ankleIndex].y).isWithin(1e-4f).of(0.9f / 0.4f)
    }

    @Test
    fun `reset clears the held-landmark memory`() {
        val goodFrame =
            PoseFrame(
                tMs = 0,
                landmarks =
                    blazePoseLandmarks(
                        hipY = 0.6f,
                        shoulderY = 0.2f,
                        rightAnkleY = 0.9f,
                        rightAnkleVisibility = 1f,
                    ),
                world = null,
                quality = 1f,
            )
        val droppedFrame =
            PoseFrame(
                tMs = 33,
                landmarks =
                    blazePoseLandmarks(
                        hipY = 0.6f,
                        shoulderY = 0.2f,
                        rightAnkleY = 0.1f,
                        rightAnkleVisibility = 0.05f,
                    ),
                world = null,
                quality = 0.4f,
            )
        val normalizer = ScaleOnlyPoseNormalizer()
        normalizer.normalize(goodFrame, schema)
        normalizer.reset()

        val normalized = normalizer.normalize(droppedFrame, schema)

        val ankleIndex = schema.indexOf(LandmarkName.RIGHT_ANKLE)!!
        // No memory to hold from, so the (low-visibility) current value passes through as-is.
        assertThat(normalized.landmarks[ankleIndex].y).isWithin(1e-4f).of(0.1f / 0.4f)
    }

    private fun blazePoseLandmarks(
        hipY: Float,
        shoulderY: Float,
        rightAnkleY: Float = 0.9f,
        rightAnkleVisibility: Float = 1f,
    ): List<Landmark> {
        val default = Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 1f)
        val landmarks = MutableList(33) { default }
        landmarks[schema.indexOf(LandmarkName.LEFT_HIP)!!] = default.copy(y = hipY)
        landmarks[schema.indexOf(LandmarkName.RIGHT_HIP)!!] = default.copy(y = hipY)
        landmarks[schema.indexOf(LandmarkName.LEFT_SHOULDER)!!] = default.copy(y = shoulderY)
        landmarks[schema.indexOf(LandmarkName.RIGHT_SHOULDER)!!] = default.copy(y = shoulderY)
        landmarks[schema.indexOf(LandmarkName.RIGHT_ANKLE)!!] =
            default.copy(y = rightAnkleY, visibility = rightAnkleVisibility)
        return landmarks
    }
}

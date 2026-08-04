package dev.repcounter.signals

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.LandmarkName
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.core.model.PoseFrame
import dev.repcounter.core.model.SignalId
import org.junit.jupiter.api.Test

class SignalExtractorTest {
    private val schema = LandmarkSchema.BLAZEPOSE_33

    @Test
    fun `HIP_Y and SHOULDER_Y are the mean of the left-right pair`() {
        val landmarks = fullBody()
        set(landmarks, LandmarkName.LEFT_HIP, y = 0.5f)
        set(landmarks, LandmarkName.RIGHT_HIP, y = 0.7f)
        set(landmarks, LandmarkName.LEFT_SHOULDER, y = 0.1f)
        set(landmarks, LandmarkName.RIGHT_SHOULDER, y = 0.3f)

        val signals = SignalExtractor.extract(frame(landmarks), schema)

        assertThat(signals.values[SignalId.HIP_Y]).isWithin(1e-4f).of(0.6f)
        assertThat(signals.values[SignalId.SHOULDER_Y]).isWithin(1e-4f).of(0.2f)
    }

    @Test
    fun `a straight leg reads close to 180 degrees at the knee`() {
        val landmarks = fullBody()
        set(landmarks, LandmarkName.LEFT_HIP, x = 0.5f, y = 0.3f)
        set(landmarks, LandmarkName.LEFT_KNEE, x = 0.5f, y = 0.6f)
        set(landmarks, LandmarkName.LEFT_ANKLE, x = 0.5f, y = 0.9f)

        val signals = SignalExtractor.extract(frame(landmarks), schema)

        assertThat(signals.values.getValue(SignalId.KNEE_ANGLE_L)).isWithin(1f).of(180f)
    }

    @Test
    fun `a right-angle bend at the knee reads close to 90 degrees`() {
        val landmarks = fullBody()
        set(landmarks, LandmarkName.LEFT_HIP, x = 0.5f, y = 0.3f)
        set(landmarks, LandmarkName.LEFT_KNEE, x = 0.5f, y = 0.6f)
        set(landmarks, LandmarkName.LEFT_ANKLE, x = 0.8f, y = 0.6f)

        val signals = SignalExtractor.extract(frame(landmarks), schema)

        assertThat(signals.values.getValue(SignalId.KNEE_ANGLE_L)).isWithin(1f).of(90f)
    }

    @Test
    fun `TORSO_TILT reads close to 0 for a horizontal torso and close to 90 for an upright one`() {
        val horizontal = fullBody()
        set(horizontal, LandmarkName.LEFT_HIP, x = 0.2f, y = 0.5f)
        set(horizontal, LandmarkName.RIGHT_HIP, x = 0.2f, y = 0.5f)
        set(horizontal, LandmarkName.LEFT_SHOULDER, x = 0.8f, y = 0.5f)
        set(horizontal, LandmarkName.RIGHT_SHOULDER, x = 0.8f, y = 0.5f)

        val upright = fullBody()
        set(upright, LandmarkName.LEFT_HIP, x = 0.5f, y = 0.8f)
        set(upright, LandmarkName.RIGHT_HIP, x = 0.5f, y = 0.8f)
        set(upright, LandmarkName.LEFT_SHOULDER, x = 0.5f, y = 0.2f)
        set(upright, LandmarkName.RIGHT_SHOULDER, x = 0.5f, y = 0.2f)

        val horizontalSignals = SignalExtractor.extract(frame(horizontal), schema)
        val uprightSignals = SignalExtractor.extract(frame(upright), schema)

        assertThat(horizontalSignals.values.getValue(SignalId.TORSO_TILT)).isWithin(1f).of(0f)
        assertThat(uprightSignals.values.getValue(SignalId.TORSO_TILT)).isWithin(1f).of(90f)
    }

    @Test
    fun `a signal whose landmarks are missing from this frame is simply absent, not zero`() {
        val shortLandmarks = fullBody().take(schema.indexOf(LandmarkName.LEFT_SHOULDER)!!)

        val signals = SignalExtractor.extract(frame(shortLandmarks), schema)

        assertThat(signals.values).doesNotContainKey(SignalId.SHOULDER_Y)
        assertThat(signals.values).doesNotContainKey(SignalId.TORSO_TILT)
    }

    private fun frame(landmarks: List<Landmark>) =
        PoseFrame(tMs = 100, landmarks = landmarks, world = null, quality = 1f)

    private fun fullBody(): MutableList<Landmark> =
        MutableList(33) {
            Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 1f)
        }

    private fun set(
        landmarks: MutableList<Landmark>,
        name: LandmarkName,
        x: Float = 0.5f,
        y: Float = 0.5f,
    ) {
        landmarks[schema.indexOf(name)!!] = Landmark(x = x, y = y, z = 0f, visibility = 1f)
    }
}

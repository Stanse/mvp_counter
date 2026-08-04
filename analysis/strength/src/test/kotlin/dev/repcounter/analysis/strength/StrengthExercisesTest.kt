package dev.repcounter.analysis.strength

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId
import org.junit.jupiter.api.Test

class StrengthExercisesTest {
    @Test
    fun `squat requires only the knee angle`() {
        assertThat(
            StrengthExercises.squat
                .let { ThresholdAnalyzerFactory(it) }
                .descriptor.requiredSignals,
        ).containsExactly(SignalId.KNEE_ANGLE_MEAN)
    }

    @Test
    fun `pushup requires the elbow angle and its torso-horizontal gate signal`() {
        assertThat(
            StrengthExercises.pushup
                .let { ThresholdAnalyzerFactory(it) }
                .descriptor.requiredSignals,
        ).containsExactly(SignalId.ELBOW_ANGLE_MEAN, SignalId.TORSO_TILT)
    }

    @Test
    fun `each factory creates a fresh, independently-stateful analyzer`() {
        val factory = ThresholdAnalyzerFactory(StrengthExercises.squat)
        val first = factory.create()
        val second = factory.create()

        squatCycle().forEach { first.process(it) }

        assertThat(second.process(SignalFrame(0, mapOf(SignalId.KNEE_ANGLE_MEAN to 170f)))).isEmpty()
    }

    @Test
    fun `a full squat cycle through the registered config counts one rep`() {
        val analyzer = ThresholdAnalyzerFactory(StrengthExercises.squat).create()

        val events = squatCycle().flatMap { analyzer.process(it) }

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).hasSize(1)
    }

    @Test
    fun `a pushup cycle only counts while the torso stays roughly horizontal`() {
        val analyzer = ThresholdAnalyzerFactory(StrengthExercises.pushup).create()
        val angles = listOf(170f, 90f, 170f)

        val events =
            angles
                .mapIndexed { i, angle ->
                    SignalFrame(
                        (i * 500).toLong(),
                        mapOf(SignalId.ELBOW_ANGLE_MEAN to angle, SignalId.TORSO_TILT to 10f),
                    )
                }.flatMap { analyzer.process(it) }

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).hasSize(1)
    }

    private fun squatCycle(): List<SignalFrame> =
        listOf(170f, 90f, 170f).mapIndexed { i, angle ->
            SignalFrame((i * 600).toLong(), mapOf(SignalId.KNEE_ANGLE_MEAN to angle))
        }
}

package dev.repcounter.analysis.jumprope

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class CrossCorrelationTechniqueClassifierTest {
    private val sampleRateHz = 50f
    private val frequencyHz = 2.5f

    @Test
    fun `ankles moving in phase classify as BOTH_FEET`() {
        val classifier = CrossCorrelationTechniqueClassifier()

        val results = classifier.classifyAll(ankleFrames(lagSamples = 0))

        assertThat(results.filterNotNull()).isNotEmpty()
        assertThat(results.filterNotNull().last()).isEqualTo(JumpRopeTechnique.BOTH_FEET)
    }

    @Test
    fun `ankles half a period out of phase classify as ALTERNATING`() {
        val classifier = CrossCorrelationTechniqueClassifier()
        val halfPeriodSamples = (sampleRateHz / frequencyHz / 2).toInt()

        val results = classifier.classifyAll(ankleFrames(lagSamples = halfPeriodSamples))

        assertThat(results.filterNotNull().last()).isEqualTo(JumpRopeTechnique.ALTERNATING)
    }

    @Test
    fun `a still right ankle with an active left ankle classifies as SINGLE_LEFT`() {
        val classifier = CrossCorrelationTechniqueClassifier()

        val results =
            classifier.classifyAll(ankleFrames(lagSamples = 0, rightAmplitude = 0f))

        assertThat(results.filterNotNull().last()).isEqualTo(JumpRopeTechnique.SINGLE_LEFT)
    }

    @Test
    fun `a still left ankle with an active right ankle classifies as SINGLE_RIGHT`() {
        val classifier = CrossCorrelationTechniqueClassifier()

        val results =
            classifier.classifyAll(ankleFrames(lagSamples = 0, leftAmplitude = 0f))

        assertThat(results.filterNotNull().last()).isEqualTo(JumpRopeTechnique.SINGLE_RIGHT)
    }

    @Test
    fun `no data before the window fills yields null`() {
        val classifier = CrossCorrelationTechniqueClassifier()

        val results = classifier.classifyAll(ankleFrames(lagSamples = 0, durationMs = 300))

        assertThat(results).doesNotContain(JumpRopeTechnique.BOTH_FEET)
    }

    @Test
    fun `reset forgets the window so a new pattern needs to fill it again`() {
        val classifier = CrossCorrelationTechniqueClassifier()
        classifier.classifyAll(ankleFrames(lagSamples = 0))

        classifier.reset()
        val firstFewAfterReset = ankleFrames(lagSamples = 0, durationMs = 200).map { classifier.classify(it) }

        assertThat(firstFewAfterReset).containsExactly(null, null, null, null, null, null, null, null, null, null)
    }

    private fun CrossCorrelationTechniqueClassifier.classifyAll(frames: List<SignalFrame>) = frames.map { classify(it) }

    private fun ankleFrames(
        lagSamples: Int,
        leftAmplitude: Float = 0.1f,
        rightAmplitude: Float = 0.1f,
        durationMs: Long = 4000,
    ): List<SignalFrame> {
        val periodMs = 1000.0 / sampleRateHz
        val sampleCount = (durationMs / periodMs).toInt()
        return List(sampleCount) { i ->
            val tMs = (i * periodMs).toLong()
            val left = leftAmplitude * sin(2.0 * PI * frequencyHz * tMs / 1000.0).toFloat()
            val shiftedMs = tMs - lagSamples * periodMs
            val right = rightAmplitude * sin(2.0 * PI * frequencyHz * shiftedMs / 1000.0).toFloat()
            SignalFrame(tMs, mapOf(SignalId.ANKLE_Y_L to left, SignalId.ANKLE_Y_R to right))
        }
    }
}

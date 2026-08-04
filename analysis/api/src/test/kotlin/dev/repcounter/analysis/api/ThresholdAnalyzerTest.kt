package dev.repcounter.analysis.api

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId
import org.junit.jupiter.api.Test

class ThresholdAnalyzerTest {
    private val squatConfig =
        ThresholdAnalyzerConfig(
            id = "squat",
            displayName = "Squat",
            signal = SignalId.KNEE_ANGLE_MEAN,
            downBelow = 100f,
            upAbove = 160f,
            minRepMs = 500,
            minAmplitude = 40f,
            setupHint = "stand back",
            minFps = 12,
        )

    @Test
    fun `a full down-up cycle with enough time and range counts one rep`() {
        val analyzer = ThresholdAnalyzer(squatConfig)

        val events = feed(analyzer, angleTrace(startTMs = 0, stepMs = 600, angles = listOf(170f, 90f, 170f)))

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).hasSize(1)
    }

    @Test
    fun `bouncing near the down threshold during a rep produces exactly one rep, not several`() {
        // Once the FSM has crossed downBelow and moved into WAITING_FOR_UP, it stays there
        // through repeated dips back toward (but never reaching) upAbove - spec §10's "дребезг
        // вокруг порога не должен давать лишних повторов". A naive re-arm-on-every-crossing FSM
        // would count several reps here; this one should count exactly the one genuine cycle.
        val analyzer = ThresholdAnalyzer(squatConfig)

        val events =
            feed(
                analyzer,
                angleTrace(
                    startTMs = 0,
                    stepMs = 200,
                    angles = listOf(170f, 95f, 105f, 95f, 105f, 95f, 170f),
                ),
            )

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).hasSize(1)
    }

    @Test
    fun `a rep faster than minRepMs is rejected`() {
        val analyzer = ThresholdAnalyzer(squatConfig)

        // Same down-up shape as the passing case, but compressed into 20 ms (vs. the
        // required 500 ms) between the down and up crossings.
        val events = feed(analyzer, angleTrace(startTMs = 0, stepMs = 10, angles = listOf(170f, 90f, 170f)))

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).isEmpty()
    }

    @Test
    fun `a rep that doesn't reach minAmplitude is rejected`() {
        val shallowConfig = squatConfig.copy(downBelow = 140f)
        val analyzer = ThresholdAnalyzer(shallowConfig)

        // Only dips to 135 (amplitude 170-135=35 < minAmplitude 40), even though it does cross
        // both thresholds and takes long enough (600 ms >= minRepMs 500 ms).
        val events = feed(analyzer, angleTrace(startTMs = 0, stepMs = 600, angles = listOf(170f, 135f, 170f)))

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).isEmpty()
    }

    @Test
    fun `a closed gate blocks counting even through a valid threshold crossing`() {
        val pushupConfig =
            ThresholdAnalyzerConfig(
                id = "pushup",
                displayName = "Pushup",
                signal = SignalId.ELBOW_ANGLE_MEAN,
                downBelow = 100f,
                upAbove = 160f,
                minRepMs = 400,
                minAmplitude = 35f,
                setupHint = "get on the floor",
                minFps = 12,
                gate = Gate.TorsoHorizontal(maxTiltDeg = 35f),
            )
        val analyzer = ThresholdAnalyzer(pushupConfig)

        val events =
            angleTrace(startTMs = 0, stepMs = 500, angles = listOf(170f, 90f, 170f), signal = SignalId.ELBOW_ANGLE_MEAN)
                .map { it.copy(values = it.values + (SignalId.TORSO_TILT to 90f)) }
                .flatMap { analyzer.process(it) }

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).isEmpty()
    }

    @Test
    fun `reset clears rep count and in-flight phase`() {
        val analyzer = ThresholdAnalyzer(squatConfig)
        feed(analyzer, angleTrace(startTMs = 0, stepMs = 600, angles = listOf(170f, 90f)))

        analyzer.reset()
        val events = feed(analyzer, angleTrace(startTMs = 10_000, stepMs = 600, angles = listOf(170f, 90f, 170f)))

        val rep = events.filterIsInstance<AnalyzerEvent.Rep>().single()
        assertThat(rep.index).isEqualTo(0)
    }

    private fun feed(
        analyzer: ThresholdAnalyzer,
        frames: List<SignalFrame>,
    ): List<AnalyzerEvent> = frames.flatMap { analyzer.process(it) }

    private fun angleTrace(
        startTMs: Long,
        stepMs: Long,
        angles: List<Float>,
        signal: SignalId = SignalId.KNEE_ANGLE_MEAN,
    ): List<SignalFrame> = angles.mapIndexed { i, angle -> SignalFrame(startTMs + i * stepMs, mapOf(signal to angle)) }
}

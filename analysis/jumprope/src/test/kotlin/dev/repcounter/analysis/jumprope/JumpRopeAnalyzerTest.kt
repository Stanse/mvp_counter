package dev.repcounter.analysis.jumprope

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class JumpRopeAnalyzerTest {
    private val sampleRateHz = 50f

    @Test
    fun `a steady jump-rope session counts a plausible number of reps`() {
        val analyzer = JumpRopeAnalyzer(sampleRateHz)
        val frequencyHz = 2.5f
        val durationMs = 12_000L
        val totalCycles = (durationMs / 1000f * frequencyHz).toInt()

        val events = jumpRopeFrames(durationMs, frequencyHz).flatMap { analyzer.process(it) }
        val reps = events.filterIsInstance<AnalyzerEvent.Rep>()

        // Filter/RMS-window warmup and the activity gate's own lead-in necessarily eat a handful
        // of the leading cycles - see docs/DECISIONS.md. Assert a plausible range, not an exact
        // count, since the exact loss depends on several interacting DSP stages settling.
        assertThat(reps.size).isAtLeast((totalCycles * 0.6).toInt())
        assertThat(reps.size).isAtMost(totalCycles)
    }

    @Test
    fun `rep indices are contiguous starting at 0, with the gate backfilling several at once`() {
        val analyzer = JumpRopeAnalyzer(sampleRateHz)

        val perFrameEvents = jumpRopeFrames(durationMs = 8000, frequencyHz = 2.5f).map { analyzer.process(it) }
        val reps = perFrameEvents.flatten().filterIsInstance<AnalyzerEvent.Rep>()

        assertThat(reps.map { it.index }).isEqualTo(reps.indices.toList())
        val batchSizes = perFrameEvents.map { it.filterIsInstance<AnalyzerEvent.Rep>().size }
        assertThat(batchSizes.max()).isAtLeast(2)
    }

    @Test
    fun `low-amplitude motion never produces a rep`() {
        val analyzer = JumpRopeAnalyzer(sampleRateHz)

        val events =
            jumpRopeFrames(durationMs = 10_000, frequencyHz = 0.9f, hipAmplitude = 0.01f, ankleAmplitude = 0.01f)
                .flatMap { analyzer.process(it) }

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).isEmpty()
    }

    @Test
    fun `in-phase ankles eventually report BOTH_FEET`() {
        val analyzer = JumpRopeAnalyzer(sampleRateHz)

        val events =
            jumpRopeFrames(
                durationMs = 6000,
                frequencyHz = 2.5f,
                ankleLagSamples = 0,
            ).flatMap { analyzer.process(it) }

        val techniques = events.filterIsInstance<AnalyzerEvent.TechniqueChanged>()
        assertThat(techniques).isNotEmpty()
        assertThat(techniques.last().technique).isEqualTo(JumpRopeTechnique.BOTH_FEET.name)
    }

    @Test
    fun `half-period-shifted ankles eventually report ALTERNATING`() {
        val analyzer = JumpRopeAnalyzer(sampleRateHz)
        val frequencyHz = 2.5f
        val halfPeriodSamples = (sampleRateHz / frequencyHz / 2).toInt()

        val events =
            jumpRopeFrames(durationMs = 6000, frequencyHz = frequencyHz, ankleLagSamples = halfPeriodSamples)
                .flatMap { analyzer.process(it) }

        val techniques = events.filterIsInstance<AnalyzerEvent.TechniqueChanged>()
        assertThat(techniques).isNotEmpty()
        assertThat(techniques.last().technique).isEqualTo(JumpRopeTechnique.ALTERNATING.name)
    }

    @Test
    fun `reset clears rep counting and gate state for a fresh session`() {
        val analyzer = JumpRopeAnalyzer(sampleRateHz)
        jumpRopeFrames(durationMs = 6000, frequencyHz = 2.5f).forEach { analyzer.process(it) }

        analyzer.reset()
        val events = jumpRopeFrames(durationMs = 12_000, frequencyHz = 2.5f).flatMap { analyzer.process(it) }

        val reps = events.filterIsInstance<AnalyzerEvent.Rep>()
        assertThat(reps.first().index).isEqualTo(0)
    }

    private fun jumpRopeFrames(
        durationMs: Long,
        frequencyHz: Float,
        hipAmplitude: Float = 0.1f,
        ankleAmplitude: Float = 0.1f,
        ankleLagSamples: Int = 0,
    ): List<SignalFrame> {
        val periodMs = 1000.0 / sampleRateHz
        val sampleCount = (durationMs / periodMs).toInt()
        return List(sampleCount) { i ->
            val tMs = (i * periodMs).toLong()
            val hipY = hipAmplitude * sin(2.0 * PI * frequencyHz * tMs / 1000.0).toFloat()
            val left = ankleAmplitude * sin(2.0 * PI * frequencyHz * tMs / 1000.0).toFloat()
            val shiftedMs = tMs - ankleLagSamples * periodMs
            val right = ankleAmplitude * sin(2.0 * PI * frequencyHz * shiftedMs / 1000.0).toFloat()
            SignalFrame(
                tMs,
                mapOf(
                    SignalId.HIP_Y to hipY,
                    SignalId.ANKLE_Y_L to left,
                    SignalId.ANKLE_Y_R to right,
                    SignalId.SHOULDER_Y to 0f,
                ),
            )
        }
    }
}

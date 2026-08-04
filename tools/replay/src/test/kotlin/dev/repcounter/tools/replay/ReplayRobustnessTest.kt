package dev.repcounter.tools.replay

import com.google.common.truth.Truth.assertThat
import dev.repcounter.analysis.jumprope.JumpRopeAnalyzerFactory
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.LandmarkName
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.core.model.PoseFrame
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Spec §10's robustness list: "полное отсутствие человека, обрыв потока кадров на 2 с, quality
 * ниже порога, скачок tMs назад, дублирующиеся timestamp'ы. Пайплайн не должен падать и не
 * должен накручивать счётчик." None of these should throw, and none should inflate the rep
 * count.
 */
class ReplayRobustnessTest {
    private val schema = LandmarkSchema.BLAZEPOSE_33

    @Test
    fun `a session with no person at all produces no reps and does not crash`() {
        val frames =
            (0 until 300).map { i ->
                PoseFrame(tMs = i * 20L, landmarks = emptyList(), world = null, quality = 0f)
            }

        val events = ReplayPipeline.run(frames, schema, JumpRopeAnalyzerFactory().create())

        assertThat(events.filterIsInstance<AnalyzerEvent.Rep>()).isEmpty()
    }

    @Test
    fun `a 2-second gap in the frame stream does not crash and does not inflate the count`() {
        val before = jumpRopeFrames(startTMs = 0, durationMs = 3000)
        val after = jumpRopeFrames(startTMs = 5000, durationMs = 3000) // a 2 s hole between 3000 and 5000
        val frames = before + after

        val events = ReplayPipeline.run(frames, schema, JumpRopeAnalyzerFactory().create())

        val reps = events.filterIsInstance<AnalyzerEvent.Rep>()
        // Loose upper bound: what physically fits in 6 s of real jump-rope frames, generously
        // rounded up. The point of this test is "didn't explode", not an exact count.
        assertThat(reps.size).isAtMost(20)
    }

    @Test
    fun `quality below any threshold still runs without crashing`() {
        val frames = jumpRopeFrames(startTMs = 0, durationMs = 4000, quality = 0.01f)

        val events = ReplayPipeline.run(frames, schema, JumpRopeAnalyzerFactory().create())

        // The pipeline doesn't gate on PoseFrame.quality yet (deferred - see docs/DECISIONS.md);
        // this test only pins down that a low-quality stream is handled, not thrown away.
        assertThat(events).isNotNull()
    }

    @Test
    fun `a backward jump in tMs is dropped, not crashed on or double-counted`() {
        val frames = jumpRopeFrames(startTMs = 0, durationMs = 4000)
        val withBackwardsJump =
            frames.subList(0, frames.size / 2) +
                listOf(frames[2].copy(tMs = frames[2].tMs - 500)) +
                frames.subList(frames.size / 2, frames.size)

        val cleanEvents = ReplayPipeline.run(frames, schema, JumpRopeAnalyzerFactory().create())
        val jumpedEvents = ReplayPipeline.run(withBackwardsJump, schema, JumpRopeAnalyzerFactory().create())

        assertThat(jumpedEvents.filterIsInstance<AnalyzerEvent.Rep>())
            .isEqualTo(cleanEvents.filterIsInstance<AnalyzerEvent.Rep>())
    }

    @Test
    fun `duplicate timestamps are dropped, not double-counted`() {
        val frames = jumpRopeFrames(startTMs = 0, durationMs = 4000)
        val withDuplicate = frames.subList(0, 10) + listOf(frames[9]) + frames.subList(10, frames.size)

        val cleanEvents = ReplayPipeline.run(frames, schema, JumpRopeAnalyzerFactory().create())
        val duplicatedEvents = ReplayPipeline.run(withDuplicate, schema, JumpRopeAnalyzerFactory().create())

        assertThat(duplicatedEvents.filterIsInstance<AnalyzerEvent.Rep>())
            .isEqualTo(cleanEvents.filterIsInstance<AnalyzerEvent.Rep>())
    }

    private fun jumpRopeFrames(
        startTMs: Long,
        durationMs: Long,
        frequencyHz: Float = 2.5f,
        quality: Float = 0.95f,
    ): List<PoseFrame> {
        val fps = 30f
        val periodMs = 1000.0 / fps
        val sampleCount = (durationMs / periodMs).toInt()
        val hipBaselineY = 0.55f
        val shoulderBaselineY = 0.25f
        val ankleBaselineY = 0.9f
        val amplitude = 0.03f

        return List(sampleCount) { i ->
            val elapsedMs = i * periodMs
            val tMs = startTMs + elapsedMs.toLong()
            val phase = 2.0 * PI * frequencyHz * elapsedMs / 1000.0
            val offset = (amplitude * sin(phase)).toFloat()

            val landmarks = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f) }
            set(landmarks, LandmarkName.LEFT_HIP, hipBaselineY - offset)
            set(landmarks, LandmarkName.RIGHT_HIP, hipBaselineY - offset)
            set(landmarks, LandmarkName.LEFT_SHOULDER, shoulderBaselineY - offset)
            set(landmarks, LandmarkName.RIGHT_SHOULDER, shoulderBaselineY - offset)
            set(landmarks, LandmarkName.LEFT_ANKLE, ankleBaselineY - offset)
            set(landmarks, LandmarkName.RIGHT_ANKLE, ankleBaselineY - offset)
            PoseFrame(tMs = tMs, landmarks = landmarks, world = null, quality = quality)
        }
    }

    private fun set(
        landmarks: MutableList<Landmark>,
        name: LandmarkName,
        y: Float,
    ) {
        val index = schema.indexOf(name) ?: return
        landmarks[index] = Landmark(x = 0.5f, y = y, z = 0f, visibility = 1f)
    }
}

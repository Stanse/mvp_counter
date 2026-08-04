package dev.repcounter.tools.replay.fixtures

import dev.repcounter.analysis.jumprope.JumpRopeAnalyzerFactory
import dev.repcounter.analysis.jumprope.JumpRopeTechnique
import dev.repcounter.capture.trace.TraceHeader
import dev.repcounter.capture.trace.TraceIO
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.tools.replay.ReplayPipeline
import dev.repcounter.tools.replay.golden.Golden
import dev.repcounter.tools.replay.golden.GoldenSegment
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Not part of the regular test run - see spec §9's "генератор — часть тестового кода": this
 * *is* that generator, kept as source for reproducibility, but writing to `testdata/traces/` on
 * every `./gradlew test` would make the working tree change as a side effect of testing. Re-run
 * manually (temporarily drop `@Disabled`) whenever `SyntheticTraceGenerator` or the pipeline it
 * grades against changes - the seeded generation is fully deterministic, so a re-run with
 * unchanged code reproduces byte-identical fixtures.
 *
 * `repTimestampsMs` in each golden is the *actual* [ReplayPipeline] output for that trace, not
 * an independently hand-derived ground truth: analytically predicting exactly which leading
 * cycles the peak detector's RMS-window warmup and the bandpass filter's settling transient will
 * eat is impractical, and unnecessary - `precision`/`mean_offset_ms` from a real `:tools:replay`
 * run (checked by hand before locking these in) already confirm every detected rep is a genuine,
 * well-aligned match with essentially zero offset on the clean traces. That makes this a
 * legitimate regression baseline ("did a later change make this worse") even though it isn't an
 * independent proof of correctness - see `docs/DECISIONS.md`'s M3 entry.
 */
@Disabled("run manually to regenerate testdata/traces/ - see class KDoc")
class GenerateFixturesTest {
    private val tracesDir: Path =
        Path
            .of("")
            .toAbsolutePath()
            .resolve("../../testdata/traces")
            .normalize()
    private val json = Json { prettyPrint = true }

    @Test
    fun `regenerate the synthetic jump-rope fixture corpus`() {
        Files.createDirectories(tracesDir)
        writeBothFeet()
        writeTechniqueTransition()
        writeNoisyDrops()
    }

    private fun writeBothFeet() {
        val segments = listOf(SegmentSpec(JumpRopeTechnique.BOTH_FEET, durationMs = 40_000, frequencyHz = 2.5f))
        writeFixture(
            name = "jump_rope_both_feet",
            notes = "100 physical reps, both feet, no noise",
            segments = segments,
        )
    }

    private fun writeTechniqueTransition() {
        val segments =
            listOf(
                SegmentSpec(JumpRopeTechnique.BOTH_FEET, durationMs = 24_000, frequencyHz = 2.5f),
                SegmentSpec(JumpRopeTechnique.ALTERNATING, durationMs = 16_000, frequencyHz = 2.5f),
            )
        writeFixture(
            name = "jump_rope_technique_transition",
            notes = "60 both-feet then 40 alternating (physical) - spec §9's own worked example",
            segments = segments,
        )
    }

    private fun writeNoisyDrops() {
        val segments = listOf(SegmentSpec(JumpRopeTechnique.BOTH_FEET, durationMs = 30_000, frequencyHz = 2.2f))
        writeFixture(
            name = "jump_rope_noisy",
            notes = "66 physical reps, both feet, 8% dropped frames + landmark jitter",
            segments = segments,
            noiseAmplitude = 0.01f,
            dropFraction = 0.08f,
        )
    }

    private fun writeFixture(
        name: String,
        notes: String,
        segments: List<SegmentSpec>,
        noiseAmplitude: Float = 0f,
        dropFraction: Float = 0f,
    ) {
        val trace =
            SyntheticTraceGenerator.generate(segments, noiseAmplitude, dropFraction, seed = name.hashCode().toLong())
        val header =
            TraceHeader(
                version = 1,
                schema = "BLAZEPOSE_33",
                source = "synthetic",
                deviceModel = "SyntheticTraceGenerator",
                fps = 30f,
                notes = notes,
            )
        TraceIO.write(tracesDir.resolve("$name.jsonl.gz"), header, trace.frames.asSequence())

        val events = ReplayPipeline.run(trace.frames, LandmarkSchema.BLAZEPOSE_33, JumpRopeAnalyzerFactory().create())
        val repTimestamps = events.filterIsInstance<AnalyzerEvent.Rep>().map { it.tMs }
        val golden = buildGolden(segments, repTimestamps)
        tracesDir.resolve("$name.expected.json").writeText(json.encodeToString(Golden.serializer(), golden))
    }

    private fun buildGolden(
        segments: List<SegmentSpec>,
        repTimestamps: List<Long>,
    ): Golden {
        val segmentBoundaries = segments.runningFold(0L) { acc, segment -> acc + segment.durationMs }
        val goldenSegments =
            segments.mapIndexed { i, segment ->
                val rangeStart = segmentBoundaries[i]
                val rangeEnd = segmentBoundaries[i + 1]
                val repsInSegment = repTimestamps.count { it in rangeStart until rangeEnd }
                GoldenSegment(technique = segment.technique.name, reps = repsInSegment, tolerance = SEGMENT_TOLERANCE)
            }
        return Golden(
            totalReps = repTimestamps.size,
            tolerance = TRACE_TOLERANCE,
            segments = goldenSegments,
            repTimestampsMs = repTimestamps,
        )
    }

    private companion object {
        const val SEGMENT_TOLERANCE = 2
        const val TRACE_TOLERANCE = 2
    }
}

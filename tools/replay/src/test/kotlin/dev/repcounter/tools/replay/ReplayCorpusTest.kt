package dev.repcounter.tools.replay

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.repcounter.analysis.jumprope.JumpRopeAnalyzerFactory
import dev.repcounter.capture.trace.TraceFrameSource
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.tools.replay.golden.Golden
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

/**
 * Level 2 (spec §10): `TraceFrameSource -> весь сигнальный пайплайн -> сравнение с golden`, no
 * Android/model/emulator, fully deterministic, parameterized over every file in
 * `testdata/traces/`. This is the corpus's main regression net going forward.
 */
class ReplayCorpusTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("traces")
    fun `replaying a trace reproduces its golden exactly`(tracePath: Path) {
        val name = tracePath.name.removeSuffix(".jsonl.gz")
        val golden = readGolden(tracePath)
        val counted = runTrace(tracePath)

        // Deterministic pipeline against a golden captured from this same pipeline (see
        // GenerateFixturesTest's KDoc) - anything other than an exact match is a regression.
        assertThat(counted).isEqualTo(golden.repTimestampsMs)

        val metrics = CorpusMetrics.match(name, golden.repTimestampsMs, counted)
        assertThat(metrics.absError).isAtMost(golden.tolerance)
        assertThat(metrics.f1).isAtLeast(F1_GATE)
    }

    @Test
    fun `the whole corpus passes the MAE and micro-F1 gate`() {
        val perTrace = CorpusRunner.run(tracesDir())
        assertThat(perTrace).isNotEmpty()

        val summary = CorpusMetrics.aggregate(perTrace)
        assertWithMessage("MAE=${summary.mae} microF1=${summary.microF1}, gate is MAE <= 2.0 and F1 >= 0.95")
            .that(summary.passes())
            .isTrue()
    }

    private fun runTrace(tracePath: Path): List<Long> {
        val source = TraceFrameSource(tracePath)
        val schema = LandmarkSchema.valueOf(source.header.schema)
        val poseFrames = runBlocking { source.poseFrames().toList() }
        val events = ReplayPipeline.run(poseFrames, schema, JumpRopeAnalyzerFactory().create())
        return events.filterIsInstance<AnalyzerEvent.Rep>().map { it.tMs }
    }

    private fun readGolden(tracePath: Path): Golden {
        val name = tracePath.name.removeSuffix(".jsonl.gz")
        val goldenPath = tracePath.resolveSibling("$name.expected.json")
        return json.decodeFromString(Golden.serializer(), goldenPath.readText())
    }

    private companion object {
        const val F1_GATE = 0.95

        val json = Json { ignoreUnknownKeys = true }

        fun tracesDir(): Path =
            Path
                .of("")
                .toAbsolutePath()
                .resolve("../../testdata/traces")
                .normalize()

        @JvmStatic
        fun traces(): List<Path> {
            val dir = tracesDir()
            if (!Files.isDirectory(dir)) return emptyList()
            return Files.list(dir).use { paths ->
                paths
                    .asSequence()
                    .filter { it.name.endsWith(".jsonl.gz") }
                    .sorted()
                    .toList()
            }
        }
    }
}

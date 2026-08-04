package dev.repcounter.tools.replay

import dev.repcounter.analysis.jumprope.JumpRopeAnalyzerFactory
import dev.repcounter.capture.trace.TraceFrameSource
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.tools.replay.golden.Golden
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

/** Runs every `<name>.jsonl.gz` / `<name>.expected.json` pair under [tracesDir] and grades it. */
object CorpusRunner {
    private val json = Json { ignoreUnknownKeys = true }

    fun run(tracesDir: Path): List<TraceMetrics> = listTraces(tracesDir).map { runOne(it) }.sortedBy { it.traceName }

    private fun listTraces(tracesDir: Path): List<Path> {
        if (!Files.isDirectory(tracesDir)) return emptyList()
        return Files.list(tracesDir).use { paths ->
            paths
                .asSequence()
                .filter { it.name.endsWith(".jsonl.gz") }
                .sorted()
                .toList()
        }
    }

    private fun runOne(tracePath: Path): TraceMetrics {
        val name = tracePath.name.removeSuffix(".jsonl.gz")
        val goldenPath = tracePath.resolveSibling("$name.expected.json")
        val golden = json.decodeFromString(Golden.serializer(), goldenPath.readText())

        val source = TraceFrameSource(tracePath)
        val schema = LandmarkSchema.valueOf(source.header.schema)
        val poseFrames = runBlocking { source.poseFrames().toList() }

        val events = ReplayPipeline.run(poseFrames, schema, JumpRopeAnalyzerFactory().create())
        val countedTimestamps = events.filterIsInstance<AnalyzerEvent.Rep>().map { it.tMs }

        return CorpusMetrics.match(name, golden.repTimestampsMs, countedTimestamps)
    }
}

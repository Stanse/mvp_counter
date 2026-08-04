package dev.repcounter.capture.trace

import dev.repcounter.core.model.PoseFrame
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Reads and writes the `.jsonl.gz` trace format (spec §9): a [TraceHeader] line followed by one
 * [TraceFrameLine] per recorded [PoseFrame]. Traces are small and human-diffable once
 * decompressed, which is why they're safe to commit (`testdata/traces/`, spec §16) unlike video.
 */
object TraceIO {
    private val json = Json { ignoreUnknownKeys = true }

    fun write(
        path: Path,
        header: TraceHeader,
        frames: Sequence<PoseFrame>,
    ) {
        Files.newOutputStream(path).use { fileOut ->
            GZIPOutputStream(fileOut).bufferedWriter().use { writer ->
                writer.appendLine(json.encodeToString(TraceHeader.serializer(), header))
                frames.forEach { frame ->
                    writer.appendLine(json.encodeToString(TraceFrameLine.serializer(), TraceFrameLine.from(frame)))
                }
            }
        }
    }

    fun readHeader(path: Path): TraceHeader = openReader(path).use { it.readLine().toHeader() }

    /** Lazily streams every frame line after the header; does not hold the whole trace in memory. */
    fun readFrames(path: Path): Sequence<PoseFrame> =
        sequence {
            openReader(path).use { reader ->
                reader.readLine() // header, already consumed by readHeader() when needed
                var line = reader.readLine()
                while (line != null) {
                    yield(line.toFrameLine().toPoseFrame())
                    line = reader.readLine()
                }
            }
        }

    private fun openReader(path: Path): BufferedReader =
        BufferedReader(InputStreamReader(GZIPInputStream(Files.newInputStream(path)), Charsets.UTF_8))

    private fun String.toHeader(): TraceHeader = json.decodeFromString(TraceHeader.serializer(), this)

    private fun String.toFrameLine(): TraceFrameLine = json.decodeFromString(TraceFrameLine.serializer(), this)
}

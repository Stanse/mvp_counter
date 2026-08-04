package dev.repcounter.capture.trace

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.PoseFrame
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TraceIOTest {
    @Test
    fun `a written trace reads back with the same header and frames`(
        @TempDir tempDir: Path,
    ) {
        val path = tempDir.resolve("sample.jsonl.gz")
        val header =
            TraceHeader(
                version = 1,
                schema = "BLAZEPOSE_33",
                source = "synthetic",
                deviceModel = "test-harness",
                fps = 30f,
                notes = "round-trip test",
            )
        val frames =
            listOf(
                PoseFrame(tMs = 0, landmarks = listOf(Landmark(0.1f, 0.2f, 0f, 0.9f)), world = null, quality = 0.95f),
                PoseFrame(
                    tMs = 33,
                    landmarks = listOf(Landmark(0.11f, 0.19f, 0f, 0.91f)),
                    world = null,
                    quality = 0.96f,
                ),
            )

        TraceIO.write(path, header, frames.asSequence())
        val readHeader = TraceIO.readHeader(path)
        val readFrames = TraceIO.readFrames(path).toList()

        assertThat(readHeader).isEqualTo(header)
        assertThat(readFrames).hasSize(2)
        assertThat(readFrames[0].tMs).isEqualTo(0L)
        assertThat(readFrames[0].landmarks[0].x).isWithin(1e-5f).of(0.1f)
        assertThat(readFrames[0].world).isNull()
        assertThat(readFrames[1].quality).isWithin(1e-5f).of(0.96f)
    }

    @Test
    fun `TraceFrameSource streams frames in order via its header and flow`(
        @TempDir tempDir: Path,
    ) = runTest {
        val path = tempDir.resolve("sample.jsonl.gz")
        val header = TraceHeader(version = 1, schema = "MOVENET_17", source = "synthetic", deviceModel = "x", fps = 25f)
        val frames =
            (0 until 5).map { i ->
                PoseFrame(tMs = i * 40L, landmarks = emptyList(), world = null, quality = 1f)
            }
        TraceIO.write(path, header, frames.asSequence())

        val source = TraceFrameSource(path)

        assertThat(source.header.schema).isEqualTo("MOVENET_17")
        assertThat(source.poseFrames().toList().map { it.tMs }).isEqualTo(frames.map { it.tMs })
    }
}

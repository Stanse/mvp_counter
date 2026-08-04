package dev.repcounter.capture.trace

import dev.repcounter.capture.PoseFrameSource
import dev.repcounter.core.model.PoseFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path

/**
 * Replays a recorded `.jsonl.gz` trace as a [PoseFrame] stream - spec §6/§9, the basis for
 * Level-2 replay tests ("без Android, без модели, без эмулятора, полностью детерминированно").
 *
 * Caller: `:tools:replay` and the Level-2 test suite, collected in a plain coroutine (no
 * dispatcher requirement - there's no camera or model here to keep off any particular thread).
 */
class TraceFrameSource(
    private val path: Path,
) : PoseFrameSource {
    val header: TraceHeader by lazy { TraceIO.readHeader(path) }

    override fun poseFrames(): Flow<PoseFrame> =
        flow {
            TraceIO.readFrames(path).forEach { emit(it) }
        }
}

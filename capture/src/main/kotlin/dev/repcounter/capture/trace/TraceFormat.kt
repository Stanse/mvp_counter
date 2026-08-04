package dev.repcounter.capture.trace

import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.PoseFrame
import kotlinx.serialization.Serializable

/**
 * First line of a `.jsonl.gz` trace file (spec §9) - metadata about the whole recording.
 * [schema] is a [dev.repcounter.core.model.LandmarkSchema] name (e.g. `"BLAZEPOSE_33"`); kept
 * as a plain `String` here so `:capture` doesn't need `LandmarkSchema` to be `@Serializable`
 * for a single field - callers that need the enum call `LandmarkSchema.valueOf(header.schema)`.
 */
@Serializable
data class TraceHeader(
    val version: Int,
    val schema: String,
    val source: String,
    val deviceModel: String,
    val fps: Float,
    val notes: String = "",
)

/**
 * One data line of a `.jsonl.gz` trace file: a [PoseFrame], but with landmarks as bare
 * `[x, y, z, visibility]` arrays (spec §9's example) rather than [Landmark]'s named-field JSON
 * object, to keep trace files small - these get committed to the repo (spec §16).
 */
@Serializable
internal data class TraceFrameLine(
    val tMs: Long,
    val quality: Float,
    val lm: List<List<Float>>,
) {
    companion object {
        private const val X = 0
        private const val Y = 1
        private const val Z = 2
        private const val VISIBILITY = 3

        fun from(frame: PoseFrame): TraceFrameLine =
            TraceFrameLine(
                tMs = frame.tMs,
                quality = frame.quality,
                lm = frame.landmarks.map { listOf(it.x, it.y, it.z, it.visibility) },
            )
    }

    /** Traces don't carry [PoseFrame.world] (spec §9's format has no field for it). */
    fun toPoseFrame(): PoseFrame =
        PoseFrame(
            tMs = tMs,
            landmarks = lm.map { Landmark(x = it[X], y = it[Y], z = it[Z], visibility = it[VISIBILITY]) },
            world = null,
            quality = quality,
        )
}

package dev.repcounter.core.model

/** Pixel encoding of [FrameImage.bytes]. */
enum class FrameFormat {
    /** Single-plane NV21 (Y plane followed by interleaved VU), CameraX's default analysis format. */
    NV21,

    /** Single-plane RGBA_8888, used by decoded video files and synthetic sources. */
    RGBA_8888,
}

/**
 * A single decoded camera/video frame, deliberately free of any CameraX/Android imaging type
 * so `FrameSource` implementations (`:capture`) and `PoseDetector` implementations
 * (`:pose:mediapipe`, `:pose:movenet`) can be exercised from plain JVM tests.
 *
 * `tMs` comes from the originating frame's own clock (camera driver or video container), never
 * from `System.currentTimeMillis()`.
 */
data class FrameImage(
    val tMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val format: FrameFormat,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameImage) return false
        return tMs == other.tMs &&
            width == other.width &&
            height == other.height &&
            rotationDegrees == other.rotationDegrees &&
            format == other.format &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = tMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rotationDegrees
        result = 31 * result + format.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

package dev.repcounter.capture

import dev.repcounter.core.model.FrameImage
import dev.repcounter.core.model.PoseFrame
import kotlinx.coroutines.flow.Flow

/**
 * Produces raw camera/video frames for a [dev.repcounter.pose.api.PoseDetector] to run
 * inference on. Implementations: `CameraFrameSource`, `VideoFileFrameSource` (both `:app`,
 * since they need CameraX/MediaCodec - see `docs/DECISIONS.md`'s M1 entry on why `:capture`
 * itself stays Android-SDK-free).
 *
 * Caller: the capture pipeline, collected on the pose-inference dispatcher.
 */
interface FrameSource {
    fun frames(): Flow<FrameImage>
}

/**
 * Produces already-detected [PoseFrame]s, skipping [dev.repcounter.pose.api.PoseDetector]
 * entirely - spec §6: "отдаёт уже готовые PoseFrame (минуя детектор) — это основа
 * replay-тестов." This is a different shape from [FrameSource] on purpose: a recorded trace
 * has no image bytes to hand a detector, only the landmarks it already produced, so it cannot
 * honestly implement `frames(): Flow<FrameImage>` - see `docs/DECISIONS.md`.
 */
interface PoseFrameSource {
    fun poseFrames(): Flow<PoseFrame>
}

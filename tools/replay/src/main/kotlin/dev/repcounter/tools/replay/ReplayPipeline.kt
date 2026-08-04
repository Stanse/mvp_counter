package dev.repcounter.tools.replay

import dev.repcounter.analysis.api.ExerciseAnalyzer
import dev.repcounter.core.dsp.LinearResampler
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.core.model.PoseFrame
import dev.repcounter.signals.PoseNormalizer
import dev.repcounter.signals.ScaleOnlyPoseNormalizer
import dev.repcounter.signals.SignalExtractor

/**
 * The signal pipeline (spec §7) as a plain function over an already-fully-available
 * [PoseFrame] list: `PoseNormalizer -> SignalExtractor -> Resampler -> ExerciseAnalyzer`. This
 * is exactly what `TraceFrameSource` + Level-2 replay tests need ("без Android, без модели, без
 * эмулятора, полностью детерминированно") and is not meant to be the live-capture pipeline
 * shape (that needs to be streaming/`Flow`-based - `:app`'s job from M4 on).
 */
object ReplayPipeline {
    fun run(
        poseFrames: List<PoseFrame>,
        schema: LandmarkSchema,
        analyzer: ExerciseAnalyzer,
        resampleHz: Float = DEFAULT_RESAMPLE_HZ,
        normalizer: PoseNormalizer = ScaleOnlyPoseNormalizer(),
    ): List<AnalyzerEvent> {
        val monotonic = keepStrictlyIncreasingTMs(poseFrames)
        val signalFrames =
            monotonic.map { frame -> SignalExtractor.extract(normalizer.normalize(frame, schema), schema) }
        val resampled = SignalResampling.resample(signalFrames, LinearResampler(), resampleHz)
        return resampled.flatMap { analyzer.process(it) }
    }

    const val DEFAULT_RESAMPLE_HZ = 50f

    /**
     * Every stage downstream (`Resampler` in particular) assumes non-decreasing timestamps -
     * spec §10's robustness requirements call out both a backward `tMs` jump and duplicate
     * timestamps explicitly, so this drops anything that isn't a strict advance rather than
     * leaving that assumption to break silently deeper in the pipeline.
     */
    private fun keepStrictlyIncreasingTMs(frames: List<PoseFrame>): List<PoseFrame> {
        val result = mutableListOf<PoseFrame>()
        var lastTMs: Long? = null
        for (frame in frames) {
            val last = lastTMs
            if (last != null && frame.tMs <= last) continue
            result += frame
            lastTMs = frame.tMs
        }
        return result
    }
}

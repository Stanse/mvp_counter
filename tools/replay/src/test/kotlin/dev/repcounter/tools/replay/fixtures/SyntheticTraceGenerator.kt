package dev.repcounter.tools.replay.fixtures

import dev.repcounter.analysis.jumprope.JumpRopeTechnique
import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.LandmarkName
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.core.model.PoseFrame
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** One constant-technique, constant-cadence stretch of a synthetic jump-rope session. */
data class SegmentSpec(
    val technique: JumpRopeTechnique,
    val durationMs: Long,
    val frequencyHz: Float,
)

data class SyntheticTrace(
    val frames: List<PoseFrame>,
    /** Every physical jump apex in the trace, ground truth from the generation formula itself. */
    val truePeakTimestampsMs: List<Long>,
    /** [truePeakTimestampsMs] split by which [SegmentSpec] each one falls in. */
    val segmentPeakCounts: List<Int>,
)

/**
 * Builds BLAZEPOSE_33 [PoseFrame] sequences with a known, formula-derived hip/ankle motion, for
 * the Level-1/Level-2 traces spec §9 asks for. Not a general pose simulator - only the six
 * landmarks `JumpRopeAnalyzer`/`SignalExtractor` actually read (hips, shoulders, ankles) move;
 * everything else sits at a fixed neutral pose.
 */
object SyntheticTraceGenerator {
    private const val CAPTURE_FPS = 30f
    private const val HIP_BASELINE_Y = 0.55f
    private const val TORSO_LENGTH = 0.3f
    private const val SHOULDER_BASELINE_Y = HIP_BASELINE_Y - TORSO_LENGTH
    private const val ANKLE_BASELINE_Y = 0.9f

    // Normalized-by-torso-length this becomes AMPLITUDE / TORSO_LENGTH = 0.1, comfortably above
    // both JumpRopeAnalyzer.MIN_CYCLE_AMPLITUDE (0.02) and the technique classifier's
    // MIN_ACTIVE_AMPLITUDE (0.03) placeholders.
    private const val OSCILLATION_AMPLITUDE = 0.03f

    fun generate(
        segments: List<SegmentSpec>,
        noiseAmplitude: Float = 0f,
        dropFraction: Float = 0f,
        seed: Long = 0L,
    ): SyntheticTrace {
        val random = Random(seed)
        val periodMs = 1000.0 / CAPTURE_FPS
        val frames = mutableListOf<PoseFrame>()
        val truePeaks = mutableListOf<Long>()
        val segmentPeakCounts = mutableListOf<Int>()
        var segmentStartMs = 0L

        for (segment in segments) {
            val frameCount = (segment.durationMs / periodMs).toInt()
            for (i in 0 until frameCount) {
                if (dropFraction > 0f && random.nextFloat() < dropFraction) continue
                val elapsedMs = i * periodMs
                val tMs = (segmentStartMs + elapsedMs).toLong()
                val phase = 2.0 * PI * segment.frequencyHz * elapsedMs / 1000.0
                frames += buildFrame(tMs, phase, segment.technique, noiseAmplitude, random)
            }

            val cyclePeriodMs = 1000.0 / segment.frequencyHz
            // HIP_Y is image-space y (grows downward), and buildFrame() sets it to
            // `baseline - A*sin(phase)`: the jump apex (smallest y) is at phase=90 deg, but the
            // peak *detector* looks for the filtered signal's maximum, which for a `-sin` shape
            // falls at phase=270 deg - three quarters through the cycle, not one quarter.
            var peakElapsedMs = 3.0 * cyclePeriodMs / 4.0
            var segmentPeaks = 0
            while (peakElapsedMs < segment.durationMs) {
                truePeaks += (segmentStartMs + peakElapsedMs).toLong()
                segmentPeaks++
                peakElapsedMs += cyclePeriodMs
            }
            segmentPeakCounts += segmentPeaks
            segmentStartMs += segment.durationMs
        }
        return SyntheticTrace(frames, truePeaks, segmentPeakCounts)
    }

    private fun buildFrame(
        tMs: Long,
        phase: Double,
        technique: JumpRopeTechnique,
        noiseAmplitude: Float,
        random: Random,
    ): PoseFrame {
        val hipY = HIP_BASELINE_Y - offset(phase, noiseAmplitude, random)
        val shoulderY = SHOULDER_BASELINE_Y - offset(phase, noiseAmplitude, random)
        val (leftAnkleY, rightAnkleY) = anklePositions(phase, technique, noiseAmplitude, random)

        val landmarks = MutableList(LandmarkSchema.BLAZEPOSE_33.landmarkCount()) { NEUTRAL_LANDMARK }
        set(landmarks, LandmarkName.LEFT_HIP, x = 0.5f, y = hipY)
        set(landmarks, LandmarkName.RIGHT_HIP, x = 0.5f, y = hipY)
        set(landmarks, LandmarkName.LEFT_SHOULDER, x = 0.5f, y = shoulderY)
        set(landmarks, LandmarkName.RIGHT_SHOULDER, x = 0.5f, y = shoulderY)
        set(landmarks, LandmarkName.LEFT_ANKLE, x = 0.45f, y = leftAnkleY)
        set(landmarks, LandmarkName.RIGHT_ANKLE, x = 0.55f, y = rightAnkleY)
        return PoseFrame(tMs = tMs, landmarks = landmarks, world = null, quality = 0.95f)
    }

    private fun offset(
        phase: Double,
        noiseAmplitude: Float,
        random: Random,
    ): Float = OSCILLATION_AMPLITUDE * sin(phase).toFloat() + jitter(noiseAmplitude, random)

    private fun anklePositions(
        phase: Double,
        technique: JumpRopeTechnique,
        noiseAmplitude: Float,
        random: Random,
    ): Pair<Float, Float> {
        val active = ANKLE_BASELINE_Y - offset(phase, noiseAmplitude, random)
        val activeOpposite =
            ANKLE_BASELINE_Y - OSCILLATION_AMPLITUDE * sin(phase - PI).toFloat() - jitter(noiseAmplitude, random)
        val held = ANKLE_BASELINE_Y - OSCILLATION_AMPLITUDE + jitter(noiseAmplitude, random)
        return when (technique) {
            JumpRopeTechnique.BOTH_FEET -> active to active
            JumpRopeTechnique.ALTERNATING -> active to activeOpposite
            JumpRopeTechnique.SINGLE_LEFT -> active to held
            JumpRopeTechnique.SINGLE_RIGHT -> held to active
        }
    }

    private fun jitter(
        amplitude: Float,
        random: Random,
    ): Float = if (amplitude <= 0f) 0f else (random.nextFloat() * 2f - 1f) * amplitude

    private fun set(
        landmarks: MutableList<Landmark>,
        name: LandmarkName,
        x: Float,
        y: Float,
    ) {
        val index = LandmarkSchema.BLAZEPOSE_33.indexOf(name) ?: return
        landmarks[index] = Landmark(x = x, y = y, z = 0f, visibility = 1f)
    }

    private val NEUTRAL_LANDMARK = Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 1f)

    private fun LandmarkSchema.landmarkCount(): Int = LandmarkName.entries.mapNotNull { indexOf(it) }.max() + 1
}

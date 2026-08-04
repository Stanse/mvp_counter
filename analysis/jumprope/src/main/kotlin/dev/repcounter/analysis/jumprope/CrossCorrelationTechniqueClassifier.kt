package dev.repcounter.analysis.jumprope

import dev.repcounter.core.dsp.AutocorrelationCadenceEstimator
import dev.repcounter.core.dsp.NormalizedCrossCorrelator
import dev.repcounter.core.dsp.TimedSample
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId
import kotlin.math.abs

/**
 * Heuristic [TechniqueClassifier] (spec §8.1 step 5): cross-correlates a sliding window of
 * `ANKLE_Y_L` against `ANKLE_Y_R` and reads footwork off where the best-matching lag lands. Feet
 * moving together align best at lag ~0 (`BOTH_FEET`); alternating feet are a half-period-shifted
 * copy of each other, so the *best* alignment naturally lands at that half period (`ALTERNATING`).
 *
 * The lag search is bounded by an internal cadence estimate (`AutocorrelationCadenceEstimator`
 * on the left ankle), not a fixed [Long] - a fixed bound wide enough to reach a slow jumper's
 * half period is also wide enough to reach a fast jumper's *full* period, which is an exact tie
 * with lag 0 for a periodic signal (the same aliasing `docs/DECISIONS.md`'s M2 entry calls out
 * for [NormalizedCrossCorrelator] directly - it bit this classifier when the fixed bound was
 * still here, hence this rewrite).
 */
class CrossCorrelationTechniqueClassifier(
    private val sampleRateHz: Float = DEFAULT_SAMPLE_RATE_HZ,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val minActiveAmplitude: Float = DEFAULT_MIN_ACTIVE_AMPLITUDE,
    private val lagZeroToleranceMs: Long = DEFAULT_LAG_ZERO_TOLERANCE_MS,
    private val minCorrelation: Float = DEFAULT_MIN_CORRELATION,
) : TechniqueClassifier {
    private val leftWindow = ArrayDeque<TimedSample>()
    private val rightWindow = ArrayDeque<TimedSample>()
    private val correlator = NormalizedCrossCorrelator()
    private val periodEstimator =
        AutocorrelationCadenceEstimator(sampleRateHz, windowSeconds = windowMs / MILLIS_PER_SECOND)

    @Suppress("ReturnCount")
    override fun classify(frame: SignalFrame): JumpRopeTechnique? {
        val left = frame.values[SignalId.ANKLE_Y_L]
        val right = frame.values[SignalId.ANKLE_Y_R]
        if (left == null || right == null) return null

        push(leftWindow, TimedSample(frame.tMs, left))
        push(rightWindow, TimedSample(frame.tMs, right))
        // Only the both-active (correlation) path actually needs a period estimate - a single
        // still leg has no periodicity of its own to estimate, so this must not gate the whole
        // classification, only the branch that needs it (see the `cadence?.let` below).
        val cadence = periodEstimator.update(TimedSample(frame.tMs, left))
        if (!isWindowFull()) return null

        val leftActive = amplitude(leftWindow) >= minActiveAmplitude
        val rightActive = amplitude(rightWindow) >= minActiveAmplitude
        return classifyFromActivity(leftActive, rightActive, cadenceHz = cadence?.hz)
    }

    override fun reset() {
        leftWindow.clear()
        rightWindow.clear()
        periodEstimator.reset()
    }

    private fun classifyFromActivity(
        leftActive: Boolean,
        rightActive: Boolean,
        cadenceHz: Float?,
    ): JumpRopeTechnique? =
        when {
            leftActive && !rightActive -> JumpRopeTechnique.SINGLE_LEFT
            rightActive && !leftActive -> JumpRopeTechnique.SINGLE_RIGHT
            leftActive && rightActive -> cadenceHz?.let { classifyFromCorrelation(MILLIS_PER_SECOND / it) }
            else -> null
        }

    private fun classifyFromCorrelation(periodMs: Float): JumpRopeTechnique? {
        val maxLagMs = (periodMs * MAX_LAG_PERIOD_FRACTION).toLong()
        val result = correlator.correlate(leftWindow.toList(), rightWindow.toList(), maxLagMs)
        if (result.correlation < minCorrelation) return null
        return if (abs(result.lagMs) <= lagZeroToleranceMs) {
            JumpRopeTechnique.BOTH_FEET
        } else {
            JumpRopeTechnique.ALTERNATING
        }
    }

    private fun push(
        window: ArrayDeque<TimedSample>,
        sample: TimedSample,
    ) {
        window.addLast(sample)
        val cutoffMs = sample.tMs - windowMs
        while (window.isNotEmpty() && window.first().tMs < cutoffMs) window.removeFirst()
    }

    private fun isWindowFull(): Boolean {
        if (leftWindow.size < MIN_SAMPLES_FOR_CORRELATION) return false
        return leftWindow.last().tMs - leftWindow.first().tMs >= windowMs
    }

    private fun amplitude(window: ArrayDeque<TimedSample>): Float {
        val values = window.map { it.value }
        return (values.max() - values.min())
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 50f
        const val DEFAULT_WINDOW_MS = 2000L
        const val MILLIS_PER_SECOND = 1000f

        // Comfortably past the half period this search exists to find, safely short of a full
        // period (which would tie with lag 0 - see the class KDoc).
        const val MAX_LAG_PERIOD_FRACTION = 0.7f

        // Ankle-Y is torso-length-normalized (PoseNormalizer); a foot held raised and still
        // moves far less than one actively bouncing off the floor. Placeholder pending tuning
        // against real traces (spec §14: name it, document why, expect it to move).
        const val DEFAULT_MIN_ACTIVE_AMPLITUDE = 0.03f
        const val DEFAULT_LAG_ZERO_TOLERANCE_MS = 60L
        const val DEFAULT_MIN_CORRELATION = 0.5f
        const val MIN_SAMPLES_FOR_CORRELATION = 2
    }
}

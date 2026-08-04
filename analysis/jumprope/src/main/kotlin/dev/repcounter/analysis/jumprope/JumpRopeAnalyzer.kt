package dev.repcounter.analysis.jumprope

import dev.repcounter.analysis.api.ExerciseAnalyzer
import dev.repcounter.analysis.api.ExerciseDescriptor
import dev.repcounter.core.dsp.AutocorrelationCadenceEstimator
import dev.repcounter.core.dsp.ButterworthBandpassFilter
import dev.repcounter.core.dsp.HysteresisPeakDetector
import dev.repcounter.core.dsp.Peak
import dev.repcounter.core.dsp.TimedSample
import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId

/**
 * `PeriodicAnalyzer` archetype instance for jump rope (spec §4/§8.1). Composes `:core:dsp`
 * primitives directly rather than through a shared pipeline-level `FilterBank`: §8.1 assigns
 * this exercise its own detrend/bandpass parameters (`0.8-6 Hz`, not §7's generic `0.7-6 Hz`),
 * and §6 doesn't define a `FilterBank` contract, so signal conditioning is this analyzer's own
 * job - see `docs/DECISIONS.md`. Expects [SignalFrame]s already on a fixed [sampleRateHz] grid
 * (i.e. already through a `Resampler`); it does not itself handle irregular timestamps.
 */
class JumpRopeAnalyzer(
    private val sampleRateHz: Float = DEFAULT_SAMPLE_RATE_HZ,
    private val techniqueClassifier: TechniqueClassifier = CrossCorrelationTechniqueClassifier(sampleRateHz),
) : ExerciseAnalyzer {
    override val descriptor =
        ExerciseDescriptor(
            id = "jump_rope",
            displayName = "Скакалка",
            // SHOULDER_Y is spec §8.1's designated quality-control signal for this exercise, but
            // no quality check reads it yet (deferred - see docs/DECISIONS.md), so it's left out
            // of requiredSignals: this descriptor should only declare what the algorithm below
            // actually uses.
            requiredSignals = setOf(SignalId.HIP_Y, SignalId.ANKLE_Y_L, SignalId.ANKLE_Y_R),
            setupHint = "поставь телефон в 1.5-3 м, всё тело и стопы в кадре",
            minFps = 25,
        )

    private val bandpass = ButterworthBandpassFilter(sampleRateHz, HIP_Y_BANDPASS_LOW_HZ, HIP_Y_BANDPASS_HIGH_HZ)
    private val cadenceEstimator = AutocorrelationCadenceEstimator(sampleRateHz)
    private val peakDetector = HysteresisPeakDetector(sampleRateHz, minRefractoryMs = FALLBACK_REFRACTORY_MS)

    // Peaks observed before the activity gate has opened - spec §8.1 step 4.
    private val pendingPeaks = mutableListOf<Peak>()
    private var gateOpen = false
    private var repIndex = 0

    private var confirmedTechnique: JumpRopeTechnique? = null
    private var candidateTechnique: JumpRopeTechnique? = null
    private var candidateSinceTMs = 0L

    override fun reset() {
        bandpass.reset()
        cadenceEstimator.reset()
        peakDetector.reset()
        peakDetector.minRefractoryMs = FALLBACK_REFRACTORY_MS
        techniqueClassifier.reset()
        pendingPeaks.clear()
        gateOpen = false
        repIndex = 0
        confirmedTechnique = null
        candidateTechnique = null
    }

    override fun process(frame: SignalFrame): List<AnalyzerEvent> {
        val events = mutableListOf<AnalyzerEvent>()
        events += processCadenceAndPeaks(frame)
        events += processTechnique(frame)
        return events
    }

    private fun processCadenceAndPeaks(frame: SignalFrame): List<AnalyzerEvent> {
        val hipY = frame.values[SignalId.HIP_Y] ?: return emptyList()
        val filtered = bandpass.apply(TimedSample(frame.tMs, hipY))
        val events = mutableListOf<AnalyzerEvent>()

        val cadence = cadenceEstimator.update(TimedSample(frame.tMs, filtered))
        if (cadence != null) {
            // spec §8.1 step 3: refractory = 0.6 / f0, re-derived from the live cadence estimate
            // on every update rather than fixed, so it tracks the jumper's actual pace.
            val refractoryMs = (REFRACTORY_COEFFICIENT / cadence.hz * MILLIS_PER_SECOND).toLong()
            peakDetector.minRefractoryMs = refractoryMs.coerceAtLeast(MIN_REFRACTORY_FLOOR_MS)
            events += AnalyzerEvent.CadenceUpdated(cadence.hz, frame.tMs)
        }

        val peak = peakDetector.process(TimedSample(frame.tMs, filtered))
        if (peak != null && peak.amplitude >= MIN_CYCLE_AMPLITUDE) {
            events += onCycleDetected(peak)
        }
        return events
    }

    @Suppress("ReturnCount")
    private fun onCycleDetected(peak: Peak): List<AnalyzerEvent> {
        if (gateOpen) {
            val rep = AnalyzerEvent.Rep(index = repIndex, tMs = peak.tMs, confidence = 1f)
            repIndex++
            return listOf(rep)
        }

        pendingPeaks += peak
        while (pendingPeaks.size > GATE_PEAK_COUNT) pendingPeaks.removeAt(0)
        if (pendingPeaks.size < GATE_PEAK_COUNT) return emptyList()

        val periods = pendingPeaks.zipWithNext { a, b -> (b.tMs - a.tMs).toFloat() }
        if (!isStablePeriod(periods)) return emptyList()

        gateOpen = true
        // pendingPeaks[0] only marks where the first measured period starts - it has no
        // preceding cycle of its own, so the GATE_PEAK_COUNT-1 peaks after it are the backfilled
        // reps (spec §8.1 step 4: "эти 3 цикла backfill'ятся в счёт").
        val backfilled = pendingPeaks.drop(1)
        pendingPeaks.clear()
        return backfilled.map { AnalyzerEvent.Rep(index = repIndex++, tMs = it.tMs, confidence = 1f) }
    }

    private fun isStablePeriod(periods: List<Float>): Boolean {
        val mean = periods.average().toFloat()
        val spread = (periods.max() - periods.min()) / mean
        return spread < GATE_PERIOD_SPREAD_TOLERANCE
    }

    @Suppress("ReturnCount")
    private fun processTechnique(frame: SignalFrame): List<AnalyzerEvent> {
        val classified = techniqueClassifier.classify(frame) ?: return emptyList()
        if (classified != candidateTechnique) {
            candidateTechnique = classified
            candidateSinceTMs = frame.tMs
        }
        if (classified == confirmedTechnique) return emptyList()
        if (frame.tMs - candidateSinceTMs < TECHNIQUE_HYSTERESIS_MS) return emptyList()

        confirmedTechnique = classified
        return listOf(AnalyzerEvent.TechniqueChanged(classified.name, frame.tMs))
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 50f
        const val HIP_Y_BANDPASS_LOW_HZ = 0.8f
        const val HIP_Y_BANDPASS_HIGH_HZ = 6f
        const val REFRACTORY_COEFFICIENT = 0.6f
        const val MILLIS_PER_SECOND = 1000f

        // Floor for the refractory period: before the first cadence estimate lands (window not
        // full yet) and as a safety net against an implausibly high cadence reading.
        const val MIN_REFRACTORY_FLOOR_MS = 100L
        const val FALLBACK_REFRACTORY_MS = 200L

        // Torso-length-normalized HIP_Y units. Filters out small motion (walking, rope
        // adjustment) that would otherwise still cross the peak detector's *relative* (k*RMS)
        // threshold - spec §8.1 step 4's "ходьба ... малая амплитуда — не считается". Placeholder
        // pending tuning against real traces, same as CrossCorrelationTechniqueClassifier's.
        const val MIN_CYCLE_AMPLITUDE = 0.02f

        // 4 peaks -> 3 measured inter-peak periods -> spec's "3 подряд валидных цикла".
        const val GATE_PEAK_COUNT = 4
        const val GATE_PERIOD_SPREAD_TOLERANCE = 0.25f
        const val TECHNIQUE_HYSTERESIS_MS = 1500L
    }
}

package dev.repcounter.tools.replay

import dev.repcounter.core.dsp.Resampler
import dev.repcounter.core.dsp.TimedSample
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId

/**
 * [dev.repcounter.core.dsp.Resampler] operates on one named [TimedSample] series at a time, but
 * `SignalExtractor` emits a whole [SignalFrame] (multiple [SignalId]s) per pose frame. This
 * pivots a `SignalId -> irregular timeline` per signal, resamples each independently onto the
 * same fixed grid, then merges back into per-tick [SignalFrame]s - the piece of spec §7's
 * pipeline ("Resampler" between `SignalExtractor` and `FilterBank`/`ExerciseAnalyzer") that
 * isn't captured by any single-signal-at-a-time contract.
 *
 * Assumes every [SignalId] present in [frames] starts at the same first timestamp (true for
 * `SyntheticTraceGenerator`'s fixtures, where every frame reports every signal); a real capture
 * where some signal only starts partway through would need each per-signal grid handled
 * independently rather than merged into one shared timeline - out of scope for M3 replay use.
 */
object SignalResampling {
    fun resample(
        frames: List<SignalFrame>,
        resampler: Resampler,
        outputHz: Float,
    ): List<SignalFrame> {
        if (frames.isEmpty()) return emptyList()

        val resampledBySignal =
            SignalId.entries
                .associateWith { id ->
                    frames.mapNotNull { frame -> frame.values[id]?.let { TimedSample(frame.tMs, it) } }
                }.filterValues { it.size >= MIN_SAMPLES_TO_RESAMPLE }
                .mapValues { (_, samples) -> resampler.resample(samples, outputHz).associate { it.tMs to it.value } }

        val allTMs =
            resampledBySignal.values
                .flatMap { it.keys }
                .distinct()
                .sorted()
        return allTMs.map { tMs ->
            val values = resampledBySignal.mapNotNull { (id, samples) -> samples[tMs]?.let { id to it } }.toMap()
            SignalFrame(tMs, values)
        }
    }

    private const val MIN_SAMPLES_TO_RESAMPLE = 2
}

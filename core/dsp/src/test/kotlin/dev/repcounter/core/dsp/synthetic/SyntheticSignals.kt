package dev.repcounter.core.dsp.synthetic

import dev.repcounter.core.dsp.TimedSample
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic (seeded) synthetic signal generators for the Level-1 DSP unit tests required by
 * spec §10: known-frequency sines, optionally with white noise and/or dropped samples, so
 * `:core:dsp` components can be graded against ground truth without a camera, model, or trace.
 */
object SyntheticSignals {
    /**
     * A sine sampled on a uniform [sampleRateHz] grid starting at `t=0`. [phaseOffsetSamples]
     * delays the waveform by that many sample periods (`b(t) = a(t - offset)`), which is what
     * [dev.repcounter.core.dsp.CrossCorrelator] tests need to construct a signal pair with a
     * known lag.
     */
    fun sineWave(
        frequencyHz: Float,
        sampleRateHz: Float,
        durationMs: Long,
        amplitude: Float = 1f,
        phaseOffsetSamples: Int = 0,
    ): List<TimedSample> {
        val periodMs = 1000.0 / sampleRateHz
        val sampleCount = (durationMs / periodMs).toInt() + 1
        return List(sampleCount) { index ->
            val tMs = (index * periodMs).toLong()
            val shiftedMs = tMs - phaseOffsetSamples * periodMs
            val value = (amplitude * sin(2.0 * PI * frequencyHz * shiftedMs / 1000.0)).toFloat()
            TimedSample(tMs, value)
        }
    }

    /** Adds seeded uniform noise in `[-amplitude, +amplitude]` to every sample's value. */
    fun withWhiteNoise(
        samples: List<TimedSample>,
        amplitude: Float,
        seed: Long,
    ): List<TimedSample> {
        val random = Random(seed)
        return samples.map { it.copy(value = it.value + (random.nextFloat() * 2f - 1f) * amplitude) }
    }

    /**
     * Drops [dropFraction] of samples at random to simulate camera frame loss, always keeping
     * the first sample so the output's start timestamp - and therefore a resampler's phase -
     * matches the input's.
     */
    fun withDroppedSamples(
        samples: List<TimedSample>,
        dropFraction: Float,
        seed: Long,
    ): List<TimedSample> {
        require(dropFraction in 0f..1f) { "dropFraction must be in [0,1], was $dropFraction" }
        val random = Random(seed)
        return samples.filterIndexed { index, _ -> index == 0 || random.nextFloat() >= dropFraction }
    }
}

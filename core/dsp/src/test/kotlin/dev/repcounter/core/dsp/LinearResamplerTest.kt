package dev.repcounter.core.dsp

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.dsp.synthetic.SyntheticSignals
import org.junit.jupiter.api.Test

class LinearResamplerTest {
    private val resampler = LinearResampler()

    @Test
    fun `already-uniform input round-trips at the same rate`() {
        val samples = SyntheticSignals.sineWave(frequencyHz = 2f, sampleRateHz = 50f, durationMs = 2000)

        val output = resampler.resample(samples, outputHz = 50f)

        assertThat(output).hasSize(samples.size)
        samples.indices.forEach { i ->
            assertThat(output[i].tMs).isEqualTo(samples[i].tMs)
            assertThat(output[i].value).isWithin(1e-4f).of(samples[i].value)
        }
    }

    @Test
    fun `dropped samples are reconstructed without a phase shift`() {
        val original = SyntheticSignals.sineWave(frequencyHz = 2f, sampleRateHz = 50f, durationMs = 2000)
        val dropped = SyntheticSignals.withDroppedSamples(original, dropFraction = 0.1f, seed = 42)

        val reconstructed = resampler.resample(dropped, outputHz = 50f)

        val reconstructedByTMs = reconstructed.associateBy { it.tMs }
        assertThat(reconstructedByTMs.keys).containsAtLeastElementsIn(original.map { it.tMs })
        original.forEach { expected ->
            val actual = reconstructedByTMs.getValue(expected.tMs)
            assertThat(actual.value).isWithin(0.05f).of(expected.value)
        }
    }

    @Test
    fun `upsamples a sparse input onto a denser fixed grid`() {
        val sparse = listOf(TimedSample(0, 0f), TimedSample(100, 1f), TimedSample(200, 0f))

        val output = resampler.resample(sparse, outputHz = 100f)

        assertThat(output.first().tMs).isEqualTo(0L)
        assertThat(output.last().tMs).isEqualTo(200L)
        val midpoint = output.first { it.tMs == 50L }
        assertThat(midpoint.value).isWithin(1e-4f).of(0.5f)
    }
}

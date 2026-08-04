package dev.repcounter.core.dsp

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.dsp.synthetic.SyntheticSignals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class ButterworthBandpassFilterTest {
    private val sampleRateHz = 50f
    private val lowHz = 0.7f
    private val highHz = 6f

    @Test
    fun `a passband frequency survives with near-unity gain after the filter settles`() {
        val filter = ButterworthBandpassFilter(sampleRateHz, lowHz, highHz)
        val input = SyntheticSignals.sineWave(frequencyHz = 2.5f, sampleRateHz = sampleRateHz, durationMs = 6000)

        val output = input.map { filter.apply(it) }
        val gain = rms(output.takeLast(output.size / 2)) / rmsOfSamples(input.takeLast(input.size / 2))

        assertThat(gain).isWithin(0.25f).of(1f)
    }

    @Test
    fun `a frequency far above the passband is strongly attenuated`() {
        val filter = ButterworthBandpassFilter(sampleRateHz, lowHz, highHz)
        val input = SyntheticSignals.sineWave(frequencyHz = 20f, sampleRateHz = sampleRateHz, durationMs = 6000)

        val output = input.map { filter.apply(it) }
        val gain = rms(output.takeLast(output.size / 2)) / rmsOfSamples(input.takeLast(input.size / 2))

        assertThat(gain).isLessThan(0.2f)
    }

    @Test
    fun `a slow drift far below the passband is strongly attenuated`() {
        val filter = ButterworthBandpassFilter(sampleRateHz, lowHz, highHz)
        val input = SyntheticSignals.sineWave(frequencyHz = 0.05f, sampleRateHz = sampleRateHz, durationMs = 20000)

        val output = input.map { filter.apply(it) }
        val gain = rms(output.takeLast(output.size / 4)) / rmsOfSamples(input.takeLast(input.size / 4))

        assertThat(gain).isLessThan(0.3f)
    }

    @Test
    fun `no NaN or Inf from the very first sample`() {
        val filter = ButterworthBandpassFilter(sampleRateHz, lowHz, highHz)
        val input = SyntheticSignals.sineWave(frequencyHz = 2.5f, sampleRateHz = sampleRateHz, durationMs = 500)

        input.forEach { sample ->
            val output = filter.apply(sample)
            assertThat(output.isFinite()).isTrue()
        }
    }

    private fun rmsOfSamples(samples: List<TimedSample>): Float {
        val meanSquare = samples.sumOf { (it.value * it.value).toDouble() } / samples.size
        return sqrt(meanSquare).toFloat()
    }

    private fun rms(values: List<Float>): Float {
        val meanSquare = values.sumOf { (it * it).toDouble() } / values.size
        return sqrt(meanSquare).toFloat()
    }
}

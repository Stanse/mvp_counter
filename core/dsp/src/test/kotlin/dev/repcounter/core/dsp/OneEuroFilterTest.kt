package dev.repcounter.core.dsp

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.dsp.synthetic.SyntheticSignals
import org.junit.jupiter.api.Test

class OneEuroFilterTest {
    private val sampleRateHz = 50f

    @Test
    fun `smooths jitter around a slow-moving signal`() {
        val filter = OneEuroFilter()
        val slowRamp =
            SyntheticSignals.sineWave(
                frequencyHz = 0.2f,
                sampleRateHz = sampleRateHz,
                durationMs = 4000,
                amplitude = 90f,
            )
        val jittery = SyntheticSignals.withWhiteNoise(slowRamp, amplitude = 5f, seed = 3)

        val filtered = jittery.map { filter.apply(it) }

        val rawJitter = successiveDifferenceStdDev(jittery.map { it.value })
        val filteredJitter = successiveDifferenceStdDev(filtered)
        assertThat(filteredJitter).isLessThan(rawJitter)
    }

    @Test
    fun `tracks a step change without unbounded lag`() {
        val filter = OneEuroFilter()
        val step =
            (0 until 100).map { i -> TimedSample(tMs = (i * 20).toLong(), value = if (i < 20) 0f else 90f) }

        val filtered = step.map { filter.apply(it) }

        assertThat(filtered.last()).isWithin(1f).of(90f)
    }

    @Test
    fun `no NaN or Inf from the very first sample, including a zero dt`() {
        val filter = OneEuroFilter()
        val samples =
            listOf(TimedSample(0, 10f), TimedSample(0, 12f)) +
                SyntheticSignals.sineWave(frequencyHz = 1f, sampleRateHz = sampleRateHz, durationMs = 500)

        samples.forEach { sample ->
            val output = filter.apply(sample)
            assertThat(output.isFinite()).isTrue()
        }
    }

    private fun successiveDifferenceStdDev(values: List<Float>): Float {
        val diffs = values.zipWithNext { a, b -> b - a }
        val mean = diffs.average()
        val variance = diffs.sumOf { (it - mean) * (it - mean) } / diffs.size
        return kotlin.math.sqrt(variance).toFloat()
    }
}

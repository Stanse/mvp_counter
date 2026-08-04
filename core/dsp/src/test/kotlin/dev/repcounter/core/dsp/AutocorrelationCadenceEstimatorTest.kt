package dev.repcounter.core.dsp

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.dsp.synthetic.SyntheticSignals
import org.junit.jupiter.api.Test

class AutocorrelationCadenceEstimatorTest {
    private val sampleRateHz = 50f

    @Test
    fun `recovers the frequency of a clean sine to within 0-1 Hz once the window fills`() {
        val frequencyHz = 2.5f
        val samples = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 5000)
        val estimator = AutocorrelationCadenceEstimator(sampleRateHz)

        var latest: CadenceEstimate? = null
        samples.forEach { latest = estimator.update(it) ?: latest }

        val estimate = latest
        assertThat(estimate).isNotNull()
        checkNotNull(estimate)
        assertThat(estimate.hz).isWithin(0.1f).of(frequencyHz)
        // Truncated-window autocorrelation never reaches exactly 1.0 even for a perfectly clean
        // sine - the finite overlap at each candidate lag loses a little energy at the edges.
        assertThat(estimate.confidence).isAtLeast(0.8f)
    }

    @Test
    fun `stays robust to white noise and dropped samples`() {
        val frequencyHz = 3f
        val clean = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 6000)
        val noisy = SyntheticSignals.withWhiteNoise(clean, amplitude = 0.1f, seed = 5)
        val withDrops = SyntheticSignals.withDroppedSamples(noisy, dropFraction = 0.1f, seed = 13)
        val resampled = LinearResampler().resample(withDrops, outputHz = sampleRateHz)
        val estimator = AutocorrelationCadenceEstimator(sampleRateHz)

        var latest: CadenceEstimate? = null
        resampled.forEach { latest = estimator.update(it) ?: latest }

        val estimate = latest
        assertThat(estimate).isNotNull()
        checkNotNull(estimate)
        assertThat(estimate.hz).isWithin(0.2f).of(frequencyHz)
    }

    @Test
    fun `returns null before the window fills`() {
        val estimator = AutocorrelationCadenceEstimator(sampleRateHz, windowSeconds = 3f)
        val fewSamples = SyntheticSignals.sineWave(frequencyHz = 2.5f, sampleRateHz, durationMs = 500)

        val estimates = fewSamples.mapNotNull { estimator.update(it) }

        assertThat(estimates).isEmpty()
    }
}

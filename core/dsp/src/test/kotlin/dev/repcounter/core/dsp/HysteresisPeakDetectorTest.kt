package dev.repcounter.core.dsp

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.dsp.synthetic.SyntheticSignals
import org.junit.jupiter.api.Test

class HysteresisPeakDetectorTest {
    private val sampleRateHz = 50f
    private val frequencyHz = 2.5f

    @Test
    fun `counts exactly the peaks that survive RMS-window warmup, despite noise and dropped samples`() {
        val durationMs = 8000L
        val clean = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs)
        val noisy = SyntheticSignals.withWhiteNoise(clean, amplitude = 0.08f, seed = 7)
        val withDrops = SyntheticSignals.withDroppedSamples(noisy, dropFraction = 0.1f, seed = 11)
        val resampled = LinearResampler().resample(withDrops, outputHz = sampleRateHz)

        val detector = HysteresisPeakDetector(sampleRateHz = sampleRateHz)
        val peaks = resampled.mapNotNull { detector.process(it) }

        // The detector can't report a peak until its RMS window fills, so the first cycle(s)
        // inside that warmup are necessarily lost - this computes the exact survivor count from
        // the same ground-truth peak schedule the synthetic signal was generated from, rather
        // than a hand-picked magic number.
        val periodMs = 1000.0 / frequencyHz
        val expectedPeakTimesMs =
            generateSequence(periodMs / 4) { it + periodMs }.takeWhile { it < durationMs }.toList()
        val expectedCount =
            expectedPeakTimesMs.count { it >= HysteresisPeakDetector.DEFAULT_RMS_WINDOW_MS }

        assertThat(peaks).hasSize(expectedCount)
    }

    @Test
    fun `noise well below the trigger threshold produces no peaks once the window fills`() {
        val flatNoise =
            SyntheticSignals.withWhiteNoise(
                SyntheticSignals.sineWave(
                    frequencyHz = 0f,
                    sampleRateHz = sampleRateHz,
                    durationMs = 4000,
                    amplitude = 0f,
                ),
                amplitude = 0.05f,
                seed = 99,
            )
        // A high kThreshold here isolates the property under test - no dominant cycle means no
        // peaks - from the separate concern (covered above) of tuning k against real signal
        // amplitude.
        val detector = HysteresisPeakDetector(sampleRateHz = sampleRateHz, kThreshold = 5f)

        val peaks = flatNoise.mapNotNull { detector.process(it) }

        assertThat(peaks).isEmpty()
    }

    @Test
    fun `reset clears warmup state and refractory memory`() {
        val detector = HysteresisPeakDetector(sampleRateHz = sampleRateHz)
        SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 2000).forEach { detector.process(it) }

        detector.reset()
        val secondPass = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 8000)
        val peaksAfterReset = secondPass.mapNotNull { detector.process(it) }

        val periodMs = 1000.0 / frequencyHz
        val expectedCount =
            generateSequence(periodMs / 4) { it + periodMs }
                .takeWhile { it < 8000 }
                .count { it >= HysteresisPeakDetector.DEFAULT_RMS_WINDOW_MS }

        assertThat(peaksAfterReset).hasSize(expectedCount)
    }
}

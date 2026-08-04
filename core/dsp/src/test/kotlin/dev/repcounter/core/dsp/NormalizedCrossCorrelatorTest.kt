package dev.repcounter.core.dsp

import com.google.common.truth.Truth.assertThat
import dev.repcounter.core.dsp.synthetic.SyntheticSignals
import org.junit.jupiter.api.Test

class NormalizedCrossCorrelatorTest {
    private val sampleRateHz = 50f
    private val periodMs = (1000f / sampleRateHz).toLong()
    private val correlator = NormalizedCrossCorrelator()

    @Test
    fun `recovers a known positive lag to within one sample`() {
        val frequencyHz = 1.5f
        val lagSamples = 7
        val a = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 4000)
        val b = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 4000, phaseOffsetSamples = lagSamples)

        val result = correlator.correlate(a, b, maxLagMs = 500)

        assertThat(result.lagMs).isEqualTo(lagSamples * periodMs)
        assertThat(result.correlation).isGreaterThan(0.95f)
    }

    @Test
    fun `recovers a known negative lag`() {
        val frequencyHz = 1.5f
        val lagSamples = -5
        val a = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 4000)
        val b = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 4000, phaseOffsetSamples = lagSamples)

        val result = correlator.correlate(a, b, maxLagMs = 500)

        assertThat(result.lagMs).isEqualTo(lagSamples * periodMs)
    }

    @Test
    fun `identical signals correlate at lag zero`() {
        // maxLagMs is kept below the signal's own period (500 ms at 2 Hz) - otherwise the
        // search window would also contain lag = +/-one full period, which is an exact tie
        // with lag 0 for a periodic signal correlated with itself.
        val a = SyntheticSignals.sineWave(frequencyHz = 2f, sampleRateHz, durationMs = 2000)

        val result = correlator.correlate(a, a, maxLagMs = 200)

        assertThat(result.lagMs).isEqualTo(0L)
        assertThat(result.correlation).isWithin(1e-3f).of(1f)
    }

    @Test
    fun `half-period lag is detected for the boxer-step ankle pattern`() {
        // Alternating left/right ankles are a half-period-shifted copy of each other, so at
        // *zero* lag they're out of phase (spec §8.1: "лаг ≈ T/2 -> ALTERNATING") - but the
        // correlator searches lags for the *best* alignment, and shifting an out-of-phase sine
        // by another half period brings it back in phase. So the lag the search settles on is
        // exactly the true offset, with a strong *positive* correlation there.
        // frequencyHz = 2.5 Hz gives a 20-sample period, so its half (10 samples) is exact -
        // no rounding. maxLagMs stays well below the full period (400 ms) to avoid the same
        // one-full-period aliasing called out in the lag-zero test above.
        val frequencyHz = 2.5f
        val halfPeriodSamples = (sampleRateHz / frequencyHz / 2).toInt()
        val ankleLeft = SyntheticSignals.sineWave(frequencyHz, sampleRateHz, durationMs = 4000)
        val ankleRight =
            SyntheticSignals.sineWave(
                frequencyHz,
                sampleRateHz,
                durationMs = 4000,
                phaseOffsetSamples = halfPeriodSamples,
            )

        val result = correlator.correlate(ankleLeft, ankleRight, maxLagMs = 300)

        // +halfPeriod and -halfPeriod are mathematically indistinguishable for an exact
        // half-period shift of a pure sinusoid (shifting "the other way" lands on the same
        // waveform), so either sign is a correct answer here.
        assertThat(kotlin.math.abs(result.lagMs)).isEqualTo(halfPeriodSamples * periodMs)
        assertThat(result.correlation).isGreaterThan(0.9f)
    }
}

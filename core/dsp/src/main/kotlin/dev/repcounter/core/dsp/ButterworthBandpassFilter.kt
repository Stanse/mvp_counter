package dev.repcounter.core.dsp

/**
 * Wideband Butterworth bandpass built from a highpass section (removes drift below [lowHz])
 * cascaded with a lowpass section (removes jitter/noise above [highHz]) - spec §7 step 3 and
 * §8.1 step 1 (`0.7-6 Hz` on the detrended `HIP_Y`). Operates on an already-resampled,
 * fixed-rate stream; ignores [TimedSample.tMs] and assumes exactly [sampleRateHz] between calls.
 */
class ButterworthBandpassFilter(
    private val sampleRateHz: Float,
    lowHz: Float,
    highHz: Float,
) : StreamingFilter {
    init {
        require(sampleRateHz > 0f) { "sampleRateHz must be positive, was $sampleRateHz" }
        require(lowHz > 0f && lowHz < highHz) { "require 0 < lowHz < highHz, was $lowHz, $highHz" }
        require(highHz < sampleRateHz / NYQUIST_DIVISOR) {
            "highHz must be below Nyquist (${sampleRateHz / NYQUIST_DIVISOR}), was $highHz"
        }
    }

    private val highpass = Biquad(ButterworthDesign.highpass(sampleRateHz, lowHz))
    private val lowpass = Biquad(ButterworthDesign.lowpass(sampleRateHz, highHz))

    override fun apply(sample: TimedSample): Float = lowpass.process(highpass.process(sample.value))

    override fun reset() {
        highpass.reset()
        lowpass.reset()
    }

    private companion object {
        const val NYQUIST_DIVISOR = 2f
    }
}

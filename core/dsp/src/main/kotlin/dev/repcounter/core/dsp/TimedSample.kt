package dev.repcounter.core.dsp

/**
 * A single scalar sample of an arbitrary time series. `:core:dsp` is domain-agnostic: it knows
 * nothing about poses or exercises, only about timestamped floats.
 */
data class TimedSample(
    val tMs: Long,
    val value: Float,
)

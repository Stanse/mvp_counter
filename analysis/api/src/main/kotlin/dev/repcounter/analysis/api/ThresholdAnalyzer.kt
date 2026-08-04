package dev.repcounter.analysis.api

import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId

/**
 * Declarative configuration for a [ThresholdAnalyzer] - spec §8.2: adding an exercise like a
 * squat or pushup is one value of this, zero new code. `downBelow`/`upAbove` are two-threshold
 * hysteresis (not a single midpoint) so noise sitting near one value can't register as a rep;
 * see [ThresholdAnalyzer]'s KDoc for why that's the whole point of this shape.
 */
data class ThresholdAnalyzerConfig(
    val id: String,
    val displayName: String,
    val signal: SignalId,
    val downBelow: Float,
    val upAbove: Float,
    val minRepMs: Long,
    val minAmplitude: Float,
    val setupHint: String,
    val minFps: Int,
    val gate: Gate = Gate.None,
) {
    init {
        require(downBelow < upAbove) { "downBelow ($downBelow) must be < upAbove ($upAbove)" }
    }
}

/**
 * FSM archetype for discrete, slow movements (spec §4/§8.2): squats, pushups, lunges - any
 * exercise expressible as "one signal crosses a low threshold, then a high threshold, with
 * enough time and range of motion in between". Config-only, so a new exercise of this shape
 * never touches this class - see `:analysis:strength`'s squat/pushup [ThresholdAnalyzerConfig]s.
 *
 * `downBelow` and `upAbove` are deliberately two different thresholds, not one midpoint with a
 * dead zone: a single threshold means noise oscillating right at it registers a rep every
 * crossing (spec §10's "дребезг вокруг порога не должен давать лишних повторов"). With two
 * thresholds separated by a real gap, the signal has to travel that whole gap - and with
 * [ThresholdAnalyzerConfig.minAmplitude] enforced against the actual peak/valley reached, not
 * just the threshold values - before a rep counts.
 */
class ThresholdAnalyzer(
    private val config: ThresholdAnalyzerConfig,
) : ExerciseAnalyzer {
    override val descriptor =
        ExerciseDescriptor(
            id = config.id,
            displayName = config.displayName,
            requiredSignals = requiredSignals(config),
            setupHint = config.setupHint,
            minFps = config.minFps,
        )

    private var phase = Phase.WAITING_FOR_DOWN
    private var repIndex = 0
    private var maxSinceUp = Float.NEGATIVE_INFINITY
    private var topBeforeDown = Float.NaN
    private var minSinceDown = Float.POSITIVE_INFINITY
    private var downStartTMs = 0L

    override fun reset() {
        phase = Phase.WAITING_FOR_DOWN
        repIndex = 0
        maxSinceUp = Float.NEGATIVE_INFINITY
        topBeforeDown = Float.NaN
        minSinceDown = Float.POSITIVE_INFINITY
    }

    @Suppress("ReturnCount")
    override fun process(frame: SignalFrame): List<AnalyzerEvent> {
        val value = frame.values[config.signal] ?: return emptyList()
        if (!config.gate.isOpen(frame)) return emptyList()

        return when (phase) {
            Phase.WAITING_FOR_DOWN -> processWaitingForDown(value, frame.tMs)
            Phase.WAITING_FOR_UP -> processWaitingForUp(value, frame.tMs)
        }
    }

    private fun processWaitingForDown(
        value: Float,
        tMs: Long,
    ): List<AnalyzerEvent> {
        maxSinceUp = maxOf(maxSinceUp, value)
        if (value >= config.downBelow) return emptyList()

        phase = Phase.WAITING_FOR_UP
        topBeforeDown = maxSinceUp
        minSinceDown = value
        downStartTMs = tMs
        return emptyList()
    }

    @Suppress("ReturnCount")
    private fun processWaitingForUp(
        value: Float,
        tMs: Long,
    ): List<AnalyzerEvent> {
        minSinceDown = minOf(minSinceDown, value)
        if (value <= config.upAbove) return emptyList()

        phase = Phase.WAITING_FOR_DOWN
        maxSinceUp = value

        val durationMs = tMs - downStartTMs
        val amplitude = topBeforeDown - minSinceDown
        if (durationMs < config.minRepMs || amplitude < config.minAmplitude) return emptyList()

        val rep =
            AnalyzerEvent.Rep(
                index = repIndex++,
                tMs = tMs,
                confidence = 1f,
                meta = mapOf("amplitude" to amplitude, "durationMs" to durationMs.toFloat()),
            )
        return listOf(rep)
    }

    private enum class Phase { WAITING_FOR_DOWN, WAITING_FOR_UP }

    private companion object {
        fun requiredSignals(config: ThresholdAnalyzerConfig): Set<SignalId> {
            val gateSignal =
                when (config.gate) {
                    is Gate.None -> null
                    is Gate.TorsoHorizontal -> SignalId.TORSO_TILT
                }
            return setOfNotNull(config.signal, gateSignal)
        }
    }
}

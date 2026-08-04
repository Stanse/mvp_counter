package dev.repcounter.analysis.api

import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId

/**
 * A precondition [ThresholdAnalyzer] (spec §8.2) checks before it will count a rep - e.g. "the
 * torso must be roughly horizontal" so a pushup analyzer doesn't count push-press-style standing
 * arm bends. If the signal a gate needs isn't in this frame, [isOpen] returns `false`: an
 * inconclusive check is treated as "don't count", not "count anyway" - the whole point of a gate
 * is to prevent wrongly-counted reps, so failing open would defeat it.
 */
sealed interface Gate {
    fun isOpen(frame: SignalFrame): Boolean

    /** Always open - the analyzer's own threshold crossing is the only requirement. */
    data object None : Gate {
        override fun isOpen(frame: SignalFrame): Boolean = true
    }

    /** Open only while `TORSO_TILT` (spec §7, `0` = horizontal, `90` = upright) stays low. */
    data class TorsoHorizontal(
        val maxTiltDeg: Float,
    ) : Gate {
        override fun isOpen(frame: SignalFrame): Boolean {
            val tilt = frame.values[SignalId.TORSO_TILT] ?: return false
            return tilt <= maxTiltDeg
        }
    }
}

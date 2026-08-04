package dev.repcounter.analysis.jumprope

import dev.repcounter.core.model.SignalFrame

/**
 * Classifies jump-rope footwork from `ANKLE_Y_L`/`ANKLE_Y_R` (spec §8.1 step 5). A separate
 * interface from [JumpRopeAnalyzer] on purpose: the spec calls out that the heuristic here is
 * one implementation and a trained model could replace it later without touching the analyzer
 * that owns rep counting.
 *
 * Caller: [JumpRopeAnalyzer], once per [SignalFrame], in time order.
 */
interface TechniqueClassifier {
    /** Returns `null` when there isn't enough recent history (or ankle data) to decide yet. */
    fun classify(frame: SignalFrame): JumpRopeTechnique?

    fun reset()
}

package dev.repcounter.analysis.api

import dev.repcounter.core.model.AnalyzerEvent
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId

/**
 * Everything the UI/registry needs to know about an exercise without instantiating its
 * analyzer: what to show in the exercise picker, what camera setup it needs, and which signals
 * (spec §6) it can't function without. `ExerciseListScreen` (`:feature:workout`) reads this
 * directly from `ExerciseAnalyzerFactory.descriptor` - no reflection, no hardcoded exercise list.
 */
data class ExerciseDescriptor(
    val id: String,
    val displayName: String,
    val requiredSignals: Set<SignalId>,
    /** e.g. "поставь телефон вертикально в 2 м, всё тело в кадре". */
    val setupHint: String,
    /** Below this effective FPS, `QualityKind.LOW_FRAMERATE` fires (spec §12). */
    val minFps: Int,
)

/**
 * Pure Kotlin exercise counting logic - knows nothing about landmarks, the camera, or Android
 * (spec §4). Caller: the signal pipeline, one already-resampled-and-filtered [SignalFrame] at a
 * time, in time order, off the camera thread.
 */
interface ExerciseAnalyzer {
    val descriptor: ExerciseDescriptor

    /** Clears all internal state (counters, phase, technique history) for a fresh session. */
    fun reset()

    fun process(frame: SignalFrame): List<AnalyzerEvent>
}

/**
 * Hilt `@IntoSet`-bound factory (spec §6) - one per exercise, registered without touching
 * `:app`. The actual `@Module`/`@Binds` wiring lives in an Android module (Hilt's
 * `SingletonComponent` requires `hilt-android`), not here; `:analysis:api` only defines the
 * plain-Kotlin shape the registry multibinds.
 */
interface ExerciseAnalyzerFactory {
    val descriptor: ExerciseDescriptor

    fun create(): ExerciseAnalyzer
}

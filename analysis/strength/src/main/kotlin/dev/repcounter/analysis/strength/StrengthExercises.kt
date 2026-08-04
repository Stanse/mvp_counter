package dev.repcounter.analysis.strength

import dev.repcounter.analysis.api.ExerciseAnalyzerFactory
import dev.repcounter.analysis.api.Gate
import dev.repcounter.analysis.api.ThresholdAnalyzerConfig
import dev.repcounter.core.model.SignalId

/**
 * The full list of [ThresholdAnalyzer][dev.repcounter.analysis.api.ThresholdAnalyzer] exercises
 * this app ships. Spec §8.2: "Добавление жима/подтягиваний = одна запись в этом списке" - a new
 * exercise of this archetype is exactly one more entry in [all], no new class.
 */
object StrengthExercises {
    val squat =
        ThresholdAnalyzerConfig(
            id = "squat",
            displayName = "Приседания",
            signal = SignalId.KNEE_ANGLE_MEAN,
            // A relaxed standing knee reads close to 170-180 deg; a parallel-depth squat drops
            // well under 100. The 60 deg gap between the two thresholds is what makes normal
            // knee-angle sensor jitter unable to register as a rep on its own.
            downBelow = 100f,
            upAbove = 160f,
            minRepMs = 500,
            minAmplitude = 40f,
            setupHint = "поставь телефон вертикально в 2 м, ноги и корпус целиком в кадре",
            minFps = 12,
            gate = Gate.None,
        )

    val pushup =
        ThresholdAnalyzerConfig(
            id = "pushup",
            displayName = "Отжимания",
            signal = SignalId.ELBOW_ANGLE_MEAN,
            downBelow = 100f,
            upAbove = 160f,
            minRepMs = 400,
            minAmplitude = 35f,
            setupHint = "камера сбоку на уровне пола, корпус целиком в кадре",
            minFps = 12,
            // Rejects standing arm bends (torso near-vertical) that would otherwise look like a
            // valid elbow-angle cycle.
            gate = Gate.TorsoHorizontal(maxTiltDeg = 35f),
        )

    val all: List<ThresholdAnalyzerConfig> = listOf(squat, pushup)
}

val strengthExerciseFactories: List<ExerciseAnalyzerFactory> =
    StrengthExercises.all.map { ThresholdAnalyzerFactory(it) }

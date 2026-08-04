package dev.repcounter.analysis.strength

import dev.repcounter.analysis.api.ExerciseAnalyzer
import dev.repcounter.analysis.api.ExerciseAnalyzerFactory
import dev.repcounter.analysis.api.ExerciseDescriptor
import dev.repcounter.analysis.api.ThresholdAnalyzer
import dev.repcounter.analysis.api.ThresholdAnalyzerConfig

/** Wraps a [ThresholdAnalyzerConfig] as an [ExerciseAnalyzerFactory] - one instance per config. */
class ThresholdAnalyzerFactory(
    private val config: ThresholdAnalyzerConfig,
) : ExerciseAnalyzerFactory {
    override val descriptor: ExerciseDescriptor = ThresholdAnalyzer(config).descriptor

    override fun create(): ExerciseAnalyzer = ThresholdAnalyzer(config)
}

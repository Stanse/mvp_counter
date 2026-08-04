package dev.repcounter.analysis.jumprope

import dev.repcounter.analysis.api.ExerciseAnalyzer
import dev.repcounter.analysis.api.ExerciseAnalyzerFactory
import dev.repcounter.analysis.api.ExerciseDescriptor

class JumpRopeAnalyzerFactory : ExerciseAnalyzerFactory {
    override val descriptor: ExerciseDescriptor = JumpRopeAnalyzer().descriptor

    override fun create(): ExerciseAnalyzer = JumpRopeAnalyzer()
}

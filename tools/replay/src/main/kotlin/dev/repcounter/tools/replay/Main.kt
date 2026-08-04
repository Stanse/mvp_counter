package dev.repcounter.tools.replay

/**
 * Entry point for the replay corpus runner (`./gradlew :tools:replay:run`).
 *
 * Reads every trace under `testdata/traces/`, runs it through the signal pipeline and the
 * matching `ExerciseAnalyzer`, and prints a `trace,expected,counted,abs_err,precision,recall,f1,
 * mean_offset_ms` CSV per the spec's §10 metrics table. Wired up in M3 once `:signals` and
 * `:analysis:jumprope` exist.
 */
fun main() {
    println("repcounter replay tool - corpus runner lands in M3, see REPCOUNTER_ANDROID.md §13")
}

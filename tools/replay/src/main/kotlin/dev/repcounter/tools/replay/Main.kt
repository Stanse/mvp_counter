package dev.repcounter.tools.replay

import java.nio.file.Path
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Entry point for the replay corpus runner (`./gradlew :tools:replay:run`).
 *
 * Reads every trace under `testdata/traces/`, runs it through the signal pipeline and the
 * matching `ExerciseAnalyzer`, and prints a `trace,expected,counted,abs_err,precision,recall,f1,
 * mean_offset_ms` CSV per spec §10. Exits non-zero when the corpus-wide gate (§10: `MAE > 2.0`
 * or `F1 < 0.95`) fails, so this can run as a CI step.
 */
fun main() {
    val tracesDir =
        Path
            .of("")
            .toAbsolutePath()
            .resolve("../../testdata/traces")
            .normalize()
    val perTrace = CorpusRunner.run(tracesDir)

    if (perTrace.isEmpty()) {
        println("No traces found under $tracesDir")
        exitProcess(1)
    }

    println(TraceMetrics.CSV_HEADER)
    perTrace.forEach { println(it.toCsvRow()) }

    val summary = CorpusMetrics.aggregate(perTrace)
    println()
    val mae = String.format(Locale.ROOT, "%.4f", summary.mae)
    val microF1 = String.format(Locale.ROOT, "%.4f", summary.microF1)
    println("corpus MAE=$mae microF1=$microF1")

    if (!summary.passes()) {
        println("FAILED: gate is MAE <= 2.0 and F1 >= 0.95")
        exitProcess(1)
    }
}

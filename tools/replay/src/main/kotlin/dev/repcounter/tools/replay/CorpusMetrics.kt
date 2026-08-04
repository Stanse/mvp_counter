package dev.repcounter.tools.replay

import java.util.Locale
import kotlin.math.abs

/** One CSV row of `trace,expected,counted,abs_err,precision,recall,f1,mean_offset_ms` (spec §10). */
data class TraceMetrics(
    val traceName: String,
    val expected: Int,
    val counted: Int,
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val meanOffsetMs: Double,
) {
    val absError: Int get() = abs(expected - counted)
    val precision: Double get() = ratio(truePositives, truePositives + falsePositives)
    val recall: Double get() = ratio(truePositives, truePositives + falseNegatives)
    val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)

    private fun ratio(
        numerator: Int,
        denominator: Int,
    ): Double = if (denominator == 0) 1.0 else numerator.toDouble() / denominator

    fun toCsvRow(): String =
        listOf(
            traceName,
            expected,
            counted,
            absError,
            precision.round(),
            recall.round(),
            f1.round(),
            meanOffsetMs.round(),
        ).joinToString(",")

    // A CSV column must not contain a locale-dependent decimal separator - a comma here would
    // silently corrupt the column count on any machine whose default locale uses ',' for it.
    private fun Double.round(): String = String.format(Locale.ROOT, "%.4f", this)

    companion object {
        const val CSV_HEADER = "trace,expected,counted,abs_err,precision,recall,f1,mean_offset_ms"
    }
}

/**
 * Matches counted rep timestamps against a golden's expected ones within [windowMs] (spec §10:
 * `±150 мс`) to grade rep counting as detection, not exact pass/fail. Greedy nearest-available
 * match in expected-timestamp order; safe for jump-rope cadences where consecutive reps are
 * always much further apart than the matching window, so there's no ambiguity about which
 * candidate a match "should" prefer.
 */
object CorpusMetrics {
    fun match(
        traceName: String,
        expectedTimestampsMs: List<Long>,
        countedTimestampsMs: List<Long>,
        windowMs: Long = DEFAULT_MATCH_WINDOW_MS,
    ): TraceMetrics {
        val usedCounted = BooleanArray(countedTimestampsMs.size)
        var truePositives = 0
        var offsetSumMs = 0L

        for (expected in expectedTimestampsMs) {
            val matchIndex =
                countedTimestampsMs.indices.firstOrNull { i ->
                    !usedCounted[i] && abs(countedTimestampsMs[i] - expected) <= windowMs
                }
            if (matchIndex != null) {
                usedCounted[matchIndex] = true
                truePositives++
                offsetSumMs += abs(countedTimestampsMs[matchIndex] - expected)
            }
        }

        val falseNegatives = expectedTimestampsMs.size - truePositives
        val falsePositives = countedTimestampsMs.size - truePositives
        val meanOffsetMs = if (truePositives == 0) 0.0 else offsetSumMs.toDouble() / truePositives

        return TraceMetrics(
            traceName = traceName,
            expected = expectedTimestampsMs.size,
            counted = countedTimestampsMs.size,
            truePositives = truePositives,
            falsePositives = falsePositives,
            falseNegatives = falseNegatives,
            meanOffsetMs = meanOffsetMs,
        )
    }

    /** Corpus-wide MAE (spec §10: gate at `> 2.0`) and micro-F1 (gate at `< 0.95`). */
    fun aggregate(perTrace: List<TraceMetrics>): CorpusSummary {
        val mae = perTrace.map { it.absError }.average()
        val totalTp = perTrace.sumOf { it.truePositives }
        val totalFp = perTrace.sumOf { it.falsePositives }
        val totalFn = perTrace.sumOf { it.falseNegatives }
        val precision = if (totalTp + totalFp == 0) 1.0 else totalTp.toDouble() / (totalTp + totalFp)
        val recall = if (totalTp + totalFn == 0) 1.0 else totalTp.toDouble() / (totalTp + totalFn)
        val microF1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        return CorpusSummary(mae, microF1)
    }

    private const val DEFAULT_MATCH_WINDOW_MS = 150L
}

data class CorpusSummary(
    val mae: Double,
    val microF1: Double,
) {
    /** spec §10's CI gate: `MAE > 2.0 или F1 < 0.95`. */
    fun passes(): Boolean = mae <= MAE_THRESHOLD && microF1 >= F1_THRESHOLD

    private companion object {
        const val MAE_THRESHOLD = 2.0
        const val F1_THRESHOLD = 0.95
    }
}

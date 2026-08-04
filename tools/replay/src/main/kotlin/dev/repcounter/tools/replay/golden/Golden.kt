package dev.repcounter.tools.replay.golden

import kotlinx.serialization.Serializable

/** One technique segment's expected rep count within a trace - spec §9's golden-file format. */
@Serializable
data class GoldenSegment(
    val technique: String,
    val reps: Int,
    val tolerance: Int,
)

/**
 * `<trace>.expected.json` (spec §9): what a correct pipeline should produce for a given
 * synthetic trace. [repTimestampsMs] is a verified-correct [dev.repcounter.tools.replay.ReplayPipeline]
 * run captured as a regression baseline, not an independently hand-derived ground truth - see
 * `dev.repcounter.tools.replay.fixtures.GenerateFixturesTest` (test sources) and
 * `docs/DECISIONS.md`'s M3 entry on why.
 */
@Serializable
data class Golden(
    val totalReps: Int,
    val tolerance: Int,
    val segments: List<GoldenSegment>,
    val repTimestampsMs: List<Long>,
)

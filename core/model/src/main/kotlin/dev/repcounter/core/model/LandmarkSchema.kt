package dev.repcounter.core.model

/**
 * Maps semantic [LandmarkName]s to indices into [PoseFrame.landmarks] for a given pose model.
 *
 * `SignalExtractor` (`:signals`) reads landmarks exclusively through [indexOf] so it works
 * unmodified against any detector that supplies a schema here. If a schema has no point for a
 * requested name, [indexOf] returns `null` and the extractor must skip the signals derived
 * from it (see [dev.repcounter.core.model.ExerciseDescriptor] `requiredSignals` at the
 * analysis layer for how that propagates to exercise selection).
 */
@Suppress("MagicNumber") // these are the upstream model's own point indices, already named via the map key
enum class LandmarkSchema(
    private val indices: Map<LandmarkName, Int>,
) {
    /** MediaPipe BlazePose, 33 landmarks. */
    BLAZEPOSE_33(
        mapOf(
            LandmarkName.NOSE to 0,
            LandmarkName.LEFT_SHOULDER to 11,
            LandmarkName.RIGHT_SHOULDER to 12,
            LandmarkName.LEFT_ELBOW to 13,
            LandmarkName.RIGHT_ELBOW to 14,
            LandmarkName.LEFT_WRIST to 15,
            LandmarkName.RIGHT_WRIST to 16,
            LandmarkName.LEFT_HIP to 23,
            LandmarkName.RIGHT_HIP to 24,
            LandmarkName.LEFT_KNEE to 25,
            LandmarkName.RIGHT_KNEE to 26,
            LandmarkName.LEFT_ANKLE to 27,
            LandmarkName.RIGHT_ANKLE to 28,
            LandmarkName.LEFT_FOOT_INDEX to 31,
            LandmarkName.RIGHT_FOOT_INDEX to 32,
        ),
    ),

    /** TFLite MoveNet (Lightning/Thunder), 17 landmarks, COCO layout. No foot-index points. */
    MOVENET_17(
        mapOf(
            LandmarkName.NOSE to 0,
            LandmarkName.LEFT_SHOULDER to 5,
            LandmarkName.RIGHT_SHOULDER to 6,
            LandmarkName.LEFT_ELBOW to 7,
            LandmarkName.RIGHT_ELBOW to 8,
            LandmarkName.LEFT_WRIST to 9,
            LandmarkName.RIGHT_WRIST to 10,
            LandmarkName.LEFT_HIP to 11,
            LandmarkName.RIGHT_HIP to 12,
            LandmarkName.LEFT_KNEE to 13,
            LandmarkName.RIGHT_KNEE to 14,
            LandmarkName.LEFT_ANKLE to 15,
            LandmarkName.RIGHT_ANKLE to 16,
        ),
    ),
    ;

    /** Index into [PoseFrame.landmarks]/[PoseFrame.world] for [name], or `null` if unsupported. */
    fun indexOf(name: LandmarkName): Int? = indices[name]
}

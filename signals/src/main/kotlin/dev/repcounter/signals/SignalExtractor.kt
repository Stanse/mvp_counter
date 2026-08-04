package dev.repcounter.signals

import dev.repcounter.core.model.Landmark
import dev.repcounter.core.model.LandmarkName
import dev.repcounter.core.model.LandmarkSchema
import dev.repcounter.core.model.PoseFrame
import dev.repcounter.core.model.SignalFrame
import dev.repcounter.core.model.SignalId
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Pure `Skeleton -> Float` functions (spec §7): turns one normalized [PoseFrame]
 * ([PoseNormalizer]'s output) into a [SignalFrame]. A [SignalId] is present in the output only
 * if every landmark it depends on exists in [schema] and was actually reported this frame -
 * `ExerciseDescriptor.requiredSignals` (`:analysis:api`) is how an analyzer declares it needs
 * one that might be missing.
 */
object SignalExtractor {
    fun extract(
        frame: PoseFrame,
        schema: LandmarkSchema,
    ): SignalFrame {
        val joints = Joints(frame, schema)
        val values = mutableMapOf<SignalId, Float>()
        addVerticalPositionSignals(joints, values)
        addLimbAngleSignals(joints, values)
        addTorsoTiltSignal(joints, values)
        return SignalFrame(frame.tMs, values)
    }

    private fun addVerticalPositionSignals(
        joints: Joints,
        values: MutableMap<SignalId, Float>,
    ) {
        val hipY = meanY(joints.leftHip, joints.rightHip)
        val shoulderY = meanY(joints.leftShoulder, joints.rightShoulder)
        hipY?.let { values[SignalId.HIP_Y] = it }
        shoulderY?.let { values[SignalId.SHOULDER_Y] = it }
        joints.leftAnkle?.let { values[SignalId.ANKLE_Y_L] = it.y }
        joints.rightAnkle?.let { values[SignalId.ANKLE_Y_R] = it.y }
    }

    private fun addLimbAngleSignals(
        joints: Joints,
        values: MutableMap<SignalId, Float>,
    ) {
        val kneeAngleL = angleAtVertex(joints.leftHip, joints.leftKnee, joints.leftAnkle)
        val kneeAngleR = angleAtVertex(joints.rightHip, joints.rightKnee, joints.rightAnkle)
        putAngleTriple(
            values,
            kneeAngleL,
            kneeAngleR,
            SignalId.KNEE_ANGLE_L,
            SignalId.KNEE_ANGLE_R,
            SignalId.KNEE_ANGLE_MEAN,
        )

        val elbowAngleL = angleAtVertex(joints.leftShoulder, joints.leftElbow, joints.leftWrist)
        val elbowAngleR = angleAtVertex(joints.rightShoulder, joints.rightElbow, joints.rightWrist)
        putAngleTriple(
            values,
            elbowAngleL,
            elbowAngleR,
            SignalId.ELBOW_ANGLE_L,
            SignalId.ELBOW_ANGLE_R,
            SignalId.ELBOW_ANGLE_MEAN,
        )
    }

    private fun putAngleTriple(
        values: MutableMap<SignalId, Float>,
        left: Float?,
        right: Float?,
        leftId: SignalId,
        rightId: SignalId,
        meanId: SignalId,
    ) {
        left?.let { values[leftId] = it }
        right?.let { values[rightId] = it }
        if (left != null && right != null) values[meanId] = (left + right) / MEAN_DIVISOR
    }

    private fun addTorsoTiltSignal(
        joints: Joints,
        values: MutableMap<SignalId, Float>,
    ) {
        val hipCenter = center(joints.leftHip, joints.rightHip) ?: return
        val shoulderCenter = center(joints.leftShoulder, joints.rightShoulder) ?: return
        values[SignalId.TORSO_TILT] = torsoTiltFromHorizontalDeg(hipCenter, shoulderCenter)
    }

    private fun meanY(
        a: Landmark?,
        b: Landmark?,
    ): Float? {
        if (a == null || b == null) return null
        return (a.y + b.y) / MEAN_DIVISOR
    }

    private fun center(
        a: Landmark?,
        b: Landmark?,
    ): Landmark? {
        if (a == null || b == null) return null
        return Landmark(
            x = (a.x + b.x) / MEAN_DIVISOR,
            y = (a.y + b.y) / MEAN_DIVISOR,
            z = (a.z + b.z) / MEAN_DIVISOR,
            visibility = minOf(a.visibility, b.visibility),
        )
    }

    /**
     * Angle at [vertex] between the rays to [a] and [c], in degrees, using only `x`/`y` (angle
     * is unaffected by [PoseNormalizer]'s uniform scale, and 2D is what both BlazePose and
     * MoveNet reliably agree on - `z` depth quality varies a lot more between detectors).
     */
    @Suppress("ReturnCount")
    private fun angleAtVertex(
        a: Landmark?,
        vertex: Landmark?,
        c: Landmark?,
    ): Float? {
        if (a == null || vertex == null || c == null) return null
        val ax = a.x - vertex.x
        val ay = a.y - vertex.y
        val cx = c.x - vertex.x
        val cy = c.y - vertex.y
        val magnitudeA = hypot(ax.toDouble(), ay.toDouble())
        val magnitudeC = hypot(cx.toDouble(), cy.toDouble())
        if (magnitudeA < MIN_VECTOR_LENGTH || magnitudeC < MIN_VECTOR_LENGTH) return null
        val cosAngle = ((ax * cx + ay * cy) / (magnitudeA * magnitudeC)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosAngle)).toFloat()
    }

    /**
     * Angle in degrees between the hip->shoulder vector and the horizontal: `0` = lying flat
     * (pushup position), `90` = standing upright. Matches `Gate.TorsoHorizontal(maxTiltDeg)`
     * (spec §8.2): a low value passes the gate, a standing posture fails it.
     */
    private fun torsoTiltFromHorizontalDeg(
        hipCenter: Landmark,
        shoulderCenter: Landmark,
    ): Float {
        val dx = (shoulderCenter.x - hipCenter.x).toDouble()
        val dy = (shoulderCenter.y - hipCenter.y).toDouble()
        return Math.toDegrees(atan2(kotlin.math.abs(dy), kotlin.math.abs(dx))).toFloat()
    }

    private const val MIN_VECTOR_LENGTH = 1e-6
    private const val MEAN_DIVISOR = 2f

    /** Every joint a signal might depend on, looked up once per frame. */
    private class Joints(
        private val frame: PoseFrame,
        private val schema: LandmarkSchema,
    ) {
        private fun point(name: LandmarkName): Landmark? = schema.indexOf(name)?.let { frame.landmarks.getOrNull(it) }

        val leftHip = point(LandmarkName.LEFT_HIP)
        val rightHip = point(LandmarkName.RIGHT_HIP)
        val leftShoulder = point(LandmarkName.LEFT_SHOULDER)
        val rightShoulder = point(LandmarkName.RIGHT_SHOULDER)
        val leftKnee = point(LandmarkName.LEFT_KNEE)
        val rightKnee = point(LandmarkName.RIGHT_KNEE)
        val leftAnkle = point(LandmarkName.LEFT_ANKLE)
        val rightAnkle = point(LandmarkName.RIGHT_ANKLE)
        val leftElbow = point(LandmarkName.LEFT_ELBOW)
        val rightElbow = point(LandmarkName.RIGHT_ELBOW)
        val leftWrist = point(LandmarkName.LEFT_WRIST)
        val rightWrist = point(LandmarkName.RIGHT_WRIST)
    }
}

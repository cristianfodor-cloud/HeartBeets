package com.heartbeets.audio

/**
 * Computes the target playback cadence at any point in time given an active
 * [HeartbeatProfile] and the user's BPM when the profile was activated.
 *
 * The interpolator walks through stages sequentially, applying each stage's
 * easing curve to ramp from the previous offset to the current stage's offset.
 */
class ProfileInterpolator(
    private val profile: HeartbeatProfile,
    private val anchorBpm: Int,
    private val startTimeMs: Long = System.currentTimeMillis()
) {

    /**
     * Returns the target cadence (BPM) at the given instant.
     *
     * If [nowMs] is past the end of all stages, returns the final target BPM.
     */
    fun cadenceAt(nowMs: Long): Int {
        val elapsedSec = ((nowMs - startTimeMs) / 1000f).coerceAtLeast(0f)
        val offset = offsetAt(elapsedSec)
        return (anchorBpm + offset.toInt()).coerceIn(1, 220)
    }

    /**
     * Returns true if all stages have completed.
     */
    fun isFinished(nowMs: Long): Boolean {
        val elapsedSec = (nowMs - startTimeMs) / 1000f
        return elapsedSec >= profile.totalDurationSec
    }

    /**
     * Computes the BPM offset at a given elapsed time (seconds from start).
     */
    private fun offsetAt(elapsedSec: Float): Float {
        if (profile.stages.isEmpty()) return 0f

        var accumulatedSec = 0f
        var previousOffset = 0f // offset at the start of the current stage

        for (stage in profile.stages) {
            val stageEnd = accumulatedSec + stage.durationSec

            if (elapsedSec < stageEnd) {
                // We're within this stage — interpolate
                val stageElapsed = elapsedSec - accumulatedSec
                val progress = if (stage.durationSec > 0) {
                    stageElapsed / stage.durationSec
                } else {
                    1f
                }
                val eased = stage.curve.apply(progress)
                return previousOffset + (stage.targetOffsetBpm - previousOffset) * eased
            }

            // Stage complete — move to next
            previousOffset = stage.targetOffsetBpm.toFloat()
            accumulatedSec = stageEnd
        }

        // Past all stages — hold at final offset
        return previousOffset
    }
}

package com.heartbeets.audio

/**
 * Easing function applied to a profile stage's cadence ramp.
 */
enum class EasingCurve {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    /**
     * Maps a linear progress value [t] (0.0 to 1.0) to an eased value.
     */
    fun apply(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> clamped
            EASE_IN -> clamped * clamped
            EASE_OUT -> 1f - (1f - clamped) * (1f - clamped)
            EASE_IN_OUT -> if (clamped < 0.5f) {
                2f * clamped * clamped
            } else {
                1f - (-2f * clamped + 2f).let { it * it } / 2f
            }
        }
    }
}

/**
 * A single stage in a [HeartbeatProfile].
 *
 * @param targetOffsetBpm BPM offset from anchor (user's BPM at profile start).
 *   Negative = slower, positive = faster.
 * @param durationSec Duration of this stage's transition in seconds.
 * @param curve Easing function for the ramp.
 */
data class ProfileStage(
    val targetOffsetBpm: Int,
    val durationSec: Int,
    val curve: EasingCurve = EasingCurve.LINEAR
)

/**
 * Whether a profile's BPM targets are relative to the user's current heart rate
 * or absolute fixed values.
 */
enum class ProfileAnchorMode {
    /** Stages are offsets from the user's live BPM at activation time. */
    RELATIVE,
    /** Stages are absolute BPM targets (anchorBpm is ignored, startBpm is used instead). */
    ABSOLUTE
}

/**
 * A cadence profile: a sequence of stages that guide the playback tempo
 * away from (or back to) the user's current heart rate.
 *
 * @param id Unique identifier (UUID for user-created, slug for presets).
 * @param name Display name.
 * @param description Short description of the profile's purpose.
 * @param stages Ordered list of stages.
 * @param isPreset true if this profile is shipped with the app.
 * @param anchorMode Whether stages are relative offsets or absolute BPM targets.
 * @param startBpm For ABSOLUTE mode: the BPM the profile starts at. Ignored in RELATIVE mode.
 */
data class HeartbeatProfile(
    val id: String,
    val name: String,
    val description: String,
    val stages: List<ProfileStage>,
    val isPreset: Boolean = false,
    val anchorMode: ProfileAnchorMode = ProfileAnchorMode.RELATIVE,
    val startBpm: Int? = null
) {
    /** Total duration of all stages in seconds. */
    val totalDurationSec: Int get() = stages.sumOf { it.durationSec }
}

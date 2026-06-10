package com.heartbeets.audio

/**
 * The current mode of the [AudioEngine].
 */
enum class PlaybackMode {
    /** Audio engine is idle. */
    STOPPED,
    /** Cadence follows the live BPM 1:1. */
    MIRROR,
    /** Cadence follows an active [HeartbeatProfile] curve. */
    PROFILE
}

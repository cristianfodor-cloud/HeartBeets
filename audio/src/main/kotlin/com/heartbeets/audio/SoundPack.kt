package com.heartbeets.audio

/**
 * A heartbeat sound that can be loaded and played by the [AudioEngine].
 *
 * All packs are synthesized from [SynthParams].
 * User-created packs are persisted via [SoundPackRepository].
 */
data class SoundPack(
    val id: String,
    val displayName: String,
    val description: String,
    val synthParams: SynthParams? = null,
    val isUserCreated: Boolean = false,
)

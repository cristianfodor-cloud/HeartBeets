package com.heartbeets.audio

import androidx.annotation.RawRes

/**
 * A heartbeat sound that can be loaded and played by the [AudioEngine].
 *
 * A pack is either resource-based (bundled WAV) or synthesized from [SynthParams].
 * User-created packs are always synthesized.
 */
data class SoundPack(
    val id: String,
    val displayName: String,
    val description: String,
    @RawRes val sampleRes: Int? = null,
    val synthParams: SynthParams? = null,
    val isUserCreated: Boolean = false,
    /** If set, trim decoded audio to this duration (useful for extracting one beat from a loop). */
    val maxDurationMs: Int? = null,
) {
    /** True if this pack uses the synthesizer rather than a bundled resource. */
    val isSynthesized: Boolean get() = synthParams != null
}

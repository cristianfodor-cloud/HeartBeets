package com.heartbeets.audio

import androidx.annotation.RawRes

/**
 * A heartbeat sound that can be loaded and played by the [AudioEngine].
 *
 * Bundled packs reference a raw resource; future packs could reference a URI.
 */
data class SoundPack(
    val id: String,
    val displayName: String,
    val description: String,
    @RawRes val sampleRes: Int
)

package com.heartbeets.audio

/**
 * Registry of available [SoundPack]s. Bundled packs are always present;
 * future versions may add downloaded or user-imported packs.
 */
object SoundPackRegistry {

    private val packs: List<SoundPack> = listOf(
        SoundPack(
            id = "classic",
            displayName = "Classic",
            description = "Warm, realistic lub-dub heartbeat.",
            sampleRes = R.raw.heartbeat_classic
        ),
        SoundPack(
            id = "soft",
            displayName = "Soft",
            description = "Muffled, ambient-style pulse.",
            sampleRes = R.raw.heartbeat_soft
        ),
        SoundPack(
            id = "mechanical",
            displayName = "Mechanical",
            description = "Click/tick, metronome-like beat.",
            sampleRes = R.raw.heartbeat_mechanical
        ),
    )

    fun getAll(): List<SoundPack> = packs

    fun getById(id: String): SoundPack? = packs.find { it.id == id }

    fun getDefault(): SoundPack = packs.first()
}

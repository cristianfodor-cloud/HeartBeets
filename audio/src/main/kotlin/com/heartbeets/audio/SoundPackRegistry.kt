package com.heartbeets.audio

/**
 * Registry of available [SoundPack]s. Bundled packs are always present;
 * user-created packs are added at runtime.
 */
object SoundPackRegistry {

    private val builtInPacks: List<SoundPack> = listOf(
        SoundPack(
            id = "classic",
            displayName = "Classic",
            description = "Warm, realistic lub-dub heartbeat.",
            synthParams = SynthParams.CLASSIC,
        ),
        SoundPack(
            id = "soft",
            displayName = "Soft",
            description = "Muffled, ambient-style pulse.",
            synthParams = SynthParams.SOFT,
        ),
        SoundPack(
            id = "mechanical",
            displayName = "Mechanical",
            description = "Click/tick, metronome-like beat.",
            synthParams = SynthParams.MECHANICAL,
        ),
    )

    private val userPacks = mutableListOf<SoundPack>()

    fun getAll(): List<SoundPack> = builtInPacks + userPacks

    fun getBuiltIn(): List<SoundPack> = builtInPacks

    fun getUserPacks(): List<SoundPack> = userPacks.toList()

    fun getById(id: String): SoundPack? =
        builtInPacks.find { it.id == id } ?: userPacks.find { it.id == id }

    fun getDefault(): SoundPack = builtInPacks.first()

    fun addUserPack(pack: SoundPack) {
        userPacks.removeAll { it.id == pack.id }
        userPacks.add(pack)
    }

    fun removeUserPack(id: String) {
        userPacks.removeAll { it.id == id }
    }
}

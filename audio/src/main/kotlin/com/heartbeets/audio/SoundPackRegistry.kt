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
        SoundPack(
            id = "real_clean",
            displayName = "Real — Clean",
            description = "Single heartbeat recording, clean. CC0 by Lunardrive.",
            sampleRes = R.raw.heartbeat_real_clean,
        ),
        SoundPack(
            id = "real_stethoscope",
            displayName = "Real — Stethoscope",
            description = "Single heartbeat through a stethoscope. CC0 by Lunardrive.",
            sampleRes = R.raw.heartbeat_real_stethoscope,
        ),
        SoundPack(
            id = "real_warm",
            displayName = "Real — Warm",
            description = "MATLAB-synthesized realistic heartbeat (60 BPM). CC0 by loudernoises.",
            sampleRes = R.raw.heartbeat_real_matlab60,
            maxDurationMs = 900,
        ),
        SoundPack(
            id = "real_deep",
            displayName = "Real — Deep",
            description = "Deep, resonant heartbeat. CC0 by KaBlazik_Samples.",
            sampleRes = R.raw.heartbeat_real_deep,
            maxDurationMs = 900,
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

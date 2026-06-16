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
            id = "deep",
            displayName = "Deep",
            description = "Low, bassy heartbeat for deep relaxation.",
            synthParams = SynthParams.DEEP,
        ),
        SoundPack(
            id = "pulse",
            displayName = "Pulse",
            description = "Quick, light beat with energetic feel.",
            synthParams = SynthParams.PULSE,
        ),
        SoundPack(
            id = "womb",
            displayName = "Womb",
            description = "Warm, muffled thud — like being in the womb.",
            synthParams = SynthParams.WOMB,
        ),
        SoundPack(
            id = "deep-sleep",
            displayName = "Deep Sleep",
            description = "Deep heartbeat + brown noise + delta binaural (2 Hz).",
            synthParams = SynthParams.DEEP,
            noiseType = NoiseType.BROWN,
            noiseVolume = 0.4f,
            binauralPreset = BinauralPreset.DELTA,
            binauralVolume = 0.25f,
        ),
        SoundPack(
            id = "meditation",
            displayName = "Meditation",
            description = "Soft heartbeat + pink noise + theta binaural (6 Hz).",
            synthParams = SynthParams.SOFT,
            noiseType = NoiseType.PINK,
            noiseVolume = 0.3f,
            binauralPreset = BinauralPreset.THETA,
            binauralVolume = 0.2f,
        ),
        SoundPack(
            id = "focus",
            displayName = "Focus",
            description = "Classic heartbeat + white noise + alpha binaural (10 Hz).",
            synthParams = SynthParams.CLASSIC,
            noiseType = NoiseType.WHITE,
            noiseVolume = 0.2f,
            binauralPreset = BinauralPreset.ALPHA,
            binauralVolume = 0.2f,
        ),
        SoundPack(
            id = "energy",
            displayName = "Energy",
            description = "Pulse heartbeat + beta binaural (20 Hz) for alertness.",
            synthParams = SynthParams.PULSE,
            noiseType = NoiseType.NONE,
            binauralPreset = BinauralPreset.BETA,
            binauralVolume = 0.25f,
        ),
        // --- Solfeggio packs ---
        SoundPack(
            id = "solfeggio-174",
            displayName = "174 Hz — Grounding",
            description = "Grounding & pain relief. Deep heartbeat + brown noise + 174 Hz foundation tone.",
            synthParams = SynthParams.DEEP,
            noiseType = NoiseType.BROWN,
            noiseVolume = 0.25f,
            binauralPreset = BinauralPreset.DELTA,
            binauralVolume = 0.15f,
            solfeggioFrequency = SolfeggioFrequency.HZ_174,
            solfeggioVolume = 0.35f,
        ),
        SoundPack(
            id = "solfeggio-285",
            displayName = "285 Hz — Healing",
            description = "Cellular healing & regeneration. Soft pulse + pink noise + 285 Hz tone.",
            synthParams = SynthParams.SOFT,
            noiseType = NoiseType.PINK,
            noiseVolume = 0.2f,
            binauralPreset = BinauralPreset.THETA,
            binauralVolume = 0.15f,
            solfeggioFrequency = SolfeggioFrequency.HZ_285,
            solfeggioVolume = 0.35f,
        ),
        SoundPack(
            id = "solfeggio-396",
            displayName = "396 Hz — Liberation",
            description = "Releasing guilt & fear. Classic heartbeat + pink noise + 396 Hz tone.",
            synthParams = SynthParams.CLASSIC,
            noiseType = NoiseType.PINK,
            noiseVolume = 0.15f,
            binauralPreset = BinauralPreset.NONE,
            solfeggioFrequency = SolfeggioFrequency.HZ_396,
            solfeggioVolume = 0.35f,
        ),
        SoundPack(
            id = "solfeggio-432",
            displayName = "432 Hz — Earth",
            description = "Natural harmony & inner peace. Soft heartbeat + alpha binaural + 432 Hz tone.",
            synthParams = SynthParams.SOFT,
            noiseType = NoiseType.NONE,
            binauralPreset = BinauralPreset.ALPHA,
            binauralVolume = 0.15f,
            solfeggioFrequency = SolfeggioFrequency.HZ_432,
            solfeggioVolume = 0.35f,
        ),
        SoundPack(
            id = "solfeggio-528",
            displayName = "528 Hz — Love",
            description = "Love & transformation. Womb heartbeat + pink noise + theta binaural + 528 Hz.",
            synthParams = SynthParams.WOMB,
            noiseType = NoiseType.PINK,
            noiseVolume = 0.2f,
            binauralPreset = BinauralPreset.THETA,
            binauralVolume = 0.15f,
            solfeggioFrequency = SolfeggioFrequency.HZ_528,
            solfeggioVolume = 0.35f,
        ),
        SoundPack(
            id = "solfeggio-639",
            displayName = "639 Hz — Heart",
            description = "Compassion & connection. Soft heartbeat + pink noise + alpha binaural + 639 Hz.",
            synthParams = SynthParams.SOFT,
            noiseType = NoiseType.PINK,
            noiseVolume = 0.15f,
            binauralPreset = BinauralPreset.ALPHA,
            binauralVolume = 0.15f,
            solfeggioFrequency = SolfeggioFrequency.HZ_639,
            solfeggioVolume = 0.35f,
        ),
        SoundPack(
            id = "solfeggio-741",
            displayName = "741 Hz — Clarity",
            description = "Intuition & clarity. Classic heartbeat + white noise + beta binaural + 741 Hz.",
            synthParams = SynthParams.CLASSIC,
            noiseType = NoiseType.WHITE,
            noiseVolume = 0.15f,
            binauralPreset = BinauralPreset.BETA,
            binauralVolume = 0.15f,
            solfeggioFrequency = SolfeggioFrequency.HZ_741,
            solfeggioVolume = 0.35f,
        ),
        SoundPack(
            id = "solfeggio-963",
            displayName = "963 Hz — Crown",
            description = "Pure awareness & consciousness. Deep heartbeat + brown noise + delta binaural + 963 Hz.",
            synthParams = SynthParams.DEEP,
            noiseType = NoiseType.BROWN,
            noiseVolume = 0.2f,
            binauralPreset = BinauralPreset.DELTA,
            binauralVolume = 0.15f,
            solfeggioFrequency = SolfeggioFrequency.HZ_963,
            solfeggioVolume = 0.35f,
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

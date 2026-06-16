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
    // Background noise
    val noiseType: NoiseType = NoiseType.NONE,
    val noiseVolume: Float = 0.1f,
    // Binaural beats
    val binauralPreset: BinauralPreset = BinauralPreset.NONE,
    val binauralCarrierHz: Float = 200f,
    val binauralBeatHz: Float = 10f,
    val binauralVolume: Float = 0.3f,
    // Solfeggio tone
    val solfeggioFrequency: SolfeggioFrequency = SolfeggioFrequency.NONE,
    val solfeggioVolume: Float = 0.3f,
)

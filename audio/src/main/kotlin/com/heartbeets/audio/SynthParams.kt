package com.heartbeets.audio

/**
 * Parameters for synthesizing a single heartbeat sound (lub-dub).
 *
 * All durations are in milliseconds. Frequencies in Hz. Amplitudes 0.0–1.0.
 */
data class SynthParams(
    // --- Lub (S1 sound) ---
    val lubFrequency: Float = 65f,
    val lubAmplitude: Float = 1.0f,
    val lubDurationMs: Int = 80,
    val lubAttackMs: Int = 5,
    val lubDecayMs: Int = 60,

    // --- Dub (S2 sound) ---
    val dubFrequency: Float = 95f,
    val dubAmplitude: Float = 0.6f,
    val dubDurationMs: Int = 60,
    val dubAttackMs: Int = 5,
    val dubDecayMs: Int = 45,
    /** Delay between lub onset and dub onset, in ms. */
    val dubOffsetMs: Int = 150,

    // --- Body / resonance ---
    /** Low-frequency body thump layered under the lub. */
    val bodyFrequency: Float = 35f,
    val bodyAmplitude: Float = 0.3f,
    val bodyDurationMs: Int = 100,

    // --- Character ---
    /** Low-pass filter cutoff in Hz. Lower = more muffled. 0 = no filter. */
    val lowPassHz: Float = 0f,
    /** Noise layer amplitude (adds organic texture). 0 = clean. */
    val noiseAmplitude: Float = 0.02f,
    /** Overall output gain (0.0–1.0). */
    val masterGain: Float = 0.9f,
) {
    companion object {
        /** Warm, realistic lub-dub. */
        val CLASSIC = SynthParams()

        /** Muffled, ambient pulse. */
        val SOFT = SynthParams(
            lubFrequency = 50f,
            lubAmplitude = 0.8f,
            lubDurationMs = 100,
            lubDecayMs = 80,
            dubFrequency = 70f,
            dubAmplitude = 0.4f,
            dubDurationMs = 70,
            dubDecayMs = 55,
            dubOffsetMs = 170,
            bodyFrequency = 30f,
            bodyAmplitude = 0.4f,
            bodyDurationMs = 120,
            lowPassHz = 200f,
            noiseAmplitude = 0.01f,
            masterGain = 0.85f,
        )

        /** Sharp, click-like metronome beat. */
        val MECHANICAL = SynthParams(
            lubFrequency = 120f,
            lubAmplitude = 1.0f,
            lubDurationMs = 40,
            lubAttackMs = 2,
            lubDecayMs = 30,
            dubFrequency = 180f,
            dubAmplitude = 0.7f,
            dubDurationMs = 30,
            dubAttackMs = 2,
            dubDecayMs = 20,
            dubOffsetMs = 120,
            bodyFrequency = 60f,
            bodyAmplitude = 0.1f,
            bodyDurationMs = 50,
            lowPassHz = 0f,
            noiseAmplitude = 0.05f,
            masterGain = 0.95f,
        )

        /** Deep, bassy heartbeat for sleep/relaxation. */
        val DEEP = SynthParams(
            lubFrequency = 40f,
            lubAmplitude = 0.9f,
            lubDurationMs = 120,
            lubAttackMs = 8,
            lubDecayMs = 90,
            dubFrequency = 55f,
            dubAmplitude = 0.4f,
            dubDurationMs = 90,
            dubAttackMs = 8,
            dubDecayMs = 70,
            dubOffsetMs = 200,
            bodyFrequency = 25f,
            bodyAmplitude = 0.5f,
            bodyDurationMs = 150,
            lowPassHz = 150f,
            noiseAmplitude = 0.01f,
            masterGain = 0.8f,
        )

        /** Quick, light pulse — energetic feel. */
        val PULSE = SynthParams(
            lubFrequency = 90f,
            lubAmplitude = 0.85f,
            lubDurationMs = 50,
            lubAttackMs = 3,
            lubDecayMs = 35,
            dubFrequency = 130f,
            dubAmplitude = 0.5f,
            dubDurationMs = 40,
            dubAttackMs = 3,
            dubDecayMs = 28,
            dubOffsetMs = 110,
            bodyFrequency = 50f,
            bodyAmplitude = 0.2f,
            bodyDurationMs = 60,
            lowPassHz = 0f,
            noiseAmplitude = 0.03f,
            masterGain = 0.9f,
        )

        /** Warm, womb-like muffled thud. */
        val WOMB = SynthParams(
            lubFrequency = 45f,
            lubAmplitude = 0.7f,
            lubDurationMs = 140,
            lubAttackMs = 15,
            lubDecayMs = 110,
            dubFrequency = 60f,
            dubAmplitude = 0.3f,
            dubDurationMs = 100,
            dubAttackMs = 12,
            dubDecayMs = 80,
            dubOffsetMs = 220,
            bodyFrequency = 22f,
            bodyAmplitude = 0.6f,
            bodyDurationMs = 180,
            lowPassHz = 120f,
            noiseAmplitude = 0.0f,
            masterGain = 0.75f,
        )
    }
}

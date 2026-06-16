package com.heartbeets.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates a continuous solfeggio tone — a pure sine wave at the given frequency,
 * panned equally to both channels (center, not binaural).
 */
internal class SolfeggioGenerator(private val sampleRate: Int = 44100) {

    private var phase = 0.0

    /**
     * Mix solfeggio tone into an existing stereo interleaved [buffer].
     * Uses a cubic volume curve for perceptual linearity.
     */
    fun fillStereo(buffer: ShortArray, frequencyHz: Float, volume: Float) {
        if (frequencyHz <= 0f || volume <= 0f) return
        // Cubic curve for consistent feel with noise volume
        val gain = volume * volume * volume * Short.MAX_VALUE * 0.5
        val phaseInc = 2.0 * PI * frequencyHz / sampleRate

        var i = 0
        while (i < buffer.size) {
            val sample = (sin(phase) * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            // Center-panned: same sample to both channels
            buffer[i] = (buffer[i] + sample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i + 1] = (buffer[i + 1] + sample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            phase += phaseInc
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            i += 2
        }
    }

    fun reset() {
        phase = 0.0
    }
}

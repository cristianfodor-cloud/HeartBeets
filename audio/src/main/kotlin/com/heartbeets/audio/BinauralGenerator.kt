package com.heartbeets.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates binaural beat tones — left channel gets carrierHz, right channel gets carrierHz + beatHz.
 * The perceived beat frequency is the difference between the two.
 */
internal class BinauralGenerator(private val sampleRate: Int = 44100) {

    private var phaseLeft = 0.0
    private var phaseRight = 0.0

    /**
     * Fill [buffer] with binaural stereo samples (interleaved L/R), mixed into existing content.
     * [carrierHz] is the base frequency, [beatHz] is the difference frequency.
     */
    fun fillStereo(
        buffer: ShortArray,
        carrierHz: Float,
        beatHz: Float,
        volume: Float,
    ) {
        if (carrierHz <= 0f || volume <= 0f) return
        val freqLeft = carrierHz.toDouble()
        val freqRight = (carrierHz + beatHz).toDouble()
        val gain = volume * Short.MAX_VALUE * 0.5 // keep headroom
        val phaseIncLeft = 2.0 * PI * freqLeft / sampleRate
        val phaseIncRight = 2.0 * PI * freqRight / sampleRate

        var i = 0
        while (i < buffer.size) {
            val sampleL = (sin(phaseLeft) * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val sampleR = (sin(phaseRight) * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            // Mix into existing buffer
            buffer[i] = (buffer[i] + sampleL).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i + 1] = (buffer[i + 1] + sampleR).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            phaseLeft += phaseIncLeft
            phaseRight += phaseIncRight

            // Keep phase bounded to avoid precision loss
            if (phaseLeft > 2.0 * PI) phaseLeft -= 2.0 * PI
            if (phaseRight > 2.0 * PI) phaseRight -= 2.0 * PI

            i += 2
        }
    }

    fun reset() {
        phaseLeft = 0.0
        phaseRight = 0.0
    }
}

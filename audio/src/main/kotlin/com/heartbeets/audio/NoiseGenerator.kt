package com.heartbeets.audio

import kotlin.random.Random

/**
 * Generates continuous background noise samples (white, pink, or brown).
 * All methods fill a buffer with mono samples in [-1.0, 1.0] range scaled to Short.
 */
internal class NoiseGenerator {

    // Pink noise state (Voss-McCartney algorithm, 4 octaves)
    private val pinkRows = IntArray(4)
    private var pinkRunningSum = 0
    private var pinkIndex = 0

    // Brown noise state
    private var brownLast = 0f

    /**
     * Fill [buffer] with noise samples of the given [type], scaled by [volume].
     * Writes interleaved stereo (same noise in both channels).
     */
    fun fillStereo(buffer: ShortArray, type: NoiseType, volume: Float) {
        if (type == NoiseType.NONE || volume <= 0f) return
        // Cubic curve: slider 0.1 → gain ~0.001 (barely audible), 1.0 → full
        val gain = volume * volume * volume * Short.MAX_VALUE
        var i = 0
        while (i < buffer.size) {
            val sample = when (type) {
                NoiseType.WHITE -> nextWhite()
                NoiseType.PINK -> nextPink()
                NoiseType.BROWN -> nextBrown()
                NoiseType.NONE -> 0f
            }
            val s = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            // Add to existing buffer content (mix)
            buffer[i] = (buffer[i] + s).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i + 1] = (buffer[i + 1] + s).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            i += 2
        }
    }

    private fun nextWhite(): Float = Random.nextFloat() * 2f - 1f

    private fun nextPink(): Float {
        // Voss-McCartney: update one row based on trailing zeros of index
        val lastIndex = pinkIndex
        pinkIndex++
        var diff = lastIndex xor pinkIndex
        var row = 0
        while (diff > 0 && row < pinkRows.size) {
            if (diff and 1 == 1) {
                pinkRunningSum -= pinkRows[row]
                val newVal = (Random.nextFloat() * 2f - 1f).let { (it * 1024).toInt() }
                pinkRows[row] = newVal
                pinkRunningSum += newVal
            }
            diff = diff shr 1
            row++
        }
        val white = (Random.nextFloat() * 2f - 1f) * 1024
        return (pinkRunningSum + white) / (1024f * (pinkRows.size + 1))
    }

    private fun nextBrown(): Float {
        // Brown noise: integrate white noise with leak
        brownLast += (Random.nextFloat() * 2f - 1f) * 0.1f
        brownLast = brownLast.coerceIn(-1f, 1f) * 0.998f // slight decay to prevent drift
        return brownLast
    }
}

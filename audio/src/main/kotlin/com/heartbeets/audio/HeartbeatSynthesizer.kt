package com.heartbeets.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates heartbeat PCM audio from [SynthParams].
 *
 * Produces a 16-bit mono PCM [ShortArray] at 44100 Hz containing a single
 * lub-dub heartbeat sound. The output can be used directly with [CadenceScheduler].
 */
object HeartbeatSynthesizer {

    private const val SAMPLE_RATE = 44_100
    private const val TWO_PI = 2.0 * PI

    /**
     * Synthesize a single heartbeat from the given parameters.
     * Returns a PCM ShortArray ready for AudioTrack playback.
     */
    fun synthesize(params: SynthParams): ShortArray {
        // Total duration: from start to end of dub + some tail
        val totalMs = params.dubOffsetMs + params.dubDurationMs + 50 // 50ms tail for decay
        val totalSamples = msToSamples(totalMs)

        val buffer = FloatArray(totalSamples)

        // Layer 1: Body thump
        addComponent(
            buffer = buffer,
            startMs = 0,
            frequency = params.bodyFrequency,
            amplitude = params.bodyAmplitude,
            durationMs = params.bodyDurationMs,
            attackMs = params.lubAttackMs,
            decayMs = params.bodyDurationMs - params.lubAttackMs,
        )

        // Layer 2: Lub (S1)
        addComponent(
            buffer = buffer,
            startMs = 0,
            frequency = params.lubFrequency,
            amplitude = params.lubAmplitude,
            durationMs = params.lubDurationMs,
            attackMs = params.lubAttackMs,
            decayMs = params.lubDecayMs,
        )

        // Layer 3: Dub (S2)
        addComponent(
            buffer = buffer,
            startMs = params.dubOffsetMs,
            frequency = params.dubFrequency,
            amplitude = params.dubAmplitude,
            durationMs = params.dubDurationMs,
            attackMs = params.dubAttackMs,
            decayMs = params.dubDecayMs,
        )

        // Layer 4: Noise texture
        if (params.noiseAmplitude > 0f) {
            addNoise(buffer, params.noiseAmplitude, params.lubDurationMs + params.dubOffsetMs + params.dubDurationMs)
        }

        // Apply low-pass filter if specified
        if (params.lowPassHz > 0f) {
            applyLowPass(buffer, params.lowPassHz)
        }

        // Apply master gain and convert to ShortArray
        return toShortArray(buffer, params.masterGain)
    }

    private fun addComponent(
        buffer: FloatArray,
        startMs: Int,
        frequency: Float,
        amplitude: Float,
        durationMs: Int,
        attackMs: Int,
        decayMs: Int,
    ) {
        val startSample = msToSamples(startMs)
        val durationSamples = msToSamples(durationMs)
        val attackSamples = msToSamples(attackMs)
        val decaySamples = msToSamples(decayMs)

        for (i in 0 until durationSamples) {
            val idx = startSample + i
            if (idx >= buffer.size) break

            // Oscillator
            val t = i.toDouble() / SAMPLE_RATE
            val osc = sin(TWO_PI * frequency * t).toFloat()

            // Envelope: attack then exponential decay
            val envelope = if (i < attackSamples) {
                i.toFloat() / attackSamples.coerceAtLeast(1)
            } else {
                val decayProgress = (i - attackSamples).toFloat() / decaySamples.coerceAtLeast(1)
                exp(-3.0 * decayProgress).toFloat()
            }

            buffer[idx] += osc * amplitude * envelope
        }
    }

    private fun addNoise(buffer: FloatArray, amplitude: Float, durationMs: Int) {
        val samples = msToSamples(durationMs).coerceAtMost(buffer.size)
        val rng = Random(42) // deterministic for reproducibility
        for (i in 0 until samples) {
            // Shaped noise: envelope follows the overall beat shape
            val progress = i.toFloat() / samples
            val env = exp(-2.0 * progress).toFloat()
            buffer[i] += (rng.nextFloat() * 2f - 1f) * amplitude * env
        }
    }

    private fun applyLowPass(buffer: FloatArray, cutoffHz: Float) {
        // Simple one-pole low-pass filter
        val rc = 1.0f / (TWO_PI.toFloat() * cutoffHz)
        val dt = 1.0f / SAMPLE_RATE
        val alpha = dt / (rc + dt)

        var prev = buffer[0]
        for (i in 1 until buffer.size) {
            prev += alpha * (buffer[i] - prev)
            buffer[i] = prev
        }
    }

    private fun toShortArray(buffer: FloatArray, masterGain: Float): ShortArray {
        // Find peak for normalization
        var peak = 0f
        for (sample in buffer) {
            val abs = if (sample < 0) -sample else sample
            if (abs > peak) peak = abs
        }
        if (peak == 0f) peak = 1f

        // Normalize to [-1, 1] then scale by masterGain and convert to Short
        val scale = masterGain / peak * Short.MAX_VALUE
        return ShortArray(buffer.size) { i ->
            (buffer[i] * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun msToSamples(ms: Int): Int = (SAMPLE_RATE.toLong() * ms / 1000).toInt()
}

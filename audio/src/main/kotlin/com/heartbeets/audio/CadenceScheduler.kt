package com.heartbeets.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Schedules heartbeat sound playback at a given cadence (BPM).
 *
 * Runs a dedicated coroutine that writes PCM data (heartbeat sample + silence)
 * into an [AudioTrack] stream continuously. The interval between beats is
 * recalculated whenever [updateBpm] is called; the new tempo takes effect at
 * the start of the next beat cycle.
 */
internal class CadenceScheduler {

    private val sampleRate = PcmLoader.SAMPLE_RATE
    private val currentBpm = AtomicInteger(72)
    private val pcmBuffer = AtomicReference<ShortArray>(null)

    /** Phase offset in milliseconds. Positive = delay beat, negative = advance beat. */
    private val phaseOffsetMs = AtomicInteger(0)

    /** Tracks whether a phase adjustment was made and needs to be applied on the next beat. */
    private val pendingPhaseAdjustmentSamples = AtomicInteger(0)

    private var audioTrack: AudioTrack? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    /**
     * Replace the heartbeat PCM sample used for playback.
     * Takes effect on the next beat.
     */
    fun setSample(pcm: ShortArray) {
        pcmBuffer.set(pcm)
    }

    /**
     * Update the target cadence. The next beat will use this BPM.
     */
    fun updateBpm(bpm: Int) {
        currentBpm.set(bpm.coerceIn(1, 220))
    }

    /**
     * Shift phase by [deltaMs] milliseconds. Positive = delay, negative = advance.
     * Each tap accumulates. The adjustment is applied on the next beat boundary.
     */
    fun adjustPhase(deltaMs: Int) {
        phaseOffsetMs.addAndGet(deltaMs)
        val deltaSamples = (sampleRate.toLong() * deltaMs / 1000).toInt()
        pendingPhaseAdjustmentSamples.addAndGet(deltaSamples)
    }

    /** Current accumulated phase offset in milliseconds. */
    fun getPhaseOffsetMs(): Int = phaseOffsetMs.get()

    /** Reset phase offset to zero. */
    fun resetPhase() {
        phaseOffsetMs.set(0)
        pendingPhaseAdjustmentSamples.set(0)
    }

    /**
     * Start the playback loop. Does nothing if already running.
     */
    fun start() {
        if (job?.isActive == true) return

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(sampleRate * 2)) // ~1s buffer
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        track.play()
        audioTrack = track

        val handler = CoroutineExceptionHandler { _, t ->
            if (t !is CancellationException) {
                android.util.Log.w("CadenceScheduler", "Playback error", t)
            }
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
        job = scope!!.launch {
            playbackLoop(track)
        }
    }

    /**
     * Stop playback and release audio resources.
     */
    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) { /* already stopped */ }
            it.release()
        }
        audioTrack = null
    }

    val isPlaying: Boolean get() = job?.isActive == true

    private fun playbackLoop(track: AudioTrack) {
        val silence = ShortArray(sampleRate / 10) // 100ms chunks of silence for filling gaps

        while (scope?.isActive == true) {
            val sample = pcmBuffer.get()
            if (sample == null || sample.isEmpty()) {
                // No sample loaded yet — write silence and retry
                track.write(silence, 0, silence.size)
                continue
            }

            val bpm = currentBpm.get()
            val intervalSamples = (sampleRate * 60L / bpm).toInt()

            // Apply any pending phase adjustment as extra silence (or skip) on this beat
            val pendingPhase = pendingPhaseAdjustmentSamples.getAndSet(0)
            val silenceSamples = (intervalSamples + pendingPhase - sample.size).coerceAtLeast(0)

            // Write the heartbeat sound
            track.write(sample, 0, sample.size)

            // Fill the rest of the interval with silence
            var remaining = silenceSamples
            while (remaining > 0 && scope?.isActive == true) {
                val chunkSize = remaining.coerceAtMost(silence.size)
                track.write(silence, 0, chunkSize)
                remaining -= chunkSize
            }
        }
    }
}

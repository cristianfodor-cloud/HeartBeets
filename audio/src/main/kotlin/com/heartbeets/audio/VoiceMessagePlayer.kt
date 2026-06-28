package com.heartbeets.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays recorded voice messages at regular intervals.
 *
 * Audio is played through the system mixer alongside the heartbeat [AudioTrack],
 * so both are heard simultaneously.
 */
class VoiceMessagePlayer {

    private var scope: CoroutineScope? = null
    private var timerJob: Job? = null

    private var recordings: List<String> = emptyList()
    private var intervalMs: Long = 30_000L
    private var volume: Float = 0.8f
    private var currentIndex: Int = 0
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Configure for playback.
     * @param filePaths List of local file paths to recorded messages.
     * @param intervalSec Seconds between messages (min 10).
     * @param vol Volume 0..1.
     */
    fun configure(filePaths: List<String>, intervalSec: Int, vol: Float) {
        recordings = filePaths.filter { java.io.File(it).exists() }
        intervalMs = intervalSec.coerceAtLeast(10) * 1000L
        volume = vol.coerceIn(0f, 1f)
        currentIndex = 0
    }

    /**
     * Start playing voice messages at the configured interval.
     * First message plays after one full interval.
     */
    fun start() {
        if (recordings.isEmpty()) return
        stop()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        timerJob = scope?.launch {
            try {
                delay(intervalMs)
                while (isActive) {
                    playNext()
                    delay(intervalMs)
                }
            } catch (_: CancellationException) { /* normal */ }
        }
    }

    /** Stop playback and release resources. */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        scope?.cancel()
        scope = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /** Play a single recording immediately (for preview). */
    fun playOne(filePath: String) {
        mediaPlayer?.release()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setVolume(volume, volume)
                prepare()
                start()
                setOnCompletionListener { mp -> mp.release() }
            }
        } catch (e: Exception) {
            Log.e("VoiceMessagePlayer", "Failed to play: $filePath", e)
        }
    }

    /** Release all resources. */
    fun release() {
        stop()
    }

    private fun playNext() {
        if (recordings.isEmpty()) return
        val filePath = recordings[currentIndex % recordings.size]
        currentIndex++
        playOne(filePath)
    }
}

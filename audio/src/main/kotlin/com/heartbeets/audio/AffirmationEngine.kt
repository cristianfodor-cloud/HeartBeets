package com.heartbeets.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
import java.util.Locale

/**
 * Speaks positive affirmations at regular intervals using the Android TTS engine,
 * or plays recorded voice messages from files.
 *
 * The audio is played through the system audio mixer alongside the [AudioTrack]
 * from [CadenceScheduler], so both are heard simultaneously without PCM-level mixing.
 */
class AffirmationEngine(private val appContext: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    private var scope: CoroutineScope? = null
    private var timerJob: Job? = null

    private var mode: AffirmationMode = AffirmationMode.NONE
    private var affirmations: List<String> = emptyList()
    private var recordings: List<String> = emptyList()
    private var intervalMs: Long = 30_000L
    private var volume: Float = 0.8f
    private var currentIndex: Int = 0
    private var mediaPlayer: MediaPlayer? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
            }
        }
    }

    /**
     * Returns offline-capable voices for the device locale.
     * Each [Voice] has a [Voice.getName] that can be stored in the sound pack.
     */
    fun getAvailableVoices(): List<Voice> {
        if (!ttsReady) return emptyList()
        return tts?.voices
            ?.filter { !it.isNetworkConnectionRequired && it.locale.language == "en" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Configure the engine for a sound pack's affirmation settings.
     * Call before [start].
     */
    fun configure(
        texts: List<String>,
        intervalSec: Int,
        vol: Float,
        speechRate: Float = 0.9f,
        pitch: Float = 1.0f,
        voiceName: String? = null,
    ) {
        mode = AffirmationMode.TTS
        affirmations = texts
        recordings = emptyList()
        intervalMs = intervalSec.coerceAtLeast(10) * 1000L
        volume = vol.coerceIn(0f, 1f)
        currentIndex = 0

        tts?.setSpeechRate(speechRate.coerceIn(0.5f, 1.5f))
        tts?.setPitch(pitch.coerceIn(0.5f, 1.5f))

        if (voiceName != null) {
            tts?.voices?.firstOrNull { it.name == voiceName }?.let { tts?.voice = it }
        } else {
            tts?.voice = tts?.defaultVoice
        }
    }

    /**
     * Configure the engine for recorded voice message playback.
     * Call before [start].
     */
    fun configureRecorded(
        filePaths: List<String>,
        intervalSec: Int,
        vol: Float,
    ) {
        mode = AffirmationMode.RECORDED
        recordings = filePaths.filter { java.io.File(it).exists() }
        affirmations = emptyList()
        intervalMs = intervalSec.coerceAtLeast(10) * 1000L
        volume = vol.coerceIn(0f, 1f)
        currentIndex = 0
    }

    /**
     * Start speaking affirmations at the configured interval.
     * The first affirmation is spoken after one full interval (not immediately).
     */
    fun start() {
        if (mode == AffirmationMode.TTS && (affirmations.isEmpty() || !ttsReady)) return
        if (mode == AffirmationMode.RECORDED && recordings.isEmpty()) return
        if (mode == AffirmationMode.NONE) return
        stop()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        timerJob = scope?.launch {
            try {
                delay(intervalMs)
                while (isActive) {
                    when (mode) {
                        AffirmationMode.TTS -> speakNext()
                        AffirmationMode.RECORDED -> playNextRecording()
                        AffirmationMode.NONE -> {}
                    }
                    delay(intervalMs)
                }
            } catch (_: CancellationException) { /* normal */ }
        }
    }

    /**
     * Stop the affirmation timer and silence any in-progress speech/playback.
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        scope?.cancel()
        scope = null
        tts?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * Speak a single affirmation immediately (for preview in the sound designer).
     */
    fun speakOne(text: String) {
        if (!ttsReady) return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "preview")
    }

    /**
     * Play a single recorded file immediately (for preview).
     */
    fun playRecording(filePath: String) {
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
            Log.e("AffirmationEngine", "Failed to play recording: $filePath", e)
        }
    }

    /**
     * Release the TTS engine and media player. Call when no longer needed.
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    val isReady: Boolean get() = ttsReady

    private fun speakNext() {
        if (affirmations.isEmpty() || !ttsReady) return
        val text = affirmations[currentIndex % affirmations.size]
        currentIndex++
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "affirmation_$currentIndex")
    }

    private fun playNextRecording() {
        if (recordings.isEmpty()) return
        val filePath = recordings[currentIndex % recordings.size]
        currentIndex++
        playRecording(filePath)
    }
}

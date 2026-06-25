package com.heartbeets.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
 * Speaks positive affirmations at regular intervals using the Android TTS engine.
 *
 * The TTS audio is played through the system audio mixer alongside the [AudioTrack]
 * from [CadenceScheduler], so both are heard simultaneously without PCM-level mixing.
 */
class AffirmationEngine(context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    private var scope: CoroutineScope? = null
    private var timerJob: Job? = null

    private var affirmations: List<String> = emptyList()
    private var intervalMs: Long = 30_000L
    private var volume: Float = 0.8f
    private var currentIndex: Int = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
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
        affirmations = texts
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
     * Start speaking affirmations at the configured interval.
     * The first affirmation is spoken after one full interval (not immediately).
     */
    fun start() {
        if (affirmations.isEmpty() || !ttsReady) return
        stop()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        timerJob = scope?.launch {
            try {
                delay(intervalMs)
                while (isActive) {
                    speakNext()
                    delay(intervalMs)
                }
            } catch (_: CancellationException) { /* normal */ }
        }
    }

    /**
     * Stop the affirmation timer and silence any in-progress speech.
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        scope?.cancel()
        scope = null
        tts?.stop()
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
     * Release the TTS engine. Call when no longer needed.
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
}

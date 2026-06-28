package com.heartbeets.audio

import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Top-level API for heartbeat audio playback.
 *
 * Plays a [Heartbeat] configuration: the synth sound follows the BPM timeline,
 * background layers (binaural, solfeggio, noise) play continuously,
 * and voice messages play at intervals.
 */
class AudioEngine(private val context: Context) {

    private val scheduler = CadenceScheduler()
    val voicePlayer = VoiceMessagePlayer()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _currentBpm = MutableStateFlow(0)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _elapsedSec = MutableStateFlow(0)
    val elapsedSec: StateFlow<Int> = _elapsedSec.asStateFlow()

    private var timelineJob: Job? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            android.util.Log.w("AudioEngine", "Coroutine error", throwable)
        }
    }
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private var activeHeartbeat: Heartbeat? = null

    /**
     * Configure the engine for a [Heartbeat]. Call before [play].
     */
    fun setHeartbeat(heartbeat: Heartbeat) {
        activeHeartbeat = heartbeat
        val pcm = HeartbeatSynthesizer.synthesize(heartbeat.synthParams)
        scheduler.setSample(pcm)

        // Background layers
        scheduler.setNoise(heartbeat.noiseType, heartbeat.noiseVolume)
        val carrierHz = if (heartbeat.binauralPreset == BinauralPreset.CUSTOM) {
            heartbeat.binauralCarrierHz
        } else {
            heartbeat.binauralPreset.carrierHz
        }
        val beatHz = if (heartbeat.binauralPreset == BinauralPreset.CUSTOM) {
            heartbeat.binauralBeatHz
        } else {
            heartbeat.binauralPreset.beatHz
        }
        val binVol = if (heartbeat.binauralPreset == BinauralPreset.NONE) 0f else heartbeat.binauralVolume
        scheduler.setBinaural(carrierHz, beatHz, binVol)

        val solVol = if (heartbeat.solfeggioFrequency == SolfeggioFrequency.NONE) 0f else heartbeat.solfeggioVolume
        scheduler.setSolfeggio(heartbeat.solfeggioFrequency.hz, solVol)

        // Voice messages
        if (heartbeat.voiceEnabled && heartbeat.voiceRecordings.isNotEmpty()) {
            voicePlayer.configure(
                filePaths = heartbeat.voiceRecordings,
                intervalSec = heartbeat.voiceIntervalSec,
                vol = heartbeat.voiceVolume,
            )
        }
    }

    /**
     * Start playback following the heartbeat's BPM timeline.
     */
    fun play() {
        val heartbeat = activeHeartbeat ?: return
        stop()
        _playing.value = true

        // Start at initial BPM
        val startBpm = heartbeat.timeline.firstOrNull()?.bpmStart ?: 65
        scheduler.updateBpm(startBpm)
        _currentBpm.value = startBpm
        scheduler.start()

        // Start voice messages
        if (heartbeat.voiceEnabled && heartbeat.voiceRecordings.isNotEmpty()) {
            voicePlayer.start()
        }

        // Follow the timeline
        timelineJob = scope.launch {
            val startTime = System.currentTimeMillis()
            try {
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val elapsedSec = (elapsed / 1000).toInt()
                    _elapsedSec.value = elapsedSec

                    val bpm = computeBpmAtTime(heartbeat.timeline, elapsed)
                    scheduler.updateBpm(bpm)
                    _currentBpm.value = bpm

                    // Check if timeline is complete
                    if (elapsedSec >= heartbeat.totalDurationSec) {
                        break
                    }
                    delay(200)
                }
                // Timeline finished — stop playback directly
                scheduler.stop()
                voicePlayer.stop()
                _playing.value = false
                _currentBpm.value = 0
                _elapsedSec.value = 0
            } catch (_: CancellationException) { /* normal */ }
        }
    }

    /** Stop playback. */
    fun stop() {
        timelineJob?.cancel()
        timelineJob = null
        scheduler.stop()
        voicePlayer.stop()
        _playing.value = false
        _currentBpm.value = 0
        _elapsedSec.value = 0
    }

    /** Release all resources. */
    fun release() {
        stop()
        voicePlayer.release()
        scope.cancel()
    }

    /**
     * Load synth params directly for preview (no timeline/layers).
     */
    fun setSynthParams(params: SynthParams) {
        val pcm = HeartbeatSynthesizer.synthesize(params)
        scheduler.setSample(pcm)
    }

    private var previewJob: Job? = null

    /**
     * Play a single heartbeat for preview.
     */
    fun previewBeat(params: SynthParams = activeHeartbeat?.synthParams ?: SynthParams.CLASSIC) {
        previewJob?.cancel()
        val pcm = boostSamples(HeartbeatSynthesizer.synthesize(params))
        previewJob = scope.launch(Dispatchers.Default) {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                val durationMs = (pcm.size * 1000L) / 44100
                delay(durationMs + 50)
            } catch (_: CancellationException) { }
            finally {
                try { track.stop() } catch (_: IllegalStateException) {}
                track.release()
            }
        }
    }

    /**
     * Play 3 heartbeats at a specific BPM for preview.
     */
    fun previewBeatsAtBpm(params: SynthParams, bpm: Int) {
        previewJob?.cancel()
        val pcm = boostSamples(HeartbeatSynthesizer.synthesize(params))
        val beatIntervalMs = 60_000L / bpm
        previewJob = scope.launch(Dispatchers.Default) {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(pcm, 0, pcm.size)
                repeat(3) {
                    track.stop()
                    track.reloadStaticData()
                    track.play()
                    delay(beatIntervalMs)
                }
            } catch (_: CancellationException) { }
            finally {
                try { track.stop() } catch (_: IllegalStateException) {}
                track.release()
            }
        }
    }

    private fun computeBpmAtTime(timeline: List<TimelineSegment>, elapsedMs: Long): Int {
        var accumulatedMs = 0L
        for (seg in timeline) {
            val segDurationMs = seg.durationSec * 1000L
            if (elapsedMs < accumulatedMs + segDurationMs) {
                // We're in this segment
                val progress = ((elapsedMs - accumulatedMs).toFloat() / segDurationMs).coerceIn(0f, 1f)
                val easedProgress = applyEasing(progress, seg.easing)
                return (seg.bpmStart + (seg.bpmEnd - seg.bpmStart) * easedProgress).toInt()
            }
            accumulatedMs += segDurationMs
        }
        // Past all segments — hold at final BPM
        return timeline.lastOrNull()?.bpmEnd ?: 65
    }

    private fun applyEasing(t: Float, easing: EasingCurve): Float = when (easing) {
        EasingCurve.LINEAR -> t
        EasingCurve.EASE_IN -> t * t
        EasingCurve.EASE_OUT -> 1f - (1f - t) * (1f - t)
        EasingCurve.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f
    }

    /** Boost PCM samples by 3x for louder preview output. */
    private fun boostSamples(pcm: ShortArray): ShortArray {
        return ShortArray(pcm.size) { i ->
            (pcm[i].toInt() * 3).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

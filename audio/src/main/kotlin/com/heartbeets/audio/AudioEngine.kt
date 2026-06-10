package com.heartbeets.audio

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Top-level API for heartbeat audio playback.
 *
 * Modes:
 * - **Mirror**: cadence follows the user's live BPM.
 * - **Profile**: cadence follows a [HeartbeatProfile] curve, independent of live BPM.
 *
 * The engine is decoupled from the sound: [setSoundPack] controls *what* plays,
 * while the mode controls *when* it plays.
 */
class AudioEngine(private val context: Context) {

    private val scheduler = CadenceScheduler()
    private val _mode = MutableStateFlow(PlaybackMode.STOPPED)
    val mode: StateFlow<PlaybackMode> = _mode.asStateFlow()

    private val _currentBpm = MutableStateFlow(0)
    /** The cadence currently being played (may differ from live BPM in profile mode). */
    val currentCadence: StateFlow<Int> = _currentBpm.asStateFlow()

    /** Last raw BPM received from the wearable (before offset). */
    private var lastRawBpm: Int = 0

    /** User-applied BPM offset (added to wearable reading). */
    private val _bpmOffset = MutableStateFlow(0)
    val bpmOffset: StateFlow<Int> = _bpmOffset.asStateFlow()

    /** Accumulated phase offset in milliseconds. */
    private val _phaseOffsetMs = MutableStateFlow(0)
    val phaseOffsetMs: StateFlow<Int> = _phaseOffsetMs.asStateFlow()

    private var mirrorJob: Job? = null
    private var profileJob: Job? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            android.util.Log.w("AudioEngine", "Coroutine error", throwable)
        }
    }
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private var activePack: SoundPack? = null
    private var profileInterpolator: ProfileInterpolator? = null

    // --- Adjustments ---

    /**
     * Shift the phase of the heartbeat by [deltaMs]. Positive = delay beat,
     * negative = advance beat. Each call accumulates.
     */
    fun adjustPhase(deltaMs: Int) {
        scheduler.adjustPhase(deltaMs)
        _phaseOffsetMs.value = scheduler.getPhaseOffsetMs()
    }

    /**
     * Adjust the BPM offset applied on top of the wearable reading.
     * Positive = faster, negative = slower.
     * Immediately updates the scheduler so the change is heard on the next beat.
     */
    fun adjustBpmOffset(delta: Int) {
        _bpmOffset.value = (_bpmOffset.value + delta).coerceIn(-30, 30)
        applyBpmImmediately()
    }

    /** Reset both phase and BPM offsets to zero. */
    fun resetAdjustments() {
        scheduler.resetPhase()
        _phaseOffsetMs.value = 0
        _bpmOffset.value = 0
        applyBpmImmediately()
    }

    private fun applyBpmImmediately() {
        if (lastRawBpm > 0 && _mode.value == PlaybackMode.MIRROR) {
            val adjusted = (lastRawBpm + _bpmOffset.value).coerceIn(30, 220)
            scheduler.updateBpm(adjusted)
            _currentBpm.value = adjusted
        }
    }

    /**
     * Load a sound pack. The PCM sample is decoded and held in memory.
     * Takes effect on the next beat if playback is active.
     */
    fun setSoundPack(pack: SoundPack) {
        activePack = pack
        val pcm = PcmLoader.load(context, pack.sampleRes)
        scheduler.setSample(pcm)
    }

    /**
     * Play the heartbeat sound once (for preview / testing).
     * If not already playing, starts and stops the scheduler for one beat.
     */
    fun playBeatOnce() {
        if (activePack == null) {
            setSoundPack(SoundPackRegistry.getDefault())
        }
        if (!scheduler.isPlaying) {
            scheduler.updateBpm(72) // default preview tempo
            scheduler.start()
            scope.launch {
                delay(900) // let one beat play (~830ms at 72 BPM)
                if (_mode.value == PlaybackMode.STOPPED) {
                    scheduler.stop()
                }
            }
        }
    }

    /**
     * Start mirror mode: cadence follows the provided BPM flow 1:1,
     * plus any user-applied BPM offset.
     * Playback begins only when the first real BPM value arrives.
     */
    fun startMirrorMode(bpmFlow: Flow<Int>) {
        stopPlayback()
        if (activePack == null) {
            setSoundPack(SoundPackRegistry.getDefault())
        }
        _mode.value = PlaybackMode.MIRROR

        mirrorJob = scope.launch {
            try {
                var started = false
                bpmFlow.collect { bpm ->
                    if (bpm > 0) {
                        lastRawBpm = bpm
                        val adjusted = (bpm + _bpmOffset.value).coerceIn(30, 220)
                        scheduler.updateBpm(adjusted)
                        _currentBpm.value = adjusted
                        if (!started) {
                            scheduler.start()
                            started = true
                        }
                    }
                }
            } catch (_: CancellationException) { /* normal shutdown */ }
        }
    }

    /**
     * Start profile mode: cadence follows the profile's curve.
     * In RELATIVE mode, anchored at [currentBpm] as the starting point.
     * In ABSOLUTE mode, starts at the profile's startBpm regardless of live HR.
     */
    fun startProfile(profile: HeartbeatProfile, currentBpm: Int) {
        stopPlayback()
        if (activePack == null) {
            setSoundPack(SoundPackRegistry.getDefault())
        }

        val anchorBpm = when (profile.anchorMode) {
            ProfileAnchorMode.RELATIVE -> currentBpm
            ProfileAnchorMode.ABSOLUTE -> profile.startBpm ?: currentBpm
        }

        val interpolator = ProfileInterpolator(
            profile = profile,
            anchorBpm = anchorBpm
        )
        profileInterpolator = interpolator
        _mode.value = PlaybackMode.PROFILE
        scheduler.updateBpm(currentBpm)
        _currentBpm.value = currentBpm
        scheduler.start()

        profileJob = scope.launch {
            while (true) {
                val cadence = interpolator.cadenceAt(System.currentTimeMillis())
                scheduler.updateBpm(cadence)
                _currentBpm.value = cadence

                if (interpolator.isFinished(System.currentTimeMillis())) {
                    // Hold at final cadence — stay in PROFILE mode but stop updating
                    break
                }
                delay(200) // update cadence ~5 times per second
            }
        }
    }

    /**
     * Stop all playback and return to STOPPED mode.
     */
    fun stopPlayback() {
        mirrorJob?.cancel()
        mirrorJob = null
        profileJob?.cancel()
        profileJob = null
        profileInterpolator = null
        scheduler.stop()
        _mode.value = PlaybackMode.STOPPED
        _currentBpm.value = 0
    }

    /**
     * Release all resources. Call when the engine is no longer needed.
     */
    fun release() {
        stopPlayback()
        scope.cancel()
    }
}

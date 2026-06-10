package com.heartbeets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.heartbeets.audio.AudioEngine
import com.heartbeets.audio.HeartbeatProfile
import com.heartbeets.audio.PlaybackMode
import com.heartbeets.audio.ProfileAnchorMode
import com.heartbeets.audio.ProfileRepository
import com.heartbeets.audio.SoundPack
import com.heartbeets.audio.SoundPackRegistry
import com.heartbeets.audio.SoundPackRepository
import com.heartbeets.audio.SynthParams
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.DeviceRegistry
import com.heartbeets.core.HrDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.heartbeets.core.ConnectionState as CS

class LiveHrViewModel(
    application: Application,
    private val address: String,
    private val factoryId: String,
) : AndroidViewModel(application) {

    private val factory = DeviceRegistry.findById(factoryId)
    private val driver: HrDriver? = factory?.create(address, null)

    val displayName: String = factory?.displayName ?: "Unknown Device"

    val connectionState: StateFlow<ConnectionState> =
        driver?.state ?: MutableStateFlow(ConnectionState.Error)

    val battery: StateFlow<Int?> =
        driver?.battery ?: MutableStateFlow(null)

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    /** Millisecond timestamp of the last received HR sample. Updates on every sample even if BPM is unchanged. */
    private val _lastUpdatedMs = MutableStateFlow(0L)
    val lastUpdatedMs: StateFlow<Long> = _lastUpdatedMs.asStateFlow()

    // --- Audio ---
    val audioEngine = AudioEngine(application)
    val profileRepository = ProfileRepository(application)
    private val soundPackRepository = SoundPackRepository(application)
    val playbackMode: StateFlow<PlaybackMode> = audioEngine.mode
    val playbackCadence: StateFlow<Int> = audioEngine.currentCadence
    val bpmOffset: StateFlow<Int> = audioEngine.bpmOffset
    val phaseOffsetMs: StateFlow<Int> = audioEngine.phaseOffsetMs

    private val _profiles = MutableStateFlow<List<HeartbeatProfile>>(emptyList())
    val profiles: StateFlow<List<HeartbeatProfile>> = _profiles.asStateFlow()

    init {
        driver?.let { d ->
            viewModelScope.launch {
                try {
                    d.samples.collect { sample ->
                        _bpm.value = sample.bpm
                        _lastUpdatedMs.value = sample.timestamp
                    }
                } catch (_: CancellationException) { /* normal on exit */ }
            }
            viewModelScope.launch {
                try {
                    d.state.collect { state ->
                        if (state == CS.Disconnected || state == CS.Error) {
                            _bpm.value = null
                            _lastUpdatedMs.value = 0L
                        }
                    }
                } catch (_: CancellationException) { /* normal on exit */ }
            }
            viewModelScope.launch {
                try {
                    d.state.collect { state ->
                        if (state == CS.Connected) {
                            _bpm.value = null
                            delay(5_000)
                        }
                    }
                } catch (_: CancellationException) { /* normal on exit */ }
            }
        }
        connect()
        loadProfiles()
        viewModelScope.launch { soundPackRepository.loadAndRegister() }
    }

    fun connect() {
        driver ?: return
        viewModelScope.launch {
            runCatching { driver.connect() }
        }
    }

    fun disconnect() {
        driver ?: return
        viewModelScope.launch {
            runCatching { driver.disconnect() }
        }
    }

    // --- Audio controls ---

    fun startMirrorMode() {
        val bpmFlow = _bpm.filterNotNull().map { it }
        audioEngine.startMirrorMode(bpmFlow)
    }

    fun startProfile(profile: HeartbeatProfile, anchorMode: ProfileAnchorMode) {
        val adjusted = profile.copy(anchorMode = anchorMode)
        val currentBpm = _bpm.value ?: 72
        audioEngine.startProfile(adjusted, currentBpm)
    }

    fun stopAudio() {
        audioEngine.stopPlayback()
    }

    private val _activeSoundPackId = MutableStateFlow(SoundPackRegistry.getDefault().id)
    val activeSoundPackId: StateFlow<String> = _activeSoundPackId.asStateFlow()

    fun setSoundPack(pack: SoundPack) {
        audioEngine.setSoundPack(pack)
        _activeSoundPackId.value = pack.id
    }

    fun previewPack(pack: SoundPack) {
        audioEngine.previewPack(pack)
    }

    fun previewBeat() {
        audioEngine.playBeatOnce()
    }

    // --- Adjustments ---

    fun adjustPhase(deltaMs: Int) {
        audioEngine.adjustPhase(deltaMs)
    }

    fun adjustBpmOffset(delta: Int) {
        audioEngine.adjustBpmOffset(delta)
    }

    fun resetAdjustments() {
        audioEngine.resetAdjustments()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            _profiles.value = profileRepository.getAll()
        }
    }

    override fun onCleared() {
        audioEngine.release()
        // Don't use viewModelScope here — it's already cancelled.
        // disconnect() synchronously since the driver handles it.
        driver?.let {
            runCatching { kotlinx.coroutines.runBlocking { it.disconnect() } }
        }
    }
}

class LiveHrViewModelFactory(
    private val application: Application,
    private val address: String,
    private val factoryId: String,
) : ViewModelProvider.AndroidViewModelFactory(application) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LiveHrViewModel(application, address, factoryId) as T
}


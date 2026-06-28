package com.heartbeets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.heartbeets.audio.AudioEngine
import com.heartbeets.audio.BinauralPreset
import com.heartbeets.audio.EasingCurve
import com.heartbeets.audio.Heartbeat
import com.heartbeets.audio.HeartbeatRepository
import com.heartbeets.audio.NoiseType
import com.heartbeets.audio.SolfeggioFrequency
import com.heartbeets.audio.SynthParams
import com.heartbeets.audio.TimelineSegment
import com.heartbeets.audio.VoiceRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HeartbeatCreatorViewModel(
    application: Application,
    private val editId: String?,
) : AndroidViewModel(application) {

    val audioEngine = AudioEngine(application)
    val voiceRecorder = VoiceRecorder(application)
    private val repository = HeartbeatRepository(application)

    // --- Name ---
    private val _name = MutableStateFlow("My Heartbeat")
    val name: StateFlow<String> = _name.asStateFlow()

    // --- Synth params ---
    private val _synthParams = MutableStateFlow(SynthParams.CLASSIC)
    val synthParams: StateFlow<SynthParams> = _synthParams.asStateFlow()

    // --- Timeline ---
    private val _timeline = MutableStateFlow(listOf(TimelineSegment(65, 65, 300)))
    val timeline: StateFlow<List<TimelineSegment>> = _timeline.asStateFlow()

    // --- Noise ---
    private val _noiseType = MutableStateFlow(NoiseType.NONE)
    val noiseType: StateFlow<NoiseType> = _noiseType.asStateFlow()
    private val _noiseVolume = MutableStateFlow(0.1f)
    val noiseVolume: StateFlow<Float> = _noiseVolume.asStateFlow()

    // --- Binaural ---
    private val _binauralPreset = MutableStateFlow(BinauralPreset.NONE)
    val binauralPreset: StateFlow<BinauralPreset> = _binauralPreset.asStateFlow()
    private val _binauralCarrierHz = MutableStateFlow(200f)
    val binauralCarrierHz: StateFlow<Float> = _binauralCarrierHz.asStateFlow()
    private val _binauralBeatHz = MutableStateFlow(10f)
    val binauralBeatHz: StateFlow<Float> = _binauralBeatHz.asStateFlow()
    private val _binauralVolume = MutableStateFlow(0.3f)
    val binauralVolume: StateFlow<Float> = _binauralVolume.asStateFlow()

    // --- Solfeggio ---
    private val _solfeggioFrequency = MutableStateFlow(SolfeggioFrequency.NONE)
    val solfeggioFrequency: StateFlow<SolfeggioFrequency> = _solfeggioFrequency.asStateFlow()
    private val _solfeggioVolume = MutableStateFlow(0.3f)
    val solfeggioVolume: StateFlow<Float> = _solfeggioVolume.asStateFlow()

    // --- Voice ---
    private val _voiceEnabled = MutableStateFlow(false)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()
    private val _voiceRecordings = MutableStateFlow<List<String>>(emptyList())
    val voiceRecordings: StateFlow<List<String>> = _voiceRecordings.asStateFlow()
    private val _voiceIntervalSec = MutableStateFlow(30)
    val voiceIntervalSec: StateFlow<Int> = _voiceIntervalSec.asStateFlow()
    private val _voiceVolume = MutableStateFlow(0.8f)
    val voiceVolume: StateFlow<Float> = _voiceVolume.asStateFlow()
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                repository.loadAll().find { it.id == editId }?.let { h ->
                    _name.value = h.displayName
                    _synthParams.value = h.synthParams
                    _timeline.value = h.timeline
                    _noiseType.value = h.noiseType
                    _noiseVolume.value = h.noiseVolume
                    _binauralPreset.value = h.binauralPreset
                    _binauralCarrierHz.value = h.binauralCarrierHz
                    _binauralBeatHz.value = h.binauralBeatHz
                    _binauralVolume.value = h.binauralVolume
                    _solfeggioFrequency.value = h.solfeggioFrequency
                    _solfeggioVolume.value = h.solfeggioVolume
                    _voiceEnabled.value = h.voiceEnabled
                    _voiceRecordings.value = h.voiceRecordings
                    _voiceIntervalSec.value = h.voiceIntervalSec
                    _voiceVolume.value = h.voiceVolume
                }
            }
        }
    }

    // --- Updates ---
    fun updateName(name: String) { _name.value = name }
    fun updateSynthParams(params: SynthParams) { _synthParams.value = params }

    fun addSegment(segment: TimelineSegment) {
        _timeline.value = _timeline.value + segment
    }

    fun removeSegment(index: Int) {
        val t = _timeline.value.toMutableList()
        if (index in t.indices && t.size > 1) {
            t.removeAt(index)
            _timeline.value = t
        }
    }

    fun updateSegment(index: Int, segment: TimelineSegment) {
        val t = _timeline.value.toMutableList()
        if (index in t.indices) {
            t[index] = segment
            _timeline.value = t
        }
    }

    fun updateNoiseType(type: NoiseType) { _noiseType.value = type }
    fun updateNoiseVolume(v: Float) { _noiseVolume.value = v }

    fun updateBinauralPreset(preset: BinauralPreset) {
        _binauralPreset.value = preset
        if (preset != BinauralPreset.CUSTOM && preset != BinauralPreset.NONE) {
            _binauralCarrierHz.value = preset.carrierHz
            _binauralBeatHz.value = preset.beatHz
        }
    }
    fun updateBinauralCarrierHz(hz: Float) { _binauralCarrierHz.value = hz; _binauralPreset.value = BinauralPreset.CUSTOM }
    fun updateBinauralBeatHz(hz: Float) { _binauralBeatHz.value = hz; _binauralPreset.value = BinauralPreset.CUSTOM }
    fun updateBinauralVolume(v: Float) { _binauralVolume.value = v }

    fun updateSolfeggioFrequency(f: SolfeggioFrequency) { _solfeggioFrequency.value = f }
    fun updateSolfeggioVolume(v: Float) { _solfeggioVolume.value = v }

    fun updateVoiceEnabled(enabled: Boolean) { _voiceEnabled.value = enabled }
    fun updateVoiceIntervalSec(sec: Int) { _voiceIntervalSec.value = sec }
    fun updateVoiceVolume(v: Float) { _voiceVolume.value = v }

    fun startRecording() {
        val packId = editId ?: "new_heartbeat"
        val index = _voiceRecordings.value.size
        val path = voiceRecorder.start(packId, index)
        if (path != null) _isRecording.value = true
    }

    fun stopRecording() {
        val path = voiceRecorder.stop()
        _isRecording.value = false
        if (path != null) _voiceRecordings.value = _voiceRecordings.value + path
    }

    fun deleteRecording(index: Int) {
        val list = _voiceRecordings.value.toMutableList()
        if (index in list.indices) {
            voiceRecorder.deleteRecording(list[index])
            list.removeAt(index)
            _voiceRecordings.value = list
        }
    }

    fun previewRecording(index: Int) {
        val list = _voiceRecordings.value
        if (index in list.indices) audioEngine.voicePlayer.playOne(list[index])
    }

    fun previewBeat() {
        audioEngine.previewBeat(_synthParams.value)
    }

    fun save(onDone: (id: String) -> Unit) {
        viewModelScope.launch {
            val id = editId ?: repository.newId()
            val heartbeat = Heartbeat(
                id = id,
                displayName = _name.value.ifBlank { "My Heartbeat" },
                synthParams = _synthParams.value,
                timeline = _timeline.value,
                noiseType = _noiseType.value,
                noiseVolume = _noiseVolume.value,
                binauralPreset = _binauralPreset.value,
                binauralCarrierHz = _binauralCarrierHz.value,
                binauralBeatHz = _binauralBeatHz.value,
                binauralVolume = _binauralVolume.value,
                solfeggioFrequency = _solfeggioFrequency.value,
                solfeggioVolume = _solfeggioVolume.value,
                voiceEnabled = _voiceEnabled.value,
                voiceRecordings = _voiceRecordings.value,
                voiceIntervalSec = _voiceIntervalSec.value,
                voiceVolume = _voiceVolume.value,
            )
            repository.save(heartbeat)
            onDone(id)
        }
    }

    override fun onCleared() {
        audioEngine.release()
    }
}

class HeartbeatCreatorViewModelFactory(
    private val application: Application,
    private val editId: String? = null,
) : ViewModelProvider.AndroidViewModelFactory(application) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HeartbeatCreatorViewModel(application, editId) as T
}

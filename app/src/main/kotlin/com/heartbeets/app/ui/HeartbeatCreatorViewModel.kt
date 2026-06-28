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
import kotlinx.coroutines.Job
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
    private val _timeline = MutableStateFlow(listOf(TimelineSegment(66, 55, 213)))
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

    fun updateVoiceVolume(v: Float) { _voiceVolume.value = v }

    /** Apply a named preset and reset warmth/intensity to defaults. */
    fun applySoundPreset(preset: SynthParams) {
        _synthParams.value = preset
    }

    // Recording countdown
    private val _recordingSecondsLeft = MutableStateFlow(0)
    val recordingSecondsLeft: StateFlow<Int> = _recordingSecondsLeft.asStateFlow()
    private var recordingTimerJob: Job? = null

    fun startRecording() {
        // Only one recording allowed — delete existing first
        val existing = _voiceRecordings.value
        existing.forEach { voiceRecorder.deleteRecording(it) }
        _voiceRecordings.value = emptyList()

        val packId = editId ?: "new_heartbeat"
        val path = voiceRecorder.start(packId, 0)
        if (path != null) {
            _isRecording.value = true
            _recordingStartTime = System.currentTimeMillis()
            val maxSec = 213 // always 3m33s max
            _recordingSecondsLeft.value = maxSec
            recordingTimerJob = viewModelScope.launch {
                for (remaining in maxSec downTo 1) {
                    _recordingSecondsLeft.value = remaining
                    kotlinx.coroutines.delay(1000L)
                }
                // Auto-stop when time runs out
                stopRecording()
            }
        }
    }

    private var _recordingStartTime = 0L

    fun stopRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        val elapsedSec = ((System.currentTimeMillis() - _recordingStartTime) / 1000).toInt().coerceIn(11, 213)
        _recordingSecondsLeft.value = 0
        val path = voiceRecorder.stop()
        _isRecording.value = false
        if (path != null) {
            _voiceRecordings.value = listOf(path)
            // Set timeline duration to match actual recording length
            val seg = _timeline.value.first()
            _timeline.value = listOf(seg.copy(durationSec = elapsedSec))
        }
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

    /** Preview a few beats at a specific BPM. */
    fun previewAtBpm(bpm: Int) {
        audioEngine.previewBeatsAtBpm(_synthParams.value, bpm)
    }

    private val _previewingFull = MutableStateFlow(false)
    val previewingFull: StateFlow<Boolean> = _previewingFull.asStateFlow()

    private val _previewingBg = MutableStateFlow(false)
    val previewingBg: StateFlow<Boolean> = _previewingBg.asStateFlow()

    private var bgPreviewJob: Job? = null

    /** Preview only the background layers (noise, binaural, solfeggio) — auto-stops after 5s. */
    fun previewBackground() {
        stopBackgroundPreview()
        stopFullPreview()
        val h = buildCurrentHeartbeat().copy(
            synthParams = _synthParams.value.copy(masterGain = 0f),
            voiceEnabled = false,
        )
        audioEngine.setHeartbeat(h)
        audioEngine.play()
        _previewingBg.value = true
        bgPreviewJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000L)
            stopBackgroundPreview()
        }
    }

    fun stopBackgroundPreview() {
        bgPreviewJob?.cancel()
        bgPreviewJob = null
        if (_previewingBg.value) {
            audioEngine.stop()
            _previewingBg.value = false
        }
    }

    /** Preview only the voice message. */
    fun previewVoice() {
        val list = _voiceRecordings.value
        if (list.isNotEmpty()) audioEngine.voicePlayer.playOne(list.first())
    }

    private var fullPreviewWatchJob: Job? = null

    /** Preview the full heartbeat with all layers. Auto-stops when timeline ends. */
    fun previewFull() {
        if (_previewingFull.value) { stopFullPreview(); return }
        stopBackgroundPreview()
        val h = buildCurrentHeartbeat()
        audioEngine.setHeartbeat(h)
        audioEngine.play()
        _previewingFull.value = true
        // Watch for auto-stop
        fullPreviewWatchJob = viewModelScope.launch {
            val durationMs = h.totalDurationSec * 1000L + 500L
            kotlinx.coroutines.delay(durationMs)
            audioEngine.stop()
            _previewingFull.value = false
        }
    }

    fun stopFullPreview() {
        fullPreviewWatchJob?.cancel()
        fullPreviewWatchJob = null
        if (_previewingFull.value) {
            audioEngine.stop()
            _previewingFull.value = false
        }
    }

    /** Returns error message if heartbeat is not valid, null if OK. */
    fun validate(): String? {
        if (_voiceRecordings.value.isEmpty()) return "Please record a voice message first."
        return null
    }

    private fun buildCurrentHeartbeat(): Heartbeat {
        val id = editId ?: "preview"
        return Heartbeat(
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
            voiceEnabled = true,
            voiceRecordings = _voiceRecordings.value,
            voiceIntervalSec = _voiceIntervalSec.value,
            voiceVolume = _voiceVolume.value,
        )
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
                voiceEnabled = true,
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

package com.heartbeets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.heartbeets.audio.AffirmationMode
import com.heartbeets.audio.AffirmationSet
import com.heartbeets.audio.AudioEngine
import com.heartbeets.audio.BinauralPreset
import com.heartbeets.audio.NoiseType
import com.heartbeets.audio.SolfeggioFrequency
import com.heartbeets.audio.SoundPack
import com.heartbeets.audio.SoundPackRegistry
import com.heartbeets.audio.SoundPackRepository
import com.heartbeets.audio.SynthParams
import com.heartbeets.audio.VoiceRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SoundDesignerViewModel(
    application: Application,
    initialParams: SynthParams?,
    private val editPackId: String?,
) : AndroidViewModel(application) {

    val audioEngine = AudioEngine(application)
    private val repository = SoundPackRepository(application)
    val voiceRecorder = VoiceRecorder(application)

    private val _params = MutableStateFlow(initialParams ?: SynthParams.CLASSIC)
    val params: StateFlow<SynthParams> = _params.asStateFlow()

    private val _name = MutableStateFlow(if (editPackId != null) "" else "My Sound")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    // Background noise
    private val _noiseType = MutableStateFlow(NoiseType.NONE)
    val noiseType: StateFlow<NoiseType> = _noiseType.asStateFlow()

    private val _noiseVolume = MutableStateFlow(0.1f)
    val noiseVolume: StateFlow<Float> = _noiseVolume.asStateFlow()

    // Binaural beats
    private val _binauralPreset = MutableStateFlow(BinauralPreset.NONE)
    val binauralPreset: StateFlow<BinauralPreset> = _binauralPreset.asStateFlow()

    private val _binauralCarrierHz = MutableStateFlow(200f)
    val binauralCarrierHz: StateFlow<Float> = _binauralCarrierHz.asStateFlow()

    private val _binauralBeatHz = MutableStateFlow(10f)
    val binauralBeatHz: StateFlow<Float> = _binauralBeatHz.asStateFlow()

    private val _binauralVolume = MutableStateFlow(0.3f)
    val binauralVolume: StateFlow<Float> = _binauralVolume.asStateFlow()

    // Solfeggio tone
    private val _solfeggioFrequency = MutableStateFlow(SolfeggioFrequency.NONE)
    val solfeggioFrequency: StateFlow<SolfeggioFrequency> = _solfeggioFrequency.asStateFlow()

    private val _solfeggioVolume = MutableStateFlow(0.3f)
    val solfeggioVolume: StateFlow<Float> = _solfeggioVolume.asStateFlow()

    // Affirmations
    private val _affirmationMode = MutableStateFlow(AffirmationMode.NONE)
    val affirmationMode: StateFlow<AffirmationMode> = _affirmationMode.asStateFlow()

    private val _affirmationSet = MutableStateFlow(AffirmationSet.NONE)
    val affirmationSet: StateFlow<AffirmationSet> = _affirmationSet.asStateFlow()

    private val _affirmationCustomTexts = MutableStateFlow<List<String>>(emptyList())
    val affirmationCustomTexts: StateFlow<List<String>> = _affirmationCustomTexts.asStateFlow()

    private val _affirmationIntervalSec = MutableStateFlow(30)
    val affirmationIntervalSec: StateFlow<Int> = _affirmationIntervalSec.asStateFlow()

    private val _affirmationVolume = MutableStateFlow(0.8f)
    val affirmationVolume: StateFlow<Float> = _affirmationVolume.asStateFlow()

    private val _affirmationSpeechRate = MutableStateFlow(0.9f)
    val affirmationSpeechRate: StateFlow<Float> = _affirmationSpeechRate.asStateFlow()

    private val _affirmationPitch = MutableStateFlow(1.0f)
    val affirmationPitch: StateFlow<Float> = _affirmationPitch.asStateFlow()

    private val _affirmationVoiceName = MutableStateFlow<String?>(null)
    val affirmationVoiceName: StateFlow<String?> = _affirmationVoiceName.asStateFlow()

    private val _affirmationRecordings = MutableStateFlow<List<String>>(emptyList())
    val affirmationRecordings: StateFlow<List<String>> = _affirmationRecordings.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Available TTS voices as (name, displayLabel) pairs. */
    private val _availableVoices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val availableVoices: StateFlow<List<Pair<String, String>>> = _availableVoices.asStateFlow()

    init {
        // If editing an existing pack, load its name
        editPackId?.let { id ->
            SoundPackRegistry.getById(id)?.let { pack ->
                _name.value = pack.displayName
                _description.value = pack.description
                pack.synthParams?.let { _params.value = it }
                _noiseType.value = pack.noiseType
                _noiseVolume.value = pack.noiseVolume
                _binauralPreset.value = pack.binauralPreset
                _binauralCarrierHz.value = pack.binauralCarrierHz
                _binauralBeatHz.value = pack.binauralBeatHz
                _binauralVolume.value = pack.binauralVolume
                _solfeggioFrequency.value = pack.solfeggioFrequency
                _solfeggioVolume.value = pack.solfeggioVolume
                _affirmationMode.value = pack.affirmationMode
                _affirmationSet.value = pack.affirmationSet
                _affirmationCustomTexts.value = pack.affirmationCustomTexts
                _affirmationRecordings.value = pack.affirmationRecordings
                _affirmationIntervalSec.value = pack.affirmationIntervalSec
                _affirmationVolume.value = pack.affirmationVolume
                _affirmationSpeechRate.value = pack.affirmationSpeechRate
                _affirmationPitch.value = pack.affirmationPitch
                _affirmationVoiceName.value = pack.affirmationVoiceName
            }
        }
        // Start preview playback
        audioEngine.setSynthParams(_params.value)
        // Load voices once TTS initializes
        loadVoices()
    }

    private fun loadVoices() {
        viewModelScope.launch {
            // TTS may take a moment to init; retry a few times
            repeat(10) {
                val voices = audioEngine.getAffirmationEngine().getAvailableVoices()
                if (voices.isNotEmpty()) {
                    _availableVoices.value = voices.mapIndexed { index, voice ->
                        val country = voice.locale.displayCountry.ifBlank { voice.locale.country }
                        val label = "Voice ${index + 1} ($country)"
                        voice.name to label
                    }
                    return@launch
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun updateParams(newParams: SynthParams) {
        _params.value = newParams
        audioEngine.setSynthParams(newParams)
    }

    fun updateName(newName: String) {
        _name.value = newName
    }

    fun updateDescription(newDescription: String) {
        _description.value = newDescription
    }

    fun updateNoiseType(type: NoiseType) {
        _noiseType.value = type
    }

    fun updateNoiseVolume(volume: Float) {
        _noiseVolume.value = volume
    }

    fun updateBinauralPreset(preset: BinauralPreset) {
        _binauralPreset.value = preset
        if (preset != BinauralPreset.CUSTOM && preset != BinauralPreset.NONE) {
            _binauralCarrierHz.value = preset.carrierHz
            _binauralBeatHz.value = preset.beatHz
        }
    }

    fun updateBinauralCarrierHz(hz: Float) {
        _binauralCarrierHz.value = hz
        _binauralPreset.value = BinauralPreset.CUSTOM
    }

    fun updateBinauralBeatHz(hz: Float) {
        _binauralBeatHz.value = hz
        _binauralPreset.value = BinauralPreset.CUSTOM
    }

    fun updateBinauralVolume(volume: Float) {
        _binauralVolume.value = volume
    }

    fun updateSolfeggioFrequency(freq: SolfeggioFrequency) {
        _solfeggioFrequency.value = freq
    }

    fun updateSolfeggioVolume(volume: Float) {
        _solfeggioVolume.value = volume
    }

    fun updateAffirmationSet(set: AffirmationSet) {
        _affirmationSet.value = set
    }

    fun updateAffirmationCustomTexts(texts: List<String>) {
        _affirmationCustomTexts.value = texts
    }

    fun updateAffirmationIntervalSec(sec: Int) {
        _affirmationIntervalSec.value = sec
    }

    fun updateAffirmationVolume(volume: Float) {
        _affirmationVolume.value = volume
    }

    fun updateAffirmationSpeechRate(rate: Float) {
        _affirmationSpeechRate.value = rate
    }

    fun updateAffirmationPitch(pitch: Float) {
        _affirmationPitch.value = pitch
    }

    fun updateAffirmationVoiceName(name: String?) {
        _affirmationVoiceName.value = name
    }

    fun updateAffirmationMode(mode: AffirmationMode) {
        _affirmationMode.value = mode
    }

    /** Start recording a new voice message at the next available index. */
    fun startRecording() {
        val packId = editPackId ?: "new_pack"
        val index = _affirmationRecordings.value.size
        val path = voiceRecorder.start(packId, index)
        if (path != null) {
            _isRecording.value = true
        }
    }

    /** Stop the current recording and add it to the list. */
    fun stopRecording() {
        val path = voiceRecorder.stop()
        _isRecording.value = false
        if (path != null) {
            _affirmationRecordings.value = _affirmationRecordings.value + path
        }
    }

    /** Delete a recording at the given index. */
    fun deleteRecording(index: Int) {
        val recordings = _affirmationRecordings.value.toMutableList()
        if (index in recordings.indices) {
            voiceRecorder.deleteRecording(recordings[index])
            recordings.removeAt(index)
            _affirmationRecordings.value = recordings
        }
    }

    /** Preview a recorded message. */
    fun previewRecording(index: Int) {
        val recordings = _affirmationRecordings.value
        if (index in recordings.indices) {
            audioEngine.getAffirmationEngine().playRecording(recordings[index])
        }
    }

    fun previewAffirmation() {
        val texts = if (_affirmationSet.value == AffirmationSet.CUSTOM) {
            _affirmationCustomTexts.value
        } else {
            _affirmationSet.value.affirmations
        }
        val sample = texts.firstOrNull() ?: return
        val engine = audioEngine.getAffirmationEngine()
        engine.configure(
            texts = texts,
            intervalSec = _affirmationIntervalSec.value,
            vol = _affirmationVolume.value,
            speechRate = _affirmationSpeechRate.value,
            pitch = _affirmationPitch.value,
            voiceName = _affirmationVoiceName.value,
        )
        engine.speakOne(sample)
    }

    fun preview() {
        audioEngine.previewSynthParams(_params.value)
    }

    fun save(onDone: (packId: String) -> Unit) {
        viewModelScope.launch {
            val id = editPackId ?: repository.newId()
            val pack = SoundPack(
                id = id,
                displayName = _name.value.ifBlank { "Custom" },
                description = _description.value.ifBlank { "User-created heartbeat sound." },
                synthParams = _params.value,
                isUserCreated = true,
                noiseType = _noiseType.value,
                noiseVolume = _noiseVolume.value,
                binauralPreset = _binauralPreset.value,
                binauralCarrierHz = _binauralCarrierHz.value,
                binauralBeatHz = _binauralBeatHz.value,
                binauralVolume = _binauralVolume.value,
                solfeggioFrequency = _solfeggioFrequency.value,
                solfeggioVolume = _solfeggioVolume.value,
                affirmationMode = _affirmationMode.value,
                affirmationSet = _affirmationSet.value,
                affirmationCustomTexts = _affirmationCustomTexts.value,
                affirmationRecordings = _affirmationRecordings.value,
                affirmationIntervalSec = _affirmationIntervalSec.value,
                affirmationVolume = _affirmationVolume.value,
                affirmationSpeechRate = _affirmationSpeechRate.value,
                affirmationPitch = _affirmationPitch.value,
                affirmationVoiceName = _affirmationVoiceName.value,
            )
            repository.save(pack)
            onDone(id)
        }
    }

    override fun onCleared() {
        audioEngine.release()
    }
}

class SoundDesignerViewModelFactory(
    private val application: Application,
    private val initialParams: SynthParams? = null,
    private val editPackId: String? = null,
) : ViewModelProvider.AndroidViewModelFactory(application) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SoundDesignerViewModel(application, initialParams, editPackId) as T
}

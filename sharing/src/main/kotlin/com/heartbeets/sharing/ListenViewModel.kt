package com.heartbeets.sharing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartbeets.audio.AffirmationSet
import com.heartbeets.audio.AudioEngine
import com.heartbeets.audio.BinauralPreset
import com.heartbeets.audio.NoiseType
import com.heartbeets.audio.SolfeggioFrequency
import com.heartbeets.audio.SoundPack
import com.heartbeets.audio.SoundPackRegistry
import com.heartbeets.audio.SoundPackRepository
import com.heartbeets.audio.SynthParams
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Receiver-side ViewModel: observes a friend's permanent code, receives BPM, plays audio.
 */
class ListenViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HeartbeatRepository(application)
    private val profileRepo = ProfileSyncRepository()
    private val soundPackRepo = SoundPackRepository(application)

    val audioEngine = AudioEngine(application)

    private val _friends = MutableStateFlow(repo.getFriends())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _status = MutableStateFlow(ListenStatus.IDLE)
    val status: StateFlow<ListenStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _friendName = MutableStateFlow<String?>(null)
    val friendName: StateFlow<String?> = _friendName.asStateFlow()

    /** The sound pack received from the friend (available to save). */
    private val _receivedPack = MutableStateFlow<SoundPack?>(null)
    val receivedPack: StateFlow<SoundPack?> = _receivedPack.asStateFlow()

    private var listenJob: Job? = null

    /**
     * Add a friend by code and name. Persists locally.
     */
    fun addFriend(code: String, name: String) {
        val normalized = code.trim().uppercase()
        if (normalized.length != 10) {
            _error.value = "Code must be 10 characters"
            return
        }
        repo.addFriend(normalized, name.trim())
        _friends.value = repo.getFriends()
    }

    /**
     * Remove a friend.
     */
    fun removeFriend(code: String) {
        repo.removeFriend(code)
        _friends.value = repo.getFriends()
    }

    /**
     * Start listening to a friend's heartbeat.
     */
    fun listenTo(friend: Friend) {
        listenJob?.cancel()
        _friendName.value = friend.name
        _status.value = ListenStatus.CONNECTING
        _bpm.value = null

        listenJob = viewModelScope.launch {
            try {
                repo.observe(friend.code).collect { data ->
                    if (data == null) {
                        _status.value = ListenStatus.OFFLINE
                        audioEngine.stopPlayback()
                        return@collect
                    }

                    when (data.status) {
                        HeartbeatLive.STATUS_OFFLINE -> {
                            _status.value = ListenStatus.OFFLINE
                            audioEngine.stopPlayback()
                        }
                        HeartbeatLive.STATUS_LIVE -> {
                            // First time connecting — load profile and start audio
                            if (_status.value == ListenStatus.CONNECTING || _status.value == ListenStatus.OFFLINE) {
                                loadProfileSound(data.profileId)
                                startAudio()
                            }
                            _status.value = ListenStatus.LISTENING

                            if (data.bpm > 0) {
                                _bpm.value = data.bpm
                            }

                            // Check staleness (15s = signal lost)
                            val age = System.currentTimeMillis() - data.updatedAt
                            if (age > 15_000) {
                                _status.value = ListenStatus.SIGNAL_LOST
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _status.value = ListenStatus.ERROR
                _error.value = e.message
            }
        }
    }

    private suspend fun loadProfileSound(profileId: String) {
        val profile = if (profileId.isNotBlank()) profileRepo.getProfile(profileId) else null
        val synthParams = profile?.synthParams?.toSynthParams() ?: SynthParams.CLASSIC
        val noiseType = try { NoiseType.valueOf(profile?.noiseType ?: "NONE") } catch (_: Exception) { NoiseType.NONE }
        val binauralPreset = try { BinauralPreset.valueOf(profile?.binauralPreset ?: "NONE") } catch (_: Exception) { BinauralPreset.NONE }
        val solfeggioFreq = try { SolfeggioFrequency.valueOf(profile?.solfeggioFrequency ?: "NONE") } catch (_: Exception) { SolfeggioFrequency.NONE }
        val affirmSet = try { AffirmationSet.valueOf(profile?.affirmationSet ?: "NONE") } catch (_: Exception) { AffirmationSet.NONE }
        val pack = SoundPack(
            id = "shared_${profileId.ifBlank { "default" }}",
            displayName = profile?.name ?: "Shared",
            description = "Received via sharing",
            synthParams = synthParams,
            noiseType = noiseType,
            noiseVolume = (profile?.noiseVolume ?: 0.1).toFloat(),
            binauralPreset = binauralPreset,
            binauralCarrierHz = (profile?.binauralCarrierHz ?: 200.0).toFloat(),
            binauralBeatHz = (profile?.binauralBeatHz ?: 10.0).toFloat(),
            binauralVolume = (profile?.binauralVolume ?: 0.3).toFloat(),
            solfeggioFrequency = solfeggioFreq,
            solfeggioVolume = (profile?.solfeggioVolume ?: 0.3).toFloat(),
            affirmationSet = affirmSet,
            affirmationCustomTexts = profile?.affirmationCustomTexts ?: emptyList(),
            affirmationIntervalSec = profile?.affirmationIntervalSec ?: 30,
            affirmationVolume = (profile?.affirmationVolume ?: 0.8).toFloat(),
            affirmationSpeechRate = (profile?.affirmationSpeechRate ?: 0.9).toFloat(),
            affirmationPitch = (profile?.affirmationPitch ?: 1.0).toFloat(),
        )
        _receivedPack.value = pack
        audioEngine.setSoundPack(pack)
    }

    /**
     * Save the received sound pack to the user's local collection.
     */
    fun saveReceivedPack() {
        val pack = _receivedPack.value ?: return
        // Check if already saved
        if (SoundPackRegistry.getById(pack.id) != null) {
            _error.value = "Sound pack already saved"
            return
        }
        viewModelScope.launch {
            soundPackRepo.save(pack)
            _error.value = "Sound pack \"${pack.displayName}\" saved!"
        }
    }

    private fun startAudio() {
        val bpmFlow = _bpm.filterNotNull().map { it }
        audioEngine.startMirrorMode(bpmFlow)
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        audioEngine.stopPlayback()
        _status.value = ListenStatus.IDLE
        _bpm.value = null
        _friendName.value = null
        _receivedPack.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}

enum class ListenStatus {
    IDLE,
    CONNECTING,
    LISTENING,
    SIGNAL_LOST,
    OFFLINE,
    ERROR,
}

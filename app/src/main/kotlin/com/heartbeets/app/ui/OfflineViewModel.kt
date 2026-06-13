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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfflineViewModel(application: Application) : AndroidViewModel(application) {

    val audioEngine = AudioEngine(application)
    val profileRepository = ProfileRepository(application)
    private val soundPackRepository = SoundPackRepository(application)

    val playbackMode: StateFlow<PlaybackMode> = audioEngine.mode
    val playbackCadence: StateFlow<Int> = audioEngine.currentCadence

    private val _profiles = MutableStateFlow<List<HeartbeatProfile>>(emptyList())
    val profiles: StateFlow<List<HeartbeatProfile>> = _profiles.asStateFlow()

    private val _activeSoundPackId = MutableStateFlow(SoundPackRegistry.getDefault().id)
    val activeSoundPackId: StateFlow<String> = _activeSoundPackId.asStateFlow()

    private val _activeProfileName = MutableStateFlow<String?>(null)
    val activeProfileName: StateFlow<String?> = _activeProfileName.asStateFlow()

    init {
        loadProfiles()
        viewModelScope.launch { soundPackRepository.loadAndRegister() }
    }

    fun startProfile(profile: HeartbeatProfile, startBpm: Int) {
        val adjusted = profile.copy(anchorMode = ProfileAnchorMode.ABSOLUTE, startBpm = startBpm)
        audioEngine.startProfile(adjusted, startBpm)
        _activeProfileName.value = profile.name
    }

    fun stopAudio() {
        audioEngine.stopPlayback()
        _activeProfileName.value = null
    }

    fun setSoundPack(pack: SoundPack) {
        audioEngine.setSoundPack(pack)
        _activeSoundPackId.value = pack.id
    }

    fun previewPack(pack: SoundPack) {
        audioEngine.previewPack(pack)
    }

    fun refreshProfiles() {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            _profiles.value = profileRepository.getAll()
        }
    }

    override fun onCleared() {
        audioEngine.release()
        super.onCleared()
    }
}

class OfflineViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        OfflineViewModel(application) as T
}

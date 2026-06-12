package com.heartbeets.sharing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages HeartCodes and live broadcasting status.
 */
class ShareViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HeartbeatRepository(application)
    private val profileRepo = ProfileSyncRepository()

    private val _heartCodes = MutableStateFlow(repo.getHeartCodes())
    val heartCodes: StateFlow<List<HeartCode>> = _heartCodes.asStateFlow()

    private val _isLive = MutableStateFlow(false)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pushJob: Job? = null

    // --- HeartCode management ---

    fun createCode(name: String) {
        repo.createHeartCode(name)
        _heartCodes.value = repo.getHeartCodes()
    }

    fun setCodeEnabled(code: String, enabled: Boolean) {
        repo.setHeartCodeEnabled(code, enabled)
        _heartCodes.value = repo.getHeartCodes()
    }

    fun deleteCode(code: String) {
        // Immediate local removal — UI updates instantly
        repo.deleteHeartCodeLocal(code)
        _heartCodes.value = repo.getHeartCodes()
        // Fire-and-forget RTDB cleanup
        viewModelScope.launch {
            try { repo.deleteHeartCodeRemote(code) } catch (_: Exception) { }
        }
    }

    // --- Live status ---

    /**
     * Go live: broadcasts to all enabled codes.
     */
    fun goLive(profile: SharedProfile) {
        if (_isLive.value) return
        if (repo.getEnabledCodes().isEmpty()) {
            _error.value = "No enabled codes. Create or enable a HeartCode first."
            return
        }
        // Set live immediately — don't wait for network
        _isLive.value = true
        viewModelScope.launch {
            try { repo.goLive(profile.id) } catch (_: Exception) { }
            try { profileRepo.uploadProfile(profile) } catch (_: Exception) { }
        }
    }

    /**
     * Push BPM to all enabled codes.
     */
    fun pushBpm(bpm: Int) {
        if (!_isLive.value) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            try {
                repo.pushBpm(bpm)
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    /**
     * Go offline on all enabled codes.
     */
    fun goOffline() {
        _isLive.value = false
        viewModelScope.launch {
            try { repo.goOffline() } catch (_: Exception) { }
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        if (_isLive.value) {
            viewModelScope.launch { runCatching { repo.goOffline() } }
        }
    }
}

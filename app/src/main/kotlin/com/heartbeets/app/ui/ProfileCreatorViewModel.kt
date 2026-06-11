package com.heartbeets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.heartbeets.audio.EasingCurve
import com.heartbeets.audio.HeartbeatProfile
import com.heartbeets.audio.ProfileRepository
import com.heartbeets.audio.ProfileStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StageUiState(
    val targetOffsetBpm: Int = 0,   // cumulative offset from start BPM
    val durationSec: Int = 30,
    val curve: EasingCurve = EasingCurve.LINEAR,
)

class ProfileCreatorViewModel(
    application: Application,
    private val editProfileId: String?,
) : AndroidViewModel(application) {

    private val repository = ProfileRepository(application)

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _stages = MutableStateFlow(listOf(StageUiState()))
    val stages: StateFlow<List<StageUiState>> = _stages.asStateFlow()

    init {
        editProfileId?.let { id ->
            viewModelScope.launch {
                repository.getAll().find { it.id == id }?.let { profile ->
                    _name.value = profile.name
                    _stages.value = profile.stages.map { s ->
                        StageUiState(s.targetOffsetBpm, s.durationSec, s.curve)
                    }
                }
            }
        }
    }

    fun updateName(value: String) { _name.value = value }

    fun updateStage(index: Int, stage: StageUiState) {
        _stages.value = _stages.value.toMutableList().also { it[index] = stage }
    }

    fun addStage() {
        val lastOffset = _stages.value.lastOrNull()?.targetOffsetBpm ?: 0
        _stages.value = _stages.value + StageUiState(targetOffsetBpm = lastOffset)
    }

    fun removeStage(index: Int) {
        if (_stages.value.size <= 1) return
        _stages.value = _stages.value.toMutableList().also { it.removeAt(index) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = editProfileId ?: repository.newId()
            val profile = HeartbeatProfile(
                id = id,
                name = _name.value.ifBlank { "My Profile" },
                description = "Custom profile.",
                stages = _stages.value.map { s ->
                    ProfileStage(s.targetOffsetBpm, s.durationSec, s.curve)
                },
                isPreset = false,
            )
            repository.save(profile)
            onDone()
        }
    }
}

class ProfileCreatorViewModelFactory(
    private val application: Application,
    private val editProfileId: String? = null,
) : ViewModelProvider.AndroidViewModelFactory(application) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProfileCreatorViewModel(application, editProfileId) as T
}

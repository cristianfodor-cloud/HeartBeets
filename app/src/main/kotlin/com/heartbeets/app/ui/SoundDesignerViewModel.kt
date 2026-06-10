package com.heartbeets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.heartbeets.audio.AudioEngine
import com.heartbeets.audio.SoundPack
import com.heartbeets.audio.SoundPackRegistry
import com.heartbeets.audio.SoundPackRepository
import com.heartbeets.audio.SynthParams
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

    private val _params = MutableStateFlow(initialParams ?: SynthParams.CLASSIC)
    val params: StateFlow<SynthParams> = _params.asStateFlow()

    private val _name = MutableStateFlow(if (editPackId != null) "" else "My Sound")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    init {
        // If editing an existing pack, load its name
        editPackId?.let { id ->
            SoundPackRegistry.getById(id)?.let { pack ->
                _name.value = pack.displayName
                _description.value = pack.description
                pack.synthParams?.let { _params.value = it }
            }
        }
        // Start preview playback
        audioEngine.setSynthParams(_params.value)
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

    fun preview() {
        audioEngine.previewSynthParams(_params.value)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = editPackId ?: repository.newId()
            val pack = SoundPack(
                id = id,
                displayName = _name.value.ifBlank { "Custom" },
                description = _description.value.ifBlank { "User-created heartbeat sound." },
                synthParams = _params.value,
                isUserCreated = true,
            )
            repository.save(pack)
            onDone()
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

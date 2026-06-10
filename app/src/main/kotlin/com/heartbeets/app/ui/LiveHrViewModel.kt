package com.heartbeets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.DeviceRegistry
import com.heartbeets.core.HrDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        driver?.let { d ->
            viewModelScope.launch {
                d.samples.collect { sample ->
                    _bpm.value = sample.bpm
                    _lastUpdatedMs.value = sample.timestamp
                }
            }
            viewModelScope.launch {
                d.state.collect { state ->
                    if (state == CS.Disconnected || state == CS.Error) {
                        _bpm.value = null
                        _lastUpdatedMs.value = 0L
                    }
                }
            }
            // Clear stale BPM if no live D0 reading arrives within 5s of connect.
            // During D0 01 warmup (~30s) the display shows "Measuring…" instead of a stale value.
            viewModelScope.launch {
                d.state.collect { state ->
                    if (state == CS.Connected) {
                        _bpm.value = null
                        delay(5_000)
                        // After 5s if still null it will show "Measuring…" — that's correct.
                        // Once D0 readings arrive they update _bpm normally.
                    }
                }
            }
        }
        connect()
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

    override fun onCleared() {
        disconnect()
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


package com.heartbeets.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartbeets.ble.ScanCoordinator
import com.heartbeets.core.DiscoveredDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(context: Context) {
        if (_scanning.value) return
        _devices.value = emptyList()
        _error.value = null
        _scanning.value = true
        val coordinator = ScanCoordinator(context)
        scanJob = viewModelScope.launch {
            try {
                coordinator.scan().collect { device ->
                    _devices.update { current ->
                        val existing = current.indexOfFirst { it.address == device.address }
                        if (existing >= 0) current.toMutableList().also { it[existing] = device }
                        else (current + device).sortedByDescending { it.rssi }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Scan failed"
            }
        }
        scanJob?.invokeOnCompletion { _scanning.value = false }
    }

    fun clearError() { _error.value = null }

    fun stopScan() {
        scanJob?.cancel()
        _scanning.value = false
    }

    override fun onCleared() {
        stopScan()
    }
}

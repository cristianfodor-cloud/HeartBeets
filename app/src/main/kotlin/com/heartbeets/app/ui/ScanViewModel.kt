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

    private var scanJob: Job? = null

    fun startScan(context: Context) {
        if (_scanning.value) return
        _devices.value = emptyList()
        _scanning.value = true
        val coordinator = ScanCoordinator(context)
        scanJob = viewModelScope.launch {
            coordinator.scan().collect { device ->
                _devices.update { current ->
                    val existing = current.indexOfFirst { it.address == device.address }
                    if (existing >= 0) current.toMutableList().also { it[existing] = device }
                    else (current + device).sortedByDescending { it.rssi }
                }
            }
        }
        scanJob?.invokeOnCompletion { _scanning.value = false }
    }

    fun stopScan() {
        scanJob?.cancel()
        _scanning.value = false
    }

    override fun onCleared() {
        stopScan()
    }
}

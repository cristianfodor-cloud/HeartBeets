package com.heartbeets.driver.standardhrs

import android.content.Context
import com.heartbeets.ble.BleConnection
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrSample
import com.heartbeets.core.SourceTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [HrDriver] for any device that implements the Bluetooth SIG Heart Rate Service (0x180D).
 *
 * Compatible devices include chest straps (Polar H10, Wahoo TICKR, …), cycling sensors
 * (Coospo H6), smartwatches in standard HRS mode, and most fitness trackers.
 *
 * Parses the full Heart Rate Measurement characteristic payload including:
 *   - uint8 / uint16 BPM field
 *   - Sensor contact status
 *   - R-R interval values (converted from 1/1024 s units to milliseconds)
 */
class StandardHrsDriver(
    context: Context,
    override val deviceAddress: String,
    name: String?,
) : HrDriver {

    override val displayName: String = name ?: "Heart Rate Sensor"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connection = BleConnection(context, deviceAddress, scope)

    override val state: StateFlow<ConnectionState> = connection.state

    private val _samples = MutableSharedFlow<HrSample>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val samples: SharedFlow<HrSample> = _samples.asSharedFlow()

    private val _battery = MutableStateFlow<Int?>(null)
    override val battery: StateFlow<Int?> = _battery.asStateFlow()

    private var notifyJob: Job? = null

    override suspend fun connect() {
        connection.connect()
        connection.enableNotifications(HrsProfile.HR_SERVICE, HrsProfile.HR_MEASUREMENT)

        // Optionally enable battery notifications if available.
        runCatching {
            connection.enableNotifications(HrsProfile.BATTERY_SERVICE, HrsProfile.BATTERY_LEVEL)
        }

        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                when (event.characteristicUuid) {
                    HrsProfile.HR_MEASUREMENT -> handleHrData(event.data)
                    HrsProfile.BATTERY_LEVEL -> handleBattery(event.data)
                }
            }
        }
    }

    override suspend fun disconnect() {
        notifyJob?.cancel()
        connection.disconnect()
        scope.cancel()
    }

    // ──────────────────────────── Private ────────────────────────────

    private suspend fun handleHrData(bytes: ByteArray) {
        val measurement = HrsProfile.parse(bytes) ?: return
        if (measurement.bpm <= 0) return
        _samples.emit(
            HrSample(
                bpm = measurement.bpm,
                timestamp = System.currentTimeMillis(),
                rrIntervalsMs = measurement.rrIntervalsMs,
                contactDetected = measurement.contactDetected,
                source = SourceTag("standard-hrs", deviceAddress),
            )
        )
    }

    private fun handleBattery(bytes: ByteArray) {
        if (bytes.isNotEmpty()) {
            _battery.value = bytes[0].toInt() and 0xFF
        }
    }
}

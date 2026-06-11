package com.heartbeets.driver.galaxy

import android.content.Context
import android.util.Log
import com.heartbeets.ble.BleConnection
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrSample
import com.heartbeets.core.SourceTag
import com.heartbeets.driver.standardhrs.HrsProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
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
 * [HrDriver] for Samsung Galaxy Watch, Galaxy Fit, and Galaxy Ring devices.
 *
 * Samsung devices expose the standard BLE Heart Rate Service (0x180D) when HR broadcasting
 * is enabled in Samsung Health settings. This driver:
 *
 * 1. Connects and enables HR notifications on 0x2A37 (standard format).
 * 2. Writes to the HR Control Point (0x2A39) to enable continuous streaming.
 * 3. Parses HR data using the standard BT SIG format (same as StandardHrsDriver).
 *
 * The key difference from StandardHrsDriver: this provides EXACT match on Samsung device
 * names, ensuring Samsung devices are handled with the right priority and branded correctly
 * in the UI.
 */
class GalaxyDriver(
    private val context: Context,
    override val deviceAddress: String,
    name: String?,
) : HrDriver {

    override val displayName: String = name ?: "Galaxy Watch"

    private val handler = CoroutineExceptionHandler { _, t ->
        if (t !is CancellationException) Log.w(TAG, "Coroutine error", t)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
    private val connection = BleConnection(context, deviceAddress, scope)

    override val state: StateFlow<ConnectionState> = connection.state

    private val _samples = MutableSharedFlow<HrSample>(replay = 0, extraBufferCapacity = 64)
    override val samples: SharedFlow<HrSample> = _samples.asSharedFlow()

    private val _battery = MutableStateFlow<Int?>(null)
    override val battery: StateFlow<Int?> = _battery.asStateFlow()

    private var notifyJob: Job? = null

    override suspend fun connect() {
        connection.connect()

        // Enable HR notifications on the standard Heart Rate Measurement characteristic.
        connection.enableNotifications(
            GalaxyProfile.SERVICE_HEART_RATE,
            GalaxyProfile.CHAR_HR_MEASUREMENT,
        )

        // Write to HR Control Point to start continuous measurement.
        runCatching {
            connection.write(
                GalaxyProfile.SERVICE_HEART_RATE,
                GalaxyProfile.CHAR_HR_CONTROL_POINT,
                GalaxyProfile.CMD_START_CONTINUOUS_HR,
            )
        }

        // Try to read battery via standard Battery Service if available.
        runCatching { readBattery() }

        // Start collecting HR notifications.
        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                if (event.characteristicUuid == GalaxyProfile.CHAR_HR_MEASUREMENT) {
                    handleHrData(event.data)
                }
            }
        }
    }

    override suspend fun disconnect() {
        runCatching {
            connection.write(
                GalaxyProfile.SERVICE_HEART_RATE,
                GalaxyProfile.CHAR_HR_CONTROL_POINT,
                GalaxyProfile.CMD_STOP_CONTINUOUS_HR,
            )
        }
        notifyJob?.cancel()
        notifyJob = null
        connection.disconnect()
        scope.cancel()
    }

    private fun handleHrData(data: ByteArray) {
        val parsed = HrsProfile.parse(data) ?: return
        Log.d(TAG, "HR: ${parsed.bpm} bpm, contact=${parsed.contactDetected}")
        _samples.tryEmit(
            HrSample(
                bpm = parsed.bpm,
                rrIntervalsMs = parsed.rrIntervalsMs,
                contactDetected = parsed.contactDetected,
                source = SourceTag(driverId = "galaxy", deviceAddress = deviceAddress),
            )
        )
    }

    private suspend fun readBattery() {
        // Standard Battery Service: 0x180F, Battery Level: 0x2A19
        val batteryService = java.util.UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val batteryLevel = java.util.UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        if (connection.hasService(batteryService)) {
            connection.enableNotifications(batteryService, batteryLevel)
        }
    }

    companion object {
        private const val TAG = "GalaxyDriver"
    }
}

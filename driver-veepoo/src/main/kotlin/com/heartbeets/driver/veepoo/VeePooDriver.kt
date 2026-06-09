package com.heartbeets.driver.veepoo

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [HrDriver] implementation for VeePoo devices (H59 bracelet, ET585 smartwatch, …).
 *
 * Uses a clean-room implementation of the VeePoo NUS protocol defined in
 * [VeePooProtocol] and [BleConnection] for all GATT operations — no vendor SDK.
 *
 * The CCCD write that was missing from the VeePoo SDK (which caused zero notifications
 * in RythmOfLife) is correctly handled inside [BleConnection.enableNotifications].
 */
class VeePooDriver(
    context: Context,
    override val deviceAddress: String,
    name: String?,
) : HrDriver {

    override val displayName: String = name ?: "VeePoo Device"

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
        // 1. Establish GATT + discover services.
        connection.connect()

        // 2. Enable notifications on NUS TX — writes CCCD 0x2902 correctly.
        connection.enableNotifications(VeePooProtocol.NUS_SERVICE, VeePooProtocol.NUS_TX)

        // 3. Start collecting notifications before sending init commands.
        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                if (event.characteristicUuid == VeePooProtocol.NUS_TX) {
                    handleResponse(VeePooProtocol.parse(event.data))
                }
            }
        }

        // 4. Initialization sequence (auth → status → config → battery → start HR).
        val ts = System.currentTimeMillis() / 1000L
        writeCmd(VeePooProtocol.authCommand(ts))
        delay(400)
        writeCmd(VeePooProtocol.queryStatus())
        delay(200)
        writeCmd(VeePooProtocol.configure())
        delay(200)
        writeCmd(VeePooProtocol.queryBattery())
        delay(200)
        writeCmd(VeePooProtocol.startHr())
    }

    override suspend fun disconnect() {
        try {
            writeCmd(VeePooProtocol.stopHr())
            delay(150)
        } catch (_: Exception) { /* best effort */ }
        notifyJob?.cancel()
        connection.disconnect()
        scope.cancel()
    }

    // ──────────────────────────── Private ────────────────────────────

    private suspend fun writeCmd(bytes: ByteArray) {
        connection.write(VeePooProtocol.NUS_SERVICE, VeePooProtocol.NUS_RX, bytes)
    }

    private suspend fun handleResponse(response: VeePooProtocol.Response) {
        when (response) {
            is VeePooProtocol.Response.HeartRate -> {
                if (response.bpm > 0) {
                    _samples.emit(
                        HrSample(
                            bpm = response.bpm,
                            timestamp = System.currentTimeMillis(),
                            // VeePoo does not provide R-R intervals.
                            rrIntervalsMs = null,
                            source = SourceTag("veepoo", deviceAddress),
                        )
                    )
                }
            }
            is VeePooProtocol.Response.Battery -> _battery.value = response.percent
            is VeePooProtocol.Response.AuthResult -> { /* log if needed */ }
            is VeePooProtocol.Response.Unknown -> { /* ignore */ }
        }
    }
}

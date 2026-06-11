package com.heartbeets.driver.colmi

import android.content.Context
import android.util.Log
import com.heartbeets.ble.BleConnection
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrSample
import com.heartbeets.core.SourceTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
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
 * [HrDriver] for Colmi R0x smart rings and P-series bands.
 *
 * **Protocol summary:**
 * 1. Connect GATT and discover services (V1 or V2).
 * 2. Enable notifications on CHAR_NOTIFY_V1.
 * 3. Send CMD_MANUAL_HEART_RATE (0x69, 0x01) to start live HR measurement.
 * 4. HR values arrive as notifications on V1: byte[0]=0x69, byte[1]=HR value.
 * 5. Also configure auto-HR interval for background readings.
 *
 * No authentication is required.
 */
class ColmiDriver(
    private val context: Context,
    override val deviceAddress: String,
    name: String?,
) : HrDriver {

    override val displayName: String = name ?: "Colmi Ring"

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
    private var hrLoopJob: Job? = null
    private var lastEmittedBpm: Int = 0
    private var lastEmittedTime: Long = 0L
    @Volatile private var lastNotificationTime: Long = 0L

    override suspend fun connect() {
        connection.connect()

        // Enable notifications on V1.
        connection.enableNotifications(ColmiProfile.SERVICE_V1, ColmiProfile.CHAR_NOTIFY_V1)

        // Also try V2 if available.
        runCatching {
            connection.enableNotifications(ColmiProfile.SERVICE_V2, ColmiProfile.CHAR_NOTIFY_V2)
        }

        // Start collecting notifications.
        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                when (event.characteristicUuid) {
                    ColmiProfile.CHAR_NOTIFY_V1 -> handleV1Notification(event.data)
                    ColmiProfile.CHAR_NOTIFY_V2 -> handleV2Notification(event.data)
                }
            }
        }

        // Request battery info.
        writeCommand(ColmiProfile.CMD_BATTERY)

        // Start live HR measurement — re-triggers quickly when measurement stops.
        hrLoopJob = scope.launch {
            // Initial start.
            runCatching { writeCommand(ColmiProfile.CMD_MANUAL_HEART_RATE, 0x01) }
            while (true) {
                delay(3_000)
                // If no notification received in the last 2s, the band stopped — re-trigger.
                if (System.currentTimeMillis() - lastNotificationTime > 2_000) {
                    runCatching { writeCommand(ColmiProfile.CMD_MANUAL_HEART_RATE, 0x01) }
                }
            }
        }
    }

    override suspend fun disconnect() {
        hrLoopJob?.cancel()
        hrLoopJob = null
        // Stop live HR.
        runCatching { writeCommand(ColmiProfile.CMD_MANUAL_HEART_RATE, 0x00) }
        notifyJob?.cancel()
        notifyJob = null
        connection.disconnect()
        scope.cancel()
    }

    private suspend fun writeCommand(vararg contents: Byte) {
        val packet = ColmiProfile.buildPacket(*contents)
        connection.write(ColmiProfile.SERVICE_V1, ColmiProfile.CHAR_WRITE, packet)
    }

    private fun handleV1Notification(data: ByteArray) {
        if (data.isEmpty()) return
        lastNotificationTime = System.currentTimeMillis()
        when (data[0]) {
            ColmiProfile.CMD_MANUAL_HEART_RATE -> handleLiveHr(data)
            ColmiProfile.CMD_BATTERY -> handleBattery(data)
            ColmiProfile.CMD_NOTIFICATION -> handleNotification(data)
        }
        // 0x73 0x01 = measurement done signal — immediately re-trigger
        if (data[0] == ColmiProfile.CMD_NOTIFICATION && data.size >= 2 && data[1] == 0x01.toByte()) {
            scope.launch { runCatching { writeCommand(ColmiProfile.CMD_MANUAL_HEART_RATE, 0x01) } }
        }
    }

    private fun handleV2Notification(data: ByteArray) {
        // Big-data packets (sleep, SpO2 history) — not needed for live HR.
        Log.d(TAG, "V2 notification: ${data.joinToString(" ") { "%02X".format(it) }}")
    }

    private fun handleLiveHr(data: ByteArray) {
        // Packet format per Gadgetbridge ColmiR0xPacketHandler.liveHeartRate():
        //   data[0] = CMD_MANUAL_HEART_RATE (0x69)
        //   data[1] = sub-type (unused)
        //   data[2] = error code: 0=OK, 1=worn incorrectly, 2=temporary error
        //   data[3] = HR BPM value
        if (data.size < 4) return
        val errorCode = data[2].toInt() and 0xFF
        if (errorCode != 0) {
            Log.w(TAG, "Live HR error code $errorCode (1=worn incorrectly, 2=temp error)")
            return
        }
        val hr = data[3].toInt() and 0xFF
        if (hr == 0) return // Still measuring, no value yet

        // Deduplicate — the device sends the same reading ~2x per second.
        val now = System.currentTimeMillis()
        if (hr == lastEmittedBpm && now - lastEmittedTime < 2000) return
        lastEmittedBpm = hr
        lastEmittedTime = now

        Log.d(TAG, "Live HR: $hr bpm")
        _samples.tryEmit(
            HrSample(
                bpm = hr,
                source = SourceTag(driverId = "colmi", deviceAddress = deviceAddress),
            )
        )
    }

    private fun handleBattery(data: ByteArray) {
        if (data.size < 2) return
        val level = data[1].toInt() and 0xFF
        val charging = data.size >= 3 && data[2] == 1.toByte()
        Log.d(TAG, "Battery: $level% charging=$charging")
        _battery.value = level
    }

    private fun handleNotification(data: ByteArray) {
        if (data.size < 3) return
        when (data[1]) {
            ColmiProfile.NOTIFICATION_BATTERY_LEVEL -> {
                val level = data[2].toInt() and 0xFF
                Log.d(TAG, "Battery notification: $level%")
                _battery.value = level
            }
            else -> Log.d(TAG, "Ring notification type: 0x${String.format("%02X", data[1])}")
        }
    }

    companion object {
        private const val TAG = "ColmiDriver"
    }
}

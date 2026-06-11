package com.heartbeets.driver.huawei

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [HrDriver] for Huawei/Honor Band and Watch devices.
 *
 * **Protocol summary:**
 * 1. Connect GATT and discover FE86 service.
 * 2. Enable notifications on FE02 (read characteristic).
 * 3. Send link-params handshake (serviceId=0x01, cmdId=0x01).
 * 4. Send enable-auto-HR command (serviceId=0x07, cmdId=0x17, TLV: tag=0x01, value=true).
 * 5. Send enable-realtime-HR command (serviceId=0x07, cmdId=0x23, TLV: tag=0x01, value=0x01).
 * 6. HR data arrives as TLV responses on FE02 with serviceId=0x07.
 *
 * NOTE: Full Huawei protocol involves challenge-response auth with HiChain for newer
 * devices. This implementation handles the BLE-only case for devices that are already
 * bonded at the Android OS level (which covers the majority of use cases for fitness HR
 * streaming after initial pairing via Huawei Health).
 */
class HuaweiDriver(
    private val context: Context,
    override val deviceAddress: String,
    name: String?,
) : HrDriver {

    override val displayName: String = name ?: "Huawei Band"

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

        // Enable notifications on the read characteristic.
        connection.enableNotifications(HuaweiProfile.SERVICE_HUAWEI, HuaweiProfile.CHAR_READ)

        // Start notification collector.
        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                if (event.characteristicUuid == HuaweiProfile.CHAR_READ) {
                    handleResponse(event.data)
                }
            }
        }

        // Send link params (basic handshake).
        sendLinkParams()

        // Request battery info.
        requestBattery()

        // Enable automatic heart rate monitoring.
        enableAutoHeartRate()

        // Enable real-time HR reporting.
        enableRealtimeHeartRate()
    }

    override suspend fun disconnect() {
        // Try to disable real-time HR before disconnecting.
        runCatching { disableRealtimeHeartRate() }
        notifyJob?.cancel()
        notifyJob = null
        connection.disconnect()
        scope.cancel()
    }

    private suspend fun sendLinkParams() {
        // Minimal link-params: just announce we exist (serviceId=0x01, cmdId=0x01).
        // TLV: tag=0x01, value=[0x02] (protocol version 2)
        val tlv = HuaweiProfile.tlv(0x01, byteArrayOf(HuaweiProfile.PROTOCOL_VERSION))
        val packet = HuaweiProfile.buildPacket(
            HuaweiProfile.SERVICE_ID_DEVICE,
            HuaweiProfile.CMD_LINK_PARAMS,
            tlv,
        )
        writePacket(packet)
    }

    private suspend fun requestBattery() {
        // serviceId=0x01, cmdId=0x02, TLV: tag=0x01, value=[0x01]
        val tlv = HuaweiProfile.tlvBool(0x01, true)
        val packet = HuaweiProfile.buildPacket(
            HuaweiProfile.SERVICE_ID_DEVICE,
            HuaweiProfile.CMD_BATTERY,
            tlv,
        )
        runCatching { writePacket(packet) }
    }

    private suspend fun enableAutoHeartRate() {
        // serviceId=0x07, cmdId=0x17, TLV: tag=0x01, value=true
        val tlv = HuaweiProfile.tlvBool(0x01, true)
        val packet = HuaweiProfile.buildPacket(
            HuaweiProfile.SERVICE_ID_FITNESS,
            HuaweiProfile.CMD_ENABLE_AUTO_HR,
            tlv,
        )
        writePacket(packet)
    }

    private suspend fun enableRealtimeHeartRate() {
        // serviceId=0x07, cmdId=0x23, TLV: tag=0x01, value=[0x01]
        val tlv = HuaweiProfile.tlv(0x01, byteArrayOf(0x01))
        val packet = HuaweiProfile.buildPacket(
            HuaweiProfile.SERVICE_ID_FITNESS,
            HuaweiProfile.CMD_ENABLE_REALTIME_HR,
            tlv,
        )
        writePacket(packet)
    }

    private suspend fun disableRealtimeHeartRate() {
        val tlv = HuaweiProfile.tlv(0x01, byteArrayOf(0x00))
        val packet = HuaweiProfile.buildPacket(
            HuaweiProfile.SERVICE_ID_FITNESS,
            HuaweiProfile.CMD_ENABLE_REALTIME_HR,
            tlv,
        )
        writePacket(packet)
    }

    private suspend fun writePacket(data: ByteArray) {
        connection.write(HuaweiProfile.SERVICE_HUAWEI, HuaweiProfile.CHAR_WRITE, data)
    }

    /**
     * Parse incoming responses from the Huawei device.
     * Format: [MAGIC, lenHi, lenLo, protocolVer, serviceId, commandId, ...TLV..., crcHi, crcLo]
     */
    private fun handleResponse(data: ByteArray) {
        if (data.size < 6) return
        if (data[0] != HuaweiProfile.MAGIC) {
            Log.w(TAG, "Non-magic response: ${data.joinToString(" ") { "%02X".format(it) }}")
            return
        }

        val serviceId = data[4]
        val commandId = data[5]
        val payload = if (data.size > 8) data.copyOfRange(6, data.size - 2) else ByteArray(0)

        Log.d(TAG, "Response: svc=%02X cmd=%02X payload=${payload.joinToString(" ") { "%02X".format(it) }}"
            .format(serviceId, commandId))

        when {
            // Battery response (serviceId=0x01, cmdId=0x02)
            serviceId == HuaweiProfile.SERVICE_ID_DEVICE.toByte() &&
                    commandId == HuaweiProfile.CMD_BATTERY.toByte() -> {
                parseBattery(payload)
            }
            // Fitness data (serviceId=0x07) — look for HR values
            serviceId == HuaweiProfile.SERVICE_ID_FITNESS.toByte() -> {
                parseFitnessData(commandId, payload)
            }
        }
    }

    private fun parseBattery(payload: ByteArray) {
        // Battery TLV: look for tag=0x01, value = battery level byte
        val level = findTlvByte(payload, 0x01)
        if (level != null) {
            val percent = level.toInt() and 0xFF
            Log.d(TAG, "Battery: $percent%")
            _battery.value = percent
        }
    }

    private fun parseFitnessData(commandId: Byte, payload: ByteArray) {
        // Real-time HR: command=0x23, TLV contains HR in tag=0x01 or tag=0x02
        // Auto HR notification also arrives here.
        // Look for any byte value in range 30-250 as heart rate.
        val hrByte = findTlvByte(payload, 0x02) ?: findTlvByte(payload, 0x01)
        if (hrByte != null) {
            val hr = hrByte.toInt() and 0xFF
            if (hr in 30..250) {
                Log.d(TAG, "HR: $hr bpm (cmd=0x%02X)".format(commandId))
                _samples.tryEmit(
                    HrSample(
                        bpm = hr,
                        source = SourceTag(driverId = "huawei", deviceAddress = deviceAddress),
                    )
                )
            }
        }
    }

    /**
     * Simple TLV parser — find first occurrence of a given tag and return its single-byte value.
     * TLV format: [tag, length, ...value]
     */
    private fun findTlvByte(data: ByteArray, tag: Byte): Byte? {
        var i = 0
        while (i + 1 < data.size) {
            val t = data[i]
            val len = data[i + 1].toInt() and 0xFF
            if (i + 2 + len > data.size) break
            if (t == tag && len >= 1) {
                return data[i + 2]
            }
            i += 2 + len
        }
        return null
    }

    companion object {
        private const val TAG = "HuaweiDriver"
    }
}

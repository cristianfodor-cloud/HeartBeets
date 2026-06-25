package com.heartbeets.driver.standardhrs

import android.content.Context
import android.util.Log
import com.heartbeets.ble.BleConnection
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrSample
import com.heartbeets.core.SourceTag
import java.util.concurrent.atomic.AtomicLong
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

    private val scopeExHandler = CoroutineExceptionHandler { _, t ->
        if (t !is CancellationException) Log.w("StandardHrsDriver", "Coroutine error", t)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + scopeExHandler)
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
    private var keepAliveJob: Job? = null
    /** Timestamp of last received proprietary HR data. Used to detect measurement timeout. */
    private val lastProprietaryHrMs = AtomicLong(0L)

    override suspend fun connect() {
        connection.connect()
        connection.enableNotifications(HrsProfile.HR_SERVICE, HrsProfile.HR_MEASUREMENT)

        // Optionally enable battery notifications if available.
        runCatching {
            connection.enableNotifications(HrsProfile.BATTERY_SERVICE, HrsProfile.BATTERY_LEVEL)
        }

        // Try to start continuous HR measurement via the HR Control Point.
        // Many devices (Xiaomi "Expose HR to 3rd party", some Polar, etc.) need this
        // to start streaming. It's harmless on devices that don't support it.
        runCatching {
            connection.write(
                HrsProfile.HR_SERVICE,
                HrsProfile.HR_CONTROL_POINT,
                HrsProfile.CMD_START_CONTINUOUS_HR,
            )
        }

        // Cheap bracelets (S18, M5, Y68, etc.) use proprietary BLE services.
        // Protocol reverse-engineered from BR Fit ↔ S18 BT snoop capture:
        //   Handle mapping: writes → B002 (0x0017), notifications ← B003 (0x0019)
        //   1. Enable notifications on B003 (HR data arrives here)
        //   2. Enable notifications on FF01 (some devices echo here)
        //   3. Send bind command to B002 (write without response)
        //   4. Wait for bind response on B003
        //   5. Send start-HR command to B002
        //   6. HR data arrives as notifications on B003

        // Enable FF01 notifications (some responses echo here)
        if (connection.hasService(HrsProfile.PROPRIETARY_FF00_SERVICE)) {
            Log.d("StandardHrsDriver", "FF00 service found — enabling FF01 notifications")
            runCatching {
                connection.enableNotifications(
                    HrsProfile.PROPRIETARY_FF00_SERVICE,
                    HrsProfile.PROPRIETARY_FF01_CHAR,
                )
            }
        }

        // B00B service: commands go to B002, HR data comes from B003
        if (connection.hasService(HrsProfile.PROPRIETARY_B00B_SERVICE)) {
            Log.d("StandardHrsDriver", "B00B service found — sending AB-protocol start-HR via B002")
            // Step 1: Enable notifications on B003
            runCatching {
                connection.enableNotifications(
                    HrsProfile.PROPRIETARY_B00B_SERVICE,
                    HrsProfile.PROPRIETARY_B003_CHAR,
                )
            }
            // Step 2: Send bind command to B002
            runCatching {
                connection.write(
                    HrsProfile.PROPRIETARY_B00B_SERVICE,
                    HrsProfile.PROPRIETARY_B002_CHAR,
                    HrsProfile.PROPRIETARY_BIND_CMD,
                    forceNoResponse = true,
                )
            }
            // Brief delay for bind response
            delay(500)
            // Step 3: Send start-HR command to B002
            sendStartHr()
            // Step 4: Watchdog loop — only re-send start-HR if no data received
            // for 90s, meaning the bracelet's measurement timed out.
            // The bracelet needs ~60s warm-up before first HR reading, so we
            // must wait longer than that before assuming a timeout.
            lastProprietaryHrMs.set(System.currentTimeMillis())
            keepAliveJob = scope.launch {
                while (true) {
                    delay(30_000)
                    val silenceMs = System.currentTimeMillis() - lastProprietaryHrMs.get()
                    if (silenceMs > 90_000) {
                        Log.d("StandardHrsDriver", "No HR data for ${silenceMs / 1000}s — re-sending start-HR")
                        sendStartHr()
                        lastProprietaryHrMs.set(System.currentTimeMillis())
                    }
                }
            }
        }

        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                when (event.characteristicUuid) {
                    HrsProfile.HR_MEASUREMENT -> handleHrData(event.data)
                    HrsProfile.PROPRIETARY_FF01_CHAR -> handleProprietaryHr(event.data)
                    HrsProfile.PROPRIETARY_FF03_CHAR -> handleProprietaryHr(event.data)
                    HrsProfile.PROPRIETARY_B003_CHAR -> handleProprietaryHr(event.data)
                    HrsProfile.BATTERY_LEVEL -> handleBattery(event.data)
                }
            }
        }
    }

    override suspend fun disconnect() {
        keepAliveJob?.cancel()
        notifyJob?.cancel()
        connection.disconnect()
        scope.cancel()
    }

    // ──────────────────────────── Private ────────────────────────────

    private suspend fun sendStartHr() {
        runCatching {
            connection.write(
                HrsProfile.PROPRIETARY_B00B_SERVICE,
                HrsProfile.PROPRIETARY_B002_CHAR,
                HrsProfile.PROPRIETARY_START_HR_CMD,
                forceNoResponse = true,
            )
        }
    }

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

    /**
     * Handle proprietary notifications from cheap bracelets (FF01 / FF03 / B003).
     *
     * AB protocol format (reverse-engineered from BR Fit ↔ S18 BT snoop):
     * ```
     *  Byte 0:    0xAB (header)
     *  Byte 1:    direction (0x11 = device→phone)
     *  Bytes 2-3: payload length (big-endian)
     *  Bytes 4-5: CRC16
     *  Byte 6:    command high (0x02 = health data)
     *  Byte 7:    command low  (0x24 = real-time HR)
     *  Bytes 8+:  payload data, BPM at second-to-last byte
     * ```
     */
    private suspend fun handleProprietaryHr(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        Log.d("StandardHrsDriver", "Proprietary notify: ${bytes.joinToString(" ") { "%02X".format(it) }}")

        val bpm = extractProprietaryBpm(bytes) ?: return
        if (bpm <= 0 || bpm > 250) return
        lastProprietaryHrMs.set(System.currentTimeMillis())

        _samples.emit(
            HrSample(
                bpm = bpm,
                timestamp = System.currentTimeMillis(),
                rrIntervalsMs = null,
                contactDetected = true,
                source = SourceTag("standard-hrs", deviceAddress),
            )
        )
    }

    /**
     * Try various known packet formats to extract BPM from proprietary data.
     */
    private fun extractProprietaryBpm(bytes: ByteArray): Int? {
        // AB protocol real-time HR packet: AB 11 00 09 xx xx 02 24 00 ... BPM 00
        // Only extract from command 02 24 (real-time HR data), ignore bind/ack/info packets.
        if (bytes.size >= 10
            && (bytes[0].toInt() and 0xFF) == 0xAB
            && (bytes[6].toInt() and 0xFF) == 0x02
            && (bytes[7].toInt() and 0xFF) == 0x24
        ) {
            val bpm = bytes[bytes.size - 2].toInt() and 0xFF
            if (bpm in 30..250) return bpm
        }
        // Simple [cmd, bpm] 2-byte response
        if (bytes.size == 2) {
            val bpm = bytes[1].toInt() and 0xFF
            if (bpm in 30..250) return bpm
        }
        return null
    }
}

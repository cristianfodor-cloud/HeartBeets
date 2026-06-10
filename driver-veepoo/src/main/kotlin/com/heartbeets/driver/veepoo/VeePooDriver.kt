package com.heartbeets.driver.veepoo

import android.content.Context
import android.util.Log
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

    /** Detected at connect time. */
    private var useAe40 = false

    override suspend fun connect() {
        Log.d(TAG, "connect: starting for $deviceAddress")
        connection.connect()

        // Auto-detect protocol: presence of ae40 service = HBand/ET585 device using f0080001 channel.
        useAe40 = connection.hasService(VeePooProtocol.AE40_SERVICE)
        Log.d(TAG, "connect: useAe40=$useAe40 (ae40 present → use f0080001 service)")

        if (useAe40) {
            connectAe40()
        } else {
            connectNus()
        }
    }

    private var pollJob: Job? = null

    private suspend fun connectAe40() {
        // CORRECT channel (from btsnoop ATT handle analysis):
        //   f0080002 [NOTIFY]        = watch→phone (A7, BD03, D0 live HR, all responses)
        //   f0080003 [WRITE+WRITE_NR] = phone→watch (auth, all commands)
        // FEE7/FEA1/FEA2 are NOT the protocol channel — FEA1 sends periodic status packets only.
        Log.d(TAG, "connectAe40: subscribing f0080002 (correct notify channel)")
        connection.enableNotifications(VeePooProtocol.F008_SERVICE, VeePooProtocol.F008_NOTIFY)

        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                val charName = when (event.characteristicUuid) {
                    VeePooProtocol.F008_NOTIFY -> "f0080002"
                    else -> event.characteristicUuid.toString().takeLast(8)
                }
                val raw = event.data.toHex()
                Log.d(TAG, "NOTIFY $charName raw: $raw")
                // Only process f0080002 data — FEA1 carries unrelated status frames
                if (event.characteristicUuid == VeePooProtocol.F008_NOTIFY) {
                    handleResponse(VeePooProtocol.parseNus(event.data))
                }
            }
        }

        // Auth — Write Command to f0080003 (WRITE+WRITE_NR), exactly as in btsnoop.
        // btsnoop: Write Command (0x52) to handle 0x0066 = f0080003 value.
        Log.d(TAG, "connectAe40: auth→f0080003")
        connection.write(VeePooProtocol.F008_SERVICE, VeePooProtocol.F008_WRITE, VeePooProtocol.authCommand(), forceNoResponse = true)
        delay(1000)

        // ── Settings phase: device configuration commands (150 ms apart) ──
        // Exact order from RythmOfLife hband_ble_write.txt capture.
        val settingsCmds = listOf(
            "queryStatus"     to VeePooProtocol.queryStatus(),
            "configure"       to VeePooProtocol.configure(),
            "queryBattery"    to VeePooProtocol.queryBattery(),
            "personInfo"      to VeePooProtocol.personInfo(),
            "longSeat"        to VeePooProtocol.longSeat(),
            "heartWarning"    to VeePooProtocol.heartWarning(),
            "nightTurn"       to VeePooProtocol.nightTurn(),
            "bpModel"         to VeePooProtocol.bpModel(),
            "womenMense"      to VeePooProtocol.womenMense(),
            "allSetting"      to VeePooProtocol.allSetting(),
            "screenLight"     to VeePooProtocol.screenLight(),
            "weather"         to VeePooProtocol.weather(),
            "screenTimeout"   to VeePooProtocol.screenTimeout(),
            "batteryManager"  to VeePooProtocol.batteryManager(),
            "alarm"           to VeePooProtocol.alarm(),
            "batteryBigData02" to VeePooProtocol.batteryBigData02(),
            "batteryBigData01" to VeePooProtocol.batteryBigData01(),
            "contact"         to VeePooProtocol.contact(),
            "bloodCompos"     to VeePooProtocol.bloodComposition(),
        )
        for ((name, cmd) in settingsCmds) {
            Log.d(TAG, "connectAe40: $name→f0080003")
            runCatching { connection.write(VeePooProtocol.F008_SERVICE, VeePooProtocol.F008_WRITE, cmd, forceNoResponse = true) }
                .onFailure { Log.w(TAG, "connectAe40: $name failed — ${it.message}") }
            delay(150)
        }

        // ── Data-dump phase: trigger watch to stream historical data on f0080002 ──
        // Btsnoop shows these commands after settings; watch streams historical records for each.
        // Each takes 700–1400 ms to complete. Fixed 1500 ms delay is conservative but reliable.
        val dataDumpCmds = listOf(
            "queryStatus2"   to VeePooProtocol.queryStatus(),
            "readSleep00"    to VeePooProtocol.readSleep00(),
            "readSleep01"    to VeePooProtocol.readSleep01(),
            "headOrigalDf"   to VeePooProtocol.headOrigalDf(),
            "sportModelCrc"  to VeePooProtocol.sportModelCrc(),
            "ecgDataGet02"   to VeePooProtocol.ecgDataGet02(),
            "ecgDataGet03"   to VeePooProtocol.ecgDataGet03(),
            "bodyComponent"  to VeePooProtocol.bodyComponent(),
        )
        Log.d(TAG, "connectAe40: starting data-dump phase (${dataDumpCmds.size} commands × 1500 ms)")
        for ((name, cmd) in dataDumpCmds) {
            Log.d(TAG, "connectAe40: $name→f0080003")
            runCatching { connection.write(VeePooProtocol.F008_SERVICE, VeePooProtocol.F008_WRITE, cmd, forceNoResponse = true) }
                .onFailure { Log.w(TAG, "connectAe40: $name failed — ${it.message}") }
            delay(1500)
        }

        // Extra wait after last data-dump response before D0 01 (matches btsnoop ~2s gap).
        Log.d(TAG, "connectAe40: data-dump complete, waiting before startHr")
        delay(2000)

        // D0 01 = startDetectHeart — watch streams D0 [bpm] on FEA1 (~15 s warmup then live HR).
        Log.d(TAG, "connectAe40: D0 01 startHr→f0080003")
        runCatching { connection.write(VeePooProtocol.F008_SERVICE, VeePooProtocol.F008_WRITE, VeePooProtocol.startHr(), forceNoResponse = true) }
            .onFailure { Log.w(TAG, "connectAe40: startHr failed — ${it.message}") }

        Log.d(TAG, "connectAe40: init complete")
    }

    private suspend fun connectNus() {
        Log.d(TAG, "connectNus: enabling notifications on NUS TX")
        try {
            connection.enableNotifications(VeePooProtocol.NUS_SERVICE, VeePooProtocol.NUS_TX)
        } catch (e: Exception) {
            Log.e(TAG, "connectNus: NUS service not found: ${e.message}")
            return
        }
        Log.d(TAG, "connectNus: notifications enabled")

        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                if (event.characteristicUuid == VeePooProtocol.NUS_TX) {
                    Log.d(TAG, "nus raw: ${event.data.toHex()}")
                    handleResponse(VeePooProtocol.parseNus(event.data))
                }
            }
        }

        val ts = System.currentTimeMillis() / 1000L
        writeCmd(VeePooProtocol.authCommandNus(ts)); delay(400)
        writeCmd(VeePooProtocol.queryStatus());   delay(200)
        writeCmd(VeePooProtocol.configure());      delay(200)
        writeCmd(VeePooProtocol.queryBattery());   delay(200)
        writeCmd(VeePooProtocol.startHr())
        Log.d(TAG, "connectNus: init complete")
    }

    override suspend fun disconnect() {
        pollJob?.cancel()
        try {
            if (useAe40) connection.write(VeePooProtocol.F008_SERVICE, VeePooProtocol.F008_WRITE,
                VeePooProtocol.stopHr(), forceNoResponse = true)
            else writeCmd(VeePooProtocol.stopHr())
            delay(150)
        } catch (_: Exception) { }
        notifyJob?.cancel()
        connection.disconnect()
        scope.cancel()
    }

    // ──────────────────────────── Private ────────────────────────────

    private suspend fun writeCmd(bytes: ByteArray) {
        connection.write(VeePooProtocol.NUS_SERVICE, VeePooProtocol.NUS_RX, bytes)
    }

    private suspend fun handleResponse(response: VeePooProtocol.Response) {
        Log.d(TAG, "handleResponse: $response")
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

    companion object {
        private const val TAG = "VeePooDriver"
        private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
    }
}

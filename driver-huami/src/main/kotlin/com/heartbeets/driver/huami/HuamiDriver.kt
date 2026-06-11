package com.heartbeets.driver.huami

import android.content.Context
import android.util.Log
import com.heartbeets.ble.BleConnection
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrSample
import com.heartbeets.core.SourceTag
import com.heartbeets.driver.standardhrs.HrsProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeout
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * [HrDriver] for Huami devices (Xiaomi Mi Band 2–8, Amazfit Bip/GTS/GTR, etc.).
 *
 * **Protocol summary:**
 * 1. Connect GATT and discover services (FEE0/FEE1 + 180D).
 * 2. Enable notifications on the auth characteristic (0009).
 * 3. Send 16-byte auth key → band responds with success.
 * 4. Request random number → band responds with 16 random bytes.
 * 5. Encrypt random bytes with AES/ECB using the key, send back → band confirms auth.
 * 6. Enable HR notifications on standard 0x2A37.
 * 7. Write start-continuous-HR to HR Control Point (0x2A39).
 * 8. HR data arrives on 0x2A37 in standard BT SIG format.
 *
 * For devices with "Expose HR to third-party apps" enabled, steps 2–5 may
 * succeed trivially (auth already granted). The driver still performs them for
 * devices that haven't enabled that setting.
 */
class HuamiDriver(
    private val context: Context,
    override val deviceAddress: String,
    name: String?,
    private val authKey: ByteArray = HuamiProfile.DEFAULT_AUTH_KEY,
) : HrDriver {

    override val displayName: String = name ?: "Huami HR Band"

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

        // Attempt auth if the FEE1 service is present.
        if (connection.hasService(HuamiProfile.SERVICE_MIBAND_AUTH)) {
            authenticate()
        }

        // Enable HR notifications on the standard Heart Rate Service.
        connection.enableNotifications(
            HuamiProfile.SERVICE_HEART_RATE,
            HuamiProfile.CHAR_HR_MEASUREMENT,
        )

        // Start continuous HR measurement.
        runCatching {
            connection.write(
                HuamiProfile.SERVICE_HEART_RATE,
                HuamiProfile.CHAR_HR_CONTROL_POINT,
                HuamiProfile.CMD_STOP_MANUAL_HR,
            )
        }
        runCatching {
            connection.write(
                HuamiProfile.SERVICE_HEART_RATE,
                HuamiProfile.CHAR_HR_CONTROL_POINT,
                HuamiProfile.CMD_START_CONTINUOUS_HR,
            )
        }

        // Try to set 1-minute interval for background measurement as well.
        runCatching {
            connection.write(
                HuamiProfile.SERVICE_HEART_RATE,
                HuamiProfile.CHAR_HR_CONTROL_POINT,
                HuamiProfile.CMD_HR_INTERVAL_1MIN,
            )
        }

        notifyJob = scope.launch {
            connection.notifications.collect { event ->
                when (event.characteristicUuid) {
                    HuamiProfile.CHAR_HR_MEASUREMENT -> handleHrData(event.data)
                    HuamiProfile.CHAR_BATTERY -> handleBattery(event.data)
                }
            }
        }
    }

    override suspend fun disconnect() {
        // Try to stop HR before disconnecting.
        runCatching {
            connection.write(
                HuamiProfile.SERVICE_HEART_RATE,
                HuamiProfile.CHAR_HR_CONTROL_POINT,
                HuamiProfile.CMD_STOP_CONTINUOUS_HR,
            )
        }
        notifyJob?.cancel()
        connection.disconnect()
        scope.cancel()
    }

    // ──────────────────────────── Auth ────────────────────────────

    /**
     * Three-step AES authentication handshake.
     * Throws on timeout or auth failure.
     */
    private suspend fun authenticate() {
        Log.d(TAG, "Starting Huami authentication")

        // Enable notifications on the auth characteristic to receive responses.
        connection.enableNotifications(
            HuamiProfile.SERVICE_MIBAND_AUTH,
            HuamiProfile.CHAR_AUTH,
        )

        // Collect auth responses in a shared deferred.
        val authComplete = CompletableDeferred<Unit>()
        val authJob = scope.launch {
            connection.notifications.collect { event ->
                if (event.characteristicUuid == HuamiProfile.CHAR_AUTH) {
                    try {
                        handleAuthResponse(event.data, authComplete)
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            authComplete.completeExceptionally(e)
                        }
                    }
                }
            }
        }

        try {
            // Step 1: Send the auth key.
            val sendKeyCmd = byteArrayOf(HuamiProfile.AUTH_SEND_KEY, HuamiProfile.AUTH_FLAG) + authKey
            connection.write(
                HuamiProfile.SERVICE_MIBAND_AUTH,
                HuamiProfile.CHAR_AUTH,
                sendKeyCmd,
            )

            // Wait for full auth to complete (steps 2 and 3 are triggered by responses).
            withTimeout(15_000L) { authComplete.await() }
            Log.d(TAG, "Authentication successful")
        } finally {
            authJob.cancel()
        }
    }

    /**
     * Handles each auth response from the band and progresses through the 3-step flow.
     */
    private suspend fun handleAuthResponse(value: ByteArray, completion: CompletableDeferred<Unit>) {
        if (value.size < 3) return
        if (value[0] != HuamiProfile.AUTH_RESPONSE) return

        val step = value[1].toInt() and 0x0F
        val status = value[2]

        when (step) {
            HuamiProfile.AUTH_SEND_KEY.toInt() -> {
                // Key was accepted — now request a random number.
                if (status == HuamiProfile.AUTH_SUCCESS) {
                    Log.d(TAG, "Auth step 1: key accepted, requesting random number")
                    val cmd = byteArrayOf(HuamiProfile.AUTH_REQUEST_RANDOM, HuamiProfile.AUTH_FLAG)
                    connection.write(HuamiProfile.SERVICE_MIBAND_AUTH, HuamiProfile.CHAR_AUTH, cmd)
                } else {
                    completion.completeExceptionally(HuamiAuthException("Key rejected (status=$status)"))
                }
            }

            HuamiProfile.AUTH_REQUEST_RANDOM.toInt() -> {
                // Received random number — encrypt and send back.
                if (status == HuamiProfile.AUTH_SUCCESS && value.size >= 19) {
                    Log.d(TAG, "Auth step 2: received random number, encrypting")
                    val randomBytes = value.copyOfRange(3, 19) // 16 bytes
                    val encrypted = encryptAes(randomBytes, authKey)
                    val cmd = byteArrayOf(HuamiProfile.AUTH_SEND_ENCRYPTED, HuamiProfile.AUTH_FLAG) + encrypted
                    connection.write(HuamiProfile.SERVICE_MIBAND_AUTH, HuamiProfile.CHAR_AUTH, cmd)
                } else {
                    completion.completeExceptionally(HuamiAuthException("Random request failed (status=$status)"))
                }
            }

            HuamiProfile.AUTH_SEND_ENCRYPTED.toInt() -> {
                // Final response — auth complete or failed.
                if (status == HuamiProfile.AUTH_SUCCESS) {
                    Log.d(TAG, "Auth step 3: authentication complete!")
                    completion.complete(Unit)
                } else {
                    completion.completeExceptionally(
                        HuamiAuthException("Auth failed — wrong key? (status=$status)")
                    )
                }
            }
        }
    }

    // ──────────────────────────── Crypto ────────────────────────────

    private fun encryptAes(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    // ──────────────────────────── Data parsing ────────────────────────────

    private suspend fun handleHrData(bytes: ByteArray) {
        // Standard BT SIG Heart Rate Measurement format — reuse HrsProfile.parse().
        val measurement = HrsProfile.parse(bytes) ?: return
        if (measurement.bpm <= 0) return
        _samples.emit(
            HrSample(
                bpm = measurement.bpm,
                timestamp = System.currentTimeMillis(),
                rrIntervalsMs = measurement.rrIntervalsMs,
                contactDetected = measurement.contactDetected,
                source = SourceTag("huami", deviceAddress),
            )
        )
    }

    private fun handleBattery(bytes: ByteArray) {
        // Huami battery info: byte[1] is battery percentage.
        if (bytes.size >= 2) {
            _battery.value = bytes[1].toInt() and 0xFF
        }
    }

    companion object {
        private const val TAG = "HuamiDriver"
    }
}

/** Thrown when Huami authentication fails. */
class HuamiAuthException(message: String) : Exception(message)

package com.heartbeets.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.heartbeets.core.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Native Android `BluetoothGatt` wrapper that:
 *  - Serialises all GATT operations via a [Mutex] so they never overlap.
 *  - Correctly writes the CCCD 0x2902 descriptor when enabling notifications
 *    (the missing step that caused zero notifications in RythmOfLife's VeePoo SDK).
 *  - Bridges GATT callbacks into coroutine-friendly suspend functions and Flows.
 *
 * Used by every driver; drivers never touch `BluetoothGatt` directly.
 */
class BleConnection(
    private val context: Context,
    val address: String,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val notificationChannel = Channel<NotificationEvent>(Channel.UNLIMITED)

    /** Cold-ish flow of all GATT characteristic notifications from this device. */
    val notifications: Flow<NotificationEvent> = notificationChannel.receiveAsFlow()

    // Single mutex — all GATT ops (connect, discover, write, enableNotify) are serialised.
    private val mutex = Mutex()

    // Pending completions — only one is ever "in flight" thanks to the mutex.
    private var pendingConnect = CompletableDeferred<Unit>()
    private var pendingDiscover = CompletableDeferred<Unit>()
    private var pendingWrite = CompletableDeferred<Unit>()

    @Volatile private var gatt: BluetoothGatt? = null

    // ──────────────────────────── GATT callback ────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        _state.value = ConnectionState.Connected
                        pendingConnect.complete(Unit)
                    } else {
                        _state.value = ConnectionState.Error
                        pendingConnect.completeExceptionally(
                            Exception("Connection failed, GATT status $status")
                        )
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = ConnectionState.Disconnected
                    pendingConnect.completeExceptionally(Exception("Disconnected, status $status"))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingDiscover.complete(Unit)
            } else {
                pendingDiscover.completeExceptionally(
                    Exception("Service discovery failed, status $status")
                )
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) pendingWrite.complete(Unit)
            else pendingWrite.completeExceptionally(Exception("Descriptor write failed, status $status"))
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) pendingWrite.complete(Unit)
            else pendingWrite.completeExceptionally(Exception("Characteristic write failed, status $status"))
        }

        // Called on API 31–32
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            scope.launch { notificationChannel.send(NotificationEvent(characteristic.uuid, value)) }
        }

        // Called on API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            scope.launch { notificationChannel.send(NotificationEvent(characteristic.uuid, value)) }
        }
    }

    // ──────────────────────────── Public API ────────────────────────────

    /**
     * Connect to the device and discover its GATT services.
     * Holds the mutex for the full duration (connect + service discovery).
     */
    suspend fun connect(timeoutMs: Long = 12_000L) = mutex.withLock {
        _state.value = ConnectionState.Connecting
        pendingConnect = CompletableDeferred()

        val adapter = context.getSystemService(BluetoothManager::class.java)!!.adapter
        val device = adapter.getRemoteDevice(address)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        withTimeout(timeoutMs) { pendingConnect.await() }

        // Immediately discover services after connection.
        pendingDiscover = CompletableDeferred()
        gatt!!.discoverServices()
        withTimeout(timeoutMs) { pendingDiscover.await() }
    }

    /**
     * Enable GATT notifications (or indications) on a characteristic and
     * write the mandatory CCCD 0x2902 descriptor — the step that was missing
     * from the VeePoo SDK and caused zero notifications in RythmOfLife.
     */
    suspend fun enableNotifications(
        serviceUuid: UUID,
        charUuid: UUID,
        timeoutMs: Long = 5_000L,
    ) = mutex.withLock {
        val g = gatt ?: error("Not connected")
        val service = g.getService(serviceUuid) ?: error("Service $serviceUuid not found")
        val char = service.getCharacteristic(charUuid) ?: error("Characteristic $charUuid not found")

        g.setCharacteristicNotification(char, true)

        val cccd = char.getDescriptor(CCCD_UUID) ?: error("CCCD descriptor not found on $charUuid")

        // Pick the right CCCD value based on supported properties.
        val cccdValue = when {
            char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        pendingWrite = CompletableDeferred()
        @Suppress("DEPRECATION") cccd.value = cccdValue
        @Suppress("DEPRECATION") g.writeDescriptor(cccd)
        withTimeout(timeoutMs) { pendingWrite.await() }
    }

    /**
     * Write [data] to a characteristic. Prefers WRITE (with response) if available,
     * falls back to WRITE_NO_RESPONSE with a short delay.
     */
    suspend fun write(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        timeoutMs: Long = 5_000L,
    ) = mutex.withLock {
        val g = gatt ?: error("Not connected")
        val service = g.getService(serviceUuid) ?: error("Service $serviceUuid not found")
        val char = service.getCharacteristic(charUuid) ?: error("Characteristic $charUuid not found")

        val supportsWrite = char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val supportsNoResp = char.properties and
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0

        if (supportsWrite || !supportsNoResp) {
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            pendingWrite = CompletableDeferred()
            @Suppress("DEPRECATION") char.value = data
            @Suppress("DEPRECATION") g.writeCharacteristic(char)
            withTimeout(timeoutMs) { pendingWrite.await() }
        } else {
            // WRITE_NO_RESPONSE — no callback, just write and add a small guard delay.
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION") char.value = data
            @Suppress("DEPRECATION") g.writeCharacteristic(char)
            kotlinx.coroutines.delay(50)
        }
    }

    /** Gracefully close the connection. */
    suspend fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.Disconnected
        notificationChannel.close()
    }

    companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

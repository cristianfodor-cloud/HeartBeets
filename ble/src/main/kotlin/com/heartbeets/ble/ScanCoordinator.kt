package com.heartbeets.ble

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.BluetoothManager
import android.content.Context
import com.heartbeets.core.BleScanResult
import com.heartbeets.core.DeviceRegistry
import com.heartbeets.core.DiscoveredDevice
import com.heartbeets.core.Match
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps [android.bluetooth.le.BluetoothLeScanner] and emits [DiscoveredDevice]s.
 *
 * For each scan result, the registry is consulted to find the best-matching factory.
 * Devices without a name and without a matching factory are silently skipped to
 * avoid cluttering the UI with phantom BLE peripherals.
 *
 * The returned Flow is cold: scanning starts when collected and stops when the
 * collector is cancelled.
 *
 * Requires [android.Manifest.permission.BLUETOOTH_SCAN] to be granted before calling.
 */
class ScanCoordinator(private val context: Context) {

    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IllegalStateException("Bluetooth adapter not available")

        val bleScanner = adapter.bluetoothLeScanner
            ?: throw IllegalStateException("BLE scanner not available — Bluetooth may be off")

        // Keep track of what we've already emitted so we only re-emit on RSSI/state change.
        val seen = mutableMapOf<String, DiscoveredDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                @Suppress("DEPRECATION")
                val name = result.device.name

                val uuids = result.scanRecord?.serviceUuids
                    ?.map { it.uuid }
                    ?.toSet() ?: emptySet()

                val bleScan = BleScanResult(
                    deviceAddress = address,
                    deviceName = name,
                    serviceUuids = uuids,
                    rssi = result.rssi,
                    rawRecord = result.scanRecord?.bytes,
                )

                val match = DeviceRegistry.bestMatchFor(bleScan)

                // Skip unnamed devices that no factory recognises.
                if (name == null && match == null) return

                val discovered = DiscoveredDevice(
                    address = address,
                    name = name,
                    factory = match?.first,
                    confidence = match?.second ?: Match.NO,
                    rssi = result.rssi,
                )

                if (seen[address] != discovered) {
                    seen[address] = discovered
                    trySend(discovered)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("BLE scan failed with error $errorCode"))
            }
        }

        bleScanner.startScan(callback)

        awaitClose { bleScanner.stopScan(callback) }
    }
}

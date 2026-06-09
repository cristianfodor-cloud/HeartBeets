package com.heartbeets.core

/**
 * A platform-agnostic projection of a BLE scan result.
 *
 * Built by [ScanCoordinator] from the Android [android.bluetooth.le.ScanResult] and
 * passed to [HrDriverFactory.matches] so that factories in the [core] module don't
 * pull in Android API types.
 */
data class BleScanResult(
    val deviceAddress: String,
    val deviceName: String?,
    val serviceUuids: Set<java.util.UUID>,
    val rssi: Int,
    val rawRecord: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleScanResult) return false
        return deviceAddress == other.deviceAddress &&
                deviceName == other.deviceName &&
                serviceUuids == other.serviceUuids &&
                rssi == other.rssi &&
                java.util.Arrays.equals(rawRecord, other.rawRecord)
    }

    override fun hashCode(): Int {
        var result = deviceAddress.hashCode()
        result = 31 * result + (deviceName?.hashCode() ?: 0)
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result + rssi
        result = 31 * result + java.util.Arrays.hashCode(rawRecord)
        return result
    }
}

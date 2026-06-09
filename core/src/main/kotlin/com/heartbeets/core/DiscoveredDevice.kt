package com.heartbeets.core

/**
 * A BLE device found during scanning, annotated with the best-matching factory.
 *
 * Emitted by [ScanCoordinator] and displayed in the scan screen.
 */
data class DiscoveredDevice(
    val address: String,
    val name: String?,
    /** Best-matching factory, or null if no factory claimed the device. */
    val factory: HrDriverFactory?,
    /** How confident the factory match is. [Match.NO] when factory is null. */
    val confidence: Match,
    val rssi: Int,
) {
    /** True when a driver is available for this device. */
    val isSupported: Boolean get() = factory != null
}

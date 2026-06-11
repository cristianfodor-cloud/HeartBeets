package com.heartbeets.driver.galaxy

import java.util.UUID

/**
 * Constants for Samsung Galaxy Watch / Galaxy Fit / Galaxy Ring devices.
 *
 * Samsung devices expose standard BLE Heart Rate Service (0x180D) when HR broadcasting
 * is enabled in Samsung Health. This driver provides EXACT matching on Samsung device
 * names to claim them before the generic StandardHrsDriver (which only matches LIKELY).
 *
 * Additionally, some Galaxy devices support a proprietary "Health Sensor" service
 * that can be used to request HR streaming start/stop.
 */
object GalaxyProfile {

    // ── Standard HRS (reused from StandardHrsDriver) ──
    val SERVICE_HEART_RATE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val CHAR_HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CHAR_HR_CONTROL_POINT: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")

    // ── Samsung proprietary "Health Sensor" service (Galaxy Watch 4+) ──
    val SERVICE_SAMSUNG_HEALTH: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")

    // ── HR Control Point commands ──
    val CMD_START_CONTINUOUS_HR = byteArrayOf(0x15, 0x01, 0x01)
    val CMD_STOP_CONTINUOUS_HR = byteArrayOf(0x15, 0x01, 0x00)

    // ── Known Samsung device name patterns (lowercase) ──
    val KNOWN_NAME_PREFIXES = listOf(
        "galaxy watch",
        "galaxy fit",
        "galaxy ring",
        "gear s", "gear fit", "gear sport",
        "sm-r", // Samsung model numbers (SM-R8xx, SM-R9xx, etc.)
    )

    // Some Samsung devices advertise these names
    val KNOWN_NAME_CONTAINS = listOf(
        "samsung",
    )
}

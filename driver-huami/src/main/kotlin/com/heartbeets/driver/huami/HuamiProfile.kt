package com.heartbeets.driver.huami

import java.util.UUID

/**
 * BLE service/characteristic UUIDs and constants for the Huami (Xiaomi/Amazfit) protocol.
 *
 * Covers Mi Band 2–8, Amazfit Bip/GTS/GTR, Xiaomi Band series.
 *
 * Authentication flow uses AES/ECB encryption on a 16-byte auth key.
 * After auth, HR data flows on the standard BT SIG Heart Rate Measurement (0x2A37).
 */
object HuamiProfile {

    // ──────────────── Services ────────────────

    /** MiBand main proprietary service. */
    val SERVICE_MIBAND: UUID = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")

    /** MiBand2 authentication service. */
    val SERVICE_MIBAND_AUTH: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")

    /** Standard Heart Rate Service (also used by Huami for HR data after auth). */
    val SERVICE_HEART_RATE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    // ──────────────── Characteristics ────────────────

    /** Auth characteristic (notify + write) — under SERVICE_MIBAND_AUTH (FEE1). */
    val CHAR_AUTH: UUID = UUID.fromString("00000009-0000-3512-2118-0009af100700")

    /** HR Measurement characteristic (standard 0x2A37, notify). */
    val CHAR_HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    /** HR Control Point (standard 0x2A39, write) — used to start/stop continuous HR. */
    val CHAR_HR_CONTROL_POINT: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")

    /** Battery info characteristic — proprietary format under FEE0. */
    val CHAR_BATTERY: UUID = UUID.fromString("00000006-0000-3512-2118-0009af100700")

    // ──────────────── Auth protocol bytes ────────────────

    /** Step 1: Send key — prefix before the 16-byte key. */
    const val AUTH_SEND_KEY: Byte = 0x01

    /** Step 2: Request random number from band. */
    const val AUTH_REQUEST_RANDOM: Byte = 0x02

    /** Step 3: Send encrypted random number back. */
    const val AUTH_SEND_ENCRYPTED: Byte = 0x03

    /** Response prefix from band (byte 0 of all auth responses). */
    const val AUTH_RESPONSE: Byte = 0x10

    /** Success indicator (byte 2 in response). */
    const val AUTH_SUCCESS: Byte = 0x01

    /** Failure indicator (byte 2 in response). */
    const val AUTH_FAIL: Byte = 0x04

    /** Auth flag byte used in most Huami devices. */
    const val AUTH_FLAG: Byte = 0x08

    // ──────────────── HR commands ────────────────

    /** Start continuous heart rate measurement (write to HR Control Point). */
    val CMD_START_CONTINUOUS_HR: ByteArray = byteArrayOf(0x15, 0x01, 0x01)

    /** Stop continuous heart rate measurement. */
    val CMD_STOP_CONTINUOUS_HR: ByteArray = byteArrayOf(0x15, 0x01, 0x00)

    /** Stop manual HR measurement (needed before starting continuous). */
    val CMD_STOP_MANUAL_HR: ByteArray = byteArrayOf(0x15, 0x02.toByte(), 0x00)

    /** Enable HR sleep measurement (enables background HR). */
    val CMD_ENABLE_HR_SLEEP: ByteArray = byteArrayOf(0x15, 0x00, 0x01)

    /** Set HR measurement interval to 1 minute (most frequent automatic mode). */
    val CMD_HR_INTERVAL_1MIN: ByteArray = byteArrayOf(0x14, 0x01)

    // ──────────────── Default auth key ────────────────

    /**
     * Default 16-byte auth key used by most Huami devices when no custom key is set.
     * This is the "0123456789@ABCDE" key from Gadgetbridge.
     * Users who have set a custom key in their band's settings will need to override this.
     */
    val DEFAULT_AUTH_KEY: ByteArray = byteArrayOf(
        0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
        0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45,
    )

    // ──────────────── Device name patterns ────────────────

    /** Known Mi Band / Amazfit name prefixes (case-insensitive matching). */
    val KNOWN_NAME_PREFIXES = listOf(
        "mi band",
        "mi smart band",
        "amazfit",
        "xiaomi band",
        "miband",
        "haylou",
    )
}

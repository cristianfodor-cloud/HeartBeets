package com.heartbeets.driver.standardhrs

import java.util.UUID

/**
 * Bluetooth SIG Heart Rate Service (HRS) constants.
 *
 * Specification: GATT Assigned Numbers / Heart Rate Service 1.0
 * https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.heart_rate.xml
 */
object HrsProfile {

    /** Heart Rate Service UUID */
    val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    /** Heart Rate Measurement characteristic (notify) */
    val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    /** Battery Service UUID — used to read battery level from HRS devices. */
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

    /** Battery Level characteristic (read/notify, uint8 0–100). */
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    /** Heart Rate Control Point characteristic (write) — used to start continuous HR on some devices. */
    val HR_CONTROL_POINT: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")

    /** Command to start continuous heart rate measurement. */
    val CMD_START_CONTINUOUS_HR: ByteArray = byteArrayOf(0x15, 0x01, 0x01)

    /** Command to stop continuous heart rate measurement. */
    val CMD_STOP_CONTINUOUS_HR: ByteArray = byteArrayOf(0x15, 0x01, 0x00)

    // ──────────── Proprietary 0xFF00 service (cheap Chinese bracelets) ────────────

    /** Proprietary service used by many budget fitness bands (S18, M5, Y68, D18, etc.) */
    val PROPRIETARY_FF00_SERVICE: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")

    /** Proprietary command/notify characteristic — write to start HR, notifications contain data. */
    val PROPRIETARY_FF01_CHAR: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

    /** Proprietary settings/auth characteristic — some devices need a write here before HR works. */
    val PROPRIETARY_FF02_CHAR: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

    /** Proprietary command characteristic (alternate) — used by some DaFit firmware variants. */
    val PROPRIETARY_FF03_CHAR: UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb")

    /**
     * Bind/pair command for the AB protocol.
     * Captured from BR Fit ↔ S18 snoop: AB 01 00 07 CE BC 03 01 20 76 00 31 00
     * Must be sent before start-HR; response arrives on FF03.
     */
    val PROPRIETARY_BIND_CMD: ByteArray = byteArrayOf(
        0xAB.toByte(), 0x01, 0x00, 0x07,
        0xCE.toByte(), 0xBC.toByte(),           // CRC
        0x03, 0x01, 0x20, 0x76, 0x00, 0x31, 0x00,
    )

    /**
     * Start real-time HR measurement command (AB protocol).
     * Captured from BR Fit ↔ S18 snoop: AB 01 00 06 F7 EE 06 09 00 03 05 01
     * Payload command 06 09 = "start real-time measurement".
     */
    val PROPRIETARY_START_HR_CMD: ByteArray = byteArrayOf(
        0xAB.toByte(), 0x01, 0x00, 0x06,
        0xF7.toByte(), 0xEE.toByte(),           // CRC
        0x06, 0x09, 0x00, 0x03, 0x05, 0x01,
    )

    /**
     * Keep-alive / continue HR measurement command (AB protocol).
     * Captured from BR Fit ↔ S18 snoop: AB 11 00 04 8A D0 06 09 00 00
     * Lighter than the full start command — keeps measurement running without restarting.
     */
    val PROPRIETARY_KEEPALIVE_HR_CMD: ByteArray = byteArrayOf(
        0xAB.toByte(), 0x11, 0x00, 0x04,
        0x8A.toByte(), 0xD0.toByte(),           // CRC
        0x06, 0x09, 0x00, 0x00,
    )

    // ──────────── Proprietary 0xB00B service (JStyle / cheap bracelets) ────────────

    /** JStyle proprietary service used by S18, D18, M5 and similar bands. */
    val PROPRIETARY_B00B_SERVICE: UUID = UUID.fromString("0000b00b-0000-1000-8000-00805f9b34fb")

    /** JStyle write characteristic — send commands here. */
    val PROPRIETARY_B002_CHAR: UUID = UUID.fromString("0000b002-0000-1000-8000-00805f9b34fb")

    /** JStyle notify characteristic — HR data comes back here. */
    val PROPRIETARY_B003_CHAR: UUID = UUID.fromString("0000b003-0000-1000-8000-00805f9b34fb")

    /**
     * Commands to start real-time HR on 0xB00B service devices.
     * Uses the same AB protocol as FF00.
     */
    val PROPRIETARY_B00B_START_HR_COMMANDS: List<ByteArray> = listOf(
        PROPRIETARY_BIND_CMD,
        PROPRIETARY_START_HR_CMD,
    )

    // ──────────── Heart Rate Measurement flags byte ────────────

    /** Bit 0 of flags: 0 = BPM is uint8, 1 = BPM is uint16. */
    private const val FLAG_VALUE_FORMAT_UINT16 = 0x01

    /** Bit 1 of flags: sensor contact status supported and detected. */
    private const val FLAG_SENSOR_CONTACT_DETECTED = 0x06 // bits 1+2 both set

    /** Bit 4 of flags: R-R intervals present. */
    private const val FLAG_RR_INTERVAL_PRESENT = 0x10

    /**
     * Parse a raw Heart Rate Measurement characteristic value.
     *
     * Byte layout (GATT spec):
     * ```
     *  Byte 0:  flags
     *  Byte 1:  BPM (uint8)  OR
     *  Bytes 1-2: BPM (uint16 little-endian) when bit 0 of flags is 1
     *  Optional: Energy Expended (uint16 LE) when bit 3 of flags is 1
     *  Optional: R-R interval values (uint16 LE each, 1/1024 seconds) when bit 4 set
     * ```
     */
    data class HrMeasurement(
        val bpm: Int,
        val contactDetected: Boolean?,
        /** R-R intervals converted from 1/1024-second units to milliseconds. */
        val rrIntervalsMs: IntArray?,
    )

    fun parse(bytes: ByteArray): HrMeasurement? {
        if (bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF

        val bpmUint16 = (flags and FLAG_VALUE_FORMAT_UINT16) != 0
        var offset = 1

        val bpm: Int
        if (bpmUint16) {
            if (bytes.size < 3) return null
            bpm = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
            offset = 3
        } else {
            if (bytes.size < 2) return null
            bpm = bytes[1].toInt() and 0xFF
            offset = 2
        }

        val contactDetected: Boolean? = when (flags and FLAG_SENSOR_CONTACT_DETECTED) {
            FLAG_SENSOR_CONTACT_DETECTED -> true
            0x02 -> false   // supported but not detected
            else -> null    // not supported
        }

        // Skip Energy Expended (bit 3) if present.
        val energyPresent = (flags and 0x08) != 0
        if (energyPresent) offset += 2

        // R-R intervals — each is uint16 LE in units of 1/1024 s.
        val rrPresent = (flags and FLAG_RR_INTERVAL_PRESENT) != 0
        val rrMs: IntArray? = if (rrPresent && offset < bytes.size) {
            val count = (bytes.size - offset) / 2
            IntArray(count) { i ->
                val raw = (bytes[offset + i * 2].toInt() and 0xFF) or
                        ((bytes[offset + i * 2 + 1].toInt() and 0xFF) shl 8)
                (raw * 1000) / 1024
            }
        } else null

        return HrMeasurement(bpm, contactDetected, rrMs)
    }
}

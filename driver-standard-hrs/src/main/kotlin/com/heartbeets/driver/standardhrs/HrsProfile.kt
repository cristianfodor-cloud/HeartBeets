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

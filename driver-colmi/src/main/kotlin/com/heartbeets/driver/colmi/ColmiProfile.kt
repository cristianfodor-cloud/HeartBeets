package com.heartbeets.driver.colmi

import java.util.UUID

/**
 * BLE service/characteristic UUIDs and protocol constants for Colmi R0x smart rings
 * and P-series bands.
 *
 * Protocol: Fixed 16-byte packets. Byte[0] = command, Byte[15] = checksum (sum of bytes 0–14 & 0xFF).
 * No authentication required — just connect, enable notifications, and send commands.
 */
object ColmiProfile {

    // ── GATT services ──
    val SERVICE_V1: UUID = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")
    val SERVICE_V2: UUID = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7")

    // ── Characteristics ──
    val CHAR_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val CHAR_NOTIFY_V1: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    val CHAR_COMMAND: UUID = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7")
    val CHAR_NOTIFY_V2: UUID = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")

    // ── Commands ──
    const val CMD_SET_DATE_TIME: Byte = 0x01
    const val CMD_BATTERY: Byte = 0x03
    const val CMD_PHONE_NAME: Byte = 0x04
    const val CMD_PREFERENCES: Byte = 0x0A
    const val CMD_SYNC_HEART_RATE: Byte = 0x15
    const val CMD_AUTO_HR_PREF: Byte = 0x16
    const val CMD_MANUAL_HEART_RATE: Byte = 0x69
    const val CMD_NOTIFICATION: Byte = 0x73

    // Sub-types for CMD_NOTIFICATION
    const val NOTIFICATION_BATTERY_LEVEL: Byte = 0x0C
    const val NOTIFICATION_LIVE_ACTIVITY: Byte = 0x12

    // Preference actions
    const val PREF_READ: Byte = 0x01
    const val PREF_WRITE: Byte = 0x02

    // ── Known device name patterns ──
    val KNOWN_NAME_PREFIXES = listOf(
        "colmi r0", "colmi r1", "colmi p",
        "r02", "r03", "r06", "r10",
        "p8", "p9", "p10",
        "ym_r0", "ym_r1",
        "hk ring",
    )

    /**
     * Build a standard 16-byte Colmi packet.
     * Content is placed at the beginning; the last byte is a checksum
     * (sum of all content bytes & 0xFF).
     */
    fun buildPacket(vararg contents: Byte): ByteArray {
        val packet = ByteArray(16)
        contents.copyInto(packet, 0, 0, minOf(contents.size, 15))
        var checksum = 0
        for (i in 0 until 15) {
            checksum = (checksum + packet[i]) and 0xFF
        }
        packet[15] = checksum.toByte()
        return packet
    }
}

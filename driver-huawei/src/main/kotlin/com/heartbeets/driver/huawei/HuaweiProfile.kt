package com.heartbeets.driver.huawei

import java.util.UUID

/**
 * BLE UUIDs and protocol constants for Huawei/Honor wearables.
 *
 * Protocol: TLV-based packets over a single service (FE86).
 * - Write to FE01, read/notify from FE02.
 * - Each packet starts with magic 0x5A, followed by length, serviceId, commandId, then TLV payload.
 * - Heart rate data uses serviceId=0x07 (FitnessData), various commandIds.
 */
object HuaweiProfile {

    // ── GATT service and characteristics ──
    val SERVICE_HUAWEI: UUID = UUID.fromString("0000fe86-0000-1000-8000-00805f9b34fb")
    val CHAR_WRITE: UUID = UUID.fromString("0000fe01-0000-1000-8000-00805f9b34fb")
    val CHAR_READ: UUID = UUID.fromString("0000fe02-0000-1000-8000-00805f9b34fb")

    // ── Protocol constants ──
    const val MAGIC: Byte = 0x5A
    const val PROTOCOL_VERSION: Byte = 0x02

    // ── Service IDs ──
    const val SERVICE_ID_DEVICE: Byte = 0x01
    const val SERVICE_ID_FITNESS: Byte = 0x07

    // ── Command IDs (FITNESS service) ──
    const val CMD_ENABLE_AUTO_HR: Byte = 0x17
    const val CMD_ENABLE_REALTIME_HR: Byte = 0x23

    // ── Command IDs (DEVICE service) ──
    const val CMD_LINK_PARAMS: Byte = 0x01
    const val CMD_BATTERY: Byte = 0x02
    const val CMD_TIME: Byte = 0x05

    // ── TLV result tag ──
    const val TAG_RESULT: Byte = 0x7F

    // ── Known device name prefixes (lowercase, from Gadgetbridge HuaweiConstants) ──
    val KNOWN_NAME_PREFIXES = listOf(
        "honor band 3", "honor band 4", "honor band 5",
        "honor band 6", "honor band 7",
        "honor magicwatch", "honor watch",
        "huawei band 2", "huawei band 3", "huawei band 3e",
        "huawei band 4", "huawei band 4e", "huawei band 4 pro",
        "huawei band 6", "huawei band 7", "huawei band 8", "huawei band 9",
        "huawei watch gt", "huawei watch gt 2", "huawei watch gt 3",
        "huawei watch gt 4", "huawei watch gt 5",
        "huawei watch fit", "huawei watch 3", "huawei watch 4",
        "huawei watch d", "huawei watch ultimate",
        "huawei b6",
    )

    /**
     * Build a simplified Huawei TLV packet for enabling real-time HR.
     *
     * Packet structure:
     * [MAGIC] [len_hi] [len_lo] [protocol_ver] [serviceId] [commandId] [TLV payload...] [checksum_hi] [checksum_lo]
     *
     * For our HR use-case, we send minimal commands. Full Huawei protocol uses
     * encryption/auth for some features, but basic HR monitoring works without it
     * on bonded devices.
     */
    fun buildPacket(serviceId: Byte, commandId: Byte, tlvPayload: ByteArray): ByteArray {
        // Content = [protocolVersion, serviceId, commandId, ...tlvPayload]
        val content = ByteArray(3 + tlvPayload.size)
        content[0] = PROTOCOL_VERSION
        content[1] = serviceId
        content[2] = commandId
        tlvPayload.copyInto(content, 3)

        // Total packet = [MAGIC, lenHi, lenLo, ...content, checksumHi, checksumLo]
        val packetLen = content.size + 2  // +2 for the checksum bytes themselves
        val packet = ByteArray(3 + content.size + 2)
        packet[0] = MAGIC
        packet[1] = ((packetLen shr 8) and 0xFF).toByte()
        packet[2] = (packetLen and 0xFF).toByte()
        content.copyInto(packet, 3)

        // CRC16/CCITT-FALSE checksum over content portion
        val crc = crc16(content)
        packet[packet.size - 2] = ((crc shr 8) and 0xFF).toByte()
        packet[packet.size - 1] = (crc and 0xFF).toByte()

        return packet
    }

    /**
     * Build a simple TLV: [tag, length, ...value]
     */
    fun tlv(tag: Byte, value: ByteArray): ByteArray {
        val result = ByteArray(2 + value.size)
        result[0] = tag
        result[1] = value.size.toByte()
        value.copyInto(result, 2)
        return result
    }

    fun tlvBool(tag: Byte, value: Boolean): ByteArray {
        return tlv(tag, byteArrayOf(if (value) 0x01 else 0x00))
    }

    /**
     * CRC16/CCITT-FALSE used by Huawei protocol.
     */
    private fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}

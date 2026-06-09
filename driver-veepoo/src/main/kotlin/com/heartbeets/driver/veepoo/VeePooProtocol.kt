package com.heartbeets.driver.veepoo

import java.util.UUID

/**
 * Clean-room implementation of the VeePoo BLE protocol.
 *
 * Protocol reverse-engineered via btsnoop analysis of the vendor app (documented in
 * RythmOfLife/HEART_RATE_CONTROL_STATUS.md). No vendor SDK is used here; all
 * communication goes through native Android [android.bluetooth.BluetoothGatt] via
 * [com.heartbeets.ble.BleConnection].
 *
 * The device speaks over **Nordic UART Service (NUS)**:
 *   - RX characteristic (write to device): [NUS_RX]
 *   - TX characteristic (notifications from device): [NUS_TX]
 *
 * The CCCD write that was missing from the VeePoo SDK (and caused zero notifications
 * in RythmOfLife) is handled correctly by [com.heartbeets.ble.BleConnection.enableNotifications].
 */
object VeePooProtocol {

    // ──────────── NUS Service UUIDs ────────────

    val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** Write commands to this characteristic (device ← phone). */
    val NUS_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    /** Receive notifications from this characteristic (device → phone). */
    val NUS_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    // ──────────── Command builders ────────────

    /**
     * Authentication / password handshake.
     * Format: A1 00 00 00 07 EA [timestamp 4B big-endian] 00 01 08 00 00 00 00 00
     */
    fun authCommand(timestampSec: Long): ByteArray {
        val ts = timestampSec.toInt()
        return byteArrayOf(
            0xA1.toByte(), 0x00, 0x00, 0x00, 0x07, 0xEA.toByte(),
            (ts ushr 24).toByte(), (ts ushr 16).toByte(), (ts ushr 8).toByte(), ts.toByte(),
            0x00, 0x01, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }

    /** Query device status.  Command: D8 00 */
    fun queryStatus(): ByteArray = byteArrayOf(0xD8.toByte(), 0x00)

    /**
     * Configuration / init command.
     * Format: F4 02 02 00 01 + zero padding to 20 bytes.
     */
    fun configure(): ByteArray = ByteArray(20).also { buf ->
        buf[0] = 0xF4.toByte()
        buf[1] = 0x02
        buf[2] = 0x02
        buf[3] = 0x00
        buf[4] = 0x01
    }

    /** Query battery level.  Command: A0 00 */
    fun queryBattery(): ByteArray = byteArrayOf(0xA0.toByte(), 0x00)

    /** Start continuous heart-rate measurement.  Command: D0 01 */
    fun startHr(): ByteArray = byteArrayOf(0xD0.toByte(), 0x01)

    /** Stop heart-rate measurement.  Command: D0 00 */
    fun stopHr(): ByteArray = byteArrayOf(0xD0.toByte(), 0x00)

    // ──────────── Response parsing ────────────

    sealed interface Response {
        /** Heart-rate reading; [bpm] is 0 when the device hasn't settled on a value yet. */
        data class HeartRate(val bpm: Int) : Response

        /** Battery charge level 0–100. */
        data class Battery(val percent: Int) : Response

        /** Response to the A1 auth command. [ok] is true when the device accepts. */
        data class AuthResult(val ok: Boolean) : Response

        /** Any response byte we don't handle yet. */
        data object Unknown : Response
    }

    /**
     * Parse a raw notification byte array from [NUS_TX].
     *
     * Response bytes are keyed by the first byte (command echo):
     *   D0 XX  → HeartRate(XX)
     *   A7 00  → AuthResult(ok=true)
     *   A0 XX  → Battery(XX)
     *   anything else → Unknown
     */
    fun parse(bytes: ByteArray): Response {
        if (bytes.isEmpty()) return Response.Unknown
        return when (bytes[0].toInt() and 0xFF) {
            0xD0 -> if (bytes.size >= 2) Response.HeartRate(bytes[1].toInt() and 0xFF)
                    else Response.Unknown

            0xA7 -> Response.AuthResult(
                ok = bytes.size < 2 || (bytes[1].toInt() and 0xFF) == 0
            )

            0xA0 -> if (bytes.size >= 2) Response.Battery(bytes[1].toInt() and 0xFF)
                    else Response.Unknown

            else -> Response.Unknown
        }
    }
}

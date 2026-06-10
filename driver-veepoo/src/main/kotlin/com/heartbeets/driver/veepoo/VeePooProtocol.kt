package com.heartbeets.driver.veepoo

import java.util.UUID

/**
 * BLE protocol constants for VeePoo/HBand family devices.
 *
 * H59 bracelet uses NUS (Nordic UART Service).
 * ET585 smartwatch uses a different proprietary service (AE40 / "Da Fit" style).
 *
 * Both are detected at runtime in [VeePooDriver.connect] by inspecting discovered services.
 */
object VeePooProtocol {

    // ──────────── H59 path: NUS service ────────────

    val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_RX: UUID     = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_TX: UUID     = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    // ──────────── ET585 / HBand main protocol path ────────────
    // Confirmed from btsnoop analysis: all commands go to f0080003 (WRITE+WRITE_NR),
    // all watch responses (A7 auth, BD03 push, D0 live HR, etc.) come on f0080002 (NOTIFY).
    // The presence of ae40 service is used as a discriminator flag only.
    // FEE7/FEA1/FEA2 are NOT the command channel — they carry unsolicited status packets.
    val AE40_SERVICE: UUID  = UUID.fromString("0000ae40-0000-1000-8000-00805f9b34fb") // detect flag only
    val F008_SERVICE: UUID  = UUID.fromString("f0080001-0451-4000-b000-000000000000") // main protocol
    val F008_NOTIFY: UUID   = UUID.fromString("f0080002-0451-4000-b000-000000000000") // watch→phone [NOTIFY]
    val F008_WRITE: UUID    = UUID.fromString("f0080003-0451-4000-b000-000000000000") // phone→watch [WRITE+WRITE_NR]

    // ──────────── ET585: FEE7 service UUID (scan-record matching only) ────────────
    // ET585 advertises FEE7 in BLE scan records. Used by VeePooDriverFactory.matches() only.
    // FEE7 is NOT the protocol channel — all protocol traffic uses f0080001/f0080002/f0080003.
    val FEE7_SERVICE: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb")

    // ──────────── NUS command builders (H59) ────────────

    /**
     * VeePoo auth/password command for ET585 (FEE7 path).
     * Exact format captured from RythmOfLife hband_ble_write.txt:
     *   A1 00 00 00 [year_hi] [year_lo] [month] [day] [hour] [min] [sec] 00 01 08 00 00 00 00 00 00
     * Example: A1,00,00,00,07,EA,01,16,17,31,36,00,01,08,00,00,00,00,00,00 (2026-01-22 23:49:54)
     */
    fun authCommand(): ByteArray {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        return byteArrayOf(
            0xA1.toByte(), 0x00, 0x00, 0x00,
            (year ushr 8).toByte(), (year and 0xFF).toByte(),
            (cal.get(java.util.Calendar.MONTH) + 1).toByte(),
            cal.get(java.util.Calendar.DAY_OF_MONTH).toByte(),
            cal.get(java.util.Calendar.HOUR_OF_DAY).toByte(),
            cal.get(java.util.Calendar.MINUTE).toByte(),
            cal.get(java.util.Calendar.SECOND).toByte(),
            0x00, 0x01, 0x08,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }

    /** NUS auth for H59 (timestamp-based, 18 bytes). */
    fun authCommandNus(timestampSec: Long): ByteArray {
        val ts = timestampSec.toInt()
        return byteArrayOf(
            0xA1.toByte(), 0x00, 0x00, 0x00, 0x07, 0xEA.toByte(),
            (ts ushr 24).toByte(), (ts ushr 16).toByte(), (ts ushr 8).toByte(), ts.toByte(),
            0x00, 0x01, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }
    fun queryStatus():  ByteArray = byteArrayOf(0xD8.toByte(), 0x00)
    fun configure():    ByteArray = ByteArray(20).also { it[0]=0xF4.toByte(); it[1]=0x02; it[2]=0x02; it[3]=0x00; it[4]=0x01 }
    fun queryBattery(): ByteArray = byteArrayOf(0xA0.toByte(), 0x00)

    // ── ET585 post-auth init sequence (captured from RythmOfLife hband_ble_write.txt) ──
    // These must be sent after auth and before D0 01, or the watch won't start optical HR.

    /** A3: Person info — height/weight/gender/age. Required to calibrate optical HR sensor. */
    fun personInfo(): ByteArray = byteArrayOf(
        0xA3.toByte(), 0xC0.toByte(), 0x4B, 0x2B, 0x01, 0x2A, 0xF8.toByte(), 0x01, 0xE0.toByte(),
    )

    /** E1: Long-seat (sedentary reminder) settings. */
    fun longSeat(): ByteArray = byteArrayOf(
        0xE1.toByte(), 0x08, 0x00, 0x12, 0x00, 0x3C, 0x02,
    )

    /** AC: Heart-rate warning thresholds. */
    fun heartWarning(): ByteArray = byteArrayOf(
        0xAC.toByte(), 0x00, 0x00, 0x02,
    )

    /** AA: Night-turn (wrist-raise) settings. */
    fun nightTurn(): ByteArray = byteArrayOf(
        0xAA.toByte(), 0x02, 0x00, 0x00, 0x01, 0x01,
    )

    /** 91: Blood-pressure model. */
    fun bpModel(): ByteArray = ByteArray(20).also {
        it[0] = 0x91.toByte(); it[1] = 0x02; it[2] = 0x78; it[3] = 0x50
    }

    /** 85: Women menstrual setting (all zeros = disabled). */
    fun womenMense(): ByteArray = ByteArray(20).also { it[0] = 0x85.toByte() }

    /** B3: All-setting (general device config). */
    fun allSetting(): ByteArray = ByteArray(20).also {
        it[0] = 0xB3.toByte(); it[1] = 0x00; it[2] = 0x01; it[3] = 0x16; it[5] = 0x08
    }

    /** B1: Screen brightness. */
    fun screenLight(): ByteArray = ByteArray(20).also { it[0] = 0xB1.toByte(); it[1] = 0x02 }

    /** C8: Weather (blank). */
    fun weather(): ByteArray = ByteArray(20).also { it[0] = 0xC8.toByte(); it[1] = 0x02 }

    /** B4: Screen timeout. */
    fun screenTimeout(): ByteArray = ByteArray(20).also { it[0] = 0xB4.toByte(); it[1] = 0x02 }

    /** EF: Battery manager. */
    fun batteryManager(): ByteArray = ByteArray(20).also {
        it[0] = 0xEF.toByte(); it[1] = 0x01; it[2] = 0xFE.toByte()
    }

    // ── Settings phase: commands that follow batteryManager (from hband_ble_write.txt) ──

    /** AB: Alarm (no alarm configured). From btsnoop: AB 00 00 00 00 00 00 00 00 00 06 */
    fun alarm(): ByteArray = byteArrayOf(
        0xAB.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x06,
    )

    /** 50 02 02: Battery big-data transfer (mode 02). */
    fun batteryBigData02(): ByteArray = ByteArray(20).also {
        it[0] = 0x50; it[1] = 0x02; it[2] = 0x02
    }

    /** 50 02 01: Battery big-data transfer (mode 01). */
    fun batteryBigData01(): ByteArray = ByteArray(20).also {
        it[0] = 0x50; it[1] = 0x02; it[2] = 0x01
    }

    /** 72: Contact/phonebook operate. */
    fun contact(): ByteArray = ByteArray(20).also {
        it[0] = 0x72; it[1] = 0x02; it[2] = 0xFF.toByte(); it[3] = 0xFF.toByte()
    }

    /** 8A: Blood-composition (SpO2 etc) settings. */
    fun bloodComposition(): ByteArray = ByteArray(20).also {
        it[0] = 0x8A.toByte(); it[1] = 0x02; it[2] = 0x02; it[3] = 0x01
    }

    // ── Data-dump phase: trigger historical data transfer before D0 01 ──
    // Sent after settings. Watch streams historical data on FEA1. Each command may take 700-1400ms.
    // From btsnoop: D8, E0 00, E0 01, DF A1, D3 01, 96 02, 96 03, 93 05 01 → then D0 01.

    /** E0 00: Read sleep data (start). */
    fun readSleep00(): ByteArray = byteArrayOf(0xE0.toByte(), 0x00)

    /** E0 01: Read sleep data (mode 1 / next chunk). */
    fun readSleep01(): ByteArray = byteArrayOf(0xE0.toByte(), 0x01)

    /** DF A1: Head-original-DF / body temperature history. */
    fun headOrigalDf(): ByteArray = ByteArray(20).also {
        it[0] = 0xDF.toByte(); it[1] = 0xA1.toByte()
    }

    /** D3 01: Sport-model CRC query. */
    fun sportModelCrc(): ByteArray = ByteArray(20).also { it[0] = 0xD3.toByte(); it[1] = 0x01 }

    /** 96 02: ECG data get (type 02). */
    fun ecgDataGet02(): ByteArray = ByteArray(20).also { it[0] = 0x96.toByte(); it[1] = 0x02 }

    /** 96 03: ECG data get (type 03). */
    fun ecgDataGet03(): ByteArray = ByteArray(20).also { it[0] = 0x96.toByte(); it[1] = 0x03 }

    /** 93: Body component (body fat etc). Sent as last data-dump command. */
    fun bodyComponent(): ByteArray = ByteArray(20).also {
        it[0] = 0x93.toByte(); it[1] = 0x05; it[2] = 0x01
    }

    /** D0 01: Start continuous HR streaming. Watch streams D0 [bpm] on f0080002 every ~1s (15s warmup). */
    fun startHr(): ByteArray = ByteArray(20).also { it[0] = 0xD0.toByte(); it[1] = 0x01 }

    /** D0 00: Stop HR streaming. */
    fun stopHr(): ByteArray = ByteArray(20).also { it[0] = 0xD0.toByte(); it[1] = 0x00 }

    // ──────────── Response parsing ────────────

    sealed interface Response {
        data class HeartRate(val bpm: Int) : Response
        data class Battery(val percent: Int) : Response
        data class AuthResult(val ok: Boolean) : Response
        data object Unknown : Response
    }

    /** Parse a f0080002 notification from ET585, or NUS TX notification from H59. */
    fun parseNus(bytes: ByteArray): Response {
        if (bytes.isEmpty()) return Response.Unknown
        return when (bytes[0].toInt() and 0xFF) {
            // 0xD0 = live optical HR from D0 01 (startDetectHeart).
            // bytes[1]=0 during warmup (~15 packets) — ignore. Non-zero = real BPM.
            0xD0 -> {
                val bpm = if (bytes.size >= 2) bytes[1].toInt() and 0xFF else 0
                if (bpm in 30..250) Response.HeartRate(bpm) else Response.Unknown
            }
            0xA7 -> Response.AuthResult(ok = bytes.size < 2 || (bytes[1].toInt() and 0xFF) == 0)
            0xA1 -> Response.AuthResult(ok = bytes.size >= 12 && (bytes[11].toInt() and 0xFF) == 1)
            // Battery response: A0 00 00 00 [percent] [charging] [percent] [charging] ...
            // bytes[4] = battery %, confirmed from btsnoop: A0 00 00 00 50 01 50 01 → 80%
            0xA0 -> if (bytes.size >= 5) Response.Battery(bytes[4].toInt() and 0xFF) else Response.Unknown
            // 0x01 = background passive HR (watch's own monitoring, updates every ~30-60s).
            // NOT live optical. Ignore entirely — only 0xD0 gives real-time HR.
            0x01 -> Response.Unknown
            else -> Response.Unknown
        }
    }

}

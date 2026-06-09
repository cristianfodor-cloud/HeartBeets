package com.heartbeets.core

/**
 * A single heart-rate measurement emitted by an [HrDriver].
 *
 * @param bpm             Beats per minute as reported by the device.
 * @param timestamp       System.currentTimeMillis() when the sample was received.
 * @param rrIntervalsMs   R-R interval sequence in milliseconds (time between successive
 *                        R-peaks). Populated by Standard HRS devices when the RR flag is
 *                        set. VeePoo devices don't provide RR intervals.
 * @param energyExpendedKj Optional energy-expended value (Standard HRS feature).
 * @param contactDetected  Whether the device reports confirmed skin contact.
 * @param source           Identifies the driver + device that produced this sample.
 */
data class HrSample(
    val bpm: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val rrIntervalsMs: IntArray? = null,
    val energyExpendedKj: Int? = null,
    val contactDetected: Boolean? = null,
    val source: SourceTag,
) {
    // IntArray breaks data-class structural equality — override manually.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HrSample) return false
        return bpm == other.bpm &&
                timestamp == other.timestamp &&
                java.util.Arrays.equals(rrIntervalsMs, other.rrIntervalsMs) &&
                energyExpendedKj == other.energyExpendedKj &&
                contactDetected == other.contactDetected &&
                source == other.source
    }

    override fun hashCode(): Int {
        var result = bpm
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + java.util.Arrays.hashCode(rrIntervalsMs)
        result = 31 * result + (energyExpendedKj ?: 0)
        result = 31 * result + (contactDetected?.hashCode() ?: 0)
        result = 31 * result + source.hashCode()
        return result
    }
}

/** Identifies which driver and device address produced an [HrSample]. */
data class SourceTag(val driverId: String, val deviceAddress: String)

package com.heartbeets.driver.colmi

import android.content.Context
import com.heartbeets.core.BleScanResult
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrDriverFactory
import com.heartbeets.core.Match

/**
 * Recognises Colmi R0x smart rings and P-series fitness bands.
 *
 * **Match strategy:**
 * - [Match.EXACT] — device name starts with a known Colmi prefix OR advertises the
 *   proprietary V1 service UUID (6e40fff0-...).
 * - [Match.NO] — no evidence of Colmi device.
 *
 * No authentication is required for these devices.
 */
class ColmiDriverFactory(private val context: Context) : HrDriverFactory {

    override val id = "colmi"
    override val displayName = "Colmi Ring / Band"
    override val manufacturerHint = "Colmi, YM, HK"

    override fun matches(scan: BleScanResult): Match {
        val nameLower = scan.deviceName?.lowercase() ?: ""

        // Exact match on known Colmi name prefixes.
        if (ColmiProfile.KNOWN_NAME_PREFIXES.any { nameLower.startsWith(it) }) {
            return Match.EXACT
        }

        // Exact match on proprietary V1 service UUID.
        if (scan.serviceUuids.contains(ColmiProfile.SERVICE_V1)) {
            return Match.EXACT
        }

        return Match.NO
    }

    override fun create(address: String, name: String?): HrDriver =
        ColmiDriver(context, address, name)
}

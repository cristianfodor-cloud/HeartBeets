package com.heartbeets.driver.huami

import android.content.Context
import com.heartbeets.core.BleScanResult
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrDriverFactory
import com.heartbeets.core.Match

/**
 * Recognises Xiaomi Mi Band / Amazfit / Haylou devices.
 *
 * **Match strategy:**
 * - [Match.EXACT] — device name starts with a known Huami prefix (Mi Band, Amazfit, etc.)
 *   OR advertises the proprietary FEE0/FEE1 service UUIDs.
 * - [Match.LIKELY] — device advertises HRS (0x180D) and has a name matching loose patterns.
 * - [Match.NO] — no evidence this is a Huami device.
 *
 * This factory should be registered BEFORE [StandardHrsDriverFactory] so its EXACT
 * match outranks the standard driver's LIKELY match for Huami devices.
 */
class HuamiDriverFactory(private val context: Context) : HrDriverFactory {

    override val id = "huami"
    override val displayName = "Xiaomi / Amazfit"
    override val manufacturerHint = "Xiaomi, Amazfit, Haylou"

    override fun matches(scan: BleScanResult): Match {
        val nameLower = scan.deviceName?.lowercase() ?: ""

        // Exact match on known Huami name prefixes.
        if (HuamiProfile.KNOWN_NAME_PREFIXES.any { nameLower.startsWith(it) }) {
            return Match.EXACT
        }

        // Exact match on proprietary service UUIDs (FEE0 or FEE1).
        if (scan.serviceUuids.contains(HuamiProfile.SERVICE_MIBAND) ||
            scan.serviceUuids.contains(HuamiProfile.SERVICE_MIBAND_AUTH)
        ) {
            return Match.EXACT
        }

        return Match.NO
    }

    override fun create(address: String, name: String?): HrDriver =
        HuamiDriver(context, address, name)
}

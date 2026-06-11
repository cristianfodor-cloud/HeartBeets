package com.heartbeets.driver.galaxy

import android.content.Context
import com.heartbeets.core.BleScanResult
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrDriverFactory
import com.heartbeets.core.Match

/**
 * Recognises Samsung Galaxy Watch, Galaxy Fit, and Galaxy Ring devices.
 *
 * **Match strategy:**
 * - [Match.EXACT] — device name starts with a known Galaxy/Gear/SM-R prefix.
 * - [Match.NO] — no evidence of Samsung device.
 *
 * This factory should be registered BEFORE [StandardHrsDriverFactory] so Samsung
 * devices are claimed by name with EXACT confidence rather than falling through to
 * the generic StandardHrs LIKELY match.
 */
class GalaxyDriverFactory(private val context: Context) : HrDriverFactory {

    override val id = "galaxy"
    override val displayName = "Samsung Galaxy"
    override val manufacturerHint = "Samsung"

    override fun matches(scan: BleScanResult): Match {
        val nameLower = scan.deviceName?.lowercase() ?: ""

        // Exact match on known Galaxy / Gear name prefixes.
        if (GalaxyProfile.KNOWN_NAME_PREFIXES.any { nameLower.startsWith(it) }) {
            return Match.EXACT
        }

        // Also match if "samsung" appears anywhere in the name.
        if (GalaxyProfile.KNOWN_NAME_CONTAINS.any { nameLower.contains(it) }) {
            return Match.EXACT
        }

        return Match.NO
    }

    override fun create(address: String, name: String?): HrDriver =
        GalaxyDriver(context, address, name)
}

package com.heartbeets.driver.huawei

import android.content.Context
import com.heartbeets.core.BleScanResult
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrDriverFactory
import com.heartbeets.core.Match

/**
 * Recognises Huawei and Honor Band / Watch devices.
 *
 * **Match strategy:**
 * - [Match.EXACT] — device name starts with a known Huawei/Honor prefix OR advertises
 *   the proprietary FE86 service UUID.
 * - [Match.NO] — no evidence of Huawei device.
 */
class HuaweiDriverFactory(private val context: Context) : HrDriverFactory {

    override val id = "huawei"
    override val displayName = "Huawei / Honor"
    override val manufacturerHint = "Huawei, Honor"

    override fun matches(scan: BleScanResult): Match {
        val nameLower = scan.deviceName?.lowercase() ?: ""

        // Exact match on known Huawei/Honor name prefixes.
        if (HuaweiProfile.KNOWN_NAME_PREFIXES.any { nameLower.startsWith(it) }) {
            return Match.EXACT
        }

        // Exact match on proprietary FE86 service UUID.
        if (scan.serviceUuids.contains(HuaweiProfile.SERVICE_HUAWEI)) {
            return Match.EXACT
        }

        return Match.NO
    }

    override fun create(address: String, name: String?): HrDriver =
        HuaweiDriver(context, address, name)
}

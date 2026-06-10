package com.heartbeets.driver.veepoo

import android.content.Context
import com.heartbeets.core.BleScanResult
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrDriverFactory
import com.heartbeets.core.Match

/**
 * Recognises VeePoo family devices (H59 bracelet, ET585 smartwatch, …) and
 * creates [VeePooDriver] instances for them.
 *
 * Match strategy:
 *  - EXACT  → device name starts with a known VeePoo identifier (H59, ET585, ET-).
 *  - LIKELY → device advertises the NUS service UUID (many devices use NUS, so
 *             this is not conclusive on its own).
 *  - NO     → otherwise.
 */
class VeePooDriverFactory(private val context: Context) : HrDriverFactory {

    override val id = "veepoo"
    override val displayName = "VeePoo"
    override val manufacturerHint = "VeePoo / HBand"

    override fun matches(scan: BleScanResult): Match {
        val name = scan.deviceName.orEmpty()
        return when {
            name.startsWith("H59", ignoreCase = true) -> Match.EXACT
            name.startsWith("ET585", ignoreCase = true) -> Match.EXACT
            name.startsWith("ET-", ignoreCase = true) -> Match.EXACT
            // ET585 advertises FEE7 service; H59 advertises NUS.
            scan.serviceUuids.contains(VeePooProtocol.FEE7_SERVICE) -> Match.LIKELY
            scan.serviceUuids.contains(VeePooProtocol.NUS_SERVICE) -> Match.LIKELY
            else -> Match.NO
        }
    }

    override fun create(address: String, name: String?): HrDriver =
        VeePooDriver(context, address, name)
}

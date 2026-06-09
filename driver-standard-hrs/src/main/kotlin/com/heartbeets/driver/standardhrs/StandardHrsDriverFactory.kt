package com.heartbeets.driver.standardhrs

import android.content.Context
import com.heartbeets.core.BleScanResult
import com.heartbeets.core.HrDriver
import com.heartbeets.core.HrDriverFactory
import com.heartbeets.core.Match

/**
 * Recognises any device that advertises the Bluetooth SIG Heart Rate Service (0x180D)
 * and creates a [StandardHrsDriver] for it.
 *
 * This factory intentionally returns [Match.LIKELY] rather than [Match.EXACT] because
 * advertising HRS doesn't guarantee the device only does HRS — a VeePoo device
 * might advertise it but also use NUS. [VeePooDriverFactory] should be registered
 * first so its EXACT match wins via [com.heartbeets.core.DeviceRegistry.bestMatchFor].
 */
class StandardHrsDriverFactory(private val context: Context) : HrDriverFactory {

    override val id = "standard-hrs"
    override val displayName = "Standard HR Sensor"
    override val manufacturerHint = "Any Bluetooth SIG HRS device"

    override fun matches(scan: BleScanResult): Match =
        if (scan.serviceUuids.contains(HrsProfile.HR_SERVICE)) Match.LIKELY else Match.NO

    override fun create(address: String, name: String?): HrDriver =
        StandardHrsDriver(context, address, name)
}

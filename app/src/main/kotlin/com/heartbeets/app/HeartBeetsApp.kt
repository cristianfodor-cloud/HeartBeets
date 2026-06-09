package com.heartbeets.app

import android.app.Application
import com.heartbeets.core.DeviceRegistry
import com.heartbeets.driver.standardhrs.StandardHrsDriverFactory
import com.heartbeets.driver.veepoo.VeePooDriverFactory

class HeartBeetsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // VeePooDriverFactory is registered first so its EXACT match outranks
        // StandardHrsDriverFactory's LIKELY match via DeviceRegistry.bestMatchFor().
        DeviceRegistry.register(VeePooDriverFactory(this))
        DeviceRegistry.register(StandardHrsDriverFactory(this))
    }
}

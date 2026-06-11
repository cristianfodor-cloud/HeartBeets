package com.heartbeets.app

import android.app.Application
import com.heartbeets.core.DeviceRegistry
import com.heartbeets.driver.huami.HuamiDriverFactory
import com.heartbeets.driver.standardhrs.StandardHrsDriverFactory
import com.heartbeets.driver.veepoo.VeePooDriverFactory

class HeartBeetsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Factories with EXACT matches are registered first so they outrank
        // StandardHrsDriverFactory's LIKELY match via DeviceRegistry.bestMatchFor().
        DeviceRegistry.register(VeePooDriverFactory(this))
        DeviceRegistry.register(HuamiDriverFactory(this))
        DeviceRegistry.register(StandardHrsDriverFactory(this))
    }
}

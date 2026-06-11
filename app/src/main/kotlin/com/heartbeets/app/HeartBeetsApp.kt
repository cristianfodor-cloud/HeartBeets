package com.heartbeets.app

import android.app.Application
import com.heartbeets.core.DeviceRegistry
import com.heartbeets.driver.colmi.ColmiDriverFactory
import com.heartbeets.driver.galaxy.GalaxyDriverFactory
import com.heartbeets.driver.huami.HuamiDriverFactory
import com.heartbeets.driver.huawei.HuaweiDriverFactory
import com.heartbeets.driver.standardhrs.StandardHrsDriverFactory
import com.heartbeets.driver.veepoo.VeePooDriverFactory

class HeartBeetsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Factories with EXACT matches are registered first so they outrank
        // StandardHrsDriverFactory's LIKELY match via DeviceRegistry.bestMatchFor().
        DeviceRegistry.register(VeePooDriverFactory(this))
        DeviceRegistry.register(HuamiDriverFactory(this))
        DeviceRegistry.register(ColmiDriverFactory(this))
        DeviceRegistry.register(HuaweiDriverFactory(this))
        DeviceRegistry.register(GalaxyDriverFactory(this))
        DeviceRegistry.register(StandardHrsDriverFactory(this))
    }
}

package com.heartbeets.service

import android.content.Context
import android.content.Intent

/** Convenience object for starting / stopping [HrService] without building Intents by hand. */
object HrServiceController {

    fun start(context: Context, address: String, factoryId: String?) {
        val intent = Intent(context, HrService::class.java).apply {
            putExtra(HrService.EXTRA_ADDRESS, address)
            factoryId?.let { putExtra(HrService.EXTRA_FACTORY_ID, it) }
        }
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, HrService::class.java))
    }
}

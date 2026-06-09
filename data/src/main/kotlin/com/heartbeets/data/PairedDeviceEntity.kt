package com.heartbeets.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Remembers the last-connected device so we can offer quick reconnect on app open. */
@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey val address: String,
    val name: String? = null,
    val driverId: String,
    val lastConnectedAt: Long,
)

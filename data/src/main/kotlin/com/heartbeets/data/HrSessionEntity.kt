package com.heartbeets.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One streaming session — a single connect → disconnect lifecycle. */
@Entity(tableName = "hr_sessions")
data class HrSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val driverId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)

package com.heartbeets.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single BPM (+ optional R-R) reading persisted to Room.
 *
 * [rrIntervalsMs] is stored as a CSV string ("812,798,815") rather than a
 * separate table to keep queries simple. This is easy to migrate later.
 */
@Entity(
    tableName = "hr_samples",
    foreignKeys = [
        ForeignKey(
            entity = HrSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("timestamp")],
)
data class HrSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val deviceAddress: String,
    val driverId: String,
    val bpm: Int,
    val timestamp: Long,
    /** CSV of R-R intervals in milliseconds, e.g. "812,798,815". Null when not available. */
    val rrIntervalsMs: String? = null,
    val contactDetected: Boolean? = null,
)

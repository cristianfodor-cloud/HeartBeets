package com.heartbeets.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HrSampleEntity::class, HrSessionEntity::class, PairedDeviceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HrDatabase : RoomDatabase() {
    abstract fun hrSampleDao(): HrSampleDao
    abstract fun hrSessionDao(): HrSessionDao
    abstract fun pairedDeviceDao(): PairedDeviceDao

    companion object {
        @Volatile private var instance: HrDatabase? = null

        fun getInstance(context: Context): HrDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HrDatabase::class.java,
                    "heartbeets.db",
                ).build().also { instance = it }
            }
    }
}

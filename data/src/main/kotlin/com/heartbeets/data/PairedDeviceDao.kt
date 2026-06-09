package com.heartbeets.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(device: PairedDeviceEntity)

    @Query("SELECT * FROM paired_devices ORDER BY lastConnectedAt DESC LIMIT 1")
    fun lastPaired(): PairedDeviceEntity?

    @Query("SELECT * FROM paired_devices ORDER BY lastConnectedAt DESC")
    fun observeAll(): Flow<List<PairedDeviceEntity>>
}

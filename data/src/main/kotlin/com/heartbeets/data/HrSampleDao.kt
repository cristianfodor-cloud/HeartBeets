package com.heartbeets.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HrSampleDao {
    @Insert
    fun insert(sample: HrSampleEntity): Long

    @Query("SELECT * FROM hr_samples WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeForSession(sessionId: Long): Flow<List<HrSampleEntity>>

    @Query("SELECT * FROM hr_samples ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HrSampleEntity>>
}

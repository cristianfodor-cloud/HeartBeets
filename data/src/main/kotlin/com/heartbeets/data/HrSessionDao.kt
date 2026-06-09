package com.heartbeets.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HrSessionDao {
    @Insert
    fun insert(session: HrSessionEntity): Long

    @Query("UPDATE hr_sessions SET endedAt = :endedAt WHERE id = :id")
    fun close(id: Long, endedAt: Long)

    @Query("SELECT * FROM hr_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<HrSessionEntity>>

    @Query("SELECT * FROM hr_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<HrSessionEntity?>
}

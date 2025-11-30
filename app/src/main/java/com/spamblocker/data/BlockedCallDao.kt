package com.spamblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {
    @Insert
    suspend fun insertCall(call: BlockedCall)

    @Query("SELECT COUNT(*) FROM blocked_calls")
    suspend fun getCallCount(): Int

    @Query("DELETE FROM blocked_calls WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<BlockedCall>>
}
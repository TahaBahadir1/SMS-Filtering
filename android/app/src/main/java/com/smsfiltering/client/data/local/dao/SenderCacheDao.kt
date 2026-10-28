package com.smsfiltering.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsfiltering.client.data.local.entity.SenderCacheEntity

@Dao
interface SenderCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSender(sender: SenderCacheEntity)

    @Query("SELECT * FROM sender_cache WHERE UPPER(sender) = UPPER(:sender) LIMIT 1")
    suspend fun getSender(sender: String): SenderCacheEntity?

    @Query("SELECT * FROM sender_cache")
    suspend fun getAllSenders(): List<SenderCacheEntity>

    @Query("DELETE FROM sender_cache WHERE cachedAt < :cutoffTime")
    suspend fun clearExpired(cutoffTime: Long)

    @Query("DELETE FROM sender_cache")
    suspend fun clearAll()
}

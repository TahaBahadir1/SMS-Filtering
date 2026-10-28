package com.smsfiltering.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity

@Dao
interface FilteredMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: FilteredMessageEntity): Long

    @Query("SELECT * FROM filtered_messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<FilteredMessageEntity>

    @Query("SELECT * FROM filtered_messages WHERE decision = :decision ORDER BY timestamp DESC")
    suspend fun getMessagesByDecision(decision: String): List<FilteredMessageEntity>

    @Query("DELETE FROM filtered_messages")
    suspend fun clearAll()
}

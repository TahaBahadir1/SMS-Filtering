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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<FilteredMessageEntity>)

    @Query("SELECT * FROM filtered_messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<FilteredMessageEntity>

    @Query("SELECT * FROM filtered_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): FilteredMessageEntity?

    @Query("SELECT * FROM filtered_messages WHERE UPPER(decision) = UPPER(:decision) ORDER BY timestamp DESC")
    suspend fun getMessagesByDecision(decision: String): List<FilteredMessageEntity>

    @Query("SELECT * FROM filtered_messages WHERE UPPER(decision) IN (:decisions) ORDER BY timestamp DESC")
    suspend fun getMessagesByDecisions(decisions: List<String>): List<FilteredMessageEntity>

    @Query("SELECT * FROM filtered_messages WHERE topic_id = :topicId ORDER BY timestamp DESC")
    suspend fun getMessagesByTopicId(topicId: Long): List<FilteredMessageEntity>

    @Query("UPDATE filtered_messages SET decision = :newDecision WHERE id = :id")
    suspend fun updateDecision(id: Long, newDecision: String)

    @Query("DELETE FROM filtered_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("SELECT COUNT(*) FROM filtered_messages WHERE UPPER(decision) IN (:decisions)")
    suspend fun getCountByDecisions(decisions: List<String>): Int

    @Query("DELETE FROM filtered_messages")
    suspend fun clearAll()
}

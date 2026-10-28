package com.smsfiltering.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.smsfiltering.client.data.local.entity.TopicEntity
import com.smsfiltering.client.data.local.entity.TopicKeywordEntity

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopicKeywords(keywords: List<TopicKeywordEntity>)

    @Query("SELECT * FROM topics ORDER BY id ASC")
    suspend fun getAllTopics(): List<TopicEntity>

    @Query("SELECT * FROM topic_keywords ORDER BY id ASC")
    suspend fun getAllTopicKeywords(): List<TopicKeywordEntity>

    @Query("SELECT * FROM topic_keywords WHERE topicId = :topicId")
    suspend fun getKeywordsByTopicId(topicId: Long): List<TopicKeywordEntity>

    @Query("DELETE FROM topic_keywords")
    suspend fun clearTopicKeywords()

    @Query("DELETE FROM topics")
    suspend fun clearTopics()

    @Transaction
    suspend fun replaceAll(
        topics: List<TopicEntity>,
        keywords: List<TopicKeywordEntity>
    ) {
        clearTopicKeywords()
        clearTopics()
        insertTopics(topics)
        insertTopicKeywords(keywords)
    }
}

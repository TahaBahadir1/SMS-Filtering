package com.smsfiltering.client.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.smsfiltering.client.data.local.dao.FilteredMessageDao
import com.smsfiltering.client.data.local.dao.KeywordCategoryDao
import com.smsfiltering.client.data.local.dao.SenderCacheDao
import com.smsfiltering.client.data.local.dao.SyncMetadataDao
import com.smsfiltering.client.data.local.dao.TopicDao
import com.smsfiltering.client.data.local.entity.CategoryKeywordEntity
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.data.local.entity.SyncMetadataEntity
import com.smsfiltering.client.data.local.entity.TopicEntity
import com.smsfiltering.client.data.local.entity.TopicKeywordEntity

@Database(
    entities = [
        KeywordCategoryEntity::class,
        CategoryKeywordEntity::class,
        TopicEntity::class,
        TopicKeywordEntity::class,
        SenderCacheEntity::class,
        SyncMetadataEntity::class,
        FilteredMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun keywordCategoryDao(): KeywordCategoryDao
    abstract fun topicDao(): TopicDao
    abstract fun senderCacheDao(): SenderCacheDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun filteredMessageDao(): FilteredMessageDao

    @Transaction
    open suspend fun syncAll(
        version: Long,
        categories: List<KeywordCategoryEntity>,
        categoryKeywords: List<CategoryKeywordEntity>,
        topics: List<TopicEntity>,
        topicKeywords: List<TopicKeywordEntity>
    ) {
        keywordCategoryDao().replaceAll(categories, categoryKeywords)
        topicDao().replaceAll(topics, topicKeywords)
        syncMetadataDao().setMetadata(
            SyncMetadataEntity(
                key = KEY_KEYWORD_VERSION,
                value = version.toString(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        const val DATABASE_NAME = "sms_filtering.db"
        const val KEY_KEYWORD_VERSION = "keyword_list_version"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

package com.smsfiltering.client.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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
        val now = System.currentTimeMillis()
        syncMetadataDao().setMetadata(
            SyncMetadataEntity(
                key = KEY_KEYWORD_VERSION,
                value = version.toString(),
                updatedAt = now
            )
        )
        syncMetadataDao().setMetadata(
            SyncMetadataEntity(
                key = KEY_LAST_SYNC_TIME,
                value = now.toString(),
                updatedAt = now
            )
        )
    }

    companion object {
        const val DATABASE_NAME = "sms_filtering.db"
        const val KEY_KEYWORD_VERSION = "keyword_list_version"
        const val KEY_LAST_SYNC_TIME = "last_sync_time"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. topics tablosu (id INTEGER PK, name TEXT NOT NULL)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `topics` (
                        `id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                // 2. topic_keywords tablosu (id INTEGER PK, topic_id INTEGER NOT NULL FK topics(id) ON DELETE CASCADE, keyword TEXT NOT NULL)
                val cursor = db.query("PRAGMA table_info(`topic_keywords`)")
                var hasTopicKeywords = false
                var hasTopicIdSnakeCase = false
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    hasTopicKeywords = true
                    if (nameIdx != -1) {
                        val colName = cursor.getString(nameIdx)
                        if (colName == "topic_id") {
                            hasTopicIdSnakeCase = true
                        }
                    }
                }
                cursor.close()

                if (!hasTopicKeywords) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `topic_keywords` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `topic_id` INTEGER NOT NULL,
                            `keyword` TEXT NOT NULL,
                            `normalizedKeyword` TEXT NOT NULL DEFAULT '',
                            FOREIGN KEY(`topic_id`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                } else if (!hasTopicIdSnakeCase) {
                    db.execSQL(
                        """
                        CREATE TABLE `topic_keywords_new` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `topic_id` INTEGER NOT NULL,
                            `keyword` TEXT NOT NULL,
                            `normalizedKeyword` TEXT NOT NULL DEFAULT '',
                            FOREIGN KEY(`topic_id`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO `topic_keywords_new` (`id`, `topic_id`, `keyword`, `normalizedKeyword`)
                        SELECT `id`, `topicId`, `keyword`, `normalizedKeyword` FROM `topic_keywords`
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE `topic_keywords`")
                    db.execSQL("ALTER TABLE `topic_keywords_new` RENAME TO `topic_keywords`")
                }

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_keywords_topic_id` ON `topic_keywords` (`topic_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_keywords_normalizedKeyword` ON `topic_keywords` (`normalizedKeyword`)")

                // 3. filtered_messages tablosuna topic_id INTEGER NULL ekle (FK topics(id) ON DELETE SET NULL)
                val msgCursor = db.query("PRAGMA table_info(`filtered_messages`)")
                var hasTopicIdInMessages = false
                val msgNameIdx = msgCursor.getColumnIndex("name")
                while (msgCursor.moveToNext()) {
                    if (msgNameIdx != -1) {
                        val colName = msgCursor.getString(msgNameIdx)
                        if (colName == "topic_id") {
                            hasTopicIdInMessages = true
                        }
                    }
                }
                msgCursor.close()

                if (!hasTopicIdInMessages) {
                    db.execSQL(
                        "ALTER TABLE `filtered_messages` ADD COLUMN `topic_id` INTEGER DEFAULT NULL REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
                    )
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filtered_messages_topic_id` ON `filtered_messages` (`topic_id`)")

                // messages ve sync_state uyumluluk görünümleri
                db.execSQL("CREATE VIEW IF NOT EXISTS `messages` AS SELECT * FROM `filtered_messages`")
                db.execSQL("CREATE VIEW IF NOT EXISTS `sync_state` AS SELECT * FROM `sync_metadata`")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

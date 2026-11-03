package com.smsfiltering.client.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smsfiltering.client.util.TurkishTextNormalizer

/**
 * Konulara (topics) bağlı anahtar kelimeler tablosu.
 */
@Entity(
    tableName = "topic_keywords",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["topic_id"]),
        Index(value = ["normalizedKeyword"])
    ]
)
data class TopicKeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "topic_id")
    val topicId: Long,
    val keyword: String,
    val normalizedKeyword: String = TurkishTextNormalizer.normalize(keyword)
)

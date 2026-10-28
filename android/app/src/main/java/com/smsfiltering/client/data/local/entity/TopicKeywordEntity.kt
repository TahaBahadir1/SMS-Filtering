package com.smsfiltering.client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Konulara (topics) bağlı anahtar kelimeler tablosu.
 */
@Entity(
    tableName = "topic_keywords",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["topicId"]),
        Index(value = ["normalizedKeyword"])
    ]
)
data class TopicKeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long,
    val keyword: String,
    val normalizedKeyword: String
)

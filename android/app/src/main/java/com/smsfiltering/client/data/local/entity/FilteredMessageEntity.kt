package com.smsfiltering.client.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Filtrelenen SMS kayıtları tablosu.
 */
@Entity(
    tableName = "filtered_messages",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["topic_id"])
    ]
)
data class FilteredMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val decision: String,
    val riskScore: Double,
    val matchedCategories: String,
    val matchedTopics: String,
    val matchedKeywords: String,
    val reasons: String,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "topic_id")
    val topicId: Long? = null
)

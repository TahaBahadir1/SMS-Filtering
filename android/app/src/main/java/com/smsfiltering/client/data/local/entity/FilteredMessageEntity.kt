package com.smsfiltering.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Filtrelenen SMS kayıtları tablosu.
 */
@Entity(tableName = "filtered_messages")
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
    val timestamp: Long = System.currentTimeMillis()
)

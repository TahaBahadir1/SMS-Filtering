package com.smsfiltering.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Konular tablosu (BANKA, KUMAR, BELEDIYE, KARGO, OPERATOR vb.).
 */
@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey
    val id: Long,
    val name: String
)

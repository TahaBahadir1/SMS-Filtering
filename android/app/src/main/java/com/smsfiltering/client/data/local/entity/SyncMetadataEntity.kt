package com.smsfiltering.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Senkronizasyon meta verileri (ör. keyword_version, last_sync_time) tablosu.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

package com.smsfiltering.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sorgulanan gönderenlerin yerel önbellek tablosu.
 */
@Entity(tableName = "sender_cache")
data class SenderCacheEntity(
    @PrimaryKey
    val sender: String,
    val riskScore: Double?,
    val riskLevel: String?,
    val isFinalized: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
) {
    /**
     * docs/api-sozlesmesi.md kuralı:
     * Tehlikeli gönderen = riskLevel riskli VE isFinalized true.
     */
    val isDangerous: Boolean
        get() = riskLevel?.equals("riskli", ignoreCase = true) == true && isFinalized
}

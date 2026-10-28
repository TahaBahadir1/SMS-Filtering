package com.smsfiltering.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Anahtar kelime kategorileri tablosu (IBAN, PIN, SIFRE, LINK, ACIL_ODEME vb.).
 */
@Entity(tableName = "keyword_categories")
data class KeywordCategoryEntity(
    @PrimaryKey
    val id: Long,
    val name: String
)

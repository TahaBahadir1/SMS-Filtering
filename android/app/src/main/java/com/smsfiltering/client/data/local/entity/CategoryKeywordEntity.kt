package com.smsfiltering.client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kategoriye bağlı anahtar kelimeler tablosu.
 */
@Entity(
    tableName = "category_keywords",
    foreignKeys = [
        ForeignKey(
            entity = KeywordCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["normalizedKeyword"])
    ]
)
data class CategoryKeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val keyword: String,
    val normalizedKeyword: String
)

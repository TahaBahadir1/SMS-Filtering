package com.smsfiltering.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.smsfiltering.client.data.local.entity.CategoryKeywordEntity
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity

@Dao
interface KeywordCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<KeywordCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryKeywords(keywords: List<CategoryKeywordEntity>)

    @Query("SELECT * FROM keyword_categories ORDER BY id ASC")
    suspend fun getAllCategories(): List<KeywordCategoryEntity>

    @Query("SELECT * FROM category_keywords ORDER BY id ASC")
    suspend fun getAllCategoryKeywords(): List<CategoryKeywordEntity>

    @Query("SELECT * FROM category_keywords WHERE categoryId = :categoryId")
    suspend fun getKeywordsByCategoryId(categoryId: Long): List<CategoryKeywordEntity>

    @Query("DELETE FROM category_keywords")
    suspend fun clearCategoryKeywords()

    @Query("DELETE FROM keyword_categories")
    suspend fun clearCategories()

    @Transaction
    suspend fun replaceAll(
        categories: List<KeywordCategoryEntity>,
        keywords: List<CategoryKeywordEntity>
    ) {
        clearCategoryKeywords()
        clearCategories()
        insertCategories(categories)
        insertCategoryKeywords(keywords)
    }
}

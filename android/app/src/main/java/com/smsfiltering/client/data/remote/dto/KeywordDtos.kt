package com.smsfiltering.client.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Server'dan dönen anahtar kelime senkronizasyon yanıtı DTO'su.
 *
 * GET /api/v1/keywords?version={version}
 */
data class KeywordListResponseDto(
    @SerializedName("version")
    val version: Long,

    @SerializedName("upToDate")
    val upToDate: Boolean,

    @SerializedName("categories")
    val categories: List<KeywordCategoryDto> = emptyList(),

    @SerializedName("topics")
    val topics: List<TopicDto> = emptyList()
)

/**
 * Kategori ve ilişkili anahtar kelimeler DTO'su.
 */
data class KeywordCategoryDto(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("keywords")
    val keywords: List<String> = emptyList()
)

/**
 * Konu (Topic) ve ilişkili anahtar kelimeler DTO'su.
 */
data class TopicDto(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("keywords")
    val keywords: List<String> = emptyList()
)

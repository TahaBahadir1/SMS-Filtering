package com.smsfiltering.client.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Server'dan dönen standart hata yanıt DTO'su.
 *
 * Örnek:
 * {
 *   "error": "Gönderen bilgisi boş olamaz"
 * }
 */
data class ErrorResponseDto(
    @SerializedName("error")
    val error: String
)

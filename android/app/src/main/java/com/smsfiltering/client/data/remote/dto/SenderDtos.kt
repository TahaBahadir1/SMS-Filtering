package com.smsfiltering.client.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Gönderici risk sorgulama istek DTO'su.
 *
 * POST /api/v1/senders/lookup
 * Doğrulama Kuralları:
 * - sender boş olamaz (@NotBlank)
 * - sender en fazla 32 karakter olabilir (@Size(max = 32))
 */
data class SenderLookupRequestDto(
    @SerializedName("sender")
    val sender: String
)

/**
 * Gönderici risk sorgulama yanıt DTO'su.
 *
 * Bulunursa: found=true, riskScore=0.05, riskLevel="risksiz", isFinalized=true
 * Bulunamazsa: found=false, riskScore=null, riskLevel=null, isFinalized=false
 */
data class SenderLookupResponseDto(
    @SerializedName("found")
    val found: Boolean,

    @SerializedName("riskScore")
    val riskScore: Double? = null,

    @SerializedName("riskLevel")
    val riskLevel: String? = null,

    @SerializedName("isFinalized")
    val isFinalized: Boolean = false
)

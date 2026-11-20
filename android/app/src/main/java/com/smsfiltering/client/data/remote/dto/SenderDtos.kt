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

/**
 * Gönderici risk sinyali gönderim istek DTO'su (Kapalı Döngü Geri Bildirim).
 *
 * POST /api/v1/senders/signals
 * Gizlilik: SMS metni kesinlikle gönderilmez; yalnızca gönderen kimliği, kaynak ve skor iletilir.
 */
data class SenderSignalRequestDto(
    @SerializedName("sender")
    val sender: String,

    @SerializedName("source")
    val source: String,

    @SerializedName("score")
    val score: Double
)

/**
 * Gönderici risk sinyali yanıt DTO'su.
 */
data class SenderSignalResponseDto(
    @SerializedName("sender")
    val sender: String,

    @SerializedName("riskScore")
    val riskScore: Double,

    @SerializedName("riskLevel")
    val riskLevel: String,

    @SerializedName("isFinalized")
    val isFinalized: Boolean
)

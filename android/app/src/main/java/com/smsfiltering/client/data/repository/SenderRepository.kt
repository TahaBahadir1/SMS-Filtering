package com.smsfiltering.client.data.repository

import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.remote.api.SmsFilteringApiService
import com.smsfiltering.client.data.remote.dto.SenderLookupRequestDto
import com.smsfiltering.client.data.remote.dto.SenderLookupResponseDto
import com.smsfiltering.client.util.SenderValidationResult
import com.smsfiltering.client.util.TurkishTextNormalizer

/**
 * Gönderici risk sorgusu için Repository sınıfı.
 *
 * POST /api/v1/senders/lookup
 */
class SenderRepository(
    private val apiService: SmsFilteringApiService = ApiClient.createService(),
    private val database: AppDatabase? = null
) {

    /**
     * SMS göndericisinin risk durumunu sorgular.
     * Ağ isteği öncesinde docs/api-sozlesmesi.md doğrulama kurallarını işletir:
     * - Boş olamaz
     * - En fazla 32 karakter olabilir
     *
     * Öncelikli olarak yerel Room veritabanındaki önbelleğe bakar.
     *
     * @param sender SMS başlığı veya numara ("BANKA-X", "+905559876543" vb.)
     * @param useCache Yerel önbelleğin kullanılıp kullanılmayacağı (varsayılan: true)
     * @return Risk analizi sonucu veya hata bilgisi.
     */
    suspend fun lookupSender(
        sender: String?,
        useCache: Boolean = true
    ): NetworkResult<SenderLookupResponseDto> {
        return when (val validation = TurkishTextNormalizer.validateSender(sender)) {
            is SenderValidationResult.Invalid -> {
                NetworkResult.Error(
                    code = 400,
                    message = validation.errorMessage
                )
            }
            is SenderValidationResult.Valid -> {
                val sanitized = validation.sanitizedSender

                if (useCache && database != null) {
                    val cached = database.senderCacheDao().getSender(sanitized)
                    if (cached != null) {
                        return NetworkResult.Success(
                            data = SenderLookupResponseDto(
                                found = true,
                                riskScore = cached.riskScore,
                                riskLevel = cached.riskLevel,
                                isFinalized = cached.isFinalized
                            )
                        )
                    }
                }

                val remoteResult = ApiClient.safeApiCall {
                    apiService.lookupSender(
                        SenderLookupRequestDto(sender = sanitized)
                    )
                }

                if (remoteResult is NetworkResult.Success && database != null) {
                    val data = remoteResult.data
                    if (data.found) {
                        database.senderCacheDao().insertSender(
                            SenderCacheEntity(
                                sender = sanitized,
                                riskScore = data.riskScore,
                                riskLevel = data.riskLevel,
                                isFinalized = data.isFinalized
                            )
                        )
                    }
                }

                remoteResult
            }
        }
    }
}

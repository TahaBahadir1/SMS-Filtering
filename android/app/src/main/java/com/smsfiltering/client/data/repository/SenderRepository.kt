package com.smsfiltering.client.data.repository

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
    private val apiService: SmsFilteringApiService = ApiClient.createService()
) {

    /**
     * SMS göndericisinin risk durumunu sorgular.
     * Ağ isteği öncesinde docs/api-sozlesmesi.md doğrulama kurallarını işletir:
     * - Boş olamaz
     * - En fazla 32 karakter olabilir
     *
     * @param sender SMS başlığı veya numara ("BANKA-X", "+905559876543" vb.)
     * @return Risk analizi sonucu veya hata bilgisi.
     */
    suspend fun lookupSender(sender: String?): NetworkResult<SenderLookupResponseDto> {
        return when (val validation = TurkishTextNormalizer.validateSender(sender)) {
            is SenderValidationResult.Invalid -> {
                NetworkResult.Error(
                    code = 400,
                    message = validation.errorMessage
                )
            }
            is SenderValidationResult.Valid -> {
                ApiClient.safeApiCall {
                    apiService.lookupSender(
                        SenderLookupRequestDto(sender = validation.sanitizedSender)
                    )
                }
            }
        }
    }
}

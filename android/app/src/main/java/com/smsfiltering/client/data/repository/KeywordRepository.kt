package com.smsfiltering.client.data.repository

import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.remote.api.SmsFilteringApiService
import com.smsfiltering.client.data.remote.dto.KeywordListResponseDto

/**
 * Anahtar kelime listesini sunucudan senkronize etmek için Repository sınıfı.
 *
 * GET /api/v1/keywords?version={version}
 */
class KeywordRepository(
    private val apiService: SmsFilteringApiService = ApiClient.createService()
) {

    /**
     * Sunucudan anahtar kelimeleri sorgular.
     *
     * @param currentVersion İstemcinin elindeki mevcut sürüm numarası (varsayılan: 0).
     * @return Sunucudan gelen sürüm ve kategori/kelime listesi.
     */
    suspend fun getKeywords(currentVersion: Long = 0L): NetworkResult<KeywordListResponseDto> {
        return ApiClient.safeApiCall {
            apiService.getKeywords(version = currentVersion)
        }
    }
}

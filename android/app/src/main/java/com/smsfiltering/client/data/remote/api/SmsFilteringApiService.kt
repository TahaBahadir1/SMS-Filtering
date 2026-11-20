package com.smsfiltering.client.data.remote.api

import com.smsfiltering.client.data.remote.dto.KeywordListResponseDto
import com.smsfiltering.client.data.remote.dto.SenderLookupRequestDto
import com.smsfiltering.client.data.remote.dto.SenderLookupResponseDto
import com.smsfiltering.client.data.remote.dto.SenderSignalRequestDto
import com.smsfiltering.client.data.remote.dto.SenderSignalResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * SMS-Filtering REST API uç noktaları arayüzü.
 * docs/api-sozlesmesi.md dokümanına göre yapılandırılmıştır.
 */
interface SmsFilteringApiService {

    /**
     * Anahtar kelime listesini sunucu ile senkronize eder.
     *
     * @param version İstemcinin elindeki mevcut liste sürüm numarası (varsayılan: 0).
     * @return Sürüm durumu ve kategoriler/anahtar kelimeler.
     */
    @GET("keywords")
    suspend fun getKeywords(
        @Query("version") version: Long = 0
    ): Response<KeywordListResponseDto>

    /**
     * SMS başlığı veya numara olarak gelen gönderenin risk analizini sorgular.
     *
     * @param request Gönderen bilgisi ("sender").
     * @return Risk skoru, risk düzeyi ve kesinleşme durumu.
     */
    @Headers("Content-Type: application/json")
    @POST("senders/lookup")
    suspend fun lookupSender(
        @Body request: SenderLookupRequestDto
    ): Response<SenderLookupResponseDto>

    /**
     * Kullanıcının veya cihaz içi analizin ürettiği anonim gönderen risk sinyalini sunucuya iletir.
     * Gizlilik: SMS metni kesinlikle gönderilmez.
     *
     * @param request Gönderen kimliği, sinyal kaynağı ("manuel", "yapay_zeka") ve risk skoru (0.0-1.0).
     * @return Sunucuda güncellenmiş gönderen risk durumu ve kesinleşme bilgisi.
     */
    @Headers("Content-Type: application/json")
    @POST("senders/signals")
    suspend fun submitSignal(
        @Body request: SenderSignalRequestDto
    ): Response<SenderSignalResponseDto>
}

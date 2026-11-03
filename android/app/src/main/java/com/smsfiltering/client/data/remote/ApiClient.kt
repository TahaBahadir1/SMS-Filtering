package com.smsfiltering.client.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.smsfiltering.client.BuildConfig
import com.smsfiltering.client.data.remote.api.SmsFilteringApiService
import com.smsfiltering.client.data.remote.dto.ErrorResponseDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Android istemcisi için Retrofit ve OkHttp yapılandırması.
 *
 * Base URL: local.properties dosyasındaki SERVER_BASE_URL üzerinden BuildConfig ile sağlanır.
 *
 * Not: docs/api-sozlesmesi.md Gizlilik Politikası uyarınca:
 * "istek gövdesi (request body), gönderen değeri (sender) ve istemci IP adresi
 * uygulama ve erişim loglarında kesinlikle loglanmaz."
 * Bu nedenle HttpLoggingInterceptor seviyesi DEBUG derlemelerinde BASIC olarak tutulmuştur
 * (asla BODY kullanılmaz), RELEASE derlemelerinde ise HTTP gövde/içerik loglaması tamamen kapalıdır (NONE).
 */
object ApiClient {

    val DEFAULT_BASE_URL: String get() = BuildConfig.SERVER_BASE_URL
    private const val TIMEOUT_SECONDS = 15L

    val gson: Gson by lazy {
        GsonBuilder()
            .create()
    }

    fun createOkHttpClient(
        logLevel: HttpLoggingInterceptor.Level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    ): OkHttpClient {
        // Gizlilik kuralı: Release derlemesinde gövde loglaması asla açık olamaz
        val effectiveLogLevel = if (!BuildConfig.DEBUG && logLevel == HttpLoggingInterceptor.Level.BODY) {
            HttpLoggingInterceptor.Level.NONE
        } else {
            logLevel
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = effectiveLogLevel
        }

        return OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun createRetrofit(
        baseUrl: String = DEFAULT_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): Retrofit {
        val finalUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(finalUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun createService(
        baseUrl: String = DEFAULT_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): SmsFilteringApiService {
        return createRetrofit(baseUrl, okHttpClient).create(SmsFilteringApiService::class.java)
    }

    /**
     * Retrofit çağrılarını güvenle yürüten ve standart hata formatını
     * ({ "error": "..." }) ayrıştıran yardımcı fonksiyon.
     */
    suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): NetworkResult<T> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Error(response.code(), "Sunucudan boş yanıt döndü")
                }
            } else {
                val errorBodyStr = response.errorBody()?.string()
                val errorMessage = parseErrorMessage(errorBodyStr)
                    ?: response.message().ifBlank { "HTTP Hatası: ${response.code()}" }
                NetworkResult.Error(response.code(), errorMessage)
            }
        } catch (t: Throwable) {
            NetworkResult.Exception(t)
        }
    }

    private fun parseErrorMessage(errorBodyStr: String?): String? {
        if (errorBodyStr.isNullOrBlank()) return null
        return try {
            val errorResponse = gson.fromJson(errorBodyStr, ErrorResponseDto::class.java)
            errorResponse?.error?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}

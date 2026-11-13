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
import java.io.File

/**
 * Gönderici risk sorgusu, yerel önbellek ve CSV içe/dışa aktarım yönetimi için Repository sınıfı.
 *
 * FR-05: Gönderen Bilgilerinin Kaydedilmesi
 * FR-06: Gönderen Bilgilerinin Silinmesi
 * FR-09: CSV İçe Aktarma ve Dışa Aktarma
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

    /**
     * Yerel veritabanında kayıtlı tüm gönderenleri döner.
     */
    suspend fun getAllSenders(): List<SenderCacheEntity> {
        return database?.senderCacheDao()?.getAllSenders() ?: emptyList()
    }

    /**
     * Çoklu gönderen listesini yerel veritabanına ekler (CSV içe aktarma için).
     */
    suspend fun importSenders(senders: List<SenderCacheEntity>) {
        database?.senderCacheDao()?.insertSenders(senders)
    }

    /**
     * Belirtilen göndereni yerel veritabanından siler (FR-06).
     */
    suspend fun deleteSender(sender: String) {
        database?.senderCacheDao()?.deleteSender(sender)
    }

    /**
     * Yerel veritabanındaki tüm gönderenleri siler (FR-06).
     */
    suspend fun clearAllSenders() {
        database?.senderCacheDao()?.clearAll()
    }

    /**
     * Kayıtlı gönderenleri CSV formatında belirtilen dosyaya dışa aktarır (FR-09).
     * Format: sender;riskScore;riskLevel;isFinalized;cachedAt
     */
    suspend fun exportSendersToCsv(file: File): Boolean {
        return try {
            val senders = getAllSenders()
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("sender;riskScore;riskLevel;isFinalized;cachedAt\n")
                for (s in senders) {
                    writer.write("${s.sender};${s.riskScore ?: 0.0};${s.riskLevel ?: ""};${s.isFinalized};${s.cachedAt}\n")
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * CSV dosyasındaki gönderen kayıtlarını okuyarak yerel veritabanına aktarır (FR-09).
     * Desteklenen ayırıcılar: ';' veya ','
     * @return İçe aktarılan geçerli gönderen sayısı.
     */
    suspend fun importSendersFromCsv(file: File): Int {
        if (!file.exists() || !file.canRead()) return 0
        return try {
            val lines = file.readLines(Charsets.UTF_8)
                .map { it.removePrefix("\uFEFF").trim() }
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) return 0

            val dataRows = if (lines[0].lowercase().contains("sender")) lines.drop(1) else lines
            val entities = mutableListOf<SenderCacheEntity>()

            for (line in dataRows) {
                val delimiter = if (line.contains(";")) ";" else ","
                val parts = line.split(delimiter).map { it.trim() }
                if (parts.isEmpty() || parts[0].isEmpty()) continue

                val senderName = parts[0]
                val riskScore = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                val riskLevel = parts.getOrNull(2)?.ifBlank { null }
                val isFinalized = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                val cachedAt = parts.getOrNull(4)?.toLongOrNull() ?: System.currentTimeMillis()

                entities.add(
                    SenderCacheEntity(
                        sender = senderName,
                        riskScore = riskScore,
                        riskLevel = riskLevel,
                        isFinalized = isFinalized,
                        cachedAt = cachedAt
                    )
                )
            }

            if (entities.isNotEmpty()) {
                importSenders(entities)
            }
            entities.size
        } catch (e: Exception) {
            0
        }
    }
}

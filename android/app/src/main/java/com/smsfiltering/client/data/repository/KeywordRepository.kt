package com.smsfiltering.client.data.repository

import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.CategoryKeywordEntity
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity
import com.smsfiltering.client.data.local.entity.SyncMetadataEntity
import com.smsfiltering.client.data.local.entity.TopicEntity
import com.smsfiltering.client.data.local.entity.TopicKeywordEntity
import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.remote.api.SmsFilteringApiService
import com.smsfiltering.client.data.remote.dto.KeywordListResponseDto
import com.smsfiltering.client.util.TurkishTextNormalizer

/**
 * Anahtar kelime ve konu (topic) listesini sunucu ile senkronize etmek ve
 * yerel Room veritabanını yönetmek için Repository sınıfı.
 *
 * GET /api/v1/keywords?version={version}
 */
class KeywordRepository(
    private val apiService: SmsFilteringApiService = ApiClient.createService(),
    private val database: AppDatabase? = null
) {

    /**
     * Sunucudan anahtar kelimeleri ve konuları doğrudan sorgular.
     *
     * @param currentVersion İstemcinin elindeki mevcut sürüm numarası (varsayılan: 0).
     * @return Sunucudan gelen sürüm ve kategori/konu listesi.
     */
    suspend fun getKeywords(currentVersion: Long = 0L): NetworkResult<KeywordListResponseDto> {
        return ApiClient.safeApiCall {
            apiService.getKeywords(version = currentVersion)
        }
    }

    /**
     * Yerel veritabanındaki (sync_state / sync_metadata) sürüm bilgisini okur (yoksa 0).
     */
    suspend fun getLocalVersion(): Long {
        if (database == null) return 0L
        val meta = database.syncMetadataDao().getMetadata(AppDatabase.KEY_KEYWORD_VERSION)
        return meta?.value?.toLongOrNull() ?: 0L
    }

    /**
     * Sunucu ile yerel veritabanı arasındaki anahtar kelimeleri ve konuları senkronize eder.
     *
     * - sync_state tablosundaki "keyword_list_version" değerini okur (yoksa 0).
     * - GET /keywords?version=n çağırır.
     * - upToDate=true ise yalnızca son senkron zamanını günceller, başka bir şeyi değiştirmez.
     * - upToDate=false ise tek bir veritabanı işleminde (transaction) keyword_categories,
     *   keywords, topics ve topic_keywords tablolarını sunucudan gelen veriyle değiştirir
     *   (kategori ve konu id'leri sunucudakiyle aynı kalır), yeni sürümü sync_state'e yazar.
     * - Ağ hatasında mevcut veri silinmez, uygulama çökmez, hata sessizce yutulup sonraki denemeye bırakılır.
     *
     * @param force true ise mevcut sürüme bakılmaksızın versiyon 0 ile tam liste istenir.
     */
    suspend fun sync(force: Boolean = false): KeywordSyncResult {
        val currentVersion = if (force) 0L else getLocalVersion()

        return when (val networkResult = getKeywords(currentVersion)) {
            is NetworkResult.Success -> {
                val data = networkResult.data

                if (data.upToDate) {
                    // upToDate=true ise yalnızca son senkron zamanını güncelle, başka bir şeyi değiştirme
                    if (database != null) {
                        val now = System.currentTimeMillis()
                        database.syncMetadataDao().setMetadata(
                            SyncMetadataEntity(
                                key = AppDatabase.KEY_LAST_SYNC_TIME,
                                value = now.toString(),
                                updatedAt = now
                            )
                        )
                    }
                    KeywordSyncResult.AlreadyUpToDate(version = data.version)
                } else {
                    // upToDate=false ise tek bir veritabanı işleminde (transaction)
                    // keyword_categories, keywords, topics ve topic_keywords tablolarını sunucudan gelen veriyle değiştir
                    val categoryEntities = data.categories.map {
                        KeywordCategoryEntity(id = it.id, name = it.name)
                    }
                    val categoryKeywordEntities = data.categories.flatMap { cat ->
                        cat.keywords.map { kw ->
                            CategoryKeywordEntity(
                                categoryId = cat.id,
                                keyword = kw,
                                normalizedKeyword = TurkishTextNormalizer.normalize(kw)
                            )
                        }
                    }

                    val topicEntities = data.topics.map {
                        TopicEntity(id = it.id, name = it.name)
                    }
                    val topicKeywordEntities = data.topics.flatMap { topic ->
                        topic.keywords.map { kw ->
                            TopicKeywordEntity(
                                topicId = topic.id,
                                keyword = kw,
                                normalizedKeyword = TurkishTextNormalizer.normalize(kw)
                            )
                        }
                    }

                    val totalKeywords = categoryKeywordEntities.size + topicKeywordEntities.size

                    if (database != null) {
                        database.syncAll(
                            version = data.version,
                            categories = categoryEntities,
                            categoryKeywords = categoryKeywordEntities,
                            topics = topicEntities,
                            topicKeywords = topicKeywordEntities
                        )
                    }

                    KeywordSyncResult.Success(
                        version = data.version,
                        categoriesCount = data.categories.size,
                        keywordsCount = totalKeywords,
                        topicsCount = data.topics.size
                    )
                }
            }

            is NetworkResult.Error -> {
                // Ağ hatasında mevcut veri silinmez, uygulama çökmez
                KeywordSyncResult.Error(
                    code = networkResult.code,
                    message = networkResult.message
                )
            }

            is NetworkResult.Exception -> {
                // Bağlantı/soket/uçak modu hatasında mevcut veri silinmez, uygulama çökmez
                KeywordSyncResult.Exception(networkResult.throwable)
            }
        }
    }

    /**
     * Geriye uyumluluk için alias: syncKeywordsAndTopics
     */
    suspend fun syncKeywordsAndTopics(force: Boolean = false): KeywordSyncResult = sync(force)

    /**
     * Yerel veritabanında kayıtlı kategorileri ve anahtar kelimelerini harita olarak döner.
     */
    suspend fun getLocalCategoriesWithKeywords(): Map<String, List<String>> {
        if (database == null) return emptyMap()

        val categories = database.keywordCategoryDao().getAllCategories()
        val allKeywords = database.keywordCategoryDao().getAllCategoryKeywords()

        val result = mutableMapOf<String, List<String>>()
        for (cat in categories) {
            result[cat.name] = allKeywords.filter { it.categoryId == cat.id }.map { it.keyword }
        }
        return result
    }

    /**
     * Yerel veritabanında kayıtlı konuları (topics) ve anahtar kelimelerini harita olarak döner.
     */
    suspend fun getLocalTopicsWithKeywords(): Map<String, List<String>> {
        if (database == null) return emptyMap()

        val topics = database.topicDao().getAllTopics()
        val allKeywords = database.topicDao().getAllTopicKeywords()

        val result = mutableMapOf<String, List<String>>()
        for (topic in topics) {
            result[topic.name] = allKeywords.filter { it.topicId == topic.id }.map { it.keyword }
        }
        return result
    }
}

/**
 * Anahtar kelime ve konu senkronizasyon sonucu durumları.
 */
sealed class KeywordSyncResult {
    data class Success(
        val version: Long,
        val categoriesCount: Int,
        val keywordsCount: Int = 0,
        val topicsCount: Int = 0
    ) : KeywordSyncResult()

    data class AlreadyUpToDate(val version: Long) : KeywordSyncResult()
    data class Error(val code: Int, val message: String) : KeywordSyncResult()
    data class Exception(val throwable: Throwable) : KeywordSyncResult()
}

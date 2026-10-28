package com.smsfiltering.client.data.repository

import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.CategoryKeywordEntity
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity
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
     * Yerel veritabanındaki sürüm bilgisini okur.
     */
    suspend fun getLocalVersion(): Long {
        if (database == null) return 0L
        val meta = database.syncMetadataDao().getMetadata(AppDatabase.KEY_KEYWORD_VERSION)
        return meta?.value?.toLongOrNull() ?: 0L
    }

    /**
     * Sunucu ile yerel veritabanı arasındaki anahtar kelimeleri ve konuları senkronize eder.
     *
     * @param force true ise mevcut sürüme bakılmaksızın versiyon 0 ile tam liste istenir.
     */
    suspend fun syncKeywordsAndTopics(force: Boolean = false): KeywordSyncResult {
        val currentVersion = if (force) 0L else getLocalVersion()

        return when (val networkResult = getKeywords(currentVersion)) {
            is NetworkResult.Success -> {
                val data = networkResult.data

                if (data.upToDate) {
                    KeywordSyncResult.AlreadyUpToDate(version = data.version)
                } else {
                    if (database != null) {
                        // Kategorileri ve kelimelerini hazırla
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

                        // Konuları (Topics) ve kelimelerini hazırla
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

                        // Atomik işlemle Room veritabanına kaydet
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
                        topicsCount = data.topics.size
                    )
                }
            }

            is NetworkResult.Error -> {
                KeywordSyncResult.Error(
                    code = networkResult.code,
                    message = networkResult.message
                )
            }

            is NetworkResult.Exception -> {
                KeywordSyncResult.Exception(networkResult.throwable)
            }
        }
    }

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
        val topicsCount: Int
    ) : KeywordSyncResult()

    data class AlreadyUpToDate(val version: Long) : KeywordSyncResult()
    data class Error(val code: Int, val message: String) : KeywordSyncResult()
    data class Exception(val throwable: Throwable) : KeywordSyncResult()
}

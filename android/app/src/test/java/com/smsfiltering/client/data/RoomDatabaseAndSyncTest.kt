package com.smsfiltering.client.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.CategoryKeywordEntity
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.data.local.entity.TopicEntity
import com.smsfiltering.client.data.local.entity.TopicKeywordEntity
import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.KeywordSyncResult
import com.smsfiltering.client.data.repository.SenderRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomDatabaseAndSyncTest {

    private lateinit var database: AppDatabase
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer.shutdown()
    }

    @Test
    fun testCategoryAndTopicEntities_InsertAndQuery() = runBlocking {
        val catDao = database.keywordCategoryDao()
        val topicDao = database.topicDao()

        catDao.insertCategories(listOf(KeywordCategoryEntity(1L, "IBAN")))
        catDao.insertCategoryKeywords(
            listOf(
                CategoryKeywordEntity(id = 1L, categoryId = 1L, keyword = "hesap no", normalizedKeyword = "hesap no")
            )
        )

        topicDao.insertTopics(listOf(TopicEntity(1L, "BANKA"), TopicEntity(2L, "KUMAR")))
        topicDao.insertTopicKeywords(
            listOf(
                TopicKeywordEntity(id = 1L, topicId = 1L, keyword = "kredi kartı", normalizedKeyword = "kredi karti"),
                TopicKeywordEntity(id = 2L, topicId = 2L, keyword = "canlı bahis", normalizedKeyword = "canli bahis")
            )
        )

        val categories = catDao.getAllCategories()
        val categoryKeywords = catDao.getAllCategoryKeywords()
        assertEquals(1, categories.size)
        assertEquals("IBAN", categories[0].name)
        assertEquals(1, categoryKeywords.size)
        assertEquals("hesap no", categoryKeywords[0].keyword)

        val topics = topicDao.getAllTopics()
        val topicKeywords = topicDao.getAllTopicKeywords()
        assertEquals(2, topics.size)
        assertEquals("BANKA", topics[0].name)
        assertEquals("KUMAR", topics[1].name)
        assertEquals(2, topicKeywords.size)
    }

    @Test
    fun testSenderCacheDao_InsertAndCaseInsensitiveLookup() = runBlocking {
        val senderDao = database.senderCacheDao()

        senderDao.insertSender(
            SenderCacheEntity(
                sender = "BANKA-X",
                riskScore = 0.05,
                riskLevel = "risksiz",
                isFinalized = true
            )
        )

        // Küçük harfle ve büyük harfle sorgulama
        val senderLower = senderDao.getSender("banka-x")
        assertNotNull(senderLower)
        assertEquals("BANKA-X", senderLower?.sender)
        assertEquals(0.05, senderLower?.riskScore!!, 0.001)
        assertFalse(senderLower.isDangerous)

        // Tehlikeli gönderen testi
        senderDao.insertSender(
            SenderCacheEntity(
                sender = "SPAMMER-1",
                riskScore = 0.95,
                riskLevel = "riskli",
                isFinalized = true
            )
        )

        val dangerousSender = senderDao.getSender("spammer-1")
        assertNotNull(dangerousSender)
        assertTrue(dangerousSender!!.isDangerous)
    }

    @Test
    fun testKeywordRepository_SyncKeywordsAndTopics_ScenarioA_UpdatesDatabase() = runBlocking {
        val json = """
        {
          "version": 2,
          "upToDate": false,
          "categories": [
            {
              "id": 1,
              "name": "IBAN",
              "keywords": ["iban", "hesap numarası"]
            },
            {
              "id": 2,
              "name": "PIN",
              "keywords": ["pin kodu", "kart şifresi"]
            }
          ],
          "topics": [
            {
              "id": 1,
              "name": "BANKA",
              "keywords": ["kredi kartı", "hesap ekstresi"]
            },
            {
              "id": 2,
              "name": "KUMAR",
              "keywords": ["canlı bahis", "casino"]
            },
            {
              "id": 3,
              "name": "KARGO",
              "keywords": ["kargo takip", "kurye"]
            }
          ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val apiService = ApiClient.createService(mockWebServer.url("/api/v1/").toString())
        val repository = KeywordRepository(apiService, database)

        val syncResult = repository.syncKeywordsAndTopics()
        assertTrue(syncResult is KeywordSyncResult.Success)

        val success = syncResult as KeywordSyncResult.Success
        assertEquals(2L, success.version)
        assertEquals(2, success.categoriesCount)
        assertEquals(3, success.topicsCount)

        // Veritabanını kontrol et
        val localVersion = repository.getLocalVersion()
        assertEquals(2L, localVersion)

        val categories = repository.getLocalCategoriesWithKeywords()
        assertEquals(2, categories.size)
        assertTrue(categories.containsKey("IBAN"))
        assertTrue(categories["IBAN"]!!.contains("hesap numarası"))

        val topics = repository.getLocalTopicsWithKeywords()
        assertEquals(3, topics.size)
        assertTrue(topics.containsKey("KUMAR"))
        assertTrue(topics["KUMAR"]!!.contains("canlı bahis"))
    }

    @Test
    fun testKeywordRepository_SyncKeywordsAndTopics_ScenarioB_AlreadyUpToDate() = runBlocking {
        // Önceden versiyon 2 ayarlı olsun
        database.syncMetadataDao().setMetadata(
            com.smsfiltering.client.data.local.entity.SyncMetadataEntity(
                key = AppDatabase.KEY_KEYWORD_VERSION,
                value = "2"
            )
        )

        val json = """
        {
          "version": 2,
          "upToDate": true,
          "categories": [],
          "topics": []
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val apiService = ApiClient.createService(mockWebServer.url("/api/v1/").toString())
        val repository = KeywordRepository(apiService, database)

        val syncResult = repository.syncKeywordsAndTopics()
        assertTrue(syncResult is KeywordSyncResult.AlreadyUpToDate)
        assertEquals(2L, (syncResult as KeywordSyncResult.AlreadyUpToDate).version)
    }

    @Test
    fun testSenderRepository_CacheLookup() = runBlocking {
        // Önbelleğe gönderen yerleştirelim
        database.senderCacheDao().insertSender(
            SenderCacheEntity(
                sender = "KORUMALI-BANKA",
                riskScore = 0.02,
                riskLevel = "risksiz",
                isFinalized = true
            )
        )

        val apiService = ApiClient.createService(mockWebServer.url("/api/v1/").toString())
        val senderRepository = SenderRepository(apiService, database)

        // WebServer'a istek gitmeden önbellekten dönmeli
        val result = senderRepository.lookupSender("KORUMALI-BANKA")
        assertTrue(result is com.smsfiltering.client.data.remote.NetworkResult.Success)
        val data = (result as com.smsfiltering.client.data.remote.NetworkResult.Success).data
        assertTrue(data.found)
        assertEquals("risksiz", data.riskLevel)
        assertEquals(0, mockWebServer.requestCount)
    }
}

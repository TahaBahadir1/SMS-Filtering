package com.smsfiltering.client.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.CategoryKeywordEntity
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.data.local.entity.SyncMetadataEntity
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
import org.junit.Assert.assertNull
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
    fun testKeywordRepository_Sync_ScenarioA_UpToDateFalse_UpdatesDatabase() = runBlocking {
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

        val syncResult = repository.sync()
        assertTrue(syncResult is KeywordSyncResult.Success)

        val success = syncResult as KeywordSyncResult.Success
        assertEquals(2L, success.version)
        assertEquals(2, success.categoriesCount)
        assertEquals(10, success.keywordsCount) // 4 category keywords + 6 topic keywords = 10
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

        // Son senkron zamanının kaydedildiğini doğrula
        val lastSync = database.syncMetadataDao().getMetadata(AppDatabase.KEY_LAST_SYNC_TIME)
        assertNotNull(lastSync)
    }

    @Test
    fun testKeywordRepository_Sync_ScenarioB_UpToDateTrue_OnlyUpdatesLastSyncTime() = runBlocking {
        // Önceden versiyon 2 ayarlı olsun ve mevcut veri ekleyelim
        database.syncMetadataDao().setMetadata(
            SyncMetadataEntity(
                key = AppDatabase.KEY_KEYWORD_VERSION,
                value = "2"
            )
        )
        database.keywordCategoryDao().insertCategories(listOf(KeywordCategoryEntity(1L, "ESKI_KATEGORI")))
        database.topicDao().insertTopics(listOf(TopicEntity(1L, "ESKI_KONU")))

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

        val syncResult = repository.sync()
        assertTrue(syncResult is KeywordSyncResult.AlreadyUpToDate)
        assertEquals(2L, (syncResult as KeywordSyncResult.AlreadyUpToDate).version)

        // Mevcut veriler silinmemeli
        val categories = database.keywordCategoryDao().getAllCategories()
        assertEquals(1, categories.size)
        assertEquals("ESKI_KATEGORI", categories[0].name)

        val topics = database.topicDao().getAllTopics()
        assertEquals(1, topics.size)
        assertEquals("ESKI_KONU", topics[0].name)

        // Son senkron zamanı güncellenmiş olmalı
        val lastSync = database.syncMetadataDao().getMetadata(AppDatabase.KEY_LAST_SYNC_TIME)
        assertNotNull(lastSync)
    }

    @Test
    fun testKeywordRepository_Sync_NetworkError_PreservesLocalData() = runBlocking {
        // Önceden yerel veriler mevcut olsun
        database.keywordCategoryDao().insertCategories(listOf(KeywordCategoryEntity(1L, "KORUNAN_KAT")))
        database.syncMetadataDao().setMetadata(
            SyncMetadataEntity(key = AppDatabase.KEY_KEYWORD_VERSION, value = "1")
        )

        // Sunucu 500 İç Hata dönüyor
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Internal Server Error\"}")
        )

        val apiService = ApiClient.createService(mockWebServer.url("/api/v1/").toString())
        val repository = KeywordRepository(apiService, database)

        val syncResult = repository.sync()
        assertTrue(syncResult is KeywordSyncResult.Error)
        assertEquals(500, (syncResult as KeywordSyncResult.Error).code)

        // Ağ hatasında mevcut veri silinmez
        val categories = database.keywordCategoryDao().getAllCategories()
        assertEquals(1, categories.size)
        assertEquals("KORUNAN_KAT", categories[0].name)
        assertEquals(1L, repository.getLocalVersion())
    }

    @Test
    fun testKeywordRepository_Sync_NetworkException_DoesNotCrashAndPreservesData() = runBlocking {
        // Önceden yerel veri ekleyelim
        database.keywordCategoryDao().insertCategories(listOf(KeywordCategoryEntity(1L, "KORUNAN_KAT")))
        database.syncMetadataDao().setMetadata(
            SyncMetadataEntity(key = AppDatabase.KEY_KEYWORD_VERSION, value = "1")
        )

        // Sunucuyu durdurarak bağlantı/uçak modu istisnasını taklit edelim
        mockWebServer.shutdown()

        val apiService = ApiClient.createService(mockWebServer.url("/api/v1/").toString())
        val repository = KeywordRepository(apiService, database)

        // Çökmeden Exception sonucunu dönmeli
        val syncResult = repository.sync()
        assertTrue(syncResult is KeywordSyncResult.Exception)

        // Yerel veri aynen korunmalı
        val categories = database.keywordCategoryDao().getAllCategories()
        assertEquals(1, categories.size)
        assertEquals("KORUNAN_KAT", categories[0].name)
    }

    @Test
    fun testRoomMigration_1_to_2_PreservesDataAndAddsTopicId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        dbFile.delete()

        // 1. Sürüm 1 veritabanı şemasını manuel oluştur ve test verisi ekle
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `filtered_messages` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `sender` TEXT NOT NULL,
                                `body` TEXT NOT NULL,
                                `decision` TEXT NOT NULL,
                                `riskScore` REAL NOT NULL,
                                `matchedCategories` TEXT NOT NULL,
                                `matchedTopics` TEXT NOT NULL,
                                `matchedKeywords` TEXT NOT NULL,
                                `reasons` TEXT NOT NULL,
                                `timestamp` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `keyword_categories` (
                                `id` INTEGER NOT NULL,
                                `name` TEXT NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `sync_metadata` (
                                `key` TEXT NOT NULL,
                                `value` TEXT NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                PRIMARY KEY(`key`)
                            )
                            """.trimIndent()
                        )
                        // v1 test mesajı ekle (topic_id yok)
                        db.execSQL(
                            """
                            INSERT INTO `filtered_messages` 
                            (`sender`, `body`, `decision`, `riskScore`, `matchedCategories`, `matchedTopics`, `matchedKeywords`, `reasons`, `timestamp`)
                            VALUES ('BANKA', 'Hesabiniz guvende', 'izin ver', 0.1, '', '', '', '', 1000)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val v1Db = helper.writableDatabase
        v1Db.version = 1

        // 2. MIGRATION_1_2 çalıştır
        AppDatabase.MIGRATION_1_2.migrate(v1Db)
        v1Db.version = 2

        // 3. topics tablosunun oluştuğunu ve veri alabildiğini doğrula
        v1Db.execSQL("INSERT INTO `topics` (`id`, `name`) VALUES (1, 'BANKA')")
        val topicCursor = v1Db.query("SELECT * FROM `topics` WHERE `id` = 1")
        assertTrue(topicCursor.moveToFirst())
        assertEquals("BANKA", topicCursor.getString(topicCursor.getColumnIndexOrThrow("name")))
        topicCursor.close()

        // 4. topic_keywords tablosunun oluştuğunu ve topic_id sütununu içerdiğini doğrula
        v1Db.execSQL("INSERT INTO `topic_keywords` (`topic_id`, `keyword`, `normalizedKeyword`) VALUES (1, 'kredi', 'kredi')")
        val tkCursor = v1Db.query("SELECT * FROM `topic_keywords` WHERE `topic_id` = 1")
        assertTrue(tkCursor.moveToFirst())
        assertEquals("kredi", tkCursor.getString(tkCursor.getColumnIndexOrThrow("keyword")))
        tkCursor.close()

        // 5. filtered_messages tablosunda eski verinin korunduğunu ve topic_id'nin NULL olduğunu doğrula
        val msgCursor = v1Db.query("SELECT * FROM `filtered_messages` WHERE `id` = 1")
        assertTrue(msgCursor.moveToFirst())
        assertEquals("BANKA", msgCursor.getString(msgCursor.getColumnIndexOrThrow("sender")))
        assertEquals("Hesabiniz guvende", msgCursor.getString(msgCursor.getColumnIndexOrThrow("body")))
        val topicIdCol = msgCursor.getColumnIndexOrThrow("topic_id")
        assertTrue(msgCursor.isNull(topicIdCol))
        msgCursor.close()

        // 6. topic_id ile yeni mesaj eklenebildiğini doğrula
        v1Db.execSQL(
            """
            INSERT INTO `filtered_messages` 
            (`sender`, `body`, `decision`, `riskScore`, `matchedCategories`, `matchedTopics`, `matchedKeywords`, `reasons`, `timestamp`, `topic_id`)
            VALUES ('BANKA2', 'Yeni bildirim', 'izin ver', 0.1, '', '', '', '', 2000, 1)
            """.trimIndent()
        )
        val msg2Cursor = v1Db.query("SELECT * FROM `filtered_messages` WHERE `topic_id` = 1")
        assertTrue(msg2Cursor.moveToFirst())
        assertEquals("BANKA2", msg2Cursor.getString(msg2Cursor.getColumnIndexOrThrow("sender")))
        msg2Cursor.close()

        // 7. messages ve sync_state görünümlerini kontrol et
        val viewCursor = v1Db.query("SELECT COUNT(*) FROM `messages`")
        assertTrue(viewCursor.moveToFirst())
        assertEquals(2, viewCursor.getInt(0))
        viewCursor.close()

        v1Db.close()
        helper.close()
        dbFile.delete()
    }

    @Test
    fun testFilteredMessageEntity_WithTopicId() = runBlocking {
        val topicDao = database.topicDao()
        val msgDao = database.filteredMessageDao()

        topicDao.insertTopics(listOf(TopicEntity(1L, "BANKA")))

        val msgId = msgDao.insertMessage(
            FilteredMessageEntity(
                sender = "BANKA",
                body = "Hesap hareketiniz var",
                decision = "izin ver",
                riskScore = 0.1,
                matchedCategories = "",
                matchedTopics = "BANKA",
                matchedKeywords = "",
                reasons = "",
                topicId = 1L
            )
        )

        val messages = msgDao.getMessagesByTopicId(1L)
        assertEquals(1, messages.size)
        assertEquals(msgId, messages[0].id)
        assertEquals(1L, messages[0].topicId)
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

package com.smsfiltering.client.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.repository.SenderRepository
import com.smsfiltering.client.filtering.SmsFilterEngine
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

/**
 * İP 2 & İP 5: Kapalı Döngü Anonim Sinyal Entegrasyonu ve Çevrimdışı Dayanıklılık (Resilience) Testleri.
 */
@RunWith(RobolectricTestRunner::class)
class SenderSignalAndResilienceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: AppDatabase
    private lateinit var senderRepository: SenderRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val apiService = ApiClient.createService(baseUrl = mockWebServer.url("/api/v1/").toString())
        senderRepository = SenderRepository(apiService = apiService, database = database)
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer.shutdown()
    }

    @Test
    fun submitSignal_shouldSendOnlyAnonymousFieldsAndSyncLocalCache() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"sender":"SPAM-TEST","riskScore":0.95,"riskLevel":"riskli","isFinalized":true}""")
        )

        val result = senderRepository.submitSignal(
            sender = "SPAM-TEST",
            source = "manuel",
            score = 0.95
        )

        assertTrue("Sinyal gönderimi başarılı olmalı", result is NetworkResult.Success)
        val success = result as NetworkResult.Success
        assertEquals("SPAM-TEST", success.data.sender)
        assertEquals("riskli", success.data.riskLevel)
        assertTrue(success.data.isFinalized)

        // Gizlilik doğrulaması: istek gövdesinde SMS metni kesinlikle bulunmamalı
        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/senders/signals"))
        val requestBody = recorded.body.readUtf8()
        assertTrue(requestBody.contains("\"sender\":\"SPAM-TEST\""))
        assertTrue(requestBody.contains("\"source\":\"manuel\""))
        assertFalse("İstek gövdesinde SMS metni bulunmamalı", requestBody.contains("body"))

        // Yerel Room önbelleği güncellenmiş olmalı
        val cached = database.senderCacheDao().getSender("SPAM-TEST")
        assertNotNull(cached)
        assertEquals(0.95, cached!!.riskScore!!, 0.001)
        assertTrue(cached.isFinalized)
    }

    @Test
    fun submitSignal_whenServerReturns429OrOffline_shouldFailGracefullyWithoutCrash() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":"Çok fazla istek gönderildi"}""")
        )

        val rateLimited = senderRepository.submitSignal(sender = "SPAM-429", source = "manuel", score = 0.90)
        assertTrue("429 yanıtı kontrollü hata dönmeli", rateLimited is NetworkResult.Error)
        assertEquals(429, (rateLimited as NetworkResult.Error).code)

        // Geçersiz skor veya kaynak istemci tarafında reddedilmeli (ağ isteği atılmamalı)
        val invalidScore = senderRepository.submitSignal(sender = "SPAM-X", source = "manuel", score = 1.5)
        assertTrue(invalidScore is NetworkResult.Error)
        assertEquals(400, (invalidScore as NetworkResult.Error).code)

        val invalidSource = senderRepository.submitSignal(sender = "SPAM-X", source = "bilinmeyen", score = 0.5)
        assertTrue(invalidSource is NetworkResult.Error)
        assertEquals(400, (invalidSource as NetworkResult.Error).code)
    }

    @Test
    fun offlineBurstResilience_25RapidMessagesWhileServerDown_shouldPersistAllWithoutDataLoss() = runBlocking {
        // Sunucuyu kapat (uçak modu / sunucu erişilemez senaryosu)
        mockWebServer.shutdown()

        val engine = SmsFilterEngine(
            database = database,
            senderRepository = senderRepository
        )

        val count = 25
        for (i in 1..count) {
            val res = engine.filterSms(
                sender = "+90555000${String.format("%04d", i)}",
                body = if (i % 2 == 0) "Merhaba deneme mesajı #$i" else "Acil ödeme için tıklayınız https://test-$i.xyz",
                useOnlineSenderLookup = true,
                saveToDatabase = true
            )
            assertNotNull(res.decision)
        }

        val saved = database.filteredMessageDao().getAllMessages()
        assertEquals("Sunucu erişilemezken tüm 25 SMS yerel veritabanına eksiksiz kaydedilmeli", count, saved.size)
    }
}

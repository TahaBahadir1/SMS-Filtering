package com.smsfiltering.client.evaluation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.repository.SenderRepository
import com.smsfiltering.client.filtering.SmsClassifier
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterEngine
import com.smsfiltering.client.ui.AppTab
import com.smsfiltering.client.ui.MessagesViewModel
import com.smsfiltering.client.ui.NavigationScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import java.io.File
import java.io.FileInputStream

/**
 * Hafta 7 - İP 6: Tam Döngü (Full-Cycle) Entegrasyon ve Final Demo Senaryosu Testi.
 *
 * Tüm 7. hafta iş paketlerinin (İP 1–5) birlikte uyum içinde çalıştığını uçtan uca doğrular:
 * 1. Filtre atlatma (adversarial/obfuscated) saldırı SMS'inin güçlendirilmiş SmsCleaner + SmsClassifier ile yakalanması (İP 3)
 * 2. Güvenlik özeti panosu sayaçları (totalScannedCount, blocked/quarantine) ve arama filtresi (İP 4)
 * 3. Bildirim deep-link ile doğrudan mesaj detay ekranının açılması (İP 4)
 * 4. Kullanıcı kararı sonrası SMS metni paylaşılmadan sunucuya anonim risk sinyali iletilmesi ve kesinleşme (İP 1 & İP 2)
 */
@RunWith(RobolectricTestRunner::class)
class FullCycleWeek7IntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var mockWebServer: MockWebServer
    private lateinit var classifier: SmsClassifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        mockWebServer = MockWebServer()
        mockWebServer.start()

        val possiblePaths = listOf(
            File("src/main/assets/sms_model_assets.json"),
            File("app/src/main/assets/sms_model_assets.json"),
            File("../../ai/sms_model_assets.json"),
            File("../ai/sms_model_assets.json"),
            File("ai/sms_model_assets.json")
        )
        val modelFile = possiblePaths.firstOrNull { it.exists() }
            ?: throw IllegalStateException("sms_model_assets.json bulunamadı")
        FileInputStream(modelFile).use {
            classifier = SmsClassifier.loadFromStream(it)
        }
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer.shutdown()
    }

    @Test
    fun fullCycle_adversarialSms_dashboard_search_deepLink_andAnonymousSignal() = runBlocking {
        // 1. Sunucu MockWebServer: lookup (found=false) ve signals (200 OK, isFinalized=true)
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"found":false,"riskScore":null,"riskLevel":null,"isFinalized":false}""")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"sender":"ADVR-TEST","riskScore":0.95,"riskLevel":"riskli","isFinalized":true}""")
        )

        val apiService = ApiClient.createService(baseUrl = mockWebServer.url("/api/v1/").toString())
        val senderRepo = SenderRepository(apiService = apiService, database = database)
        val engine = SmsFilterEngine(
            database = database,
            senderRepository = senderRepo,
            classifier = classifier
        )

        // 2. Çok katmanlı gizlenmiş (leetspeak + görünmez Unicode + noktalama) saldırı SMS'i
        val obfuscatedSms = "G.Ü.N.C.3.L B\u200B@H1S V3 B-0-N-U-S FIRS@@@TI! 1lk y\u200C@t1r1m@ %200 ç_3_v_r_1_m_s_1_z b000nus h.3.m.3.n k@yd0l"
        val filterResult = engine.filterSms(
            sender = "ADVR-TEST",
            body = obfuscatedSms,
            useOnlineSenderLookup = true,
            saveToDatabase = true
        )

        // Güçlendirilmiş SmsCleaner sayesinde model yakalamalı ve karar SAFE olmamalı
        assertTrue("Adversarial SMS tespit edilmeli (P >= 0.50)", (filterResult.modelSpamProbability ?: 0.0) >= 0.50)
        assertTrue("Karar SAFE olmamalı", filterResult.decision != SmsFilterDecision.SAFE)

        val allMessages = database.filteredMessageDao().getAllMessages()
        assertEquals(1, allMessages.size)
        val savedMsg = allMessages.first()

        // 3. ViewModel: Güvenlik Özeti Panosu sayaçları, arama ve bildirim deep-link (İP 4)
        val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val viewModel = MessagesViewModel(
            context = context,
            database = database,
            scope = testScope,
            ioDispatcher = Dispatchers.IO
        )

        val targetTab = if (filterResult.decision == SmsFilterDecision.SUSPICIOUS) AppTab.QUARANTINE else AppTab.BLOCKED
        viewModel.selectTab(targetTab).join()
        assertEquals(1, viewModel.uiState.value.totalScannedCount)

        // Arama filtresi testi
        viewModel.setSearchQuery("ADVR-TEST").join()
        assertEquals(1, viewModel.uiState.value.messages.size)
        viewModel.setSearchQuery("OLMAYAN-GONDEREN").join()
        assertEquals(0, viewModel.uiState.value.messages.size)
        viewModel.setSearchQuery("").join()

        // Deep-link ile doğrudan mesaj detayına geçiş testi
        viewModel.openMessageById(savedMsg.id).join()
        assertEquals(NavigationScreen.MESSAGE_DETAIL, viewModel.uiState.value.currentNav)
        assertNotNull(viewModel.uiState.value.selectedMessage)
        assertEquals(savedMsg.id, viewModel.uiState.value.selectedMessage?.id)

        // 4. Kullanıcı kararı sonrası kapalı döngü anonim sinyal gönderimi (İP 1 & İP 2)
        val signalResult = senderRepo.submitSignal(
            sender = savedMsg.sender,
            source = "manuel",
            score = 0.95
        )
        assertTrue("Anonim sinyal sunucuya başarıyla iletilmeli", signalResult is NetworkResult.Success)
        val signalData = (signalResult as NetworkResult.Success).data
        assertEquals("riskli", signalData.riskLevel)
        assertTrue("Manuel sinyal sunucuda kesinleşmiş olmalı", signalData.isFinalized)

        // Gizlilik doğrulaması: lookup + signals isteklerinin hiçbirinde SMS metni yer almamalı
        val lookupReq = mockWebServer.takeRequest()
        val signalReq = mockWebServer.takeRequest()
        assertTrue(lookupReq.path!!.endsWith("/senders/lookup"))
        assertTrue(signalReq.path!!.endsWith("/senders/signals"))
        val signalReqBody = signalReq.body.readUtf8()
        assertTrue(signalReqBody.contains("\"sender\":\"ADVR-TEST\""))
        assertFalse("Gizlilik ihlali: SMS metni sunucuya gönderilmemeli", signalReqBody.contains("B@H1S") || signalReqBody.contains("body"))
    }
}

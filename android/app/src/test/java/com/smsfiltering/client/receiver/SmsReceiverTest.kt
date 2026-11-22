package com.smsfiltering.client.receiver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity
import com.smsfiltering.client.data.local.entity.TopicEntity
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterEngine
import com.smsfiltering.client.filtering.SmsFilterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmsReceiverTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var filterEngine: SmsFilterEngine

    private val categories = mapOf(
        "SIFRE" to listOf("şifre", "parola", "şifrenizi girin"),
        "LINK" to listOf("link", "tıklayın")
    )

    private val topics = mapOf(
        "KARGO" to listOf("kargo", "kargonuz", "teslimat", "teslim")
    )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()

        // İzinleri mockla
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.grantPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS)

        // Bellek içi test veritabanı
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Test kategorilerini ve konularını veritabanına tohumla
        val catEntities = categories.keys.mapIndexed { idx, name ->
            com.smsfiltering.client.data.local.entity.KeywordCategoryEntity((idx + 1).toLong(), name)
        }
        val catKeywords = categories.flatMap { (catName, kws) ->
            val catId = catEntities.first { it.name == catName }.id
            kws.map { com.smsfiltering.client.data.local.entity.CategoryKeywordEntity(categoryId = catId, keyword = it, normalizedKeyword = it) }
        }

        val topicEntities = topics.keys.mapIndexed { idx, name ->
            com.smsfiltering.client.data.local.entity.TopicEntity((idx + 1).toLong(), name)
        }
        val topicKeywords = topics.flatMap { (topicName, kws) ->
            val tId = topicEntities.first { it.name == topicName }.id
            kws.map { com.smsfiltering.client.data.local.entity.TopicKeywordEntity(topicId = tId, keyword = it, normalizedKeyword = it) }
        }

        database.syncAll(1L, catEntities, catKeywords, topicEntities, topicKeywords)

        filterEngine = SmsFilterEngine(database = database)

        // SmsReceiver test bağımlılıkları
        SmsReceiver.injectedFilterEngine = filterEngine
        SmsReceiver.injectedDispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        SmsReceiver.resetTestOverrides()
        database.close()
    }

    @Test
    fun testOnReceive_IgnoresWrongAction() {
        var processed = false
        SmsReceiver.onSmsProcessed = { processed = true }

        val receiver = SmsReceiver()
        val intent = Intent("android.intent.action.BOOT_COMPLETED")

        receiver.onReceive(context, intent)

        org.junit.Assert.assertFalse("Yanlış action işlenmemelidir", processed)
    }

    @Test
    fun testOnReceive_IgnoresEmptyPdus() {
        var processed = false
        SmsReceiver.onSmsProcessed = { processed = true }

        val receiver = SmsReceiver()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", emptyArray<ByteArray>())
        }

        receiver.onReceive(context, intent)

        org.junit.Assert.assertFalse("Boş PDU işlenmemelidir", processed)
    }

    @Test
    fun testOnReceive_RealtimeSmsCapture_PhishingDetection() = runBlocking {
        // Hazırlık: Test verisi
        val pdu = createGsmPdu(
            sender = "+905551234567",
            message = "Tebrikler 1000 TL bonus kazandınız! Şifrenizi girin: https://sahte-banka.xyz"
        )

        val latch = CountDownLatch(1)
        var capturedResult: SmsFilterResult? = null

        SmsReceiver.onSmsProcessed = { result ->
            capturedResult = result
            latch.countDown()
        }

        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pdu))
            putExtra("format", "3gpp")
        }

        val receiver = SmsReceiver()
        receiver.onReceive(context, intent)

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertNotNull(capturedResult)

        val result = capturedResult!!
        assertTrue(result.sender.contains("905551234567"))
        assertEquals(SmsFilterDecision.PHISHING, result.decision)
        assertTrue(result.riskScore >= 0.85)

        // Veritabanına kaydedildiğini doğrula
        val savedMessages = database.filteredMessageDao().getAllMessages()
        assertEquals(1, savedMessages.size)
        assertEquals(SmsFilterDecision.PHISHING.name, savedMessages[0].decision)
    }

    @Test
    fun testOnReceive_MultipartSmsConcatenation() = runBlocking {
        // İki parçalı uzun bir SMS simülasyonu
        val part1 = createGsmPdu(
            sender = "+905559998877",
            message = "Kargonuz adresinize teslim edilemedi. "
        )
        val part2 = createGsmPdu(
            sender = "+905559998877",
            message = "Güncellemek için: https://kargo-takip.xyz/teslimat"
        )

        val latch = CountDownLatch(1)
        var capturedResult: SmsFilterResult? = null

        SmsReceiver.onSmsProcessed = { result ->
            capturedResult = result
            latch.countDown()
        }

        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(part1, part2))
            putExtra("format", "3gpp")
        }

        val receiver = SmsReceiver()
        receiver.onReceive(context, intent)

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertNotNull(capturedResult)

        val result = capturedResult!!
        // Parçaların birleştiğini doğrula
        assertTrue(result.body.contains("Kargonuz adresinize teslim edilemedi."))
        assertTrue(result.body.contains("https://kargo-takip.xyz/teslimat"))
        assertEquals(SmsFilterDecision.PHISHING, result.decision)
    }

    @Test
    fun testOnReceive_SafeMessage_ProcessedAndSaved() = runBlocking {
        val pdu = createGsmPdu(
            sender = "+905550001122",
            message = "Yarın saat 14:00'te toplantımız var, görüşmek üzere."
        )

        val latch = CountDownLatch(1)
        var capturedResult: SmsFilterResult? = null

        SmsReceiver.onSmsProcessed = { result ->
            capturedResult = result
            latch.countDown()
        }

        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pdu))
            putExtra("format", "3gpp")
        }

        val receiver = SmsReceiver()
        receiver.onReceive(context, intent)

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertNotNull(capturedResult)

        val result = capturedResult!!
        assertEquals(SmsFilterDecision.SAFE, result.decision)
        // docs/karar-hatti.md: Kayıtsız gönderen varsayılan skoru 0.30 (< 0.50 → SAFE)
        assertEquals(0.30, result.riskScore, 0.01)

        val savedMessages = database.filteredMessageDao().getAllMessages()
        assertEquals(1, savedMessages.size)
        assertEquals(SmsFilterDecision.SAFE.name, savedMessages[0].decision)
    }

    /**
     * 3GPP GSM SMS-DELIVER PDU oluşturan yardımcı fonksiyon (UCS-2 kodlama).
     */
    private fun createGsmPdu(sender: String, message: String): ByteArray {
        val output = ByteArrayOutputStream()

        // 1. SCA (Service Center Address) length = 0 (varsayılan SMSC)
        output.write(0x00)

        // 2. First octet: SMS-DELIVER
        output.write(0x04)

        // 3. Gönderici numara uzunluğu ve formatı
        val cleanSender = sender.removePrefix("+")
        output.write(cleanSender.length)
        output.write(0x91) // Type of Number: International, ISDN/telephone

        // Gönderici yarı-sekizli (semi-octet) basamakları
        for (i in 0 until cleanSender.length step 2) {
            val d1 = cleanSender[i]
            val d2 = if (i + 1 < cleanSender.length) cleanSender[i + 1] else 'F'
            val byteVal = (Character.digit(d2, 16) shl 4) or Character.digit(d1, 16)
            output.write(byteVal)
        }

        // 4. TP-PID (Protokol Tanımlayıcı): Standart SMS = 0x00
        output.write(0x00)

        // 5. TP-DCS (Veri Kodlama): UCS-2 (16-bit) = 0x08
        output.write(0x08)

        // 6. TP-SCTS (Zaman Damgası): 7 bayt
        output.write(byteArrayOf(0x62, 0x11, 0x31, 0x21, 0x03, 0x00, 0x00))

        // 7. TP-UDL & TP-UD: Metin içeriği (UCS-2 / UTF-16BE baytları)
        val ucs2Bytes = message.toByteArray(Charsets.UTF_16BE)
        output.write(ucs2Bytes.size)
        output.write(ucs2Bytes)

        return output.toByteArray()
    }
}

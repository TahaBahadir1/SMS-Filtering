package com.smsfiltering.client.filtering

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.CategoryKeywordEntity
import com.smsfiltering.client.data.local.entity.KeywordCategoryEntity
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.data.local.entity.TopicEntity
import com.smsfiltering.client.data.local.entity.TopicKeywordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsFilterEngineTest {

    private lateinit var database: AppDatabase
    private lateinit var engine: SmsFilterEngine

    private val categories = mapOf(
        "IBAN" to listOf("iban", "hesap numarası", "hesap no", "banka hesabı", "para transferi", "havale", "eft", "fast transfer", "alici iban", "iban bilgisi"),
        "PIN" to listOf("pin kodu", "kart şifresi", "güvenlik kodu", "cvv", "onay kodu", "doğrulama pini", "mobil pin", "sim pin", "tek kullanımlık pin", "pin girişi"),
        "SIFRE" to listOf("şifre", "parola", "şifrenizi paylaşmayın", "geçici şifre", "parola sıfırlama", "giriş şifresi", "şifre yenileme", "tek kullanımlık şifre", "otp doğrulama", "şifre talebi"),
        "LINK" to listOf("tıklayınız", "linke tıklayın", "bağlantıya git", "detaylar için tıkla", "link", "web sitemiz", "güncellemek için tıklayın", "formu doldurun", "adrese giriniz", "hemen tıkla"),
        "ACIL_ODEME" to listOf("acil ödeme", "son ödeme tarihi", "borcunuz bulunmaktadır", "haciz işlemi", "icra takibi", "ödenmemiş fatura", "gecikmiş borç", "derhal ödeyiniz", "son gün bugün", "askıya alınacaktır")
    )

    private val topics = mapOf(
        "BANKA" to listOf("kredi kartı", "hesap ekstresi", "kredi başvurusu", "vadeli mevduat", "şube", "bankamatik", "kart aidatı", "kredi borcu"),
        "KUMAR" to listOf("canlı bahis", "slot oyunu", "deneme bonusu", "çevrimsiz bonus", "casino", "kayıp bonusu", "güvenilir bahis", "bahis oranları"),
        "BELEDIYE" to listOf("belediye başkanlığı", "su kesintisi", "çevre temizlik vergisi", "fen işleri", "emlak vergisi", "muhtarlık", "belediye duyurusu", "askıda fatura"),
        "KARGO" to listOf("kargo takip", "teslimat şubesi", "gönderiniz yola çıktı", "dağıtıma çıkarıldı", "kurye", "kargo takip no", "adreste bulunamadı", "teslim kodu"),
        "OPERATOR" to listOf("fatura dönemi", "kalan internet paketi", "taahhüt yenileme", "tarife değişikliği", "faturalı hat", "tl yükleme", "data paketi", "paket aşımı")
    )

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        engine = SmsFilterEngine(database = database)

        // Veritabanına test verilerini tohumla
        val catEntities = categories.keys.mapIndexed { idx, name -> KeywordCategoryEntity((idx + 1).toLong(), name) }
        val catKeywords = categories.flatMap { (catName, kws) ->
            val catId = catEntities.first { it.name == catName }.id
            kws.map { CategoryKeywordEntity(categoryId = catId, keyword = it, normalizedKeyword = it) }
        }

        val topicEntities = topics.keys.mapIndexed { idx, name -> TopicEntity((idx + 1).toLong(), name) }
        val topicKeywords = topics.flatMap { (topicName, kws) ->
            val tId = topicEntities.first { it.name == topicName }.id
            kws.map { TopicKeywordEntity(topicId = tId, keyword = it, normalizedKeyword = it) }
        }

        database.syncAll(2L, catEntities, catKeywords, topicEntities, topicKeywords)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testKumarTopic_FlagsAsSpam() {
        val result = engine.evaluate(
            sender = "+905550001122",
            body = "1000 TL Çevrimsiz Bonus hesabınıza tanımlandı! Canlı bahis ve casino için hemen tıkla: https://bahis123.com",
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.SPAM, result.decision)
        assertTrue(result.matchedTopics.containsKey("KUMAR"))
        assertTrue(result.riskScore >= 0.75)
        assertTrue(result.extractedUrls.contains("https://bahis123.com"))
    }

    @Test
    fun testKargoTopic_Phishing_WhenLinked() {
        val result = engine.evaluate(
            sender = "KARGO-INFO",
            body = "Gönderiniz adreste bulunamadı. Kargo takip ve teslimat şubesi randevusu için: bit.ly/kargo-adresi",
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.PHISHING, result.decision)
        assertTrue(result.matchedTopics.containsKey("KARGO"))
        assertTrue(result.riskScore >= 0.85)
        assertTrue(result.reasons.any { it.contains("Kargo teslimat") })
    }

    @Test
    fun testBankaTopic_Phishing_WithCredentialsAndLink() {
        val result = engine.evaluate(
            sender = "08501234567",
            body = "Kredi kartı aidatınızı iade almak için kart şifresi giriniz. Detaylar için linke tıklayın: http://sahte-banka.xyz",
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.PHISHING, result.decision)
        assertTrue(result.matchedTopics.containsKey("BANKA"))
        assertTrue(result.matchedCategories.containsKey("PIN"))
        assertTrue(result.riskScore >= 0.85)
    }

    @Test
    fun testOperatorTopic_Phishing_WithAcilOdemeAndLink() {
        val result = engine.evaluate(
            sender = "+905420000000",
            body = "Faturalı hat için ödenmemiş fatura borcunuz bulunmaktadır. Derhal ödeyiniz: https://fatura-ode.top",
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.PHISHING, result.decision)
        assertTrue(result.matchedTopics.containsKey("OPERATOR"))
        assertTrue(result.matchedCategories.containsKey("ACIL_ODEME"))
    }

    @Test
    fun testBelediyeTopic_Phishing_WithAcilOdemeAndLink() {
        val result = engine.evaluate(
            sender = "BELEDIYE",
            body = "Çevre temizlik vergisi son ödeme tarihi geçti. Askıda fatura ve haciz işlemi için: https://belediye-borc.net",
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.PHISHING, result.decision)
        assertTrue(result.matchedTopics.containsKey("BELEDIYE"))
        assertTrue(result.matchedCategories.containsKey("ACIL_ODEME"))
    }

    @Test
    fun testDangerousSender_EvaluatesAsDangerousAndPhishing() {
        // riskLevel: riskli VE isFinalized: true -> Tehlikeli gönderen
        val result = engine.evaluate(
            sender = "SPAM-SENDER",
            body = "Merhaba, size özel tekliflerimiz var.",
            senderRiskScore = 0.95,
            senderRiskLevel = "riskli",
            isFinalized = true,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertTrue(result.isDangerousSender)
        assertEquals(SmsFilterDecision.PHISHING, result.decision)
        assertTrue(result.riskScore >= 0.85)
    }

    @Test
    fun testSafeSender_LegitimateNotification_IsSafe() {
        val result = engine.evaluate(
            sender = "RESMI-BANKA",
            body = "Vadeli mevduat hesabınızın faiz tahakkuku gerçekleşmiştir.",
            senderRiskScore = 0.05,
            senderRiskLevel = "risksiz",
            isFinalized = true,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertFalse(result.isDangerousSender)
        assertEquals(SmsFilterDecision.SAFE, result.decision)
        assertEquals(0.05, result.riskScore, 0.01)
    }

    @Test
    fun testTurkishCharactersNormalization_Detection() {
        val result = engine.evaluate(
            sender = "+905321112233",
            body = "CANLI BAHİS İÇİN ÇEVRİMSİZ BONUS VE SLOT OYUNU FIRSATI!",
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.SPAM, result.decision)
        assertTrue(result.matchedTopics.containsKey("KUMAR"))
        val kumarMatches = result.matchedTopics["KUMAR"]!!
        assertTrue(kumarMatches.contains("canlı bahis"))
        assertTrue(kumarMatches.contains("çevrimsiz bonus"))
        assertTrue(kumarMatches.contains("slot oyunu"))
    }

    @Test
    fun testFilterSmsWithDatabase_PersistsToFilteredMessages() = runBlocking {
        // Yerel önbelleğe sender ekle
        database.senderCacheDao().insertSender(
            SenderCacheEntity(
                sender = "DOLANDIRICI",
                riskScore = 0.88,
                riskLevel = "riskli",
                isFinalized = true
            )
        )

        val result = engine.filterSms(
            sender = "DOLANDIRICI",
            body = "Kart şifresi güncellemek için tıklayınız: http://phish.me",
            useOnlineSenderLookup = false,
            saveToDatabase = true
        )

        assertEquals(SmsFilterDecision.PHISHING, result.decision)
        assertTrue(result.isDangerousSender)

        // Veritabanındaki mesajı doğrula
        val saved = database.filteredMessageDao().getAllMessages()
        assertEquals(1, saved.size)
        assertEquals("DOLANDIRICI", saved[0].sender)
        assertEquals("PHISHING", saved[0].decision)
    }
}

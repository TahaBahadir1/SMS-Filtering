package com.smsfiltering.client.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * docs/karar-hatti.md (Gizlilik Odaklı Kademeli Karar Mimarisi) belgesindeki
 * formül, eşik ve senaryoların birim testleri.
 */
class KararHattiPipelineTest {

    private lateinit var engine: SmsFilterEngine
    private var classifier: SmsClassifier? = null

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
    fun setUp() {
        val possiblePaths = listOf(
            File("src/main/assets/sms_model_assets.json"),
            File("app/src/main/assets/sms_model_assets.json"),
            File("../../ai/sms_model_assets.json"),
            File("../ai/sms_model_assets.json"),
            File("ai/sms_model_assets.json")
        )
        val modelFile = possiblePaths.firstOrNull { it.exists() }
        if (modelFile != null) {
            FileInputStream(modelFile).use {
                classifier = SmsClassifier.loadFromStream(it)
            }
        }
        engine = SmsFilterEngine(classifier = classifier)
    }

    // --- docs/karar-hatti.md Sabitler ve Formül Tablosu Testleri ---

    @Test
    fun testKararHatti_Ornek1_TemizMesaj_BilinmeyenGonderen() {
        // Tablo Satır 1: Temiz mesaj, Bilinmeyen gönderen, k=0, P=0.05 -> Puan=0.30 -> İzin ver
        val result = engine.evaluate(
            sender = "+905550000099",
            body = "Selam, nasılsın bugün görüşebilir miyiz?",
            senderRiskScore = null, // Kayıt yoksa 0.3
            modelSpamProbability = 0.05,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals("Puan 0.30 olmalı", 0.30, result.riskScore, 0.01)
        assertEquals(SmsFilterDecision.SAFE, result.decision)
    }

    @Test
    fun testKararHatti_Ornek2_ReklamKumar_BilinmeyenGonderen() {
        // Tablo Satır 2: Reklam / kumar, Bilinmeyen, k=0, P=0.90 -> Puan=0.3 + 0.4*0.90 = 0.66 -> Karantina
        val result = engine.evaluate(
            sender = "SPAM-PROMO",
            body = "Casino oyunlarında yeni fırsatlar sizi bekliyor.",
            senderRiskScore = null, // 0.3
            modelSpamProbability = 0.90,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertTrue("Puan 0.66 civarı olmalı", result.riskScore >= 0.66)
        assertEquals(SmsFilterDecision.SUSPICIOUS, result.decision)
    }

    @Test
    fun testKararHatti_Ornek3_IbanLinkAcilOdeme_BilinmeyenGonderen() {
        // Tablo Satır 3: IBAN + link + acil ödeme, Bilinmeyen, k=3, P=0.99 -> Puan=1.00 -> Engelle
        val result = engine.evaluate(
            sender = "ICRA-DAIRESI",
            body = "Hakkınızda icra takibi var. Derhal ödeyiniz. Alıcı IBAN: TR00 0000 0000. https://e-yargi.com",
            senderRiskScore = null, // 0.3
            modelSpamProbability = 0.99,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(1.00, result.riskScore, 0.01)
        assertEquals(SmsFilterDecision.PHISHING, result.decision)
    }

    @Test
    fun testKararHatti_Ornek4_BankaDogrulamaKodu_BankaX() {
        // Tablo Satır 4: Banka doğrulama kodu, BANKA-X (0.05), k=1 (şifre), P=0.10 -> Puan = 0.05 + 0.2*1 = 0.25 -> İzin ver
        val result = engine.evaluate(
            sender = "BANKA-X",
            body = "584920 nolu tek kullanımlık onay şifreniz ile işleminizi yapınız.",
            senderRiskScore = 0.05,
            senderRiskLevel = "risksiz",
            modelSpamProbability = 0.10,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.SAFE, result.decision)
        assertTrue(result.riskScore < 0.50)
    }

    @Test
    fun testKararHatti_Ornek5_TaklitBankaX_LinkVeIban() {
        // Tablo Satır 5: Taklit BANKA-X, link + IBAN, BANKA-X (0.05), k=2, P=0.97 -> Puan = 0.05 + 0.4 + 0.4*0.97 = 0.838 -> Engelle
        val result = engine.evaluate(
            sender = "BANKA-X",
            body = "Kart aidat iadesi için IBAN bilginizi giriniz: https://banka-iade.top",
            senderRiskScore = 0.05,
            senderRiskLevel = "risksiz",
            modelSpamProbability = 0.97,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertTrue(result.riskScore >= 0.80)
        assertEquals(SmsFilterDecision.PHISHING, result.decision)
    }

    @Test
    fun testKararHatti_Ornek6_DogrulamaKodu_BilinmeyenGonderen() {
        // Tablo Satır 6: Doğrulama kodu ("onay kodu" + "şifre"), Bilinmeyen (0.3), k=2, P=0.15 -> Puan = 0.3 + 0.4 = 0.70 -> Karantina
        val result = engine.evaluate(
            sender = "+905550000000",
            body = "Onay kodu ve şifrenizi girerek giriş yapınız.",
            senderRiskScore = null, // 0.3
            modelSpamProbability = 0.15,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(0.70, result.riskScore, 0.01)
        assertEquals(SmsFilterDecision.SUSPICIOUS, result.decision)
    }

    @Test
    fun testKararHatti_Ornek7_KargoBildirimiLink_BilinmeyenGonderen() {
        // Tablo Satır 7: Kargo bildirimi, link, Bilinmeyen (0.3), k=1, P=0.20 -> Puan = 0.3 + 0.2 = 0.50 -> Karantina
        val result = engine.evaluate(
            sender = "BILINMEYEN-KARGO",
            body = "Kargonuzu takip etmek için: https://kargo-takip.xyz",
            senderRiskScore = null, // 0.3
            modelSpamProbability = 0.20,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertTrue(result.riskScore >= 0.50)
        assertEquals(SmsFilterDecision.SUSPICIOUS, result.decision)
    }

    @Test
    fun testKararHatti_Ornek8_TemizMesaj_KrediDestekGonderen() {
        // Tablo Satır 8: Temiz mesaj, KREDI-DESTEK (0.55), k=0, P=0.10 -> Puan = 0.55 -> Karantina
        val result = engine.evaluate(
            sender = "KREDI-DESTEK",
            body = "İyi günler dileriz, saygılarımızla.",
            senderRiskScore = 0.55,
            senderRiskLevel = "orta_riskli",
            modelSpamProbability = 0.10,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(0.55, result.riskScore, 0.01)
        assertEquals(SmsFilterDecision.SUSPICIOUS, result.decision)
    }

    @Test
    fun testKararHatti_UrlKurali_GuvenliMesajiKarantinayaYukseltir() {
        // Adım 7: Mesaj bağlantı içeriyorsa ve karar "izin ver" ise karantinaya yükseltilir.
        val result = engine.evaluate(
            sender = "DEFACTO",
            body = "Yaz sezonu indirimleri başladı: http://defacto.com",
            senderRiskScore = 0.05,
            senderRiskLevel = "risksiz",
            modelSpamProbability = 0.10,
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        // URL olduğu için SAFE kalamaz, SUSPICIOUS (Karantina) olmalı
        assertEquals(SmsFilterDecision.SUSPICIOUS, result.decision)
        assertTrue(result.reasons.any { it.contains("URL kuralı") || it.contains("Bağlantı") })
    }

    @Test
    fun testKararHatti_KullaniciKarari_Onceliklidir() {
        // Adım 1: Kullanıcı kararı varsa hat biter, URL kuralı uygulanmaz
        val result = engine.evaluate(
            sender = "OZEL-GONDEREN",
            body = "Bu linke tıklayın: http://ozel-link.com",
            isUserDecision = true,
            userDecisionState = "SAFE",
            categoriesWithKeywords = categories,
            topicsWithKeywords = topics
        )

        assertEquals(SmsFilterDecision.SAFE, result.decision)
        assertEquals(0.05, result.riskScore, 0.01)
    }
}


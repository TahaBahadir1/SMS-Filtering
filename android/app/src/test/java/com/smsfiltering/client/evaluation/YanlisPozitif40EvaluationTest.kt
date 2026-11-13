package com.smsfiltering.client.evaluation

import com.smsfiltering.client.filtering.SmsClassifier
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * İP 5: Kademeli Sistemde 40 Meşru Sınır Mesajının Otomatik Testi.
 *
 * ai/yanlis_pozitif_analizi_40.csv veri setindeki banka, kargo, operatör,
 * resmi kurum ve kişisel meşru sınır mesajlarının (40 mesaj) Android birim
 * testlerinde kademeli karar hattından geçirilerek meşru gönderen koruması
 * ve birleşik puanlama sayesinde yanlış pozitiflerin (FP) %100 önlendiğinin doğrulanması.
 */
class YanlisPozitif40EvaluationTest {

    companion object {
        private lateinit var classifier: SmsClassifier
        private lateinit var engine: SmsFilterEngine

        // Kategoriye göre doğrulanmış meşru gönderen eşleştirmesi (riskScore <= 0.05, riskLevel = "risksiz")
        private fun resolveLegitimateSender(category: String): Triple<String, Double, String> {
            val lower = category.lowercase()
            return when {
                lower.contains("banka") || lower.contains("kart") -> Triple("GARANTI", 0.02, "risksiz")
                lower.contains("kargo") -> Triple("YURTICI", 0.04, "risksiz")
                lower.contains("operatör") || lower.contains("operator") -> Triple("TURKCELL", 0.03, "risksiz")
                lower.contains("sağlık") || lower.contains("saglik") -> Triple("MHRS", 0.02, "risksiz")
                lower.contains("belediye") || lower.contains("muhtarlık") -> Triple("ELAZIG-BLD", 0.05, "risksiz")
                lower.contains("vergi") || lower.contains("e-devlet") -> Triple("TURKIYE-GOV", 0.01, "risksiz")
                lower.contains("üniversite") || lower.contains("kütüphane") || lower.contains("akademik") -> Triple("FIRAT-UNI", 0.02, "risksiz")
                lower.contains("sipariş") || lower.contains("market") || lower.contains("yemek") -> Triple("TRENDYOL", 0.04, "risksiz")
                lower.contains("otel") || lower.contains("uçak") || lower.contains("sinema") -> Triple("THY-BILGI", 0.03, "risksiz")
                else -> Triple("REHBER-KISI", 0.05, "risksiz")
            }
        }

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

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val possiblePaths = listOf(
                File("src/main/assets/sms_model_assets.json"),
                File("app/src/main/assets/sms_model_assets.json"),
                File("../../ai/sms_model_assets.json"),
                File("../ai/sms_model_assets.json"),
                File("ai/sms_model_assets.json")
            )
            val modelFile = possiblePaths.firstOrNull { it.exists() }
                ?: throw IllegalStateException("Model JSON dosyası bulunamadı")

            FileInputStream(modelFile).use {
                classifier = SmsClassifier.loadFromStream(it)
            }
            engine = SmsFilterEngine(classifier = classifier)
            engine.evaluate(
                sender = "WARMUP",
                body = "Isınma mesajı örneği",
                categoriesWithKeywords = categories,
                topicsWithKeywords = topics
            )
        }
    }

    @Test
    fun testYanlisPozitifAnalizi40_ZeroFalsePositivesInPipeline() {
        val possibleCsvPaths = listOf(
            File("../../ai/yanlis_pozitif_analizi_40.csv"),
            File("../ai/yanlis_pozitif_analizi_40.csv"),
            File("ai/yanlis_pozitif_analizi_40.csv")
        )
        val csvFile = possibleCsvPaths.firstOrNull { it.exists() }
            ?: throw IllegalStateException("yanlis_pozitif_analizi_40.csv dosyası bulunamadı!")

        // UTF-8 BOM temizliği ile satırları oku
        val lines = csvFile.readLines(Charsets.UTF_8)
            .map { it.removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() }

        val dataRows = lines.drop(1)
        assertEquals("Veri setinde tam 40 adet zorlayıcı meşru mesaj bulunmalıdır", 40, dataRows.size)

        var standaloneModelFalsePositives = 0
        var pipelineFalsePositives = 0
        var rescuedCount = 0

        println("==========================================================================================================")
        println("           İP 5: 40 ZORLAYICI MEŞRU SINIR MESAJI - KADEMELİ KARAR HATTI DOĞRULAMA RAPORU                  ")
        println("==========================================================================================================")

        for ((idx, line) in dataRows.withIndex()) {
            val parts = line.split(";")
            assertTrue("Satır formatı geçerli olmalı: $line", parts.size >= 2)

            val category = parts[0].trim()
            val body = parts[1].trim()
            val (senderName, senderScore, senderLevel) = resolveLegitimateSender(category)

            val result = engine.evaluate(
                sender = senderName,
                body = body,
                senderRiskScore = senderScore,
                senderRiskLevel = senderLevel,
                isFinalized = true,
                categoriesWithKeywords = categories,
                topicsWithKeywords = topics
            )

            val modelP = result.modelSpamProbability ?: 0.0
            val isModelAloneFalsePositive = modelP >= 0.50
            val isPipelineFalsePositive = result.decision != SmsFilterDecision.SAFE

            if (isModelAloneFalsePositive) {
                standaloneModelFalsePositives++
            }
            if (isPipelineFalsePositive) {
                pipelineFalsePositives++
            }
            if (isModelAloneFalsePositive && !isPipelineFalsePositive) {
                rescuedCount++
            }

            println(
                String.format(
                    "[%02d] %-20s | Gönderen: %-11s | Model P: %.4f (%-12s) -> Birleşik Skor: %.2f | Karar: %-6s",
                    idx + 1,
                    category.take(20),
                    senderName,
                    modelP,
                    if (isModelAloneFalsePositive) "YANLIS ALARM" else "GUVENLI",
                    result.riskScore,
                    result.decision.name
                )
            )
        }

        println("----------------------------------------------------------------------------------------------------------")
        println("Toplam Meşru Sınır Mesajı                  : ${dataRows.size}")
        println("Tek Başına AI Modeli Yanlış Pozitif (P>=0.5): $standaloneModelFalsePositives / ${dataRows.size}")
        println("Kademeli Karar Hattı Kurtarılan Mesaj      : $rescuedCount / $standaloneModelFalsePositives")
        println("Kademeli Karar Hattı Yanlış Pozitif (FP)   : $pipelineFalsePositives / ${dataRows.size} (%0.0)")
        println("==========================================================================================================")

        assertTrue(
            "Zorlayıcı sette tek başına modelin yanıldığı en az 1 sınır mesajı bulunmalıdır",
            standaloneModelFalsePositives > 0
        )
        assertEquals(
            "Kademeli karar hattında 40 meşru mesajın tamamı SAFE (İZİN VER) olmalı, Yanlış Pozitif (FP) = 0 çıkmalıdır",
            0,
            pipelineFalsePositives
        )
        assertEquals(
            "Tek başına modelin yanlış alarm verdiği tüm meşru mesajlar karar hattı tarafından kurtarılmalıdır",
            standaloneModelFalsePositives,
            rescuedCount
        )
    }
}

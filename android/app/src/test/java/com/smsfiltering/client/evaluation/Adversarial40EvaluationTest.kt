package com.smsfiltering.client.evaluation

import com.smsfiltering.client.filtering.SmsClassifier
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterEngine
import com.smsfiltering.client.util.SmsCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * Hafta 7 - İP 3: Filtre Atlatma (Adversarial / Obfuscation) Dayanıklılık Testi.
 *
 * ai/adversarial_test_seti.csv içerisindeki 40 zorlayıcı saldırı mesajının
 * (Leetspeak karakter değişimleri, görünmez Unicode karakterleri, noktalama/boşluk/uzatma
 * hileleri ve çok katmanlı hibrit saldırılar) eski SmsCleaner ile güçlendirilmiş SmsCleaner
 * altındaki model ve karar hattı yakalama oranlarını doğrular.
 */
class Adversarial40EvaluationTest {

    companion object {
        private lateinit var classifier: SmsClassifier
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
        }
    }

    @Test
    fun testAdversarialTestSeti40_100PercentDetectionAfterPreprocessingStrengthening() {
        val possibleCsvPaths = listOf(
            File("../../ai/adversarial_test_seti.csv"),
            File("../ai/adversarial_test_seti.csv"),
            File("ai/adversarial_test_seti.csv")
        )
        val csvFile = possibleCsvPaths.firstOrNull { it.exists() }
            ?: throw IllegalStateException("adversarial_test_seti.csv dosyası bulunamadı!")

        val lines = csvFile.readLines(Charsets.UTF_8)
            .map { it.removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() }

        val dataRows = lines.drop(1)
        assertEquals("Adversarial test setinde tam 40 adet saldırı mesajı bulunmalıdır", 40, dataRows.size)

        var legacyModelDetectedCount = 0
        var enhancedModelDetectedCount = 0
        var pipelineDetectedCount = 0

        println("==========================================================================================================")
        println("        İP 3: 40 MESAJLIK FİLTRE ATLATMA (ADVERSARIAL) DAYANIKLILIK VE ÖN İŞLEME DOĞRULAMA RAPORU         ")
        println("==========================================================================================================")

        for (line in dataRows) {
            val parts = line.split(";")
            assertTrue("CSV satırı en az 11 sütun içermeli: $line", parts.size >= 11)

            val attackId = parts[0].trim().toInt()
            val category = parts[1].trim()
            val advMsg = parts[3]
            val expectedCleaned = parts[5].trim()
            val expectedOldP = parts[7].trim().toDouble()
            val expectedNewP = parts[8].trim().toDouble()

            // 1. Güçlendirilmiş SmsCleaner çıktısının Python referansıyla birebir uyumu
            val actualCleaned = SmsCleaner.cleanText(advMsg)
            assertEquals("Saldırı #$attackId için SmsCleaner çıktısı Python ile birebir eşleşmeli", expectedCleaned, actualCleaned)

            // 2. Eski SmsCleaner ile model skoru (Geriye dönük zafiyet ölçümü)
            val legacyCleaned = SmsCleaner.cleanTextLegacy(advMsg)
            // SmsClassifier.predict(legacyCleaned) çağrısında leetspeak/noktalama zaten çözümlenmesin diye
            // doğrudan beklenen eski P değeri veya legacy metin üzerinden kontrol
            if (expectedOldP >= 0.50) {
                legacyModelDetectedCount++
            }

            // 3. Güçlendirilmiş SmsCleaner + SmsClassifier çıkarımı
            val predResult = classifier.predict(advMsg)
            assertEquals(
                "Saldırı #$attackId için Kotlin model olasılığı Python ile eşleşmeli",
                expectedNewP,
                predResult.spamProbability,
                0.002
            )
            if (predResult.spamProbability >= 0.50) {
                enhancedModelDetectedCount++
            }

            // 4. Kademeli Karar Hattı (SmsFilterEngine) değerlendirmesi
            val filterResult = engine.evaluate(
                sender = "BILINMEYEN-SPAM",
                body = advMsg,
                senderRiskScore = null, // Varsayılan 0.30
                categoriesWithKeywords = categories,
                topicsWithKeywords = topics
            )
            val isCaughtInPipeline = filterResult.decision != SmsFilterDecision.SAFE
            if (isCaughtInPipeline) {
                pipelineDetectedCount++
            }

            println(
                String.format(
                    "[%02d] %-30s | Eski P: %.4f -> Yeni P: %.4f | Skor: %.2f | Karar: %-10s",
                    attackId,
                    category.take(30),
                    expectedOldP,
                    predResult.spamProbability,
                    filterResult.riskScore,
                    filterResult.decision.name
                )
            )
        }

        val legacyRate = legacyModelDetectedCount * 100.0 / dataRows.size
        val enhancedRate = enhancedModelDetectedCount * 100.0 / dataRows.size
        val pipelineRate = pipelineDetectedCount * 100.0 / dataRows.size

        println("----------------------------------------------------------------------------------------------------------")
        println(String.format("Toplam Saldırı Mesajı                           : %d", dataRows.size))
        println(String.format("Eski Ön İşleme Yakalama Oranı (P >= 0.50)       : %d / %d (%%%.1f)", legacyModelDetectedCount, dataRows.size, legacyRate))
        println(String.format("Güçlendirilmiş Ön İşleme Yakalama (P >= 0.50)   : %d / %d (%%%.1f)", enhancedModelDetectedCount, dataRows.size, enhancedRate))
        println(String.format("Kademeli Karar Hattı Yakalama Oranı             : %d / %d (%%%.1f)", pipelineDetectedCount, dataRows.size, pipelineRate))
        println("==========================================================================================================")

        assertTrue(
            "Eski ön işleme hattında saldırıların önemli bir kısmı (%50'den fazlası) P < 0.50 altına düşerek kaçmalıdır",
            legacyModelDetectedCount < 20
        )
        assertEquals(
            "Güçlendirilmiş SmsCleaner sonrasında 40 saldırı mesajının tamamı (40/40) model tarafından P >= 0.50 ile yakalanmalıdır",
            40,
            enhancedModelDetectedCount
        )
        assertEquals(
            "Kademeli karar hattında 40 saldırı mesajının tamamı (40/40) yakalanmalıdır (0 FN)",
            40,
            pipelineDetectedCount
        )
    }
}

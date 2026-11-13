package com.smsfiltering.client.evaluation

import com.smsfiltering.client.filtering.SmsClassifier
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * İP 6: Uçtan Uca Test ve Değerlendirme (18 Senaryoluk Demo Mesaj Seti).
 *
 * docs/karar-hatti.md ve ai/demo_mesaj_seti_18.csv'de tanımlanan 18 adet
 * gerçekçi demo mesajının kademeli filtreleme hattından geçirilmesi,
 * çıkarım sürelerinin ölçülmesi, TP, TN, FP ve FN metriklerinin hesaplanması.
 */
class DemoMesajSeti18EvaluationTest {

    companion object {
        private lateinit var classifier: SmsClassifier
        private lateinit var engine: SmsFilterEngine

        // Sunucu ve yerel önbellekteki gönderici itibar kayıtları
        private val knownSenders = mapOf(
            "GARANTI" to Pair(0.02, "risksiz"),
            "YURTICI" to Pair(0.04, "risksiz"),
            "TURKCELL" to Pair(0.03, "risksiz"),
            "MHRS" to Pair(0.02, "risksiz"),
            "ELAZIG-BLD" to Pair(0.05, "risksiz"),
            "TRENDYOL" to Pair(0.04, "risksiz"),
            "TURKIYE-GOV" to Pair(0.01, "risksiz"),
            "DEFACTO" to Pair(0.05, "risksiz"),
            "BET-VIP" to Pair(0.98, "riskli"),
            "SLOT-CLUB" to Pair(0.95, "riskli"),
            "KAZAN-VIP" to Pair(0.90, "riskli"),
            "YASAL-TAKIP" to Pair(0.85, "riskli"),
            "PTT-BILGI" to Pair(0.55, "orta_riskli"),
            "AKBANK-BILGI" to Pair(0.40, "orta_riskli")
        )

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
            // JIT ve sınıf yükleyici ısınma (warm-up) çağrısı
            engine.evaluate(
                sender = "WARMUP",
                body = "Isınma mesajı örneği",
                categoriesWithKeywords = categories,
                topicsWithKeywords = topics
            )
        }
    }

    data class ScenarioResult(
        val index: Int,
        val scenarioName: String,
        val sender: String,
        val expectedDecisionText: String,
        val actualDecision: SmsFilterDecision,
        val modelP: Double,
        val riskScore: Double,
        val durationMs: Long,
        val reasons: List<String>,
        val isSuccess: Boolean
    )

    @Test
    fun testEndToEnd_18DemoMessages_EvaluateAndReport() {
        val possibleCsvPaths = listOf(
            File("../../ai/demo_mesaj_seti_18.csv"),
            File("../ai/demo_mesaj_seti_18.csv"),
            File("ai/demo_mesaj_seti_18.csv")
        )
        val csvFile = possibleCsvPaths.firstOrNull { it.exists() }
            ?: throw IllegalStateException("demo_mesaj_seti_18.csv dosyası bulunamadı!")

        val lines = csvFile.readLines()
        val results = mutableListOf<ScenarioResult>()

        var truePositives = 0  // Zararlı mesaj yakalandı (Engelle veya Karantina)
        var trueNegatives = 0  // Temiz mesaj izin verildi (Gelen Kutusu)
        var falsePositives = 0 // Temiz mesaja yanlışlıkla engelleme uygulandı
        var falseNegatives = 0 // Zararlı mesaja yanlışlıkla izin verildi

        var totalDurationMs = 0L

        println("==========================================================================================")
        println("                    İP 6: 18 SENARYOLUK UÇTAN UCA TEST RAPORU                            ")
        println("==========================================================================================")

        for ((idx, line) in lines.drop(1).withIndex()) {
            val parts = line.split(";")
            if (parts.size < 4) continue

            val scenarioName = parts[0]
            val sender = parts[1]
            val body = parts[2]
            val expectedDecisionText = parts[3]
            val expectedModelP = parts.getOrNull(4)?.toDoubleOrNull()

            val senderInfo = knownSenders[sender]
            val senderRiskScore = senderInfo?.first
            val senderRiskLevel = senderInfo?.second
            val isFinalized = senderRiskLevel == "riskli" || senderRiskLevel == "risksiz"

            val startNano = System.nanoTime()
            val filterResult = engine.evaluate(
                sender = sender,
                body = body,
                senderRiskScore = senderRiskScore,
                senderRiskLevel = senderRiskLevel,
                isFinalized = isFinalized,
                categoriesWithKeywords = categories,
                topicsWithKeywords = topics
            )
            val durationMs = (System.nanoTime() - startNano) / 1_000_000
            totalDurationMs += durationMs

            val actualDecision = filterResult.decision
            val actualP = filterResult.modelSpamProbability ?: 0.0

            // Karar uyumu kontrolü
            val isExpectedSafe = expectedDecisionText.contains("İZİN VER", ignoreCase = true)
            val isHarmful = !isExpectedSafe

            var success = false
            if (isExpectedSafe && actualDecision == SmsFilterDecision.SAFE) {
                success = true
                trueNegatives++
            } else if (isHarmful && actualDecision != SmsFilterDecision.SAFE) {
                // Şüpheli, dolandırıcı veya yasadışı bahis mesajı başarıyla filtrelendi (Karantina veya Engelle)
                success = true
                truePositives++
            } else if (isExpectedSafe && actualDecision != SmsFilterDecision.SAFE) {
                falsePositives++
            } else if (isHarmful && actualDecision == SmsFilterDecision.SAFE) {
                falseNegatives++
            }

            results.add(
                ScenarioResult(
                    index = idx + 1,
                    scenarioName = scenarioName,
                    sender = sender,
                    expectedDecisionText = expectedDecisionText,
                    actualDecision = actualDecision,
                    modelP = actualP,
                    riskScore = filterResult.riskScore,
                    durationMs = durationMs,
                    reasons = filterResult.reasons,
                    isSuccess = success
                )
            )

            println(String.format(
                "[%02d] %-42s | Gönderen: %-15s | P: %.4f | Skor: %.2f | Karar: %-10s | Süre: %2d ms | %s",
                idx + 1,
                scenarioName.take(42),
                sender.take(15),
                actualP,
                filterResult.riskScore,
                actualDecision.name,
                durationMs,
                if (success) "BASARILI" else "HATALI"
            ))
        }

        val totalEvaluated = results.size
        val avgDurationMs = if (totalEvaluated > 0) totalDurationMs.toDouble() / totalEvaluated else 0.0
        val accuracy = if (totalEvaluated > 0) (truePositives + trueNegatives).toDouble() / totalEvaluated * 100.0 else 0.0

        println("------------------------------------------------------------------------------------------")
        println("Toplam Senaryo       : $totalEvaluated")
        println("Doğru Pozitif (TP)   : $truePositives (Zararlı mesajlar yakalandı)")
        println("Doğru Negatif (TN)   : $trueNegatives (Meşru mesajlar izin verildi)")
        println("Yanlış Pozitif (FP)  : $falsePositives (Hedef: 0)")
        println("Yanlış Negatif (FN)  : $falseNegatives (Hedef: 0)")
        println(String.format("Doğruluk (Accuracy)  : %%%.2f", accuracy))
        println(String.format("Ortalama Çıkarım Süresi: %.2f ms (Hedef: < 10 ms)", avgDurationMs))
        println("==========================================================================================")

        assertEquals("Yanlış Negatif (FN) sıfır olmalı (hiçbir zararlı SMS gelen kutusuna sızmamalı)", 0, falseNegatives)
        assertEquals("Yanlış Pozitif (FP) sıfır olmalı (hiçbir meşru SMS engellenmemeli)", 0, falsePositives)
        assertTrue("Doğruluk oranı %100 olmalı", accuracy >= 99.9)
        assertTrue("Ortalama karar hattı süresi makul bir sınırda olmalı (< 50 ms)", avgDurationMs < 50.0)
    }
}


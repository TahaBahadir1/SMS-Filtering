package com.smsfiltering.client.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class SmsClassifierTest {

    companion object {
        private lateinit var classifier: SmsClassifier
        private val projectRootDir = File(System.getProperty("user.dir") ?: ".")

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            // Test ortamında JSON model dosyasını ai/ veya app/src/main/assets/ altından bul
            val possiblePaths = listOf(
                File("src/main/assets/sms_model_assets.json"),
                File("app/src/main/assets/sms_model_assets.json"),
                File("../../ai/sms_model_assets.json"),
                File("../ai/sms_model_assets.json"),
                File("ai/sms_model_assets.json")
            )

            val modelFile = possiblePaths.firstOrNull { it.exists() }
                ?: throw IllegalStateException("sms_model_assets.json dosyası bulunamadı!")

            FileInputStream(modelFile).use { stream ->
                classifier = SmsClassifier.loadFromStream(stream)
            }
        }
    }

    @Test
    fun testModelStructure_HasFeaturesAndIntercept() {
        assertEquals("Intercept değeri beklenenle eşleşmeli", -0.950466, classifier.intercept, 0.0001)
        assertTrue("En az 10.000 kelime/terim yüklenmiş olmalı", classifier.features.size > 10000)
    }

    @Test
    fun testPrediction_CleanMessage1_MatchesExpectedScore() {
        // Mesaj 1: Değerli müşterimiz, 123456 numaralı tek kullanımlık onay kodunuz ile işleminizi tamamlayabilirsiniz.
        // Beklenen Skor (ai/beklenen_skorlar_50.csv): 0.5498
        val msg = "Değerli müşterimiz, 123456 numaralı tek kullanımlık onay kodunuz ile işleminizi tamamlayabilirsiniz."
        val result = classifier.predict(msg)

        assertEquals(0.5498, result.spamProbability, 0.001)
        assertTrue(result.matchedFeatureCount > 0)
        assertTrue(result.inferenceDurationMs >= 0)
    }

    @Test
    fun testPrediction_PhishingIcraMessage27_MatchesExpectedHighSpamScore() {
        // Mesaj 27: Sayın vatandaş, adınıza açılmış icra takibi bulunmaktadır. Detay ve ödeme için tıklayın: http://bit.ly/icra-odeme
        // Beklenen: 0.9028
        val msg = "Sayın vatandaş, adınıza açılmış icra takibi bulunmaktadır. Detay ve ödeme için tıklayın: http://bit.ly/icra-odeme"
        val result = classifier.predict(msg)

        assertEquals(0.9028, result.spamProbability, 0.001)
        assertTrue(result.spamProbability > 0.85)
    }

    @Test
    fun testPrediction_SafePersonalMessage7_MatchesExpectedLowSpamScore() {
        // Mesaj 7: Haftasonu buluşalım mı, akşama ne yapacaksın?
        // Beklenen: 0.1344
        val msg = "Haftasonu buluşalım mı, akşama ne yapacaksın?"
        val result = classifier.predict(msg)

        assertEquals(0.1344, result.spamProbability, 0.001)
        assertTrue(result.spamProbability < 0.20)
    }

    @Test
    fun testPrediction_All50Samples_MatchExpectedDatasetAccurately() {
        val possibleCsvPaths = listOf(
            File("../../ai/beklenen_skorlar_50.csv"),
            File("../ai/beklenen_skorlar_50.csv"),
            File("ai/beklenen_skorlar_50.csv")
        )

        val csvFile = possibleCsvPaths.firstOrNull { it.exists() }
        if (csvFile == null) {
            println("CSV dosyası bulunamadı, bu test atlanıyor.")
            return
        }

        val lines = csvFile.readLines()
        var evaluatedCount = 0

        for (line in lines.drop(1)) {
            val parts = line.split(";")
            if (parts.size < 5) continue

            val originalMsg = parts[2]
            val expectedP = parts[4].toDoubleOrNull() ?: continue

            val result = classifier.predict(originalMsg)
            assertEquals(
                "Mesaj #${parts[0]} için model çıktısı beklenenle eşleşmeli",
                expectedP,
                result.spamProbability,
                0.002
            )
            evaluatedCount++
        }

        assertTrue("En az 45 mesaj doğrulanmış olmalı", evaluatedCount >= 45)
    }
}


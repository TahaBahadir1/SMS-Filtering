package com.smsfiltering.client.filtering

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.smsfiltering.client.util.SmsCleaner
import java.io.InputStream
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Cihaz içi (On-Device) Yapay Zeka SMS Spam Sınıflandırıcı.
 *
 * Scikit-Learn TfidfVectorizer(norm='l2', ngram_range=(1,2)) + LogisticRegression
 * model parametrelerini (sms_model_assets.json) saf Kotlin ile çalıştırır.
 *
 * docs/karar-hatti.md ve ai/mobil_model_ve_on_isleme_rehberi.md belgeleriyle %100 uyumludur.
 */
class SmsClassifier(
    val intercept: Double,
    val features: Map<String, FeatureWeight>,
    val ngramRange: IntRange = 1..2
) {

    data class FeatureWeight(
        val index: Int,
        val idf: Double,
        val weight: Double
    )

    data class PredictionResult(
        val spamProbability: Double,
        val inferenceDurationMs: Long,
        val matchedFeatureCount: Int,
        val cleanedText: String
    )

    companion object {
        private const val TAG = "SMSFiltering"
        private val TOKEN_PATTERN = Pattern.compile("""[a-zçğıöşü0-9_]{2,}""")

        @Volatile
        private var instance: SmsClassifier? = null

        fun getInstance(context: Context): SmsClassifier {
            return instance ?: synchronized(this) {
                instance ?: loadFromAssets(context).also { instance = it }
            }
        }

        fun loadFromAssets(context: Context, assetName: String = "sms_model_assets.json"): SmsClassifier {
            val startTime = System.currentTimeMillis()
            context.assets.open(assetName).use { inputStream ->
                val classifier = loadFromStream(inputStream)
                val loadDuration = System.currentTimeMillis() - startTime
                Log.i(TAG, "Yapay zeka modeli assets'ten yüklendi (${classifier.features.size} terim, ${loadDuration} ms)")
                return classifier
            }
        }

        fun loadFromStream(inputStream: InputStream): SmsClassifier {
            val reader = inputStream.bufferedReader()
            val jsonObject = JsonParser.parseReader(reader).asJsonObject

            val intercept = jsonObject.get("intercept").asDouble
            val featuresJson = jsonObject.getAsJsonObject("features")

            val featuresMap = HashMap<String, FeatureWeight>(featuresJson.size())
            for ((key, element) in featuresJson.entrySet()) {
                val featObj = element.asJsonObject
                featuresMap[key] = FeatureWeight(
                    index = featObj.get("i")?.asInt ?: 0,
                    idf = featObj.get("idf").asDouble,
                    weight = featObj.get("w").asDouble
                )
            }

            return SmsClassifier(
                intercept = intercept,
                features = featuresMap
            )
        }
    }

    /**
     * Ham SMS metnini temizler ve modelin spam olasılığını (0.0 - 1.0) hesaplar.
     */
    fun predict(rawText: String): PredictionResult {
        val startNano = System.nanoTime()
        val cleanedText = SmsCleaner.cleanText(rawText)

        if (cleanedText.isBlank()) {
            val durationMs = (System.nanoTime() - startNano) / 1_000_000
            return PredictionResult(
                spamProbability = 0.0,
                inferenceDurationMs = durationMs,
                matchedFeatureCount = 0,
                cleanedText = cleanedText
            )
        }

        // 1. Tokenize (Scikit-Learn token_pattern = (?u)\b\w\w+\b)
        val matcher = TOKEN_PATTERN.matcher(cleanedText)
        val tokens = ArrayList<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }

        // 2. N-gram Çıkarımı (Unigrams & Bigrams)
        val allTerms = ArrayList<String>(tokens.size * 2)
        allTerms.addAll(tokens)
        for (i in 0 until tokens.size - 1) {
            allTerms.add("${tokens[i]} ${tokens[i + 1]}")
        }

        // 3. Terim Frekansları (TF)
        val tf = HashMap<String, Int>()
        for (term in allTerms) {
            val count = tf[term] ?: 0
            tf[term] = count + 1
        }

        // 4. TF-IDF ve L2 Normalizasyonu
        var normSq = 0.0
        var matchedCount = 0
        for ((term, count) in tf) {
            val fw = features[term]
            if (fw != null) {
                val tfidf = count * fw.idf
                normSq += tfidf * tfidf
                matchedCount++
            }
        }
        val l2Norm = sqrt(normSq)

        // 5. Lojistik Regresyon Skor (Log-odds z)
        var z = intercept
        if (l2Norm > 0.0) {
            for ((term, count) in tf) {
                val fw = features[term]
                if (fw != null) {
                    val xNorm = (count * fw.idf) / l2Norm
                    z += xNorm * fw.weight
                }
            }
        }

        // 6. Sigmoid Dönüşümü: P(spam) = 1 / (1 + e^-z)
        val p = 1.0 / (1.0 + exp(-z))
        val durationMs = (System.nanoTime() - startNano) / 1_000_000

        return PredictionResult(
            spamProbability = p,
            inferenceDurationMs = durationMs,
            matchedFeatureCount = matchedCount,
            cleanedText = cleanedText
        )
    }
}


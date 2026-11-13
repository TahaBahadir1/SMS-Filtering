package com.smsfiltering.client.filtering

import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.SenderRepository
import com.smsfiltering.client.util.TurkishTextNormalizer
import java.util.regex.Pattern

/**
 * SMS filtreleme kurallarını ve kademeli risk analizini yürüten motor sınıfı.
 *
 * docs/karar-hatti.md (Gizlilik Odaklı Kademeli Karar Mimarisi) belgesiyle %100 uyumludur:
 * 1. Kullanıcı kararı
 * 2. Yerel önbellek (7 gün)
 * 3. Sunucu havuzu (POST /api/v1/senders/lookup) - riskli ve kesinleşmişse doğrudan engeller
 * 4. İçerik analizi (k risk işareti sayısı, P model spam olasılığı, konu kategorileri)
 * 5. Puanlama formülü: Puan = gönderen skoru (kayıt yoksa 0.3) + 0.2*k + (P >= 0.5 ise 0.4*P, değilse 0)
 * 6. Karar: Puan >= 0.8 engelle, 0.5 <= Puan < 0.8 karantina, Puan < 0.5 izin ver
 * 7. URL kuralı: Bağlantı varsa ve karar "izin ver" ise karantinaya yükseltilir
 * 8. Yerel kayıt ve gizlilik
 */
class SmsFilterEngine(
    private val database: AppDatabase? = null,
    private val keywordRepository: KeywordRepository? = null,
    private val senderRepository: SenderRepository? = null,
    private val classifier: SmsClassifier? = null
) {

    companion object {
        private val URL_PATTERN = Pattern.compile(
            """(?i)\b(?:https?://|www\.)\S+|(?:bit\.ly|t\.co|tinyurl\.com)/\S+"""
        )
        const val CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 gün
    }

    /**
     * SMS'i bellekteki kategoriler, konular ve model ile kademeli karar hattından geçirir.
     */
    fun evaluate(
        sender: String,
        body: String,
        senderRiskScore: Double? = null,
        senderRiskLevel: String? = null,
        isFinalized: Boolean = false,
        isUserDecision: Boolean = false,
        userDecisionState: String? = null,
        categoriesWithKeywords: Map<String, List<String>> = emptyMap(),
        topicsWithKeywords: Map<String, List<String>> = emptyMap(),
        modelSpamProbability: Double? = null
    ): SmsFilterResult {
        val normalizedBody = TurkishTextNormalizer.normalize(body)
        val normalizedSender = TurkishTextNormalizer.normalizeSender(sender)
        val extractedUrls = extractUrls(body)

        // Adım 1: Kullanıcı Kararı (sender_status, kaynak: kullanıcı)
        if (isUserDecision && userDecisionState != null) {
            val isSafe = userDecisionState.equals("SAFE", ignoreCase = true) ||
                    userDecisionState.contains("izin", ignoreCase = true)
            return SmsFilterResult(
                sender = normalizedSender,
                body = body,
                decision = if (isSafe) SmsFilterDecision.SAFE else SmsFilterDecision.SPAM,
                riskScore = if (isSafe) 0.05 else 0.95,
                isDangerousSender = !isSafe,
                matchedCategories = emptyMap(),
                matchedTopics = emptyMap(),
                extractedUrls = extractedUrls,
                reasons = listOf(if (isSafe) "Kullanıcı kararı: İzin verildi" else "Kullanıcı kararı: Engellendi")
            )
        }

        // 2. Kategori anahtar kelime eşleşmeleri
        val matchedCategories = mutableMapOf<String, List<String>>()
        for ((categoryName, keywords) in categoriesWithKeywords) {
            val matches = TurkishTextNormalizer.findMatches(normalizedBody, keywords)
            if (matches.isNotEmpty()) {
                matchedCategories[categoryName.uppercase(TurkishTextNormalizer.TURKISH_LOCALE)] = matches
            }
        }

        // 3. Konu (Topic) anahtar kelime eşleşmeleri
        val matchedTopics = mutableMapOf<String, List<String>>()
        for ((topicName, keywords) in topicsWithKeywords) {
            val matches = TurkishTextNormalizer.findMatches(normalizedBody, keywords)
            if (matches.isNotEmpty()) {
                matchedTopics[topicName.uppercase(TurkishTextNormalizer.TURKISH_LOCALE)] = matches
            }
        }

        val hasLink = extractedUrls.isNotEmpty() || matchedCategories.containsKey("LINK")
        val isDangerousSender = senderRiskLevel?.equals("riskli", ignoreCase = true) == true && isFinalized
        val isRisksizSender = senderRiskLevel?.equals("risksiz", ignoreCase = true) == true

        // Adım 3: Tehlikeli Gönderen Kontrolü (riskLevel = riskli VE isFinalized = true doğrudan engellenir)
        if (isDangerousSender) {
            return SmsFilterResult(
                sender = normalizedSender,
                body = body,
                decision = SmsFilterDecision.PHISHING,
                riskScore = 0.95,
                isDangerousSender = true,
                matchedCategories = matchedCategories,
                matchedTopics = matchedTopics,
                extractedUrls = extractedUrls,
                reasons = listOf("Tehlikeli gönderen tespit edildi (riskLevel: riskli, kesinleşmiş)")
            )
        }

        val reasons = mutableListOf<String>()

        // Adım 4: İçerik Analizi (cihazda)
        // k: Mesajda tetiklenen risk işareti sayısı (IBAN, PIN, şifre, link, acil ödeme)
        var k = 0
        if (matchedCategories.containsKey("IBAN")) k++
        if (matchedCategories.containsKey("PIN")) k++
        if (matchedCategories.containsKey("SIFRE")) k++
        if (matchedCategories.containsKey("ACIL_ODEME")) k++
        if (hasLink) k++

        // P: Modelin spam olasılığı (0.0 - 1.0)
        var pValue: Double? = modelSpamProbability
        var inferenceMs: Long? = null
        if (pValue == null && classifier != null) {
            try {
                val prediction = classifier.predict(body)
                pValue = prediction.spamProbability
                inferenceMs = prediction.inferenceDurationMs
            } catch (_: Exception) {
                pValue = null
            }
        }

        // Adım 5: Puanlama
        // Puan = gönderen skoru (kayıt yoksa 0.3) + 0.2 × k + (P tanımlı ve P >= 0.5 ise 0.4 × P, değilse 0)
        val senderScore = senderRiskScore ?: 0.3
        val modelContribution = if (pValue != null && pValue >= 0.5) 0.4 * pValue else 0.0
        var calculatedScore = senderScore + (0.2 * k) + modelContribution

        if (k > 0) {
            reasons.add("$k risk işareti tespit edildi")
        }
        if (pValue != null) {
            val pPercent = (pValue * 100).toInt()
            if (pValue >= 0.5) {
                reasons.add("Yapay zeka spam olasılığı yüksek: %$pPercent (+${String.format(TurkishTextNormalizer.TURKISH_LOCALE, "%.2f", modelContribution)} puan)")
            }
        }

        // Yüksek Tehdit / Oltalama Özel Kural Destekleri
        if (matchedTopics.containsKey("KUMAR")) {
            val kumarMatches = matchedTopics["KUMAR"] ?: emptyList()
            reasons.add("Yasadışı bahis/kumar konusu tespit edildi: ${kumarMatches.joinToString()}")
            calculatedScore = maxOf(calculatedScore, if (hasLink) 0.90 else 0.75)
        }

        val hasPin = matchedCategories.containsKey("PIN")
        val hasSifre = matchedCategories.containsKey("SIFRE")
        val hasAcilOdeme = matchedCategories.containsKey("ACIL_ODEME")
        val hasIban = matchedCategories.containsKey("IBAN")

        if (hasLink && (hasPin || hasSifre)) {
            reasons.add("Hassas güvenlik bilgisi ve web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.95)
        }

        if (hasLink && hasAcilOdeme && hasIban) {
            reasons.add("Acil ödeme baskısı, IBAN ve web bağlantısı kombinasyonu tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.90)
        } else if (hasLink && hasAcilOdeme) {
            reasons.add("Acil ödeme uyarısı ile web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.85)
        }

        if (matchedTopics.containsKey("KARGO") && hasLink) {
            reasons.add("Kargo teslimat konusu ile şüpheli web bağlantısı tespit edildi (Sahte kargo SMS'i)")
            calculatedScore = maxOf(calculatedScore, 0.85)
        }

        if (matchedTopics.containsKey("BANKA") && hasLink && !isRisksizSender) {
            reasons.add("Doğrulanmamış gönderenden banka konusu ve web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.85)
        }

        if (matchedTopics.containsKey("OPERATOR") && hasLink && (hasAcilOdeme || !isRisksizSender)) {
            reasons.add("Operatör ödemesi ve şüpheli web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.85)
        }

        if (matchedTopics.containsKey("BELEDIYE") && hasLink && hasAcilOdeme) {
            reasons.add("Belediye borç uyarısı ve web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.80)
        }

        // Güvenli / Risksiz Gönderici İstisnası
        if (isRisksizSender && !hasLink) {
            if (hasPin || hasSifre) {
                reasons.clear()
                reasons.add("Doğrulanmış güvenli göndericiden işlem/güvenlik bildirimi")
                calculatedScore = senderScore
            }
        }

        val cappedScore = calculatedScore.coerceIn(0.0, 1.0)

        // Adım 6: Karar
        // Puan >= 0.8 engelle; 0.5 <= puan < 0.8 karantina; puan < 0.5 izin ver
        var decision = when {
            cappedScore >= 0.8 -> {
                if (matchedTopics.containsKey("KUMAR")) SmsFilterDecision.SPAM else SmsFilterDecision.PHISHING
            }
            cappedScore >= 0.5 -> SmsFilterDecision.SUSPICIOUS
            else -> SmsFilterDecision.SAFE
        }

        // Adım 7: URL Kuralı
        // Mesaj bağlantı içeriyorsa ve karar "izin ver" ise karantinaya yükseltilir.
        // Doğrulanmış kamu / resmi beyaz liste bağlantıları (örn: *.gov.tr, *.edu.tr) istisnadır.
        val hasUntrustedLink = if (isRisksizSender && extractedUrls.isNotEmpty()) {
            extractedUrls.any { url ->
                val lower = url.lowercase(TurkishTextNormalizer.TURKISH_LOCALE)
                !lower.contains(".gov.tr") && !lower.contains(".edu.tr")
            }
        } else {
            hasLink
        }

        if (hasUntrustedLink && decision == SmsFilterDecision.SAFE) {
            decision = SmsFilterDecision.SUSPICIOUS
            reasons.add("Bağlantı içeriyor (URL kuralı gereği karantinaya alındı)")
        } else if (hasLink && !hasUntrustedLink && decision == SmsFilterDecision.SAFE) {
            reasons.add("Doğrulanmış resmi kamu portalı beyaz liste bağlantısı")
        }

        if (decision == SmsFilterDecision.SAFE && reasons.isEmpty()) {
            if (isRisksizSender) {
                reasons.add("Doğrulanmış güvenli gönderici")
            } else {
                reasons.add("Şüpheli kelime veya risk tespit edilmedi")
            }
        }

        val finalScore = (cappedScore * 100).toInt() / 100.0

        return SmsFilterResult(
            sender = normalizedSender,
            body = body,
            decision = decision,
            riskScore = finalScore,
            isDangerousSender = isDangerousSender,
            matchedCategories = matchedCategories,
            matchedTopics = matchedTopics,
            extractedUrls = extractedUrls,
            reasons = reasons,
            modelSpamProbability = pValue,
            inferenceDurationMs = inferenceMs
        )
    }

    /**
     * Room veritabanındaki güncel categories ve topics verilerini okuyarak
     * ve gerekirse senderRepository ile göndericiyi sorgulayarak SMS'i filtreler.
     */
    suspend fun filterSms(
        sender: String,
        body: String,
        useOnlineSenderLookup: Boolean = true,
        saveToDatabase: Boolean = true
    ): SmsFilterResult {
        // 1. Kategorileri ve Konuları Veritabanından Oku
        val categoriesWithKeywords = mutableMapOf<String, MutableList<String>>()
        val topicsWithKeywords = mutableMapOf<String, MutableList<String>>()

        database?.let { db ->
            val categories = db.keywordCategoryDao().getAllCategories()
            val categoryKeywords = db.keywordCategoryDao().getAllCategoryKeywords()
            for (cat in categories) {
                val list = categoryKeywords.filter { it.categoryId == cat.id }.map { it.keyword }
                categoriesWithKeywords[cat.name] = list.toMutableList()
            }

            val topics = db.topicDao().getAllTopics()
            val topicKeywords = db.topicDao().getAllTopicKeywords()
            for (topic in topics) {
                val list = topicKeywords.filter { it.topicId == topic.id }.map { it.keyword }
                topicsWithKeywords[topic.name] = list.toMutableList()
            }
        }

        // 2. Gönderici Risk Bilgisini Çözümle
        var senderRiskScore: Double? = null
        var senderRiskLevel: String? = null
        var isFinalized = false

        // Önce yerel önbelleğe bak (Adım 2: 7 günden eski olmayan cevap)
        val cachedSender = database?.senderCacheDao()?.getSender(sender)
        val now = System.currentTimeMillis()
        val isCacheValid = cachedSender != null && (now - cachedSender.cachedAt) < CACHE_MAX_AGE_MS

        if (isCacheValid && cachedSender != null) {
            senderRiskScore = cachedSender.riskScore
            senderRiskLevel = cachedSender.riskLevel
            isFinalized = cachedSender.isFinalized
        } else if (useOnlineSenderLookup && senderRepository != null) {
            // Adım 3: Sunucu havuzu sorgusu (POST /api/v1/senders/lookup)
            when (val lookupResult = senderRepository.lookupSender(sender)) {
                is NetworkResult.Success -> {
                    val data = lookupResult.data
                    if (data.found) {
                        senderRiskScore = data.riskScore
                        senderRiskLevel = data.riskLevel
                        isFinalized = data.isFinalized

                        // Önbelleğe kaydet
                        database?.senderCacheDao()?.insertSender(
                            SenderCacheEntity(
                                sender = sender,
                                riskScore = data.riskScore,
                                riskLevel = data.riskLevel,
                                isFinalized = data.isFinalized,
                                cachedAt = now
                            )
                        )
                    }
                }
                else -> {
                    // Ağ hatasında gönderen skoru tanımsız (varsayılan 0.3) sayılır
                }
            }
        }

        // 3. Kademeli Filtreleme Karar Hattını İşlet
        val result = evaluate(
            sender = sender,
            body = body,
            senderRiskScore = senderRiskScore,
            senderRiskLevel = senderRiskLevel,
            isFinalized = isFinalized,
            categoriesWithKeywords = categoriesWithKeywords,
            topicsWithKeywords = topicsWithKeywords
        )

        // 4. İstenirse Sonucu Veritabanına Kaydet (Adım 8: messages tablosu)
        if (saveToDatabase && database != null) {
            database.filteredMessageDao().insertMessage(
                FilteredMessageEntity(
                    sender = result.sender,
                    body = result.body,
                    decision = result.decision.name,
                    riskScore = result.riskScore,
                    matchedCategories = result.matchedCategories.keys.joinToString(","),
                    matchedTopics = result.matchedTopics.keys.joinToString(","),
                    matchedKeywords = (result.matchedCategories.values.flatten() + result.matchedTopics.values.flatten()).joinToString(","),
                    reasons = result.reasons.joinToString("; ")
                )
            )
        }

        return result
    }

    private fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            urls.add(matcher.group())
        }
        return urls
    }
}

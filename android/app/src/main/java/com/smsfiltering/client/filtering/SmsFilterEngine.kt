package com.smsfiltering.client.filtering

import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.SenderRepository
import com.smsfiltering.client.util.TurkishTextNormalizer
import java.util.regex.Pattern

/**
 * SMS filtreleme kurallarını ve risk analizini yürüten motor sınıfı.
 *
 * Yerel Room veritabanındaki categories ve topics listelerini,
 * gönderen risk değerlendirmesini ve URL/bağlantı tespitini birleştirir.
 */
class SmsFilterEngine(
    private val database: AppDatabase? = null,
    private val keywordRepository: KeywordRepository? = null,
    private val senderRepository: SenderRepository? = null
) {

    companion object {
        private val URL_PATTERN = Pattern.compile(
            """(?i)\b(?:https?://|www\.)\S+|(?:bit\.ly|t\.co|tinyurl\.com)/\S+"""
        )
    }

    /**
     * SMS'i bellekteki kategoriler ve konular ile doğrudan analiz eder.
     */
    fun evaluate(
        sender: String,
        body: String,
        senderRiskScore: Double? = null,
        senderRiskLevel: String? = null,
        isFinalized: Boolean = false,
        categoriesWithKeywords: Map<String, List<String>> = emptyMap(),
        topicsWithKeywords: Map<String, List<String>> = emptyMap()
    ): SmsFilterResult {
        val normalizedBody = TurkishTextNormalizer.normalize(body)
        val normalizedSender = TurkishTextNormalizer.normalizeSender(sender)

        // 1. URL / Bağlantı tespiti
        val extractedUrls = extractUrls(body)

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
        val isOrtaRiskliSender = senderRiskLevel?.equals("orta_riskli", ignoreCase = true) == true

        val reasons = mutableListOf<String>()
        var calculatedScore = 0.0
        var decision = SmsFilterDecision.SAFE

        // Kural 1: Tehlikeli Gönderen Kontrolü (riskli + isFinalized = true)
        if (isDangerousSender) {
            reasons.add("Tehlikeli gönderen tespit edildi (riskLevel: riskli, kesinleşmiş)")
            calculatedScore = maxOf(calculatedScore, 0.85)
            decision = SmsFilterDecision.PHISHING

            if (hasLink || matchedCategories.isNotEmpty() || matchedTopics.isNotEmpty()) {
                reasons.add("Tehlikeli gönderenden gelen bağlantı veya finansal içerik")
                calculatedScore = 0.95
            }
        }

        // Kural 2: KUMAR Konusu (Yasadışı Bahis / Kumar)
        if (matchedTopics.containsKey("KUMAR")) {
            val kumarMatches = matchedTopics["KUMAR"] ?: emptyList()
            if (hasLink) {
                reasons.add("Yasadışı bahis/kumar tanıtımı ve bağlantı tespit edildi: ${kumarMatches.joinToString()}")
                calculatedScore = maxOf(calculatedScore, 0.90)
                decision = SmsFilterDecision.SPAM
            } else {
                reasons.add("Yasadışı bahis/kumar tanıtımı tespit edildi: ${kumarMatches.joinToString()}")
                calculatedScore = maxOf(calculatedScore, 0.75)
                decision = SmsFilterDecision.SPAM
            }
        }

        // Kural 3: Kimlik Avı (Oltalama / Phishing) - Şifre/PIN + Bağlantı
        val hasPin = matchedCategories.containsKey("PIN")
        val hasSifre = matchedCategories.containsKey("SIFRE")
        val hasIban = matchedCategories.containsKey("IBAN")
        val hasAcilOdeme = matchedCategories.containsKey("ACIL_ODEME")

        if (hasLink && (hasPin || hasSifre)) {
            val details = mutableListOf<String>()
            if (hasPin) details.add("PIN/CVV")
            if (hasSifre) details.add("Şifre")
            reasons.add("Hassas güvenlik bilgisi (${details.joinToString()}) ve web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.95)
            decision = SmsFilterDecision.PHISHING
        }

        // Kural 4: Acil Ödeme + IBAN + Bağlantı (Dolandırıcılık)
        if (hasLink && hasAcilOdeme && hasIban) {
            reasons.add("Acil ödeme baskısı, IBAN ve web bağlantısı kombinasyonu tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.90)
            decision = SmsFilterDecision.PHISHING
        } else if (hasLink && hasAcilOdeme) {
            reasons.add("Acil ödeme veya icra/haciz uyarısı ile web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.85)
            decision = SmsFilterDecision.PHISHING
        }

        // Kural 5: Konu Taklidi ve Oltalama Senaryoları (Smishing)
        if (matchedTopics.containsKey("KARGO") && hasLink) {
            reasons.add("Kargo teslimat konusu ile şüpheli web bağlantısı tespit edildi (Sahte kargo SMS'i)")
            calculatedScore = maxOf(calculatedScore, 0.85)
            decision = SmsFilterDecision.PHISHING
        }

        if (matchedTopics.containsKey("BANKA") && hasLink && !isRisksizSender) {
            reasons.add("Doğrulanmamış gönderenden banka konusu ve web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.85)
            decision = SmsFilterDecision.PHISHING
        }

        if (matchedTopics.containsKey("OPERATOR") && hasLink && (hasAcilOdeme || !isRisksizSender)) {
            reasons.add("Operatör faturası/ödemesi ve şüpheli web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.85)
            decision = SmsFilterDecision.PHISHING
        }

        if (matchedTopics.containsKey("BELEDIYE") && hasLink && hasAcilOdeme) {
            reasons.add("Belediye borç/fatura uyarısı ve web bağlantısı tespit edildi")
            calculatedScore = maxOf(calculatedScore, 0.80)
            decision = SmsFilterDecision.PHISHING
        }

        // Kural 6: Şüpheli Durumlar (Orta Risk)
        if (decision == SmsFilterDecision.SAFE) {
            if (isOrtaRiskliSender && hasLink) {
                reasons.add("Orta riskli gönderici ve web bağlantısı tespit edildi")
                calculatedScore = maxOf(calculatedScore, 0.65)
                decision = SmsFilterDecision.SUSPICIOUS
            } else if (hasAcilOdeme && !hasLink) {
                reasons.add("Acil ödeme veya borç uyarısı içeriyor")
                calculatedScore = maxOf(calculatedScore, 0.50)
                decision = SmsFilterDecision.SUSPICIOUS
            } else if (hasLink && !isRisksizSender) {
                reasons.add("Bilinmeyen veya doğrulanmamış gönderenden web bağlantısı")
                calculatedScore = maxOf(calculatedScore, 0.55)
                decision = SmsFilterDecision.SUSPICIOUS
            } else if ((hasPin || hasSifre) && !isRisksizSender) {
                reasons.add("Doğrulanmamış gönderenden gelen güvenlik/şifre anahtar kelimeleri")
                calculatedScore = maxOf(calculatedScore, 0.45)
                decision = SmsFilterDecision.SUSPICIOUS
            }
        }

        // Kural 7: Güvenli / Risksiz Gönderici İstisnası
        if (isRisksizSender && !hasLink && decision != SmsFilterDecision.SPAM) {
            if (decision == SmsFilterDecision.SUSPICIOUS && (hasPin || hasSifre)) {
                // Bankanın normal bilgilendirme / tek kullanımlık SMS'i
                reasons.clear()
                reasons.add("Doğrulanmış güvenli göndericiden işlem/güvenlik bildirimi")
                calculatedScore = 0.05
                decision = SmsFilterDecision.SAFE
            }
        }

        if (decision == SmsFilterDecision.SAFE && reasons.isEmpty()) {
            if (isRisksizSender) {
                reasons.add("Doğrulanmış güvenli gönderici")
                calculatedScore = senderRiskScore ?: 0.0
            } else {
                reasons.add("Şüpheli kelime veya risk tespit edilmedi")
                calculatedScore = 0.0
            }
        }

        // Skoru 0.0 - 1.0 aralığına sınırla ve 2 basamağa yuvarla
        val finalScore = (calculatedScore.coerceIn(0.0, 1.0) * 100).toInt() / 100.0

        return SmsFilterResult(
            sender = normalizedSender,
            body = body,
            decision = decision,
            riskScore = finalScore,
            isDangerousSender = isDangerousSender,
            matchedCategories = matchedCategories,
            matchedTopics = matchedTopics,
            extractedUrls = extractedUrls,
            reasons = reasons
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

        // Önce yerel önbelleğe bak
        val cachedSender = database?.senderCacheDao()?.getSender(sender)
        if (cachedSender != null) {
            senderRiskScore = cachedSender.riskScore
            senderRiskLevel = cachedSender.riskLevel
            isFinalized = cachedSender.isFinalized
        } else if (useOnlineSenderLookup && senderRepository != null) {
            // Ağdan sorgula
            when (val lookupResult = senderRepository.lookupSender(sender)) {
                is NetworkResult.Success -> {
                    val data = lookupResult.data
                    if (data.found) {
                        senderRiskScore = data.riskScore
                        senderRiskLevel = data.riskLevel
                        isFinalized = data.isFinalized

                        // Önbelleğe kaydet
                        database?.senderCacheDao()?.insertSender(
                            com.smsfiltering.client.data.local.entity.SenderCacheEntity(
                                sender = sender,
                                riskScore = data.riskScore,
                                riskLevel = data.riskLevel,
                                isFinalized = data.isFinalized
                            )
                        )
                    }
                }
                else -> {
                    // Ağ hatasında varsayılan değerler kullanılır
                }
            }
        }

        // 3. Filtreleme Analizini Yap
        val result = evaluate(
            sender = sender,
            body = body,
            senderRiskScore = senderRiskScore,
            senderRiskLevel = senderRiskLevel,
            isFinalized = isFinalized,
            categoriesWithKeywords = categoriesWithKeywords,
            topicsWithKeywords = topicsWithKeywords
        )

        // 4. İstenirse Sonucu Veritabanına Kaydet
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

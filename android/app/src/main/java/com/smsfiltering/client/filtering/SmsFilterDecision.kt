package com.smsfiltering.client.filtering

/**
 * SMS filtreleme karar sonuçları.
 */
enum class SmsFilterDecision {
    /**
     * Güvenli mesaj. Herhangi bir risk tespit edilmedi.
     */
    SAFE,

    /**
     * Şüpheli mesaj. Bilinmeyen bağlantı, acil ödeme baskısı veya teyitsiz gönderici.
     */
    SUSPICIOUS,

    /**
     * İstenmeyen mesaj. Yasadışı bahis/kumar tanıtımları veya ticari spam.
     */
    SPAM,

    /**
     * Yüksek riskli oltalama (kimlik avı/dolandırıcılık).
     * Şifre/PIN hırsızlığı, sahte banka/kargo/operatör bağlantıları veya tehlikeli göndericiler.
     */
    PHISHING
}

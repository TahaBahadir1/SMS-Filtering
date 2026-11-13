package com.smsfiltering.client.util

import java.util.Locale
import java.util.regex.Pattern

/**
 * Yapay zeka modeli (TF-IDF + Lojistik Regresyon) için metin temizleme motoru.
 *
 * Python'daki `clean_text` fonksiyonunun Android tarafındaki birebir karşılığıdır:
 * 1. Türkçe uyumlu küçük harf dönüşümü yapar (İ -> i, I -> ı). Türkçe karakterleri korur.
 * 2. Spam filtrelerini atlatmak için harf arasına konulan hileli boşlukları ('p  a  ra' -> 'para') birleştirir.
 * 3. Hileli harf uzatmalarını ('fırsaaaat' -> 'fırsat', 'kazaaaan' -> 'kazan') sadeleştirir.
 *
 * NOT: Bu sınıf yapay zeka çıkarımı (on-device ML inference) için kullanılır.
 * Model Türkçe karakterlerle eğitildiği için karakterler kesinlikle ASCII'ye çevrilmez.
 */
object SmsCleaner {

    val TURKISH_LOCALE: Locale = Locale("tr", "TR")

    // Harf veya rakam arasına konmuş hileli boşlukları yakalayan regex
    private val SPACE_REGEX = Regex("""(?U)\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}(?U)\b)""")

    // 3 veya daha fazla ardışık harf tekrarını teke indiren regex
    private val REPEAT_REGEX = Regex("""(.)\1{2,}""")

    /**
     * Ham SMS metnini yapay zeka modelinin girdi formatına uygun şekilde temizler.
     */
    fun cleanText(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        // 1. Türkçe kurallarına göre küçük harfe çevirme (İ -> i, I -> ı)
        var text = rawText
            .replace('İ', 'i')
            .replace('I', 'ı')
            .lowercase(TURKISH_LOCALE)

        // 2. Metin doğrudan bağlantı (http/https) değilse harf arası boşluk ve uzatma hilelerini temizle
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            repeat(5) {
                text = text.replace(SPACE_REGEX, "$1")
            }
            // 3. Harf uzatma hilesini temizle (örn: 'fırsaaaat' -> 'fırsat')
            text = text.replace(REPEAT_REGEX, "$1")
        }

        return text.trim()
    }
}

package com.smsfiltering.client.util

import java.util.Locale

/**
 * Yapay zeka modeli (TF-IDF + Lojistik Regresyon) için güçlendirilmiş metin temizleme motoru (Hafta 7 - İP 3).
 *
 * Python'daki `clean_text` fonksiyonunun Android tarafındaki birebir karşılığıdır:
 * 1. Görünmez Unicode karakterlerini (Zero-Width Space, ZWNJ, ZWJ, BOM, Soft Hyphen vb.) temizler.
 * 2. Türkçe uyumlu küçük harf dönüşümü yapar (İ -> i, I -> ı). Türkçe karakterleri korur.
 * 3. URL ve e-posta dışındaki kelimelerde sembol tabanlı Leetspeak (@ -> a, $ -> s) ve
 *    harf arasına konulan noktalama/sembol gizlemelerini ('b.o.n.u.s' -> 'bonus', 'b-a-h-i-s' -> 'bahis') birleştirir.
 * 4. Spam filtrelerini atlatmak için harf arasına konulan hileli boşlukları ('p  a  ra' -> 'para') birleştirir.
 * 5. Hileli harf uzatmalarını ('fırsaaaat' -> 'fırsat', 'kazaaaan' -> 'kazan') sadeleştirir.
 * 6. Türkçe ünlü uyumuna duyarlı rakamsal Leetspeak ('0'->'o', '1'->'i'/'ı', '3'->'e', '4'->'a', '5'->'s', '7'->'t')
 *    çözümlemesi gerçekleştirir; meşru sayı, saat, yüzde (%80'ini) ve ölçü birimlerini (2gb, 3d) korur.
 */
object SmsCleaner {

    val TURKISH_LOCALE: Locale = Locale("tr", "TR")

    // 1. Görünmez Unicode karakterleri ve özel boşluklar
    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B-\u200F\u202A-\u202E\u2060-\u2064\uFEFF\u00AD\u034F]""")
    private val UNICODE_SPACES_REGEX = Regex("""[\u00A0\u2000-\u200A\u202F\u205F\u3000]""")

    // URL ve E-posta koruma desenleri
    private val URL_TOKEN_REGEX = Regex("""^(?i)(?:https?://|www\.|bit\.ly/|t\.co/|tinyurl\.com/)\S+$""")
    private val EMAIL_TOKEN_REGEX = Regex("""^(?i)[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}$""")

    // 2. Noktalama/sembol aralama hilesi (örn: b.o.n.u.s, b-a-h-i-s, k_a_z_a_n, s*l*o*t)
    private val SYMBOL_OBFUSCATION_REGEX = Regex(
        """(?U)(?<![a-zçğıöşü0-9])([a-zçğıöşü0-9])(?:[.\-_*+]([a-zçğıöşü0-9])){2,}(?![a-zçğıöşü0-9])"""
    )
    private val SYMBOL_CLEAN_REGEX = Regex("""[.\-_*+]""")

    // 3. Harf veya rakam arasına konmuş hileli boşlukları yakalayan regex
    private val SPACE_REGEX = Regex("""(?U)\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}(?U)\b)""")

    // 4. 3 veya daha fazla ardışık harf tekrarını teke indiren regex
    private val REPEAT_REGEX = Regex("""(.)\1{2,}""")

    // 5. Alfanümerik kelime parçası yakalayıcı ('%80'ini' veya '14:00'te' ifadelerini bozmamak için)
    private val LEET_SPAN_REGEX = Regex("""[a-zçğıöşü0-9]+""")

    // Meşru ölçü/para birimi sonekleri
    private val UNIT_TOKENS_REGEX = Regex("""^\d+(?:gb|mb|tb|tl|kr|dk|sn|sa|km|kg|gr|mg|ml|lt|d|g|k|m)$""")

    private const val TURKISH_LETTERS = "abcçdefgğhıijklmnoöprsştuüvyz"
    private const val LEET_DIGITS = "013457"
    private const val FRONT_VOWELS = "eiöü"
    private const val BACK_VOWELS = "aıou"
    private const val ALL_VOWELS = "aıoueiöü"

    // Kısa 2-3 karakterli özel leetspeak kelimeleri
    private val SHORT_LEET_MAP = mapOf(
        "v3" to "ve",
        "0l" to "ol",
        "3k" to "ek",
        "1l" to "il",
        "3n" to "en",
        "0n" to "on",
        "3t" to "et",
        "1ş" to "iş",
        "a7" to "at",
        "0y" to "oy",
        "7l" to "tl",
        "v1p" to "vıp"
    )

    // Kalın ünlü (a, o, u) içermesine rağmen '1' rakamının ince 'i' olması gereken kelime kökleri
    private val FRONT_I_STEMS = listOf(
        "bahis", "casino", "nakit", "limit", "kripto", "spin", "vip", "link",
        "aktif", "taksit", "icra", "iban", "hediye", "kredi", "şifre", "sifre",
        "çekiliş", "ücretsiz", "acil", "bilgi", "kilit", "şüpheli", "tebrik",
        "işlem", "çevrim", "getir", "kesin", "garanti", "risk", "site", "fatura",
        "iptal", "faiz", "sahip", "cazip", "katil", "tatil", "mesai", "bayi"
    )

    /**
     * Hafta 5-6'daki eski ön işleme fonksiyonu (Adversarial dayanıklılık testlerinde karşılaştırma referansı için).
     */
    fun cleanTextLegacy(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        var text = rawText
            .replace('İ', 'i')
            .replace('I', 'ı')
            .lowercase(TURKISH_LOCALE)

        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            repeat(5) {
                text = text.replace(SPACE_REGEX, "$1")
            }
            text = text.replace(REPEAT_REGEX, "$1")
        }

        return text.trim()
    }

    /**
     * Ham SMS metnini filtre atlatma (adversarial / obfuscation) saldırılarına karşı temizler
     * ve yapay zeka modelinin girdi formatına dönüştürür.
     */
    fun cleanText(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        // 1. Görünmez Unicode karakterlerini sil ve özel boşlukları standartlaştır
        var text = rawText
            .replace(INVISIBLE_CHARS_REGEX, "")
            .replace(UNICODE_SPACES_REGEX, " ")

        // 2. Türkçe kurallarına göre küçük harfe çevirme (İ -> i, I -> ı)
        text = text
            .replace('İ', 'i')
            .replace('I', 'ı')
            .lowercase(TURKISH_LOCALE)

        if (text.startsWith("http://") || text.startsWith("https://")) {
            return text.trim()
        }

        // 3. URL ve e-posta olmayan tokenlarda @ -> a, $ -> s ve noktalama aralama (b.o.n.u.s) temizliği
        val preTokens = text.split(" ").map { tok ->
            if (URL_TOKEN_REGEX.matches(tok) || EMAIL_TOKEN_REGEX.matches(tok)) {
                tok
            } else {
                val symReplaced = tok.replace('@', 'a').replace('$', 's')
                SYMBOL_OBFUSCATION_REGEX.replace(symReplaced) { matchResult ->
                    matchResult.value.replace(SYMBOL_CLEAN_REGEX, "")
                }
            }
        }
        text = preTokens.joinToString(" ")

        // 4. Harf arası boşluk hilesini birleştir (5 iterasyon)
        repeat(5) {
            text = text.replace(SPACE_REGEX, "$1")
        }

        // 5. URL olmayan tokenlarda harf/rakam uzatmalarını sadeleştir ve rakamsal Leetspeak (0,1,3,4,5,7) çözümle
        val cleanedTokens = text.split(" ").map { tok ->
            if (URL_TOKEN_REGEX.matches(tok) || EMAIL_TOKEN_REGEX.matches(tok)) {
                tok
            } else {
                val repeatCleaned = tok.replace(REPEAT_REGEX, "$1")
                val deleeted = LEET_SPAN_REGEX.replace(repeatCleaned) { matchResult ->
                    deleetSpan(matchResult.value)
                }
                deleeted.replace(REPEAT_REGEX, "$1")
            }
        }

        return cleanedTokens.joinToString(" ").trim()
    }

    private fun deleetSpan(span: String): String {
        SHORT_LEET_MAP[span]?.let { return it }

        val hasLetter = span.any { it in TURKISH_LETTERS }
        val hasLeet = span.any { it in LEET_DIGITS }
        if (!hasLetter || !hasLeet) return span

        if (span.length < 3 || UNIT_TOKENS_REGEX.matches(span)) return span

        val letterCount = span.count { it in TURKISH_LETTERS }
        val digitCount = span.count { it.isDigit() }
        if (letterCount < 1 || digitCount > letterCount + 2) return span

        var temp = span
            .replace('0', 'o')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')

        if ('1' in temp) {
            val candidateI = temp.replace('1', 'i')
            val matchedStem = FRONT_I_STEMS.firstOrNull { candidateI.startsWith(it) }
            if (matchedStem != null) {
                val stemLen = matchedStem.length
                val prefix = candidateI.substring(0, stemLen)
                var suffix = temp.substring(stemLen)
                if ('1' in suffix) {
                    val lastStemVowel = prefix.lastOrNull { it in ALL_VOWELS } ?: 'i'
                    val repChar = if (lastStemVowel in BACK_VOWELS) 'ı' else 'i'
                    suffix = suffix.replace('1', repChar)
                }
                temp = prefix + suffix
            } else {
                val hasFrontVowel = temp.any { it in FRONT_VOWELS }
                val hasBackVowel = temp.any { it in BACK_VOWELS }
                temp = when {
                    hasBackVowel && !hasFrontVowel -> temp.replace('1', 'ı')
                    hasFrontVowel && !hasBackVowel -> temp.replace('1', 'i')
                    else -> {
                        val chars = temp.toCharArray()
                        for (idx in chars.indices) {
                            if (chars[idx] == '1') {
                                var nearestVowel: Char? = null
                                for (p in idx - 1 downTo 0) {
                                    if (chars[p] in ALL_VOWELS) {
                                        nearestVowel = chars[p]
                                        break
                                    }
                                }
                                if (nearestVowel == null) {
                                    for (n in idx + 1 until chars.size) {
                                        if (chars[n] in ALL_VOWELS) {
                                            nearestVowel = chars[n]
                                            break
                                        }
                                    }
                                }
                                chars[idx] = if (nearestVowel != null && nearestVowel in BACK_VOWELS) 'ı' else 'i'
                            }
                        }
                        String(chars)
                    }
                }
            }
        }

        return temp
    }
}

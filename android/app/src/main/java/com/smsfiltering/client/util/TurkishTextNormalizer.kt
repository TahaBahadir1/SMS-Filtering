package com.smsfiltering.client.util

import java.text.Normalizer
import java.util.Locale

/**
 * SMS filtreleme için Türkçe karakter normalizasyonu ve metin eşleme yardımcı sınıfı.
 *
 * Türkçe'deki özel harfler:
 * - Ç, ç -> c
 * - Ğ, ğ -> g
 * - I, ı -> i
 * - İ, i -> i
 * - Ö, ö -> o
 * - Ş, ş -> s
 * - Ü, ü -> u
 * - Â, â -> a, Î, î -> i, Û, û -> u (Şapkalı sesliler)
 *
 * Bu normalizasyon sayesinde hem Türkçe karakterli ("şifre", "tıklayınız", "ödenmemiş fatura")
 * hem de ASCII karakterli ("sifre", "tiklayiniz", "odenmemis fatura") SMS içerikleri
 * sunucudan gelen anahtar kelimelerle güvenilir ve tam uyumlu şekilde eşleşir.
 */
object TurkishTextNormalizer {

    val TURKISH_LOCALE: Locale = Locale("tr", "TR")

    private val TURKISH_TO_ASCII_MAP = mapOf(
        'ç' to 'c', 'Ç' to 'c',
        'ğ' to 'g', 'Ğ' to 'g',
        'ı' to 'i', 'I' to 'i',
        'i' to 'i', 'İ' to 'i',
        'ö' to 'o', 'Ö' to 'o',
        'ş' to 's', 'Ş' to 's',
        'ü' to 'u', 'Ü' to 'u',
        'â' to 'a', 'Â' to 'a',
        'î' to 'i', 'Î' to 'i',
        'û' to 'u', 'Û' to 'u'
    )

    /**
     * Metni Türkçe yerel kurallarına (Locale("tr", "TR")) göre küçük harfe dönüştürür.
     * Örneğin: 'İ' -> 'i', 'I' -> 'ı'.
     */
    fun toTurkishLowerCase(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return text.lowercase(TURKISH_LOCALE)
    }

    /**
     * Metindeki tüm Türkçe ve aksanlı karakterleri ASCII denklerine dönüştürür.
     * Casing korunmaz, küçük harfe normalize edilir.
     */
    fun toAscii(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        val sb = StringBuilder(text.length)
        for (char in text) {
            val asciiChar = TURKISH_TO_ASCII_MAP[char]
            if (asciiChar != null) {
                sb.append(asciiChar)
            } else {
                sb.append(char)
            }
        }

        // Genel Unicode aksan temizleme (NFD biçiminde parçalayıp diacritic işaretlerini atma)
        val decomposed = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
        return decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    /**
     * SMS içeriği ve anahtar kelimeler için tam normalizasyon:
     * 1. Türkçe yerelinde küçük harfe çevirme (İ -> i, I -> ı).
     * 2. Türkçe ve aksanlı karakterleri ASCII'ye dönüştürme (ı -> i, ş -> s, vb.).
     * 3. Çoklu boşlukları tek boşluğa indirgeme ve baş/son boşlukları kırpma.
     *
     * Örnek: "  ACİL   ŞİFRENİZİ   paylaşmayın! " -> "acil sifrenizi paylasmayin!"
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val lowercased = toTurkishLowerCase(text)
        val ascii = toAscii(lowercased)
        return ascii.replace("\\s+".toRegex(), " ").trim()
    }

    /**
     * Gönderen başlığını veya numarasını API sözleşmesine uygun şekilde temizler.
     */
    fun normalizeSender(sender: String?): String {
        if (sender.isNullOrBlank()) return ""
        return sender.trim()
    }

    /**
     * docs/api-sozlesmesi.md doğrulama kurallarına göre gönderici kontrolü:
     * - sender alanı boş olamaz (@NotBlank)
     * - sender alanı en fazla 32 karakter olabilir (@Size(max = 32))
     */
    fun validateSender(sender: String?): SenderValidationResult {
        if (sender.isNullOrBlank()) {
            return SenderValidationResult.Invalid("Gönderen bilgisi boş olamaz")
        }
        val trimmed = sender.trim()
        if (trimmed.length > 32) {
            return SenderValidationResult.Invalid("Gönderen bilgisi en fazla 32 karakter olabilir")
        }
        return SenderValidationResult.Valid(trimmed)
    }

    /**
     * Normalize edilmiş SMS metninde, belirtilen anahtar kelimenin geçip geçmediğini kontrol eder.
     * Kelime veya ifade eşleşmesi normalize edilmiş biçimler üzerinden yapılır.
     */
    fun containsKeyword(smsText: String?, keyword: String?): Boolean {
        if (smsText.isNullOrBlank() || keyword.isNullOrBlank()) return false

        val normalizedText = normalize(smsText)
        val normalizedKeyword = normalize(keyword)

        if (normalizedKeyword.isEmpty()) return false

        return normalizedText.contains(normalizedKeyword)
    }

    /**
     * Bir SMS metninde verilen anahtar kelimeler listesinden eşleşenleri bulur.
     */
    fun findMatches(smsText: String?, keywords: List<String>): List<String> {
        if (smsText.isNullOrBlank() || keywords.isEmpty()) return emptyList()

        val normalizedText = normalize(smsText)
        val matches = mutableListOf<String>()

        for (kw in keywords) {
            val normalizedKw = normalize(kw)
            if (normalizedKw.isNotEmpty() && normalizedText.contains(normalizedKw)) {
                matches.add(kw)
            }
        }

        return matches
    }
}

sealed class SenderValidationResult {
    data class Valid(val sanitizedSender: String) : SenderValidationResult()
    data class Invalid(val errorMessage: String) : SenderValidationResult()

    val isValid: Boolean get() = this is Valid
}

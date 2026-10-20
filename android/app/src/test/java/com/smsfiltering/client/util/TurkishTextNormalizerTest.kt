package com.smsfiltering.client.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishTextNormalizerTest {

    @Test
    fun testTurkishLowerCase() {
        assertEquals("istanbul", TurkishTextNormalizer.toTurkishLowerCase("İSTANBUL"))
        assertEquals("ışık", TurkishTextNormalizer.toTurkishLowerCase("IŞIK"))
        assertEquals("şifre", TurkishTextNormalizer.toTurkishLowerCase("ŞİFRE"))
        assertEquals("ödeme", TurkishTextNormalizer.toTurkishLowerCase("ÖDEME"))
        assertEquals("güncelleme", TurkishTextNormalizer.toTurkishLowerCase("GÜNCELLEME"))
        assertEquals("çağrı", TurkishTextNormalizer.toTurkishLowerCase("ÇAĞRI"))
    }

    @Test
    fun testToAsciiConvertsAllTurkishDiacritics() {
        val turkishText = "çÇğĞıIİiöÖşŞüÜâÂîÎûÛ"
        val expected = "ccggiiiioossuuaaiiuu"
        assertEquals(expected, TurkishTextNormalizer.toAscii(turkishText.lowercase(TurkishTextNormalizer.TURKISH_LOCALE)))
    }

    @Test
    fun testNormalizeGeneral() {
        val input = "   ACİL   ŞİFRENİZİ   paylaşmayın!   "
        val expected = "acil sifrenizi paylasmayin!"
        assertEquals(expected, TurkishTextNormalizer.normalize(input))
    }

    @Test
    fun testKeywordsMatching_SifreVariations() {
        val keyword = "şifre"

        // Farklı yazım ve aksan varyasyonları
        val variation1 = "Tek kullanımlık ŞİFRE kodunuz 987654"
        val variation2 = "Tek kullanimlik SIFRE kodunuz 987654"
        val variation3 = "SİFRE talebiniz alınmıştır"
        val variation4 = "şifrenizi kimseyle paylaşmayın"
        val variation5 = "sifrenizi kimseyle paylasmayin"

        assertTrue(TurkishTextNormalizer.containsKeyword(variation1, keyword))
        assertTrue(TurkishTextNormalizer.containsKeyword(variation2, keyword))
        assertTrue(TurkishTextNormalizer.containsKeyword(variation3, keyword))
        assertTrue(TurkishTextNormalizer.containsKeyword(variation4, keyword))
        assertTrue(TurkishTextNormalizer.containsKeyword(variation5, keyword))
    }

    @Test
    fun testKeywordsMatching_IbanAndHesapNo() {
        val sms = "Sayın müşterimiz, alıcı IBAN ve hesap numarası bilgilerini kontrol ediniz."

        assertTrue(TurkishTextNormalizer.containsKeyword(sms, "iban"))
        assertTrue(TurkishTextNormalizer.containsKeyword(sms, "hesap numarası"))
        assertTrue(TurkishTextNormalizer.containsKeyword(sms, "alici iban"))
        assertFalse(TurkishTextNormalizer.containsKeyword(sms, "kredi kartı"))
    }

    @Test
    fun testKeywordsMatching_AcilOdemeAndLink() {
        val sms = "Sayin musterimiz, gecikmis borcunuz bulunmaktadir. Derhal odeyiniz: hemen tikla"

        assertTrue(TurkishTextNormalizer.containsKeyword(sms, "gecikmiş borç"))
        assertTrue(TurkishTextNormalizer.containsKeyword(sms, "derhal ödeyiniz"))
        assertTrue(TurkishTextNormalizer.containsKeyword(sms, "hemen tıkla"))
    }

    @Test
    fun testFindMatches() {
        val sms = "ACİL ÖDEME: Son ödeme tarihi bugün! Detaylar için linke tıklayın."
        val candidateKeywords = listOf(
            "acil ödeme",
            "son ödeme tarihi",
            "linke tıklayın",
            "kredi kartı",
            "hesap no"
        )

        val matches = TurkishTextNormalizer.findMatches(sms, candidateKeywords)
        assertEquals(3, matches.size)
        assertTrue(matches.contains("acil ödeme"))
        assertTrue(matches.contains("son ödeme tarihi"))
        assertTrue(matches.contains("linke tıklayın"))
        assertFalse(matches.contains("kredi kartı"))
    }

    @Test
    fun testNormalizeSender() {
        assertEquals("BANKA-X", TurkishTextNormalizer.normalizeSender("   BANKA-X   "))
        assertEquals("+905559876543", TurkishTextNormalizer.normalizeSender("+905559876543"))
        assertEquals("", TurkishTextNormalizer.normalizeSender(null))
        assertEquals("", TurkishTextNormalizer.normalizeSender("   "))
    }
}

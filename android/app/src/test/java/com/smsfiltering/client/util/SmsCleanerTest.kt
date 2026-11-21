package com.smsfiltering.client.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsCleanerTest {

    @Test
    fun `cleanText should lowercase with Turkish locale`() {
        val input = "ŞİFRE VE ÖDEME İŞLEMLERİ"
        val expected = "şifre ve ödeme işlemleri"
        assertEquals(expected, SmsCleaner.cleanText(input))
    }

    @Test
    fun `cleanText should merge deceptive spaces`() {
        val input = "p  a  ra KzAN ACIL TIKLA"
        val expected = "para kzan acıl tıkla"
        assertEquals(expected, SmsCleaner.cleanText(input))
    }

    @Test
    fun `cleanText should normalize repeated characters`() {
        val input = "fırsaaaat kaçırma hemen kazaaaan"
        val expected = "fırsat kaçırma hemen kazan"
        assertEquals(expected, SmsCleaner.cleanText(input))
    }

    @Test
    fun `cleanText should keep urls untouched by space regex`() {
        val url = "https://www.turkiye.gov.tr/"
        assertEquals(url, SmsCleaner.cleanText(url))
    }

    @Test
    fun `cleanText should deobfuscate leetspeak characters with Turkish vowel harmony`() {
        val input = "GÜNC3L B@H1S V3 B0NUS FIRS@TI! 1lk y@t1r1m@ %200 ç3vr1ms1z b0nus h3m3n k@yd0l"
        val expected = "güncel bahis ve bonus fırsatı! ilk yatırıma %200 çevrimsiz bonus hemen kaydol"
        assertEquals(expected, SmsCleaner.cleanText(input))
    }

    @Test
    fun `cleanText should remove invisible unicode zero-width characters`() {
        val input = "G\u200BÜ\u200CN\u200DCEL B\u200BA\u200CH\u200DİS VE B\u200BO\uFEFFN\u00ADUS FIR\u2060SATI"
        val expected = "güncel bahis ve bonus fırsatı"
        assertEquals(expected, SmsCleaner.cleanText(input))
    }

    @Test
    fun `cleanText should collapse punctuation and symbol separated letters`() {
        val input = "C.a.s.i.n.o ve s-l-o-t oyunlarında k_a_y_ı_p b*o*n*u*s*u eklendi"
        val expected = "casino ve slot oyunlarında kayıp bonusu eklendi"
        assertEquals(expected, SmsCleaner.cleanText(input))
    }

    @Test
    fun `cleanText should handle hybrid multi-layer adversarial obfuscation`() {
        val input = "G.Ü.N.C.3.L B\u200B@H1S V3 B-0-N-U-S FIRS@@@TI! 1lk y\u200C@t1r1m@ %200 ç_3_v_r_1_m_s_1_z b000nus h.3.m.3.n k@yd0l"
        val expected = "güncel bahis ve bonus fırsatı! ilk yatırıma %200 çevrimsiz bonus hemen kaydol"
        assertEquals(expected, SmsCleaner.cleanText(input))
    }

    @Test
    fun `cleanText should preserve legitimate numbers percentages times and urls`() {
        val input = "İnternet paketinizin %80'ini kullandınız. Yarın saat 14:00'te 123456 kodu ile https://www.turkiye.gov.tr/ adresine giriniz."
        val expected = "internet paketinizin %80'ini kullandınız. yarın saat 14:00'te 123456 kodu ile https://www.turkiye.gov.tr/ adresine giriniz."
        assertEquals(expected, SmsCleaner.cleanText(input))
    }
}

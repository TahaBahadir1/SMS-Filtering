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
        val expected = "para kzan acil tıkla"
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
}

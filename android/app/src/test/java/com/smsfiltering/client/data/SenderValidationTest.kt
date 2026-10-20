package com.smsfiltering.client.data

import com.smsfiltering.client.util.SenderValidationResult
import com.smsfiltering.client.util.TurkishTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderValidationTest {

    @Test
    fun testBlankSenderReturnsInvalid() {
        val resultNull = TurkishTextNormalizer.validateSender(null)
        assertFalse(resultNull.isValid)
        assertEquals("Gönderen bilgisi boş olamaz", (resultNull as SenderValidationResult.Invalid).errorMessage)

        val resultEmpty = TurkishTextNormalizer.validateSender("")
        assertFalse(resultEmpty.isValid)
        assertEquals("Gönderen bilgisi boş olamaz", (resultEmpty as SenderValidationResult.Invalid).errorMessage)

        val resultWhitespace = TurkishTextNormalizer.validateSender("     ")
        assertFalse(resultWhitespace.isValid)
        assertEquals("Gönderen bilgisi boş olamaz", (resultWhitespace as SenderValidationResult.Invalid).errorMessage)
    }

    @Test
    fun testSenderExceeding32CharsReturnsInvalid() {
        val longSender = "BU_GONDEREN_ADI_KESINLIKLE_32_KARAKTERDEN_DAHA_UZUNDUR"
        assertTrue(longSender.length > 32)

        val result = TurkishTextNormalizer.validateSender(longSender)
        assertFalse(result.isValid)
        assertEquals("Gönderen bilgisi en fazla 32 karakter olabilir", (result as SenderValidationResult.Invalid).errorMessage)
    }

    @Test
    fun testValidSenders() {
        // Tam 32 karakter
        val exact32 = "12345678901234567890123456789012"
        assertEquals(32, exact32.length)

        val result32 = TurkishTextNormalizer.validateSender(exact32)
        assertTrue(result32.isValid)
        assertEquals(exact32, (result32 as SenderValidationResult.Valid).sanitizedSender)

        // Normal banka başlığı ve telefon numarası
        val bankSender = TurkishTextNormalizer.validateSender("  BANKA-X  ")
        assertTrue(bankSender.isValid)
        assertEquals("BANKA-X", (bankSender as SenderValidationResult.Valid).sanitizedSender)

        val phoneSender = TurkishTextNormalizer.validateSender("+905559876543")
        assertTrue(phoneSender.isValid)
        assertEquals("+905559876543", (phoneSender as SenderValidationResult.Valid).sanitizedSender)
    }
}

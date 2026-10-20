package com.smsfiltering.client.data

import com.google.gson.Gson
import com.smsfiltering.client.data.remote.dto.ErrorResponseDto
import com.smsfiltering.client.data.remote.dto.KeywordListResponseDto
import com.smsfiltering.client.data.remote.dto.SenderLookupRequestDto
import com.smsfiltering.client.data.remote.dto.SenderLookupResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsFilteringApiDtoTest {

    private val gson = Gson()

    @Test
    fun testKeywordsScenarioA_FullListDeserialization() {
        val json = """
        {
          "version": 1,
          "upToDate": false,
          "categories": [
            {
              "id": 1,
              "name": "IBAN",
              "keywords": [
                "iban",
                "hesap numarası",
                "hesap no"
              ]
            },
            {
              "id": 2,
              "name": "PIN",
              "keywords": [
                "pin kodu",
                "kart şifresi"
              ]
            }
          ]
        }
        """.trimIndent()

        val response = gson.fromJson(json, KeywordListResponseDto::class.java)

        assertEquals(1L, response.version)
        assertFalse(response.upToDate)
        assertEquals(2, response.categories.size)

        val ibanCat = response.categories[0]
        assertEquals(1L, ibanCat.id)
        assertEquals("IBAN", ibanCat.name)
        assertEquals(3, ibanCat.keywords.size)
        assertTrue(ibanCat.keywords.contains("hesap numarası"))
    }

    @Test
    fun testKeywordsScenarioB_UpToDateDeserialization() {
        val json = """
        {
          "version": 1,
          "upToDate": true,
          "categories": []
        }
        """.trimIndent()

        val response = gson.fromJson(json, KeywordListResponseDto::class.java)

        assertEquals(1L, response.version)
        assertTrue(response.upToDate)
        assertTrue(response.categories.isEmpty())
    }

    @Test
    fun testSenderLookupRequestSerialization() {
        val request = SenderLookupRequestDto(sender = "BANKA-X")
        val json = gson.toJson(request)

        assertTrue(json.contains("\"sender\":\"BANKA-X\""))
    }

    @Test
    fun testSenderLookupScenarioA_FoundDeserialization() {
        val json = """
        {
          "found": true,
          "riskScore": 0.05,
          "riskLevel": "risksiz",
          "isFinalized": true
        }
        """.trimIndent()

        val response = gson.fromJson(json, SenderLookupResponseDto::class.java)

        assertTrue(response.found)
        assertEquals(0.05, response.riskScore!!, 0.0001)
        assertEquals("risksiz", response.riskLevel)
        assertTrue(response.isFinalized)
    }

    @Test
    fun testSenderLookupScenarioB_NotFoundDeserialization() {
        val json = """
        {
          "found": false,
          "riskScore": null,
          "riskLevel": null,
          "isFinalized": false
        }
        """.trimIndent()

        val response = gson.fromJson(json, SenderLookupResponseDto::class.java)

        assertFalse(response.found)
        assertNull(response.riskScore)
        assertNull(response.riskLevel)
        assertFalse(response.isFinalized)
    }

    @Test
    fun testErrorResponseDeserialization() {
        val json = """
        {
          "error": "Gönderen bilgisi boş olamaz"
        }
        """.trimIndent()

        val response = gson.fromJson(json, ErrorResponseDto::class.java)
        assertEquals("Gönderen bilgisi boş olamaz", response.error)
    }
}

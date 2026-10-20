package com.smsfiltering.client.data

import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.SenderRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SenderAndKeywordRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var keywordRepository: KeywordRepository
    private lateinit var senderRepository: SenderRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/v1/").toString()
        val apiService = ApiClient.createService(baseUrl = baseUrl)

        keywordRepository = KeywordRepository(apiService)
        senderRepository = SenderRepository(apiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testGetKeywords_VersionZero_ReturnsFullList() = runBlocking {
        val json = """
        {
          "version": 1,
          "upToDate": false,
          "categories": [
            {
              "id": 1,
              "name": "IBAN",
              "keywords": ["iban", "hesap numarası"]
            }
          ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val result = keywordRepository.getKeywords(currentVersion = 0)

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertEquals(1L, data.version)
        assertFalse(data.upToDate)
        assertEquals(1, data.categories.size)
        assertEquals("IBAN", data.categories[0].name)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/api/v1/keywords?version=0", recordedRequest.path)
    }

    @Test
    fun testGetKeywords_VersionUpToDate_ReturnsEmptyCategories() = runBlocking {
        val json = """
        {
          "version": 1,
          "upToDate": true,
          "categories": []
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val result = keywordRepository.getKeywords(currentVersion = 1)

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertEquals(1L, data.version)
        assertTrue(data.upToDate)
        assertTrue(data.categories.isEmpty())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v1/keywords?version=1", recordedRequest.path)
    }

    @Test
    fun testLookupSender_Found() = runBlocking {
        val json = """
        {
          "found": true,
          "riskScore": 0.05,
          "riskLevel": "risksiz",
          "isFinalized": true
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val result = senderRepository.lookupSender("BANKA-X")

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertTrue(data.found)
        assertEquals(0.05, data.riskScore!!, 0.0001)
        assertEquals("risksiz", data.riskLevel)
        assertTrue(data.isFinalized)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/senders/lookup", recordedRequest.path)
        assertEquals("{\"sender\":\"BANKA-X\"}", recordedRequest.body.readUtf8())
    }

    @Test
    fun testLookupSender_NotFound() = runBlocking {
        val json = """
        {
          "found": false,
          "riskScore": null,
          "riskLevel": null,
          "isFinalized": false
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val result = senderRepository.lookupSender("BILINMEYEN_GONDEREN")

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertFalse(data.found)
        assertNull(data.riskScore)
        assertNull(data.riskLevel)
        assertFalse(data.isFinalized)
    }

    @Test
    fun testLookupSender_BlankSender_FailsClientValidation() = runBlocking {
        val result = senderRepository.lookupSender("   ")

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(400, error.code)
        assertEquals("Gönderen bilgisi boş olamaz", error.message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun testLookupSender_TooLongSender_FailsClientValidation() = runBlocking {
        val tooLong = "A".repeat(33)
        val result = senderRepository.lookupSender(tooLong)

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(400, error.code)
        assertEquals("Gönderen bilgisi en fazla 32 karakter olabilir", error.message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun testLookupSender_ServerError400ParsedCorrectly() = runBlocking {
        val errorJson = """
        {
          "error": "İstek gövdesi okunamadı veya biçimi hatalı"
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorJson)
        )

        val result = senderRepository.lookupSender("VALID-SENDER")

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(400, error.code)
        assertEquals("İstek gövdesi okunamadı veya biçimi hatalı", error.message)
    }
}

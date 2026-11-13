package com.smsfiltering.client.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessagesViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testViewModel_InitialLoad_SeedsDemoDataAndPopulatesCounts() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.IO)
        val viewModel = MessagesViewModel(
            context = context,
            database = database,
            scope = testScope,
            ioDispatcher = Dispatchers.IO
        )
        viewModel.initJob.join()

        val state = viewModel.uiState.value
        assertTrue("Karantina sayısı > 0 olmalı", state.quarantineCount > 0)
        assertTrue("Gelen sayısı > 0 olmalı", state.inboxCount > 0)
        assertTrue("Engellenen sayısı > 0 olmalı", state.blockedCount > 0)
        assertTrue("Gönderenler listesi dolu olmalı", state.senders.isNotEmpty())
    }

    @Test
    fun testViewModel_SelectTab_FiltersCorrectMessages() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.IO)
        val viewModel = MessagesViewModel(
            context = context,
            database = database,
            scope = testScope,
            ioDispatcher = Dispatchers.IO
        )
        viewModel.initJob.join()

        // Karantina sekmesi seçildiğinde
        viewModel.selectTab(AppTab.QUARANTINE).join()
        val qState = viewModel.uiState.value
        assertEquals(AppTab.QUARANTINE, qState.currentTab)
        assertTrue(qState.messages.isNotEmpty())
        assertTrue(qState.messages.all {
            val d = it.decision.uppercase()
            d.contains("SUSPICIOUS") || d.contains("KARANTINA")
        })

        // Gelen sekmesi seçildiğinde
        viewModel.selectTab(AppTab.INBOX).join()
        val inState = viewModel.uiState.value
        assertEquals(AppTab.INBOX, inState.currentTab)
        assertTrue(inState.messages.isNotEmpty())
        assertTrue(inState.messages.all {
            val d = it.decision.uppercase()
            d.contains("SAFE") || d.contains("IZIN") || d.contains("GELEN")
        })

        // Engellenen sekmesi seçildiğinde
        viewModel.selectTab(AppTab.BLOCKED).join()
        val blState = viewModel.uiState.value
        assertEquals(AppTab.BLOCKED, blState.currentTab)
        assertTrue(blState.messages.isNotEmpty())
        assertTrue(blState.messages.all {
            val d = it.decision.uppercase()
            d.contains("SPAM") || d.contains("PHISHING") || d.contains("ENGELLE")
        })
    }

    @Test
    fun testViewModel_MessageDetailAndPrivacyToggle() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.IO)
        val viewModel = MessagesViewModel(
            context = context,
            database = database,
            scope = testScope,
            ioDispatcher = Dispatchers.IO
        )
        viewModel.initJob.join()

        val firstMsg = viewModel.uiState.value.messages.first()
        viewModel.openMessageDetail(firstMsg)

        var state = viewModel.uiState.value
        assertEquals(NavigationScreen.MESSAGE_DETAIL, state.currentNav)
        assertEquals(firstMsg.id, state.selectedMessage?.id)
        assertFalse("Mesaj metni varsayılan olarak gizli olmalıdır", state.isBodyRevealed)

        // Mesajı göster butonuna basıldığında
        viewModel.toggleBodyReveal()
        state = viewModel.uiState.value
        assertTrue("Mesajı göster sonrası metin açık olmalıdır", state.isBodyRevealed)

        // Mesajı gizle butonuna basıldığında
        viewModel.toggleBodyReveal()
        state = viewModel.uiState.value
        assertFalse("Tekrar tıklandığında metin gizlenmelidir", state.isBodyRevealed)
    }

    @Test
    fun testViewModel_MoveMessage_UpdatesDecisionAndSenderRule() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.IO)
        val viewModel = MessagesViewModel(
            context = context,
            database = database,
            scope = testScope,
            ioDispatcher = Dispatchers.IO
        )
        viewModel.initJob.join()

        viewModel.selectTab(AppTab.QUARANTINE).join()
        val qMsg = viewModel.uiState.value.messages.first { it.sender == "KREDI-DESTEK" }

        // Karantinadaki mesajı "SAFE" yap ve bu göndericiyi hep izinli olarak kaydet
        viewModel.moveMessage(qMsg.id, "SAFE", alsoConfigureSender = true).join()

        val updatedMsg = database.filteredMessageDao().getMessageById(qMsg.id)
        assertEquals("SAFE", updatedMsg?.decision)

        // Gönderen kuralının kaydedildiğini doğrula
        val senderRule = database.senderCacheDao().getSender("KREDI-DESTEK")
        assertNotNull(senderRule)
        assertEquals("risksiz", senderRule?.riskLevel)
        assertTrue(senderRule?.isFinalized == true)
    }

    @Test
    fun testViewModel_SenderRule_AddAndDelete() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.IO)
        val viewModel = MessagesViewModel(
            context = context,
            database = database,
            scope = testScope,
            ioDispatcher = Dispatchers.IO
        )
        viewModel.initJob.join()

        // Yeni gönderen kuralı ekle: SPAMMER-XYZ -> Engelle
        viewModel.addOrUpdateSenderRule("SPAMMER-XYZ", isBlocked = true).join()

        val senderRule = database.senderCacheDao().getSender("SPAMMER-XYZ")
        assertNotNull(senderRule)
        assertEquals("riskli", senderRule?.riskLevel)
        assertTrue(senderRule?.isDangerous == true)

        // Kuralı sil
        viewModel.deleteSenderRule("SPAMMER-XYZ").join()

        val deletedRule = database.senderCacheDao().getSender("SPAMMER-XYZ")
        assertTrue(deletedRule == null)
    }
}

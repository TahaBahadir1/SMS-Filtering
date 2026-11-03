package com.smsfiltering.client.ui

import android.content.Context
import com.smsfiltering.client.data.DemoDataSeeder
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.SenderRepository
import com.smsfiltering.client.filtering.SmsClassifier
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class AppTab {
    INBOX,          // Gelen
    QUARANTINE,     // Karantina
    BLOCKED         // Engellenen
}

enum class NavigationScreen {
    MESSAGES,       // Mesajlar listesi
    MESSAGE_DETAIL, // Mesaj detayı
    SENDERS,        // Gönderenler listesi
    SIMULATOR       // Test & Simülatör
}

data class UiState(
    val currentNav: NavigationScreen = NavigationScreen.MESSAGES,
    val currentTab: AppTab = AppTab.QUARANTINE,
    val messages: List<FilteredMessageEntity> = emptyList(),
    val inboxCount: Int = 0,
    val quarantineCount: Int = 0,
    val blockedCount: Int = 0,
    val selectedMessage: FilteredMessageEntity? = null,
    val isBodyRevealed: Boolean = false,
    val senders: List<SenderCacheEntity> = emptyList(),
    val keywordVersion: Long = 2L,
    val lastSyncFormatted: String = "10 dk önce",
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

class MessagesViewModel(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val keywordRepository = KeywordRepository(database = database)
    private val senderRepository = SenderRepository(database = database)
    private val classifier = try {
        SmsClassifier.getInstance(context)
    } catch (_: Exception) {
        null
    }
    private val filterEngine = SmsFilterEngine(
        database = database,
        keywordRepository = keywordRepository,
        senderRepository = senderRepository,
        classifier = classifier
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val initJob: Job = scope.launch {
        DemoDataSeeder.seedIfEmpty(database, ioDispatcher)
        loadAll()
    }

    fun selectTab(tab: AppTab): Job {
        _uiState.value = _uiState.value.copy(currentTab = tab)
        return scope.launch { loadMessages() }
    }

    fun navigateTo(screen: NavigationScreen): Job? {
        _uiState.value = _uiState.value.copy(currentNav = screen)
        return if (screen == NavigationScreen.SENDERS) {
            scope.launch { loadSenders() }
        } else if (screen == NavigationScreen.MESSAGES) {
            scope.launch { loadMessages() }
        } else null
    }

    fun openMessageDetail(message: FilteredMessageEntity) {
        _uiState.value = _uiState.value.copy(
            currentNav = NavigationScreen.MESSAGE_DETAIL,
            selectedMessage = message,
            isBodyRevealed = false
        )
    }

    fun toggleBodyReveal() {
        val current = _uiState.value.isBodyRevealed
        _uiState.value = _uiState.value.copy(isBodyRevealed = !current)
    }

    fun moveMessage(messageId: Long, targetDecision: String, alsoConfigureSender: Boolean = false): Job {
        return scope.launch {
            withContext(ioDispatcher) {
                val msg = database.filteredMessageDao().getMessageById(messageId)
                database.filteredMessageDao().updateDecision(messageId, targetDecision)

                if (alsoConfigureSender && msg != null) {
                    val isSafe = targetDecision.equals("SAFE", ignoreCase = true) || targetDecision.contains("izin", ignoreCase = true)
                    val cacheEntity = SenderCacheEntity(
                        sender = msg.sender,
                        riskScore = if (isSafe) 0.05 else 0.95,
                        riskLevel = if (isSafe) "risksiz" else "riskli",
                        isFinalized = true,
                        cachedAt = System.currentTimeMillis()
                    )
                    database.senderCacheDao().insertSender(cacheEntity)
                }
            }
            loadAll()
            _uiState.value = _uiState.value.copy(
                currentNav = NavigationScreen.MESSAGES,
                selectedMessage = null,
                statusMessage = "Mesaj durumu güncellendi"
            )
        }
    }

    fun addOrUpdateSenderRule(sender: String, isBlocked: Boolean): Job {
        return scope.launch {
            withContext(ioDispatcher) {
                val entity = SenderCacheEntity(
                    sender = sender.trim().uppercase(Locale("tr", "TR")),
                    riskScore = if (isBlocked) 0.95 else 0.05,
                    riskLevel = if (isBlocked) "riskli" else "risksiz",
                    isFinalized = true,
                    cachedAt = System.currentTimeMillis()
                )
                database.senderCacheDao().insertSender(entity)
            }
            loadSenders()
        }
    }

    fun deleteSenderRule(sender: String): Job {
        return scope.launch {
            withContext(ioDispatcher) {
                database.senderCacheDao().deleteSender(sender.trim())
            }
            loadSenders()
        }
    }

    fun triggerSync(): Job {
        return scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            withContext(ioDispatcher) {
                keywordRepository.sync()
            }
            loadAll()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                statusMessage = "Senkronizasyon tamamlandı"
            )
        }
    }

    fun resetDemoData(): Job {
        return scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            withContext(ioDispatcher) {
                database.filteredMessageDao().clearAll()
                DemoDataSeeder.seed(database, ioDispatcher)
            }
            loadAll()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                statusMessage = "Demo mesaj seti yüklendi"
            )
        }
    }

    fun simulateIncomingSms(sender: String, body: String): Job {
        return scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = withContext(ioDispatcher) {
                filterEngine.filterSms(
                    sender = sender,
                    body = body,
                    useOnlineSenderLookup = true,
                    saveToDatabase = true
                )
            }
            loadAll()
            val targetTab = when (result.decision) {
                SmsFilterDecision.SAFE -> AppTab.INBOX
                SmsFilterDecision.SUSPICIOUS -> AppTab.QUARANTINE
                SmsFilterDecision.SPAM, SmsFilterDecision.PHISHING -> AppTab.BLOCKED
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentNav = NavigationScreen.MESSAGES,
                currentTab = targetTab,
                statusMessage = "Yeni SMS işlendi: ${result.decision.name}"
            )
        }
    }

    suspend fun loadAll() {
        loadMessages()
        loadSenders()
        loadSyncInfo()
    }

    private suspend fun loadMessages() = withContext(ioDispatcher) {
        val currentTab = _uiState.value.currentTab

        val inboxDecisions = listOf("SAFE", "IZIN VER", "GELEN", "izin ver")
        val quarantineDecisions = listOf("SUSPICIOUS", "KARANTİNA", "KARANTINA", "karantina")
        val blockedDecisions = listOf("SPAM", "PHISHING", "ENGELLE", "ENGEL", "engelle", "riskli")

        val targetDecisions = when (currentTab) {
            AppTab.INBOX -> inboxDecisions
            AppTab.QUARANTINE -> quarantineDecisions
            AppTab.BLOCKED -> blockedDecisions
        }

        val messages = database.filteredMessageDao().getMessagesByDecisions(targetDecisions)
        val inCount = database.filteredMessageDao().getCountByDecisions(inboxDecisions)
        val qCount = database.filteredMessageDao().getCountByDecisions(quarantineDecisions)
        val bCount = database.filteredMessageDao().getCountByDecisions(blockedDecisions)

        _uiState.value = _uiState.value.copy(
            messages = messages,
            inboxCount = inCount,
            quarantineCount = qCount,
            blockedCount = bCount
        )
    }

    private suspend fun loadSenders() = withContext(ioDispatcher) {
        val senders = database.senderCacheDao().getAllSenders()
        _uiState.value = _uiState.value.copy(senders = senders)
    }

    private suspend fun loadSyncInfo() = withContext(ioDispatcher) {
        val version = keywordRepository.getLocalVersion()
        val lastSyncMeta = database.syncMetadataDao().getMetadata(AppDatabase.KEY_LAST_SYNC_TIME)
        val lastSyncTime = lastSyncMeta?.value?.toLongOrNull() ?: System.currentTimeMillis()

        val diffMinutes = ((System.currentTimeMillis() - lastSyncTime) / 60000).coerceAtLeast(0)
        val formatted = if (diffMinutes < 1) "Az önce" else "$diffMinutes dk önce"

        _uiState.value = _uiState.value.copy(
            keywordVersion = if (version == 0L) 2L else version,
            lastSyncFormatted = formatted
        )
    }
}

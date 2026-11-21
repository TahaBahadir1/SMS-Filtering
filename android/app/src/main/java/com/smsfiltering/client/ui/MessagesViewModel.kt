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
import java.io.File
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
    val totalScannedCount: Int = 0,
    val selectedMessage: FilteredMessageEntity? = null,
    val isBodyRevealed: Boolean = false,
    val senders: List<SenderCacheEntity> = emptyList(),
    val keywordVersion: Long = 2L,
    val lastSyncFormatted: String = "10 dk önce",
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val pendingDeepLinkMessageId: Long? = null
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

    // ── IP 4: Arama Özelliği ──────────────────────────────────────────

    fun setSearchQuery(query: String): Job {
        _uiState.value = _uiState.value.copy(searchQuery = query, isSearchActive = query.isNotBlank())
        return scope.launch { loadMessages() }
    }

    fun clearSearch(): Job {
        _uiState.value = _uiState.value.copy(searchQuery = "", isSearchActive = false)
        return scope.launch { loadMessages() }
    }

    // ── IP 4: Deep-link ile mesaj detay ekranına yönlendirme ──────────

    fun openMessageById(messageId: Long): Job {
        return scope.launch {
            val message = withContext(ioDispatcher) {
                database.filteredMessageDao().getMessageById(messageId)
            }
            if (message != null) {
                openMessageDetail(message)
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Mesaj bulunamadı (ID: $messageId)"
                )
            }
        }
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
                    val score = if (isSafe) 0.05 else 0.95
                    val cacheEntity = SenderCacheEntity(
                        sender = msg.sender,
                        riskScore = score,
                        riskLevel = if (isSafe) "risksiz" else "riskli",
                        isFinalized = true,
                        cachedAt = System.currentTimeMillis()
                    )
                    database.senderCacheDao().insertSender(cacheEntity)
                    // Kapalı döngü anonim sinyal (SMS metni gönderilmez, çevrimdışında sessizce yutulur)
                    try {
                        senderRepository.submitSignal(sender = msg.sender, source = "manuel", score = score)
                    } catch (_: Exception) {
                    }
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
                val normalized = sender.trim().uppercase(Locale("tr", "TR"))
                val score = if (isBlocked) 0.95 else 0.05
                val entity = SenderCacheEntity(
                    sender = normalized,
                    riskScore = score,
                    riskLevel = if (isBlocked) "riskli" else "risksiz",
                    isFinalized = true,
                    cachedAt = System.currentTimeMillis()
                )
                database.senderCacheDao().insertSender(entity)
                try {
                    senderRepository.submitSignal(sender = normalized, source = "manuel", score = score)
                } catch (_: Exception) {
                }
            }
            loadSenders()
        }
    }

    fun deleteSenderRule(sender: String): Job {
        return scope.launch {
            withContext(ioDispatcher) {
                senderRepository.deleteSender(sender.trim())
            }
            loadSenders()
            _uiState.value = _uiState.value.copy(statusMessage = "$sender kaydı silindi")
        }
    }

    /**
     * FR-06: Tüm kayıtlı gönderenleri yerel veritabanından siler.
     */
    fun deleteAllSenders(): Job {
        return scope.launch {
            withContext(ioDispatcher) {
                senderRepository.clearAllSenders()
            }
            loadSenders()
            _uiState.value = _uiState.value.copy(statusMessage = "Tüm gönderenler silindi")
        }
    }

    /**
     * FR-09: Kayıtlı gönderenleri CSV dosyasına dışa aktarır.
     */
    fun exportSenders(file: File): Job {
        return scope.launch {
            val success = withContext(ioDispatcher) {
                senderRepository.exportSendersToCsv(file)
            }
            _uiState.value = _uiState.value.copy(
                statusMessage = if (success) "Gönderenler CSV olarak dışa aktarıldı: ${file.name}" else "CSV aktarımı başarısız oldu"
            )
        }
    }

    /**
     * FR-09: Belirtilen CSV dosyasından gönderenleri içe aktarır.
     */
    fun importSenders(file: File): Job {
        return scope.launch {
            val count = withContext(ioDispatcher) {
                senderRepository.importSendersFromCsv(file)
            }
            loadSenders()
            _uiState.value = _uiState.value.copy(
                statusMessage = "$count gönderen CSV'den başarıyla içe aktarıldı"
            )
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
        val state = _uiState.value
        val currentTab = state.currentTab
        val query = state.searchQuery

        val inboxDecisions = listOf("SAFE", "IZIN VER", "GELEN", "izin ver")
        val quarantineDecisions = listOf("SUSPICIOUS", "KARANTİNA", "KARANTINA", "karantina")
        val blockedDecisions = listOf("SPAM", "PHISHING", "ENGELLE", "ENGEL", "engelle", "riskli")

        val targetDecisions = when (currentTab) {
            AppTab.INBOX -> inboxDecisions
            AppTab.QUARANTINE -> quarantineDecisions
            AppTab.BLOCKED -> blockedDecisions
        }

        // IP 4: Arama aktifse filtreleme yap, değilse normal yükle
        val messages = if (query.isNotBlank()) {
            database.filteredMessageDao().searchMessages(query, targetDecisions)
        } else {
            database.filteredMessageDao().getMessagesByDecisions(targetDecisions)
        }

        val inCount = database.filteredMessageDao().getCountByDecisions(inboxDecisions)
        val qCount = database.filteredMessageDao().getCountByDecisions(quarantineDecisions)
        val bCount = database.filteredMessageDao().getCountByDecisions(blockedDecisions)
        val totalCount = database.filteredMessageDao().getTotalCount()

        _uiState.value = _uiState.value.copy(
            messages = messages,
            inboxCount = inCount,
            quarantineCount = qCount,
            blockedCount = bCount,
            totalScannedCount = totalCount
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

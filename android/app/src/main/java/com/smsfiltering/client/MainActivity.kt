package com.smsfiltering.client

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.KeywordSyncResult
import com.smsfiltering.client.notification.NotificationHelper
import com.smsfiltering.client.permission.PermissionHelper
import com.smsfiltering.client.ui.MessagesViewModel
import com.smsfiltering.client.ui.NavigationScreen
import com.smsfiltering.client.ui.UiState
import com.smsfiltering.client.ui.theme.AppTheme
import com.smsfiltering.client.ui.views.BottomNavView
import com.smsfiltering.client.ui.views.MessageDetailView
import com.smsfiltering.client.ui.views.MessagesListView
import com.smsfiltering.client.ui.views.SendersView
import com.smsfiltering.client.ui.views.SimulatorView
import com.smsfiltering.client.worker.KeywordSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * SMS Filtreleme uygulamasının ana launcher aktivitesi.
 *
 * Rapordaki Şekil 3 (Mobil Uygulama Ekran Taslakları) ile tam uyumlu
 * 3 ana ekranı (Karantina Listesi, Mesaj Detayı, Gönderenler) ve
 * canlı karar hattı test simülatörünü barındırır.
 *
 * Gizlilik: SMS metni, gönderen ve kelimeler asla loglanmaz.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var viewModel: MessagesViewModel

    private lateinit var contentFrame: FrameLayout
    private lateinit var bottomNav: BottomNavView

    private lateinit var messagesListView: MessagesListView
    private lateinit var messageDetailView: MessageDetailView
    private lateinit var sendersView: SendersView
    private lateinit var simulatorView: SimulatorView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Bildirim Kanallarını Başlat ve Gerekli İzinleri İste (FR-02, FR-08)
        try {
            NotificationHelper.createNotificationChannels(this)
            val missing = PermissionHelper.getMissingPermissions(this)
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 1001)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bildirim ve izin yapılandırması başlatılamadı", e)
        }

        // 1. 12 saatlik arka plan WorkManager görevini planla
        try {
            KeywordSyncWorker.schedule(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager zamanlaması başlatılamadı")
        }

        // 2. Açılışta kelime ve konu senkronizasyonunu kontrol et
        syncKeywordsOnStartup()

        // 3. ViewModel ve Görünüm Yapılandırması
        viewModel = MessagesViewModel(applicationContext)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.COLOR_BG)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(contentFrame)

        // Alt Navigasyon Çubuğu
        bottomNav = BottomNavView(this, viewModel)
        rootLayout.addView(bottomNav)

        // Sayfa Görünümlerini Başlat
        messagesListView = MessagesListView(this, viewModel)
        messageDetailView = MessageDetailView(this, viewModel)
        sendersView = SendersView(this, viewModel)
        simulatorView = SimulatorView(this, viewModel)

        contentFrame.addView(messagesListView)
        contentFrame.addView(messageDetailView)
        contentFrame.addView(sendersView)
        contentFrame.addView(simulatorView)

        setContentView(rootLayout)

        // 4. UI Durum Akışını (StateFlow) Dinle
        observeState()

        // IP 4: Bildirimden veya deep-link'ten gelen intent'i işle
        handleDeepLinkIntent(intent)
    }

    // IP 4: Uygulama zaten açıkken gelen yeni intent'leri yakala (singleTop)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleDeepLinkIntent(it) }
    }

    /**
     * IP 4: Güvenlik bildiriminden deep-link yönlendirmesi.
     * Bildirime tıklandığında ACTION_OPEN_MESSAGE_DETAIL aksiyonu ve
     * EXTRA_MESSAGE_ID ile ilgili mesaj detay ekranına gidilir.
     */
    private fun handleDeepLinkIntent(intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_OPEN_MESSAGE_DETAIL) {
            val messageId = intent.getLongExtra(NotificationHelper.EXTRA_MESSAGE_ID, -1L)
            if (messageId > 0) {
                viewModel.openMessageById(messageId)
            }
            // Intent'i temizle (aynı intent tekrar işlenmesin)
            intent.action = null
        }
    }

    private fun observeState() {
        scope.launch {
            viewModel.uiState.collect { state ->
                renderState(state)
            }
        }
    }

    private fun renderState(state: UiState) {
        messagesListView.visibility = View.GONE
        messageDetailView.visibility = View.GONE
        sendersView.visibility = View.GONE
        simulatorView.visibility = View.GONE

        when (state.currentNav) {
            NavigationScreen.MESSAGES -> {
                messagesListView.visibility = View.VISIBLE
                messagesListView.render(state)
            }
            NavigationScreen.MESSAGE_DETAIL -> {
                messageDetailView.visibility = View.VISIBLE
                messageDetailView.render(state)
            }
            NavigationScreen.SENDERS -> {
                sendersView.visibility = View.VISIBLE
                sendersView.render(state)
            }
            NavigationScreen.SIMULATOR -> {
                simulatorView.visibility = View.VISIBLE
                simulatorView.render(state)
            }
        }

        bottomNav.render(state)

        state.statusMessage?.let { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (viewModel.uiState.value.currentNav != NavigationScreen.MESSAGES) {
            viewModel.navigateTo(NavigationScreen.MESSAGES)
        } else {
            super.onBackPressed()
        }
    }

    private fun syncKeywordsOnStartup() {
        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val repo = KeywordRepository(database = db)
                when (val result = repo.sync(force = false)) {
                    is KeywordSyncResult.Success -> {
                        Log.i(TAG, "Başlangıçta kelime listesi güncellendi (version: ${result.version}, kategori: ${result.categoriesCount}, konu: ${result.topicsCount})")
                    }
                    is KeywordSyncResult.AlreadyUpToDate -> {
                        Log.i(TAG, "Kategori ve kelimeler güncel (version: ${result.version})")
                    }
                    is KeywordSyncResult.Error -> {
                        Log.w(TAG, "Başlangıçta sunucuya ulaşılamadı (kod: ${result.code}), yerel veriler kullanılıyor.")
                    }
                    is KeywordSyncResult.Exception -> {
                        Log.w(TAG, "Başlangıçta ağ bağlantısı kurulamadı, yerel veriler kullanılıyor.")
                    }
                }
            } catch (_: Exception) {
                Log.w(TAG, "Senkronizasyon sırasında hata oluştu, yerel veriler korunuyor")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SMSFiltering"
    }
}

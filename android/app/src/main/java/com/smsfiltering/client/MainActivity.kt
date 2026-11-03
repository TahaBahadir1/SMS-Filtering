package com.smsfiltering.client

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.KeywordSyncResult
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

        messagesListView = MessagesListView(this, viewModel)
        messageDetailView = MessageDetailView(this, viewModel)
        sendersView = SendersView(this, viewModel)
        simulatorView = SimulatorView(this, viewModel)

        contentFrame.addView(messagesListView)
        contentFrame.addView(messageDetailView)
        contentFrame.addView(sendersView)
        contentFrame.addView(simulatorView)

        rootLayout.addView(contentFrame)

        bottomNav = BottomNavView(this, viewModel)
        rootLayout.addView(bottomNav)

        setContentView(rootLayout)

        // State dinleme ve ekran güncelleme
        observeState()
    }

    private fun observeState() {
        scope.launch {
            viewModel.uiState.collect { state ->
                renderScreen(state)
            }
        }
    }

    private fun renderScreen(state: UiState) {
        messagesListView.visibility = if (state.currentNav == NavigationScreen.MESSAGES) View.VISIBLE else View.GONE
        messageDetailView.visibility = if (state.currentNav == NavigationScreen.MESSAGE_DETAIL) View.VISIBLE else View.GONE
        sendersView.visibility = if (state.currentNav == NavigationScreen.SENDERS) View.VISIBLE else View.GONE
        simulatorView.visibility = if (state.currentNav == NavigationScreen.SIMULATOR) View.VISIBLE else View.GONE

        // Detay ekranında tam odaklanma için alt çubuk gizlenir
        bottomNav.visibility = if (state.currentNav == NavigationScreen.MESSAGE_DETAIL) View.GONE else View.VISIBLE

        when (state.currentNav) {
            NavigationScreen.MESSAGES -> messagesListView.render(state)
            NavigationScreen.MESSAGE_DETAIL -> messageDetailView.render(state)
            NavigationScreen.SENDERS -> sendersView.render(state)
            NavigationScreen.SIMULATOR -> simulatorView.render(state)
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
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getInstance(applicationContext)
            val repository = KeywordRepository(database = database)

            try {
                when (val result = repository.sync()) {
                    is KeywordSyncResult.Success -> {
                        Log.i(
                            TAG,
                            "Kategori sayısı (${result.categoriesCount}), Kelime sayısı (${result.keywordsCount}), Konu sayısı (${result.topicsCount}) ve version: ${result.version}"
                        )
                    }
                    is KeywordSyncResult.AlreadyUpToDate -> {
                        Log.i(TAG, "Kategori ve kelimeler güncel (version: ${result.version})")
                    }
                    is KeywordSyncResult.Error -> {
                        Log.w(TAG, "Senkronizasyon sunucu hatası: HTTP ${result.code}")
                    }
                    is KeywordSyncResult.Exception -> {
                        Log.w(TAG, "Senkronizasyon ağ hatası, yerel veriler korunuyor")
                    }
                }
            } catch (e: Exception) {
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

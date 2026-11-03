package com.smsfiltering.client.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.ui.AppTab
import com.smsfiltering.client.ui.MessagesViewModel
import com.smsfiltering.client.ui.UiState
import com.smsfiltering.client.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesListView(
    context: Context,
    private val viewModel: MessagesViewModel
) : LinearLayout(context) {

    private val tabsContainer: LinearLayout
    private val messagesContainer: LinearLayout
    private val emptyView: LinearLayout
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
    private val dateFormat = SimpleDateFormat("dd.MM", Locale("tr", "TR"))

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(AppTheme.COLOR_BG)

        // 1. Üst Başlık ve Hızlı Aksiyonlar Barı
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = "Mesajlar"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(title)

        val demoBtn = Button(context).apply {
            text = "Demo Yükle"
            textSize = 12f
            isAllCaps = false
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
            background = AppTheme.createButtonDrawable(AppTheme.COLOR_SURFACE_LIGHT, dp(14).toFloat())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { viewModel.resetDemoData() }
        }
        topBar.addView(demoBtn)
        addView(topBar)

        // 2. Sekmeler (Tabs): Gelen | Karantina 3 | Engellenen
        tabsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(16), 0, dp(16), dp(12))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(tabsContainer)

        // 3. Mesaj Listesi (Kaydırılabilir)
        val scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }

        val contentWrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        messagesContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        contentWrapper.addView(messagesContainer)

        // Boş Liste Durumu
        emptyView = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(48))
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            val emptyText = TextView(context).apply {
                text = "Bu sekmede mesaj bulunmuyor."
                textSize = 15f
                setTextColor(AppTheme.COLOR_TEXT_MUTED)
                gravity = Gravity.CENTER
            }
            addView(emptyText)

            val loadDemoBtn = Button(context).apply {
                text = "18 Senaryoluk Demo Mesajları Yükle"
                textSize = 13f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = AppTheme.createButtonDrawable(AppTheme.COLOR_ACCENT_BLUE, dp(16).toFloat())
                setPadding(dp(16), dp(10), dp(16), dp(10))
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(16)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                layoutParams = lp
                setOnClickListener { viewModel.resetDemoData() }
            }
            addView(loadDemoBtn)
        }
        contentWrapper.addView(emptyView)

        scrollView.addView(contentWrapper)
        addView(scrollView)
    }

    fun render(state: UiState) {
        renderTabs(state)
        renderMessages(state)
    }

    private fun renderTabs(state: UiState) {
        tabsContainer.removeAllViews()

        val tabs = listOf(
            Triple(AppTab.INBOX, "Gelen", state.inboxCount),
            Triple(AppTab.QUARANTINE, "Karantina", state.quarantineCount),
            Triple(AppTab.BLOCKED, "Engellenen", state.blockedCount)
        )

        for ((tab, label, count) in tabs) {
            val isSelected = state.currentTab == tab
            val tabView = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(8), dp(16), dp(8))
                val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                layoutParams = lp

                val bgColor = if (isSelected) {
                    if (tab == AppTab.QUARANTINE) AppTheme.BADGE_RISK_MID_BG
                    else if (tab == AppTab.BLOCKED) AppTheme.BADGE_RISK_HIGH_BG
                    else AppTheme.COLOR_SURFACE_LIGHT
                } else {
                    AppTheme.COLOR_SURFACE
                }

                val strokeColor = if (isSelected) {
                    if (tab == AppTab.QUARANTINE) AppTheme.COLOR_ACCENT_AMBER
                    else if (tab == AppTab.BLOCKED) AppTheme.COLOR_ACCENT_RED
                    else AppTheme.COLOR_BORDER
                } else {
                    AppTheme.COLOR_BORDER
                }

                background = AppTheme.createCardDrawable(bgColor, dp(18).toFloat(), strokeColor, 2)
                setOnClickListener { viewModel.selectTab(tab) }
            }

            val textLabel = TextView(context).apply {
                text = if (count > 0) "$label $count" else label
                textSize = 13f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) AppTheme.COLOR_TEXT_PRIMARY else AppTheme.COLOR_TEXT_SECONDARY)
            }
            tabView.addView(textLabel)
            tabsContainer.addView(tabView)
        }
    }

    private fun renderMessages(state: UiState) {
        messagesContainer.removeAllViews()

        if (state.messages.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE

        for (msg in state.messages) {
            val card = createMessageCard(msg, state.currentTab)
            messagesContainer.addView(card)
        }
    }

    private fun createMessageCard(msg: FilteredMessageEntity, currentTab: AppTab): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            layoutParams = lp
            background = AppTheme.createCardDrawable(
                bgColor = AppTheme.COLOR_SURFACE_CARD,
                cornerRadiusPx = dp(16).toFloat(),
                strokeColor = AppTheme.COLOR_BORDER,
                strokeWidthPx = 1
            )
            isClickable = true
            setOnClickListener { viewModel.openMessageDetail(msg) }
        }

        // 1. Satır: Gönderen ve Zaman
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val senderText = TextView(context).apply {
            text = msg.sender
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(senderText)

        val timeText = TextView(context).apply {
            text = formatTimestamp(msg.timestamp)
            textSize = 12f
            setTextColor(AppTheme.COLOR_TEXT_MUTED)
        }
        topRow.addView(timeText)
        card.addView(topRow)

        // 2. Satır: Rozetler (Risk Durumu + Konu)
        val badgesRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }

        // Risk Rozeti
        val (riskLabel, riskBg, riskTextColor) = resolveRiskBadge(msg)
        val riskBadge = TextView(context).apply {
            text = riskLabel
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(riskTextColor)
            background = AppTheme.createBadgeDrawable(riskBg, dp(10).toFloat())
            setPadding(dp(8), dp(2), dp(8), dp(2))
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(6)
            }
            layoutParams = lp
        }
        badgesRow.addView(riskBadge)

        // Konu Rozeti (Banka, Kumar, Kargo vb.)
        val topic = resolveTopicName(msg)
        if (topic.isNotEmpty()) {
            val topicBadge = TextView(context).apply {
                text = topic
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(AppTheme.BADGE_TOPIC_TEXT)
                background = AppTheme.createBadgeDrawable(AppTheme.BADGE_TOPIC_BG, dp(10).toFloat())
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
            badgesRow.addView(topicBadge)
        }
        card.addView(badgesRow)

        // 3. Satır: Sebep / Açıklama (Bağlantı içeriyor, 2 risk işareti vb.)
        val reasonStr = resolveReasonSummary(msg)
        if (reasonStr.isNotEmpty()) {
            val reasonText = TextView(context).apply {
                text = reasonStr
                textSize = 13f
                setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
                val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
                layoutParams = lp
            }
            card.addView(reasonText)
        }

        // 4. Satır: Gizlilik Koruma Bildirimi (🔒 Mesaj gizli)
        val privacyRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val isHidden = currentTab != AppTab.INBOX
        val privacyText = TextView(context).apply {
            text = if (isHidden) "🔒 Mesaj gizli" else msg.body
            textSize = 12f
            maxLines = if (isHidden) 1 else 2
            setTextColor(if (isHidden) AppTheme.COLOR_TEXT_MUTED else AppTheme.COLOR_TEXT_SECONDARY)
        }
        privacyRow.addView(privacyText)
        card.addView(privacyRow)

        return card
    }

    private fun resolveRiskBadge(msg: FilteredMessageEntity): Triple<String, Int, Int> {
        val decisionUpper = msg.decision.uppercase(Locale.ROOT)
        return when {
            decisionUpper == "SAFE" || decisionUpper.contains("IZIN") || decisionUpper.contains("GELEN") -> {
                Triple("Risksiz", AppTheme.BADGE_RISK_LOW_BG, AppTheme.BADGE_RISK_LOW_TEXT)
            }
            decisionUpper == "SUSPICIOUS" || decisionUpper.contains("KARANTINA") -> {
                Triple("Orta risk", AppTheme.BADGE_RISK_MID_BG, AppTheme.BADGE_RISK_MID_TEXT)
            }
            else -> {
                Triple("Riskli", AppTheme.BADGE_RISK_HIGH_BG, AppTheme.BADGE_RISK_HIGH_TEXT)
            }
        }
    }

    private fun resolveTopicName(msg: FilteredMessageEntity): String {
        if (msg.matchedTopics.isNotEmpty()) {
            val first = msg.matchedTopics.split(",").firstOrNull()?.trim() ?: ""
            if (first.isNotEmpty()) return first.capitalizeFirst()
        }
        return ""
    }

    private fun resolveReasonSummary(msg: FilteredMessageEntity): String {
        if (msg.reasons.isNotEmpty()) {
            val first = msg.reasons.split(";").firstOrNull()?.trim() ?: ""
            if (first.isNotEmpty()) return first
        }
        if (msg.matchedCategories.contains("LINK", ignoreCase = true)) {
            return "Bağlantı içeriyor"
        }
        return ""
    }

    private fun formatTimestamp(ts: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - ts
        return when {
            diff < 86400 * 1000 -> timeFormat.format(Date(ts))
            diff < 86400 * 1000 * 2 -> "Dün"
            else -> dateFormat.format(Date(ts))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun String.capitalizeFirst(): String =
        if (isNotEmpty()) substring(0, 1).uppercase() + substring(1).lowercase() else this
}


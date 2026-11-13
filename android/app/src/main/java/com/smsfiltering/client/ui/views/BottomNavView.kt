package com.smsfiltering.client.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.smsfiltering.client.ui.MessagesViewModel
import com.smsfiltering.client.ui.NavigationScreen
import com.smsfiltering.client.ui.UiState
import com.smsfiltering.client.ui.theme.AppTheme

class BottomNavView(
    context: Context,
    private val viewModel: MessagesViewModel
) : LinearLayout(context) {

    private val messagesTabItem: LinearLayout
    private val messagesLabel: TextView
    private val messagesBadge: TextView

    private val sendersTabItem: LinearLayout
    private val sendersLabel: TextView

    private val simulatorTabItem: LinearLayout
    private val simulatorLabel: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setBackgroundColor(AppTheme.COLOR_SURFACE)

        // 1. Mesajlar Sekmesi
        messagesTabItem = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { viewModel.navigateTo(NavigationScreen.MESSAGES) }
        }

        messagesLabel = TextView(context).apply {
            text = "💬 Mesajlar"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_ACCENT_BLUE)
        }
        messagesTabItem.addView(messagesLabel)

        messagesBadge = TextView(context).apply {
            text = "3"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.BADGE_RISK_MID_TEXT)
            background = AppTheme.createBadgeDrawable(AppTheme.BADGE_RISK_MID_BG, dp(10).toFloat())
            setPadding(dp(6), dp(2), dp(6), dp(2))
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(6)
            }
            layoutParams = lp
        }
        messagesTabItem.addView(messagesBadge)
        addView(messagesTabItem)

        // 2. Gönderenler Sekmesi
        sendersTabItem = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { viewModel.navigateTo(NavigationScreen.SENDERS) }
        }

        sendersLabel = TextView(context).apply {
            text = "🛡️ Gönderenler"
            textSize = 14f
            typeface = Typeface.DEFAULT
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
        }
        sendersTabItem.addView(sendersLabel)
        addView(sendersTabItem)

        // 3. Simülatör Sekmesi
        simulatorTabItem = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { viewModel.navigateTo(NavigationScreen.SIMULATOR) }
        }

        simulatorLabel = TextView(context).apply {
            text = "⚡ Test Et"
            textSize = 14f
            typeface = Typeface.DEFAULT
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
        }
        simulatorTabItem.addView(simulatorLabel)
        addView(simulatorTabItem)
    }

    fun render(state: UiState) {
        val isMessages = state.currentNav == NavigationScreen.MESSAGES || state.currentNav == NavigationScreen.MESSAGE_DETAIL
        val isSenders = state.currentNav == NavigationScreen.SENDERS
        val isSimulator = state.currentNav == NavigationScreen.SIMULATOR

        updateTabStyle(messagesTabItem, messagesLabel, isMessages)
        updateTabStyle(sendersTabItem, sendersLabel, isSenders)
        updateTabStyle(simulatorTabItem, simulatorLabel, isSimulator)

        if (state.quarantineCount > 0) {
            messagesBadge.visibility = VISIBLE
            messagesBadge.text = "${state.quarantineCount}"
        } else {
            messagesBadge.visibility = GONE
        }
    }

    private fun updateTabStyle(item: LinearLayout, label: TextView, isSelected: Boolean) {
        if (isSelected) {
            item.background = AppTheme.createBadgeDrawable(AppTheme.COLOR_SURFACE_LIGHT, dp(16).toFloat())
            label.setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            label.typeface = Typeface.DEFAULT_BOLD
        } else {
            item.background = null
            label.setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
            label.typeface = Typeface.DEFAULT
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}


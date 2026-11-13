package com.smsfiltering.client.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.ui.MessagesViewModel
import com.smsfiltering.client.ui.NavigationScreen
import com.smsfiltering.client.ui.UiState
import com.smsfiltering.client.ui.theme.AppTheme
import java.util.Locale

class MessageDetailView(
    context: Context,
    private val viewModel: MessagesViewModel
) : LinearLayout(context) {

    private val topSenderText: TextView
    private val badgesContainer: LinearLayout
    private val reasonCard: LinearLayout
    private val reasonBulletsContainer: LinearLayout
    private val reasonTitle: TextView

    private val bodyContainer: LinearLayout
    private val bodyStatusText: TextView
    private val bodyText: TextView
    private val toggleBodyBtn: Button

    private val allowBtn: Button
    private val blockBtn: Button
    private val rememberSenderCheckbox: CheckBox

    private var currentMessage: FilteredMessageEntity? = null

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(AppTheme.COLOR_BG)

        // 1. Üst Çubuk (Geri Butonu ve Gönderen Başlığı)
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val backBtn = TextView(context).apply {
            text = "←"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            setPadding(dp(4), dp(4), dp(16), dp(4))
            setOnClickListener { viewModel.navigateTo(NavigationScreen.MESSAGES) }
        }
        topBar.addView(backBtn)

        topSenderText = TextView(context).apply {
            text = "Gönderen"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(topSenderText)
        addView(topBar)

        // Kaydırılabilir İçerik
        val scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }

        val contentWrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(24))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // 2. Rozetler (Risk ve Konu)
        badgesContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        contentWrapper.addView(badgesContainer)

        // 3. Neden karantinada / Karar Nedeni Kartı
        reasonCard = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = AppTheme.createCardDrawable(
                bgColor = AppTheme.COLOR_SURFACE,
                cornerRadiusPx = dp(16).toFloat(),
                strokeColor = AppTheme.COLOR_BORDER,
                strokeWidthPx = 1
            )
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }

        reasonTitle = TextView(context).apply {
            text = "Neden karantinada"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            layoutParams = lp
        }
        reasonCard.addView(reasonTitle)

        reasonBulletsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        reasonCard.addView(reasonBulletsContainer)
        contentWrapper.addView(reasonCard)

        // 4. Mesaj Metni Alanı (Gizlilik Korumalı)
        bodyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = AppTheme.createCardDrawable(
                bgColor = AppTheme.COLOR_SURFACE_CARD,
                cornerRadiusPx = dp(16).toFloat(),
                strokeColor = AppTheme.COLOR_BORDER,
                strokeWidthPx = 1
            )
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
            layoutParams = lp
        }

        bodyStatusText = TextView(context).apply {
            text = "🔒 Mesajın metni gizli."
            textSize = 14f
            setTextColor(AppTheme.COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            layoutParams = lp
        }
        bodyContainer.addView(bodyStatusText)

        bodyText = TextView(context).apply {
            textSize = 15f
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            setLineSpacing(dp(4).toFloat(), 1f)
            visibility = View.GONE
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }
        bodyContainer.addView(bodyText)

        toggleBodyBtn = Button(context).apply {
            text = "Mesajı göster"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = AppTheme.createButtonDrawable(AppTheme.COLOR_SURFACE_LIGHT, dp(18).toFloat())
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener { viewModel.toggleBodyReveal() }
        }
        bodyContainer.addView(toggleBodyBtn)
        contentWrapper.addView(bodyContainer)

        // 5. Karar Aksiyon Butonları (İzin ver | Engelle)
        val actionsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
            layoutParams = lp
        }

        allowBtn = Button(context).apply {
            text = "İzin ver"
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = AppTheme.createButtonDrawable(AppTheme.BADGE_RISK_LOW_BG, dp(22).toFloat(), AppTheme.COLOR_ACCENT_GREEN, 2)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
            layoutParams = lp
            setOnClickListener {
                currentMessage?.let { msg ->
                    viewModel.moveMessage(msg.id, "SAFE", rememberSenderCheckbox.isChecked)
                }
            }
        }
        actionsRow.addView(allowBtn)

        blockBtn = Button(context).apply {
            text = "Engelle"
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = AppTheme.createButtonDrawable(AppTheme.BADGE_RISK_HIGH_BG, dp(22).toFloat(), AppTheme.COLOR_ACCENT_RED, 2)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            }
            layoutParams = lp
            setOnClickListener {
                currentMessage?.let { msg ->
                    viewModel.moveMessage(msg.id, "SPAM", rememberSenderCheckbox.isChecked)
                }
            }
        }
        actionsRow.addView(blockBtn)
        contentWrapper.addView(actionsRow)

        // 6. Onay Kutusu: [ ] Bu göndericiyi hep engelle
        rememberSenderCheckbox = CheckBox(context).apply {
            text = "Bu göndericiyi hep engelle"
            textSize = 14f
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
            setPadding(dp(6), 0, 0, 0)
        }
        contentWrapper.addView(rememberSenderCheckbox)

        scrollView.addView(contentWrapper)
        addView(scrollView)
    }

    fun render(state: UiState) {
        val msg = state.selectedMessage ?: return
        currentMessage = msg

        topSenderText.text = msg.sender

        // Rozetleri Çiz
        badgesContainer.removeAllViews()
        val (riskLabel, riskBg, riskTextColor) = resolveRiskBadge(msg)
        val riskBadge = TextView(context).apply {
            text = riskLabel
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(riskTextColor)
            background = AppTheme.createBadgeDrawable(riskBg, dp(12).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
            layoutParams = lp
        }
        badgesContainer.addView(riskBadge)

        val topic = resolveTopicName(msg)
        if (topic.isNotEmpty()) {
            val topicBadge = TextView(context).apply {
                text = topic
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(AppTheme.BADGE_TOPIC_TEXT)
                background = AppTheme.createBadgeDrawable(AppTheme.BADGE_TOPIC_BG, dp(12).toFloat())
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            badgesContainer.addView(topicBadge)
        }

        // Neden Karantinada Başlığı ve Maddeleri
        val decisionUpper = msg.decision.uppercase(Locale.ROOT)
        reasonTitle.text = when {
            decisionUpper.contains("KARANTINA") || decisionUpper == "SUSPICIOUS" -> "Neden karantinada"
            decisionUpper == "SAFE" || decisionUpper.contains("IZIN") -> "Güvenilirlik Gerekçesi"
            else -> "Neden engellendi"
        }

        reasonBulletsContainer.removeAllViews()
        val bullets = mutableListOf<String>()

        if (msg.reasons.isNotEmpty()) {
            bullets.addAll(msg.reasons.split(";").map { it.trim() }.filter { it.isNotEmpty() })
        }
        if (msg.matchedCategories.contains("LINK", ignoreCase = true) && bullets.none { it.contains("bağlantı", ignoreCase = true) }) {
            bullets.add("Mesajda bağlantı var")
            bullets.add("Risk işareti: link")
        }
        if (msg.riskScore > 0) {
            bullets.add("Gönderen skoru: ${msg.riskScore}")
        }
        if (bullets.isEmpty()) {
            bullets.add("Filtreleme motoru kural değerlendirmesi")
        }

        for (bullet in bullets) {
            val bulletView = TextView(context).apply {
                text = "• $bullet"
                textSize = 14f
                setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
                val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
                layoutParams = lp
            }
            reasonBulletsContainer.addView(bulletView)
        }

        // Mesaj Göster/Gizle Durumu
        if (state.isBodyRevealed) {
            bodyStatusText.visibility = View.GONE
            bodyText.visibility = View.VISIBLE
            bodyText.text = msg.body
            toggleBodyBtn.text = "Mesajı gizle"
        } else {
            bodyStatusText.visibility = View.VISIBLE
            bodyText.visibility = View.GONE
            toggleBodyBtn.text = "Mesajı göster"
        }

        // Checkbox metni
        rememberSenderCheckbox.text = if (decisionUpper == "SAFE" || decisionUpper.contains("IZIN")) {
            "Bu göndericiye hep izin ver"
        } else {
            "Bu göndericiyi hep engelle"
        }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun String.capitalizeFirst(): String =
        if (isNotEmpty()) substring(0, 1).uppercase() + substring(1).lowercase() else this
}


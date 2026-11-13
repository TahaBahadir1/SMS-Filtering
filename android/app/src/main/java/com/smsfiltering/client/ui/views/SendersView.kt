package com.smsfiltering.client.ui.views

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import com.smsfiltering.client.ui.MessagesViewModel
import com.smsfiltering.client.ui.UiState
import com.smsfiltering.client.ui.theme.AppTheme
import java.util.Locale

class SendersView(
    context: Context,
    private val viewModel: MessagesViewModel
) : LinearLayout(context) {

    private val sendersContainer: LinearLayout
    private val emptyView: LinearLayout
    private val lastSyncText: TextView
    private val versionText: TextView

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(AppTheme.COLOR_BG)

        // 1. Üst Bar: Başlık ve [+ Ekle] Butonu
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = "Gönderenler"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(title)

        val addBtn = Button(context).apply {
            text = "＋ Ekle"
            textSize = 13f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = AppTheme.createButtonDrawable(AppTheme.COLOR_ACCENT_BLUE, dp(16).toFloat())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { showAddSenderDialog() }
        }
        topBar.addView(addBtn)
        addView(topBar)

        // 2. Kaydırılabilir Gönderenler Listesi
        val scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }

        val contentWrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), 0, dp(16), dp(20))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        sendersContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        contentWrapper.addView(sendersContainer)

        // Boş Liste Durumu
        emptyView = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(48))
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            val emptyText = TextView(context).apply {
                text = "Henüz kayıtlı gönderen bulunmuyor."
                textSize = 15f
                setTextColor(AppTheme.COLOR_TEXT_MUTED)
                gravity = Gravity.CENTER
            }
            addView(emptyText)
        }
        contentWrapper.addView(emptyView)

        // 3. Alt Bilgi / Durum Çubuğu (Şekil 3'teki Alt Bölüm)
        val footerCard = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
                bottomMargin = dp(16)
            }
            layoutParams = lp
            background = AppTheme.createCardDrawable(
                bgColor = AppTheme.COLOR_SURFACE,
                cornerRadiusPx = dp(16).toFloat(),
                strokeColor = AppTheme.COLOR_BORDER,
                strokeWidthPx = 1
            )
        }

        val footerTopRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val syncInfoWrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        lastSyncText = TextView(context).apply {
            text = "🔄 Son eşitleme: 10 dk önce"
            textSize = 13f
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
        }
        syncInfoWrapper.addView(lastSyncText)

        versionText = TextView(context).apply {
            text = "Kelime listesi sürüm 2"
            textSize = 12f
            setTextColor(AppTheme.COLOR_TEXT_MUTED)
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        syncInfoWrapper.addView(versionText)
        footerTopRow.addView(syncInfoWrapper)

        val syncBtn = Button(context).apply {
            text = "Eşitle"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = AppTheme.createButtonDrawable(AppTheme.COLOR_SURFACE_LIGHT, dp(14).toFloat(), AppTheme.COLOR_BORDER, 1)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { viewModel.triggerSync() }
        }
        footerTopRow.addView(syncBtn)
        footerCard.addView(footerTopRow)

        contentWrapper.addView(footerCard)
        scrollView.addView(contentWrapper)
        addView(scrollView)
    }

    fun render(state: UiState) {
        lastSyncText.text = "🔄 Son eşitleme: ${state.lastSyncFormatted}"
        versionText.text = "Kelime listesi sürüm ${state.keywordVersion}"

        sendersContainer.removeAllViews()

        if (state.senders.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE

        for (sender in state.senders) {
            val card = createSenderCard(sender)
            sendersContainer.addView(card)
        }
    }

    private fun createSenderCard(sender: SenderCacheEntity): View {
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
        }

        // Üst Satır: Gönderen Adı ve Durum Rozeti
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val nameText = TextView(context).apply {
            text = sender.sender
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(nameText)

        val isBlocked = sender.isDangerous || sender.riskLevel?.contains("riskli", ignoreCase = true) == true
        val isAllowed = sender.riskLevel?.equals("risksiz", ignoreCase = true) == true

        val statusBadge = TextView(context).apply {
            text = if (isBlocked) "Engelli" else if (isAllowed) "Hep izin" else "İnceleniyor"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            val bg = if (isBlocked) AppTheme.BADGE_RISK_HIGH_BG else if (isAllowed) AppTheme.BADGE_RISK_LOW_BG else AppTheme.BADGE_RISK_MID_BG
            val txtColor = if (isBlocked) AppTheme.BADGE_RISK_HIGH_TEXT else if (isAllowed) AppTheme.BADGE_RISK_LOW_TEXT else AppTheme.BADGE_RISK_MID_TEXT
            setTextColor(txtColor)
            background = AppTheme.createBadgeDrawable(bg, dp(10).toFloat())
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        topRow.addView(statusBadge)
        card.addView(topRow)

        // Alt Satır: Kaynak Bilgisi (Sizin kararınız veya Sunucudan)
        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val sourceSubtitle = TextView(context).apply {
            val sourceDesc = if (sender.isFinalized) "Sizin kararınız" else "Sunucudan (${formatDaysAgo(sender.cachedAt)})"
            text = sourceDesc
            textSize = 13f
            setTextColor(AppTheme.COLOR_TEXT_MUTED)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        bottomRow.addView(sourceSubtitle)

        // Hızlı Değiştir / Sil Butonları
        val toggleBtn = TextView(context).apply {
            text = if (isBlocked) "İzin Ver" else "Engelle"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isBlocked) AppTheme.COLOR_ACCENT_GREEN else AppTheme.COLOR_ACCENT_RED)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                viewModel.addOrUpdateSenderRule(sender.sender, !isBlocked)
            }
        }
        bottomRow.addView(toggleBtn)

        val deleteBtn = TextView(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(AppTheme.COLOR_TEXT_MUTED)
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener {
                viewModel.deleteSenderRule(sender.sender)
            }
        }
        bottomRow.addView(deleteBtn)

        card.addView(bottomRow)
        return card
    }

    private fun showAddSenderDialog() {
        val dialogView = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
            setBackgroundColor(AppTheme.COLOR_SURFACE)
        }

        val dialogTitle = TextView(context).apply {
            text = "Gönderen Kuralı Ekle"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }
        dialogView.addView(dialogTitle)

        val input = EditText(context).apply {
            hint = "Gönderen başlığı veya tel no (örn: BANKA-X)"
            setHintTextColor(AppTheme.COLOR_TEXT_MUTED)
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            background = AppTheme.createCardDrawable(AppTheme.COLOR_SURFACE_CARD, dp(12).toFloat(), AppTheme.COLOR_BORDER, 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }
        dialogView.addView(input)

        val radioGroup = RadioGroup(context).apply {
            orientation = VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
            layoutParams = lp
        }

        val allowRadio = RadioButton(context).apply {
            text = "Hep İzin Ver (Beyaz Liste)"
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            isChecked = true
        }
        radioGroup.addView(allowRadio)

        val blockRadio = RadioButton(context).apply {
            text = "Engelle (Kara Liste)"
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
        }
        radioGroup.addView(blockRadio)
        dialogView.addView(radioGroup)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        val buttonsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val cancelBtn = Button(context).apply {
            text = "İptal"
            isAllCaps = false
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonsRow.addView(cancelBtn)

        val saveBtn = Button(context).apply {
            text = "Kaydet"
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = AppTheme.createButtonDrawable(AppTheme.COLOR_ACCENT_BLUE, dp(14).toFloat())
            setOnClickListener {
                val senderName = input.text.toString().trim()
                if (senderName.isNotEmpty()) {
                    viewModel.addOrUpdateSenderRule(senderName, blockRadio.isChecked)
                    dialog.dismiss()
                }
            }
        }
        buttonsRow.addView(saveBtn)
        dialogView.addView(buttonsRow)

        dialog.show()
    }

    private fun formatDaysAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val days = (diff / (86400 * 1000)).toInt()
        return when {
            days <= 0 -> "bugün"
            days == 1 -> "dün"
            else -> "$days gün önce"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}


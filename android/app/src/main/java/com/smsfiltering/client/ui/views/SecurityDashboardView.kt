package com.smsfiltering.client.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.smsfiltering.client.ui.UiState
import com.smsfiltering.client.ui.theme.AppTheme

/**
 * IP 4 — Güvenlik Özeti Panosu
 *
 * Taranan, engellenen ve karantinaya alınan mesaj istatistiklerini
 * kompakt bir özet kartı olarak gösteren bileşen.
 * MessagesListView'ın üst kısmına gömülür.
 */
class SecurityDashboardView(
    context: Context
) : LinearLayout(context) {

    private val scannedValue: TextView
    private val blockedValue: TextView
    private val quarantineValue: TextView
    private val safeValue: TextView

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(4))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        // Ana Kart
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = AppTheme.createCardDrawable(
                bgColor = AppTheme.COLOR_SURFACE,
                cornerRadiusPx = dp(18).toFloat(),
                strokeColor = AppTheme.COLOR_BORDER,
                strokeWidthPx = 1
            )
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Başlık Satırı
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        val shieldIcon = TextView(context).apply {
            text = "🛡️"
            textSize = 18f
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
            layoutParams = lp
        }
        headerRow.addView(shieldIcon)

        val headerTitle = TextView(context).apply {
            text = "Güvenlik Özeti"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(headerTitle)

        card.addView(headerRow)

        // İstatistik Satırı (4 kutu yan yana)
        val statsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Taranan (Toplam)
        val (scannedBox, scannedVal) = createStatBox(context, "Taranan", AppTheme.COLOR_ACCENT_BLUE)
        scannedValue = scannedVal
        statsRow.addView(scannedBox)

        // Güvenli
        val (safeBox, safeVal) = createStatBox(context, "Güvenli", AppTheme.COLOR_ACCENT_GREEN)
        safeValue = safeVal
        statsRow.addView(safeBox)

        // Karantina
        val (quarantineBox, quarantineVal) = createStatBox(context, "Karantina", AppTheme.COLOR_ACCENT_AMBER)
        quarantineValue = quarantineVal
        statsRow.addView(quarantineBox)

        // Engellenen
        val (blockedBox, blockedVal) = createStatBox(context, "Engellenen", AppTheme.COLOR_ACCENT_RED)
        blockedValue = blockedVal
        statsRow.addView(blockedBox)

        card.addView(statsRow)
        addView(card)
    }

    fun render(state: UiState) {
        val total = state.inboxCount + state.quarantineCount + state.blockedCount
        scannedValue.text = "$total"
        safeValue.text = "${state.inboxCount}"
        quarantineValue.text = "${state.quarantineCount}"
        blockedValue.text = "${state.blockedCount}"
    }

    private fun createStatBox(
        context: Context,
        label: String,
        accentColor: Int
    ): Pair<LinearLayout, TextView> {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            layoutParams = lp
            background = AppTheme.createCardDrawable(
                bgColor = AppTheme.COLOR_SURFACE_CARD,
                cornerRadiusPx = dp(14).toFloat(),
                strokeColor = AppTheme.COLOR_BORDER,
                strokeWidthPx = 1
            )
        }

        // Renkli Üst Şerit (İnce çizgi)
        val stripe = android.view.View(context).apply {
            setBackgroundColor(accentColor)
            val lp = LayoutParams(dp(28), dp(3)).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }
        box.addView(stripe)

        val valueText = TextView(context).apply {
            text = "0"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
        }
        box.addView(valueText)

        val labelText = TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(AppTheme.COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        box.addView(labelText)

        return Pair(box, valueText)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}


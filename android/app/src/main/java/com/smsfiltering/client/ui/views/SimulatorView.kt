package com.smsfiltering.client.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smsfiltering.client.ui.MessagesViewModel
import com.smsfiltering.client.ui.UiState
import com.smsfiltering.client.ui.theme.AppTheme

class SimulatorView(
    context: Context,
    private val viewModel: MessagesViewModel
) : LinearLayout(context) {

    private val senderInput: EditText
    private val bodyInput: EditText

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(AppTheme.COLOR_BG)

        // 1. Başlık
        val topBar = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = "SMS Simülatörü"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
        }
        topBar.addView(title)

        val subTitle = TextView(context).apply {
            text = "Gizlilik Odaklı Kademeli Karar Hattını canlı test edin."
            textSize = 13f
            setTextColor(AppTheme.COLOR_TEXT_MUTED)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        topBar.addView(subTitle)
        addView(topBar)

        val scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }

        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), 0, dp(20), dp(24))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Hızlı Şablonlar
        val templateLabel = TextView(context).apply {
            text = "Hızlı Test Şablonları:"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
            layoutParams = lp
        }
        content.addView(templateLabel)

        val templatesScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }

        val templatesRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }

        val templates = listOf(
            Triple(
                "Sahte Banka Linki (Oltalama)",
                "+90 555 000 00 11",
                "Kartınız güvenlik sebebiyle askıya alınmıştır. Kilidi açmak ve kartınızı güncellemek için tıklayınız: http://banka-onay.top"
            ),
            Triple(
                "Yasadışı Kumar / Bahis",
                "BET-VIP",
                "GÜNCEL BAHİS FIRSATI! 500 TL çevrimsiz kayıp bonusu ve casino slot oyunları hemen tıkla: http://betvip99.xyz"
            ),
            Triple(
                "Kargo Gri Alan (Karantina)",
                "PTT-BILGI",
                "Gönderiniz adres yetersizliği nedeniyle şubede bekletilmektedir. Teslimat için adresinizi güncelleyin: http://ptt-kargo.xyz"
            ),
            Triple(
                "Güvenli Operatör Bildirimi",
                "TURKCELL",
                "0532 123 45 67 nolu hattınıza ait fatura tutarınız 215.50 TL'dir. Son ödeme tarihi: 25.11.2026."
            )
        )

        for ((name, s, b) in templates) {
            val chip = Button(context).apply {
                text = name
                textSize = 12f
                isAllCaps = false
                setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
                background = AppTheme.createCardDrawable(AppTheme.COLOR_SURFACE, dp(14).toFloat(), AppTheme.COLOR_BORDER, 1)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(8)
                }
                layoutParams = lp
                setOnClickListener {
                    senderInput.setText(s)
                    bodyInput.setText(b)
                }
            }
            templatesRow.addView(chip)
        }
        templatesScroll.addView(templatesRow)
        content.addView(templatesScroll)

        // Gönderen Girdisi
        val senderLabel = TextView(context).apply {
            text = "Gönderen Başlığı / Numara"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }
        content.addView(senderLabel)

        senderInput = EditText(context).apply {
            hint = "örn: KREDI-DESTEK veya +90 555..."
            setHintTextColor(AppTheme.COLOR_TEXT_MUTED)
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            background = AppTheme.createCardDrawable(AppTheme.COLOR_SURFACE_CARD, dp(12).toFloat(), AppTheme.COLOR_BORDER, 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }
        content.addView(senderInput)

        // Mesaj Metni Girdisi
        val bodyLabel = TextView(context).apply {
            text = "SMS Metni"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppTheme.COLOR_TEXT_SECONDARY)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }
        content.addView(bodyLabel)

        bodyInput = EditText(context).apply {
            hint = "SMS içeriğini buraya yazın..."
            setHintTextColor(AppTheme.COLOR_TEXT_MUTED)
            setTextColor(AppTheme.COLOR_TEXT_PRIMARY)
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            background = AppTheme.createCardDrawable(AppTheme.COLOR_SURFACE_CARD, dp(12).toFloat(), AppTheme.COLOR_BORDER, 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
            layoutParams = lp
        }
        content.addView(bodyInput)

        // Gönder / Analiz Et Butonu
        val submitBtn = Button(context).apply {
            text = "⚡ SMS'i Filtrele ve Karar Ver"
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = AppTheme.createButtonDrawable(AppTheme.COLOR_ACCENT_BLUE, dp(20).toFloat())
            setPadding(dp(20), dp(14), dp(20), dp(14))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val s = senderInput.text.toString().trim()
                val b = bodyInput.text.toString().trim()
                if (s.isNotEmpty() && b.isNotEmpty()) {
                    viewModel.simulateIncomingSms(s, b)
                }
            }
        }
        content.addView(submitBtn)

        scrollView.addView(content)
        addView(scrollView)
    }

    fun render(state: UiState) {
        // İhtiyaç halinde state güncellemeleri
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}


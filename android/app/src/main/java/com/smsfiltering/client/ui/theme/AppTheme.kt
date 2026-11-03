package com.smsfiltering.client.ui.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

/**
 * Şekil 3 (Mobil Uygulama Ekran Taslakları) ile birebir uyumlu
 * koyu tema (dark theme) renk paleti ve çizim oluşturucuları.
 */
object AppTheme {
    const val COLOR_BG = 0xFF121214.toInt()
    const val COLOR_SURFACE = 0xFF1E1E22.toInt()
    const val COLOR_SURFACE_CARD = 0xFF24242A.toInt()
    const val COLOR_SURFACE_LIGHT = 0xFF2D2D35.toInt()
    const val COLOR_BORDER = 0xFF35353E.toInt()

    const val COLOR_TEXT_PRIMARY = 0xFFF9FAFB.toInt()
    const val COLOR_TEXT_SECONDARY = 0xFF9CA3AF.toInt()
    const val COLOR_TEXT_MUTED = 0xFF6B7280.toInt()

    const val COLOR_ACCENT_BLUE = 0xFF3B82F6.toInt()
    const val COLOR_ACCENT_GREEN = 0xFF10B981.toInt()
    const val COLOR_ACCENT_RED = 0xFFEF4444.toInt()
    const val COLOR_ACCENT_AMBER = 0xFFF59E0B.toInt()

    // Rozet Renkleri (Şekil 3)
    const val BADGE_RISK_LOW_BG = 0xFF14532D.toInt()
    const val BADGE_RISK_LOW_TEXT = 0xFF4ADE80.toInt()      // Risksiz (Yeşil)

    const val BADGE_RISK_MID_BG = 0xFF78350F.toInt()
    const val BADGE_RISK_MID_TEXT = 0xFFFBBF24.toInt()      // Orta risk (Turuncu)

    const val BADGE_RISK_HIGH_BG = 0xFF7F1D1D.toInt()
    const val BADGE_RISK_HIGH_TEXT = 0xFFF87171.toInt()     // Riskli (Kırmızı)

    const val BADGE_TOPIC_BG = 0xFF2A2A34.toInt()
    const val BADGE_TOPIC_TEXT = 0xFFE2E8F0.toInt()         // Konu Etiketi (Koyu gri/bej)

    fun createCardDrawable(
        bgColor: Int = COLOR_SURFACE_CARD,
        cornerRadiusPx: Float = 24f,
        strokeColor: Int = COLOR_BORDER,
        strokeWidthPx: Int = 2
    ): Drawable {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = cornerRadiusPx
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = cornerRadiusPx
        }
        return RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), gd, mask)
    }

    fun createBadgeDrawable(bgColor: Int, cornerRadiusPx: Float = 16f): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = cornerRadiusPx
        }
    }

    fun createButtonDrawable(
        bgColor: Int,
        cornerRadiusPx: Float = 20f,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthPx: Int = 0
    ): Drawable {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = cornerRadiusPx
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = cornerRadiusPx
        }
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), gd, mask)
    }
}


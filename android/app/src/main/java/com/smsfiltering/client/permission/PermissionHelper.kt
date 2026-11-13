package com.smsfiltering.client.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android çalışma zamanı izinleri (RECEIVE_SMS, POST_NOTIFICATIONS)
 * kontrol ve yönetim yardımcı sınıfı.
 */
object PermissionHelper {

    /**
     * SMS alma izni (RECEIVE_SMS).
     */
    const val PERMISSION_RECEIVE_SMS = Manifest.permission.RECEIVE_SMS

    /**
     * Android 13+ (API 33) bildirim gösterme izni (POST_NOTIFICATIONS).
     */
    const val PERMISSION_POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    /**
     * Uygulamanın tam işlevsel çalışması için gerekli tüm izinlerin listesini döner.
     * POST_NOTIFICATIONS izni Android 13 (TIRAMISU / API 33) ve üzerinde gereklidir.
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(PERMISSION_RECEIVE_SMS, PERMISSION_POST_NOTIFICATIONS)
        } else {
            arrayOf(PERMISSION_RECEIVE_SMS)
        }
    }

    /**
     * RECEIVE_SMS izninin verilip verilmediğini kontrol eder.
     */
    fun hasReceiveSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * POST_NOTIFICATIONS izninin verilip verilmediğini kontrol eder.
     * Android 13 (API 33) öncesinde bildirim izni yükleme anında otomatik verilir, dolayısıyla true döner.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                PERMISSION_POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Gerekli tüm izinlerin verilip verilmediğini kontrol eder.
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return hasReceiveSmsPermission(context) && hasNotificationPermission(context)
    }

    /**
     * Henüz verilmemiş (eksik) izinlerin listesini döner.
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!hasReceiveSmsPermission(context)) {
            missing.add(PERMISSION_RECEIVE_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            missing.add(PERMISSION_POST_NOTIFICATIONS)
        }
        return missing
    }
}

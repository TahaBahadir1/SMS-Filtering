package com.smsfiltering.client.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterResult
import com.smsfiltering.client.permission.PermissionHelper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Karar hattından geçen SMS'ler için bildirim kanallarını yöneten ve
 * risk düzeyine uygun zengin bildirimler oluşturan yardımcı sınıf.
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    // Bildirim Kanalları
    const val CHANNEL_ID_ALERTS = "sms_filter_security_alerts"
    const val CHANNEL_NAME_ALERTS = "SMS Güvenlik Uyarıları"
    const val CHANNEL_DESC_ALERTS = "Oltalama (Phishing), Spam ve Şüpheli SMS bildirimleri"

    const val CHANNEL_ID_INFO = "sms_filter_general_info"
    const val CHANNEL_NAME_INFO = "SMS Bilgilendirme"
    const val CHANNEL_DESC_INFO = "Güvenli olarak sınıflandırılan SMS bildirimleri"

    // Benzersiz bildirim kimliği üretici
    private val notificationIdGenerator = AtomicInteger(1000)

    /**
     * Android 8.0 (API 26) ve üzeri için bildirim kanallarını kaydeder.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Yüksek Öncelikli Güvenlik Uyarıları Kanalı (Phishing, Spam, Şüpheli)
            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                CHANNEL_NAME_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_ALERTS
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }

            // 2. Standart Bilgilendirme Kanalı (Güvenli SMS'ler)
            val infoChannel = NotificationChannel(
                CHANNEL_ID_INFO,
                CHANNEL_NAME_INFO,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_INFO
                enableLights(false)
            }

            notificationManager.createNotificationChannels(listOf(alertsChannel, infoChannel))
        }
    }

    /**
     * Karar sonucuna göre NotificationCompat.Builder nesnesini yapılandırır.
     */
    fun buildNotification(
        context: Context,
        result: SmsFilterResult,
        targetIntent: Intent? = null
    ): NotificationCompat.Builder {
        val channelId = when (result.decision) {
            SmsFilterDecision.PHISHING,
            SmsFilterDecision.SPAM,
            SmsFilterDecision.SUSPICIOUS -> CHANNEL_ID_ALERTS
            SmsFilterDecision.SAFE -> CHANNEL_ID_INFO
        }

        val title: String
        val shortContent: String
        val priority: Int
        val color: Int
        val smallIcon: Int

        val scorePercent = (result.riskScore * 100).toInt()

        when (result.decision) {
            SmsFilterDecision.PHISHING -> {
                title = "🚨 OLTALAMA (PHISHING) UYARISI"
                shortContent = "Tehlikeli SMS tespit edildi! Gönderen: ${result.sender} (Risk: %$scorePercent)"
                priority = NotificationCompat.PRIORITY_MAX
                color = Color.parseColor("#D32F2F") // Kırmızı
                smallIcon = android.R.drawable.stat_sys_warning
            }
            SmsFilterDecision.SPAM -> {
                title = "🚫 İstenmeyen SMS (Spam)"
                shortContent = "Spam SMS tespit edildi: ${result.sender} (Risk: %$scorePercent)"
                priority = NotificationCompat.PRIORITY_HIGH
                color = Color.parseColor("#E65100") // Turuncu
                smallIcon = android.R.drawable.stat_notify_error
            }
            SmsFilterDecision.SUSPICIOUS -> {
                title = "⚠️ Şüpheli SMS Uyarısı"
                shortContent = "Şüpheli içerik: ${result.sender} (Risk: %$scorePercent)"
                priority = NotificationCompat.PRIORITY_HIGH
                color = Color.parseColor("#F57F17") // Kehribar/Sarı
                smallIcon = android.R.drawable.stat_notify_more
            }
            SmsFilterDecision.SAFE -> {
                title = "✅ Güvenli SMS"
                shortContent = "Doğrulanmış mesaj: ${result.sender}"
                priority = NotificationCompat.PRIORITY_DEFAULT
                color = Color.parseColor("#2E7D32") // Yeşil
                smallIcon = android.R.drawable.stat_notify_chat
            }
        }

        // Genişletilebilir Detaylı Açıklama (BigTextStyle)
        val expandedDetails = buildString {
            appendLine("Gönderici: ${result.sender}")
            appendLine("Risk Skoru: %$scorePercent (${result.decision.name})")

            if (result.reasons.isNotEmpty()) {
                appendLine("Tespit Sebepleri:")
                for (reason in result.reasons) {
                    appendLine(" • $reason")
                }
            }

            if (result.extractedUrls.isNotEmpty()) {
                appendLine("Tespit Edilen Bağlantılar: ${result.extractedUrls.joinToString(", ")}")
            }

            appendLine("\nMesaj Metni:")
            append(result.body)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(shortContent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedDetails)
                    .setBigContentTitle(title)
                    .setSummaryText(result.decision.name)
            )
            .setPriority(priority)
            .setColor(color)
            .setAutoCancel(true)

        if (result.decision == SmsFilterDecision.PHISHING) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        } else {
            builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
        }

        // PendingIntent: Bildirime tıklandığında uygulamanın açılmasını sağlar
        val intent = targetIntent ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationIdGenerator.get(),
                intent,
                flags
            )
            builder.setContentIntent(pendingIntent)
        }

        return builder
    }

    /**
     * Karar hattı sonucunu kullanıcıya bildirim olarak gösterir.
     * Android 13+ için POST_NOTIFICATIONS izni yoksa bildirim çağrısı güvenle atlanır.
     *
     * @return Gösterilen bildirimin ID'si; izin yoksa veya hata oluşursa -1 döner.
     */
    fun showDecisionNotification(
        context: Context,
        result: SmsFilterResult,
        notificationId: Int = notificationIdGenerator.incrementAndGet(),
        targetIntent: Intent? = null
    ): Int {
        // Kanalların oluşturulduğundan emin ol
        createNotificationChannels(context)

        // Çalışma zamanı bildirim izni kontrolü
        if (!PermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS izni verilmediğinden bildirim gösterilemedi.")
            return -1
        }

        return try {
            val builder = buildNotification(context, result, targetIntent)
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            notificationId
        } catch (e: SecurityException) {
            Log.e(TAG, "Bildirim gönderme güvenlik hatası", e)
            -1
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim gösterilirken beklenmeyen hata", e)
            -1
        }
    }
}

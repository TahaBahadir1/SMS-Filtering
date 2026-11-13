package com.smsfiltering.client.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationHelperTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun testCreateNotificationChannels_RegistersExpectedChannels() {
        NotificationHelper.createNotificationChannels(context)

        val alertsChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID_ALERTS)
        val infoChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID_INFO)

        assertNotNull("Güvenlik uyarıları kanalı oluşturulmalıdır", alertsChannel)
        assertEquals(NotificationHelper.CHANNEL_NAME_ALERTS, alertsChannel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, alertsChannel.importance)

        assertNotNull("Bilgilendirme kanalı oluşturulmalıdır", infoChannel)
        assertEquals(NotificationHelper.CHANNEL_NAME_INFO, infoChannel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, infoChannel.importance)
    }

    @Test
    fun testBuildNotification_PhishingDecision() {
        val phishingResult = SmsFilterResult(
            sender = "+905551234567",
            body = "Tebrikler! Hesabınıza 50.000 TL bonus. Şifrenizi girin: https://sahte.com",
            decision = SmsFilterDecision.PHISHING,
            riskScore = 0.95,
            isDangerousSender = false,
            matchedCategories = mapOf("SIFRE" to listOf("şifre"), "LINK" to listOf("https://sahte.com")),
            matchedTopics = emptyMap(),
            extractedUrls = listOf("https://sahte.com"),
            reasons = listOf("Hassas güvenlik bilgisi ve web bağlantısı tespit edildi")
        )

        val builder = NotificationHelper.buildNotification(context, phishingResult)
        val notification = builder.build()

        assertEquals(NotificationHelper.CHANNEL_ID_ALERTS, notification.channelId)
        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val text = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()

        assertTrue(title?.contains("OLTALAMA") == true || title?.contains("PHISHING") == true)
        assertTrue(text?.contains("+905551234567") == true)
        assertTrue(text?.contains("%95") == true)
        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
    }

    @Test
    fun testBuildNotification_SpamDecision() {
        val spamResult = SmsFilterResult(
            sender = "BAHIS-VIP",
            body = "Giriş yapın 1000 TL deneme bonusu slot oyunları!",
            decision = SmsFilterDecision.SPAM,
            riskScore = 0.80,
            isDangerousSender = false,
            matchedCategories = emptyMap(),
            matchedTopics = mapOf("KUMAR" to listOf("deneme bonusu", "slot oyunu")),
            extractedUrls = emptyList(),
            reasons = listOf("Yasadışı bahis/kumar tanıtımı tespit edildi")
        )

        val builder = NotificationHelper.buildNotification(context, spamResult)
        val notification = builder.build()

        assertEquals(NotificationHelper.CHANNEL_ID_ALERTS, notification.channelId)
        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        assertTrue(title?.contains("Spam") == true)
    }

    @Test
    fun testBuildNotification_SuspiciousDecision() {
        val suspiciousResult = SmsFilterResult(
            sender = "+905559876543",
            body = "Son ödeme tarihiniz geçmiştir, lütfen kontrol ediniz.",
            decision = SmsFilterDecision.SUSPICIOUS,
            riskScore = 0.50,
            isDangerousSender = false,
            matchedCategories = mapOf("ACIL_ODEME" to listOf("son ödeme")),
            matchedTopics = emptyMap(),
            extractedUrls = emptyList(),
            reasons = listOf("Acil ödeme veya borç uyarısı içeriyor")
        )

        val builder = NotificationHelper.buildNotification(context, suspiciousResult)
        val notification = builder.build()

        assertEquals(NotificationHelper.CHANNEL_ID_ALERTS, notification.channelId)
        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        assertTrue(title?.contains("Şüpheli") == true)
    }

    @Test
    fun testBuildNotification_SafeDecision() {
        val safeResult = SmsFilterResult(
            sender = "GUVENLI-BANKA",
            body = "123456 nolu tek kullanımlık şifrenizdir. Kimseyle paylaşmayınız.",
            decision = SmsFilterDecision.SAFE,
            riskScore = 0.05,
            isDangerousSender = false,
            matchedCategories = emptyMap(),
            matchedTopics = emptyMap(),
            extractedUrls = emptyList(),
            reasons = listOf("Doğrulanmış güvenli gönderici")
        )

        val builder = NotificationHelper.buildNotification(context, safeResult)
        val notification = builder.build()

        assertEquals(NotificationHelper.CHANNEL_ID_INFO, notification.channelId)
        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        assertTrue(title?.contains("Güvenli") == true)
    }

    @Test
    fun testShowDecisionNotification_WhenPermissionGranted_DispatchesNotification() {
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val result = SmsFilterResult(
            sender = "+905550001122",
            body = "Hesabınız askıya alındı https://phish.example",
            decision = SmsFilterDecision.PHISHING,
            riskScore = 0.90,
            isDangerousSender = false,
            matchedCategories = emptyMap(),
            matchedTopics = emptyMap(),
            extractedUrls = listOf("https://phish.example"),
            reasons = listOf("Oltalama bağlantısı")
        )

        val notificationId = NotificationHelper.showDecisionNotification(context, result)
        assertTrue(notificationId > 0)

        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val activeNotifications = shadowNotificationManager.allNotifications
        assertTrue(activeNotifications.isNotEmpty())
    }

    @Test
    fun testShowDecisionNotification_WhenPermissionDenied_ReturnsNegativeOne() {
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val result = SmsFilterResult(
            sender = "+905550001122",
            body = "Mesaj içeriği",
            decision = SmsFilterDecision.SAFE,
            riskScore = 0.0,
            isDangerousSender = false,
            matchedCategories = emptyMap(),
            matchedTopics = emptyMap(),
            extractedUrls = emptyList(),
            reasons = emptyList()
        )

        val notificationId = NotificationHelper.showDecisionNotification(context, result)
        assertEquals(-1, notificationId)
    }
}

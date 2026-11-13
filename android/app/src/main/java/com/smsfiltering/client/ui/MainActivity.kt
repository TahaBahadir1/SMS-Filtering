package com.smsfiltering.client.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smsfiltering.client.R
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.filtering.SmsFilterDecision
import com.smsfiltering.client.filtering.SmsFilterEngine
import com.smsfiltering.client.notification.NotificationHelper
import com.smsfiltering.client.permission.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SMS Filtreleme ana ekranı.
 *
 * Çalışma zamanı izinlerini (RECEIVE_SMS, POST_NOTIFICATIONS) yönetir,
 * izin durumlarını kullanıcıya gösterir ve filtrelenen mesajları listeler.
 */
class MainActivity : Activity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
    }

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var tvSmsPermissionStatus: TextView
    private lateinit var tvNotificationPermissionStatus: TextView
    private lateinit var btnRequestPermissions: Button
    private lateinit var btnSimulatePhishing: Button
    private lateinit var llMessagesContainer: LinearLayout
    private lateinit var tvEmptyMessages: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bildirim kanallarını oluştur
        NotificationHelper.createNotificationChannels(this)

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        loadRecentMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun initViews() {
        tvSmsPermissionStatus = findViewById(R.id.tvSmsPermissionStatus)
        tvNotificationPermissionStatus = findViewById(R.id.tvNotificationPermissionStatus)
        btnRequestPermissions = findViewById(R.id.btnRequestPermissions)
        btnSimulatePhishing = findViewById(R.id.btnSimulatePhishing)
        llMessagesContainer = findViewById(R.id.llMessagesContainer)
        tvEmptyMessages = findViewById(R.id.tvEmptyMessages)
    }

    private fun setupListeners() {
        btnRequestPermissions.setOnClickListener {
            requestRequiredPermissions()
        }

        btnSimulatePhishing.setOnClickListener {
            simulateTestPhishingSms()
        }
    }

    /**
     * İzin durumlarını kontrol eder ve UI bileşenlerini günceller.
     */
    fun updatePermissionUi() {
        val hasSms = PermissionHelper.hasReceiveSmsPermission(this)
        val hasNotification = PermissionHelper.hasNotificationPermission(this)

        if (hasSms) {
            tvSmsPermissionStatus.text = getString(R.string.status_granted)
            tvSmsPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.color_safe))
        } else {
            tvSmsPermissionStatus.text = getString(R.string.status_denied)
            tvSmsPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.color_phishing))
        }

        if (hasNotification) {
            tvNotificationPermissionStatus.text = getString(R.string.status_granted)
            tvNotificationPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.color_safe))
        } else {
            tvNotificationPermissionStatus.text = getString(R.string.status_denied)
            tvNotificationPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.color_phishing))
        }

        if (hasSms && hasNotification) {
            btnRequestPermissions.visibility = View.GONE
        } else {
            btnRequestPermissions.visibility = View.VISIBLE
        }
    }

    /**
     * Gerekli çalışma zamanı izinlerini kullanıcıdan talep eder.
     */
    fun requestRequiredPermissions() {
        val required = PermissionHelper.getRequiredPermissions()
        ActivityCompat.requestPermissions(this, required, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionUi()

            if (PermissionHelper.areAllPermissionsGranted(this)) {
                Toast.makeText(this, "Tüm izinler başarıyla verildi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Bazı izinler verilmedi. Gerçek zamanlı SMS filtreleme kısıtlanabilir.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Veritabanında kayıtlı son filtrelenen mesajları yükler ve ekranda gösterir.
     */
    private fun loadRecentMessages() {
        activityScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getInstance(applicationContext)
            val messages = database.filteredMessageDao().getAllMessages()
            withContext(Dispatchers.Main) {
                renderMessages(messages)
            }
        }
    }

    private fun renderMessages(messages: List<FilteredMessageEntity>) {
        llMessagesContainer.removeAllViews()

        if (messages.isEmpty()) {
            tvEmptyMessages.visibility = View.VISIBLE
            return
        }

        tvEmptyMessages.visibility = View.GONE

        // En son gelenler üstte olacak şekilde ters sırala
        for (msg in messages.reversed()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(24, 20, 24, 20)
                elevation = 2f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                layoutParams = params
            }

            val tvHeader = TextView(this).apply {
                val decisionBadge = when (msg.decision) {
                    SmsFilterDecision.PHISHING.name -> "🚨 PHISHING (%${(msg.riskScore * 100).toInt()})"
                    SmsFilterDecision.SPAM.name -> "🚫 SPAM (%${(msg.riskScore * 100).toInt()})"
                    SmsFilterDecision.SUSPICIOUS.name -> "⚠️ ŞÜPHELİ (%${(msg.riskScore * 100).toInt()})"
                    else -> "✅ GÜVENLİ (%${(msg.riskScore * 100).toInt()})"
                }
                text = "${msg.sender}  •  $decisionBadge"
                textSize = 14f
                paint.isFakeBoldText = true
                setTextColor(
                    when (msg.decision) {
                        SmsFilterDecision.PHISHING.name -> ContextCompat.getColor(this@MainActivity, R.color.color_phishing)
                        SmsFilterDecision.SPAM.name -> ContextCompat.getColor(this@MainActivity, R.color.color_spam)
                        SmsFilterDecision.SUSPICIOUS.name -> ContextCompat.getColor(this@MainActivity, R.color.color_suspicious)
                        else -> ContextCompat.getColor(this@MainActivity, R.color.color_safe)
                    }
                )
            }

            val tvBody = TextView(this).apply {
                text = msg.body
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                setPadding(0, 8, 0, 8)
            }

            val tvReasons = TextView(this).apply {
                text = "Neden: ${msg.reasons}"
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            }

            card.addView(tvHeader)
            card.addView(tvBody)
            card.addView(tvReasons)

            llMessagesContainer.addView(card)
        }
    }

    /**
     * Karar hattını ve bildirim sistemini yerel olarak test etmek için
     * örnek bir oltalama SMS'i simüle eder.
     */
    private fun simulateTestPhishingSms() {
        activityScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getInstance(applicationContext)
            val engine = SmsFilterEngine(database = database)

            val testSender = "+905551234567"
            val testBody = "Tebrikler! Hesabınıza 50.000 TL bonus tanımlandı. Doğrulamak için hemen tıklayın: https://sahte-banka.com/login"

            // Karar hattından geçir
            val result = engine.filterSms(
                sender = testSender,
                body = testBody,
                useOnlineSenderLookup = false,
                saveToDatabase = true
            )

            // Bildirim göster
            NotificationHelper.showDecisionNotification(this@MainActivity, result)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Test SMS filtreleme tamamlandı ve bildirim gönderildi.", Toast.LENGTH_SHORT).show()
                loadRecentMessages()
            }
        }
    }
}

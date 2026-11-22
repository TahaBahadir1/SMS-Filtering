package com.smsfiltering.client.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.remote.ApiClient
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.SenderRepository
import com.smsfiltering.client.filtering.SmsClassifier
import com.smsfiltering.client.filtering.SmsFilterEngine
import com.smsfiltering.client.filtering.SmsFilterResult
import com.smsfiltering.client.notification.NotificationHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
/**
 * Android işletim sisteminde gelen SMS'leri gerçek zamanlı yakalayan BroadcastReceiver bileşeni.
 *
 * "android.provider.Telephony.SMS_RECEIVED" intent'ini dinler, çok parçalı (multi-part)
 * SMS'leri birleştirir, arka planda karar hattı (SmsFilterEngine) üzerinden filtreler,
 * veritabanına kaydeder ve karar sonucuna göre kullanıcıya bildirim gösterir.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"

        @VisibleForTesting
        var injectedFilterEngine: SmsFilterEngine? = null

        @VisibleForTesting
        var injectedDispatcher: CoroutineDispatcher = Dispatchers.IO

        @VisibleForTesting
        var onSmsProcessed: ((SmsFilterResult) -> Unit)? = null

        /**
         * Test amacıyla receiver'a SMS mesajı enjekte etmek için kullanılır.
         */
        @VisibleForTesting
        fun resetTestOverrides() {
            injectedFilterEngine = null
            injectedDispatcher = Dispatchers.IO
            onSmsProcessed = null
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Desteklenmeyen action: $action")
            return
        }

        // 1. Intent üzerinden SMS mesajlarını çözümle
        val messages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "SMS intent mesajları okunamadı", e)
            emptyArray()
        }

        if (messages.isEmpty()) {
            Log.w(TAG, "SMS_RECEIVED alındı ancak mesaj dizisi boş.")
            return
        }

        // 2. Çok parçalı (multi-part) mesajları göndericiye göre grupla ve birleştir
        val groupedMessages = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "Bilinmeyen Gönderici" }

        // 3. BroadcastReceiver ana iş parçacığını (Main Thread) bloke etmemek ve
        // veritabanı/ağ işlemlerini güvenle tamamlamak için goAsync() kullan
        val pendingResult = goAsync()
        val scope = CoroutineScope(injectedDispatcher + SupervisorJob())

        scope.launch {
            try {
                // Filtreleme motorunu hazırla
                val filterEngine = injectedFilterEngine ?: createDefaultFilterEngine(context)

                for ((sender, partList) in groupedMessages) {
                    val fullBody = partList.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }

                    Log.d(TAG, "Gelen SMS işleniyor (uzunluk=${fullBody.length})")

                    // 4. Karar Hattından Geçir (Filtreleme & Risk Analizi)
                    val result = filterEngine.filterSms(
                        sender = sender,
                        body = fullBody,
                        useOnlineSenderLookup = true,
                        saveToDatabase = true
                    )

                    Log.i(TAG, "SMS filtreleme kararı: Karar=${result.decision}, Skor=${result.riskScore}, ModelP=${result.modelSpamProbability}")

                    // 5. Karar hattından geçen mesaj için kullanıcıya bildirim göster
                    // IP 4: Deep-link için mesajın veritabanı ID'sini bildirime ekle
                    val messageId = runCatching {
                        if (injectedFilterEngine == null) {
                            val db = AppDatabase.getInstance(context.applicationContext)
                            db.filteredMessageDao().getAllMessages().firstOrNull { msg ->
                                msg.sender == result.sender && msg.body == result.body
                            }?.id ?: -1L
                        } else {
                            -1L
                        }
                    }.getOrDefault(-1L)
                    val deepLinkIntent = runCatching {
                        Intent(context, Class.forName("com.smsfiltering.client.MainActivity")).apply {
                            this.action = NotificationHelper.ACTION_OPEN_MESSAGE_DETAIL
                            putExtra(NotificationHelper.EXTRA_MESSAGE_ID, messageId)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    }.getOrNull()
                    NotificationHelper.showDecisionNotification(context, result, targetIntent = deepLinkIntent)

                    // Test kancası
                    onSmsProcessed?.invoke(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS filtreleme işlemi sırasında beklenmeyen hata oluştu", e)
            } finally {
                // Asenkron işlemin bittiğini Android sistemine bildir
                try {
                    pendingResult.finish()
                } catch (e: Exception) {
                    Log.w(TAG, "pendingResult.finish() çağrısında hata", e)
                }
            }
        }
    }

    /**
     * Varsayılan Room veritabanı ve Repository bağımlılıkları ile filtre motoru oluşturur.
     */
    private fun createDefaultFilterEngine(context: Context): SmsFilterEngine {
        val appContext = context.applicationContext
        val database = AppDatabase.getInstance(appContext)

        val keywordRepository = KeywordRepository(
            apiService = ApiClient.createService(),
            database = database
        )

        val senderRepository = SenderRepository(
            apiService = ApiClient.createService(),
            database = database
        )

        val classifier = runCatching {
            SmsClassifier.getInstance(appContext)
        }.getOrNull()

        return SmsFilterEngine(
            database = database,
            keywordRepository = keywordRepository,
            senderRepository = senderRepository,
            classifier = classifier
        )
    }
}

package com.smsfiltering.client.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.repository.KeywordRepository
import com.smsfiltering.client.data.repository.KeywordSyncResult
import java.util.concurrent.TimeUnit

/**
 * 12 saatte bir anahtar kelime ve konu senkronizasyonunu yürüten arka plan WorkManager işçisi.
 * Yalnızca ağ bağlıyken çalışır ve başarısızlık durumunda üstel geri çekilme (exponential backoff) uygular.
 * Gizlilik: Kelimeler, konu adları veya mesaj içerikleri loglanmaz, yalnızca sayılar ve sürüm bilgisi yazılır.
 */
class KeywordSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = KeywordRepository(database = database)

        return try {
            when (val syncResult = repository.sync()) {
                is KeywordSyncResult.Success -> {
                    // Gizlilik: Sadece kategori, kelime, konu sayıları ve sürüm loglanır
                    Log.i(
                        TAG,
                        "Periyodik senkronizasyon başarılı: Kategori sayısı (${syncResult.categoriesCount}), Kelime sayısı (${syncResult.keywordsCount}), Konu sayısı (${syncResult.topicsCount}) ve version: ${syncResult.version}"
                    )
                    Result.success()
                }
                is KeywordSyncResult.AlreadyUpToDate -> {
                    Log.i(TAG, "Periyodik senkronizasyon: Veriler güncel (version: ${syncResult.version})")
                    Result.success()
                }
                is KeywordSyncResult.Error -> {
                    Log.w(TAG, "Periyodik senkronizasyon sunucu hatası: HTTP ${syncResult.code}")
                    Result.retry()
                }
                is KeywordSyncResult.Exception -> {
                    // Ağ hatasında mevcut veri korunur, sessizce sonraki denemeye bırakılır
                    Log.w(TAG, "Periyodik senkronizasyon ağ hatası, yeniden denenecek")
                    Result.retry()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Periyodik senkronizasyon beklenmeyen hata, yeniden denenecek")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "KeywordSyncWorker"
        const val WORK_NAME = "keyword_sync_periodic_work"

        /**
         * 12 saatte bir, yalnızca ağ bağlıyken çalışan ve üstel geri çekilmeli periyodik iş zamanlaması.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<KeywordSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}


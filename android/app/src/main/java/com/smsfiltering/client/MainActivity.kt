package com.smsfiltering.client

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.smsfiltering.client.data.remote.NetworkResult
import com.smsfiltering.client.data.repository.KeywordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Uygulamanın ana başlatıcı (launcher) aktivitesi.
 *
 * Açılışta test amaçlı GET /keywords?version=0 çağrısı yapar ve
 * yalnızca kategori sayısı ve version bilgisini logcat'e yazar.
 * Gizlilik kuralı gereği kelimeler veya hassas veriler asla loglanmaz.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "SMS Filtering İstemcisi"
            textSize = 18f
            setPadding(40, 40, 40, 40)
        }
        setContentView(textView)

        checkKeywordsOnStartup()
    }

    private fun checkKeywordsOnStartup() {
        scope.launch {
            val repository = KeywordRepository()
            when (val result = repository.getKeywords(currentVersion = 0L)) {
                is NetworkResult.Success -> {
                    val data = result.data
                    // Gizlilik: Yalnızca kategori sayısı ve version bilgisi yazılır, kelimeler asla loglanmaz.
                    Log.i(TAG, "Kategori sayısı (${data.categories.size}) ve version: ${data.version}")
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Keywords çağrısı başarısız: HTTP ${result.code}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Keywords çağrısı istisna: ${result.throwable.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "SMSFiltering"
    }
}


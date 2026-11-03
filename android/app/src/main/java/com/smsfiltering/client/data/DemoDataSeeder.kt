package com.smsfiltering.client.data

import com.smsfiltering.client.data.local.AppDatabase
import com.smsfiltering.client.data.local.entity.FilteredMessageEntity
import com.smsfiltering.client.data.local.entity.SenderCacheEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Raporlardaki Şekil 3 (Mobil Ekran Taslakları) ve 18 senaryoluk demo mesaj setine
 * (ai/demo_mesaj_seti_18.csv) uygun örnek verileri Room veritabanına aktaran yardımcı sınıf.
 */
object DemoDataSeeder {

    suspend fun seedIfEmpty(database: AppDatabase, dispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext(dispatcher) {
        val existingCount = database.filteredMessageDao().getAllMessages().size
        if (existingCount == 0) {
            seed(database, dispatcher)
        }
    }

    suspend fun seed(database: AppDatabase, dispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600 * 1000
        val twoHoursAgo = now - 7200 * 1000
        val yesterday = now - 86400 * 1000

        // 1. Örnek Gönderenler (Şekil 3'teki Gönderenler ekranı verileri)
        val demoSenders = listOf(
            SenderCacheEntity(
                sender = "BANKA-X",
                riskScore = 0.05,
                riskLevel = "risksiz",
                isFinalized = true,
                cachedAt = now - 3600 * 1000 * 2
            ),
            SenderCacheEntity(
                sender = "+90 555 000 00 00",
                riskScore = 0.95,
                riskLevel = "riskli",
                isFinalized = true,
                cachedAt = now - 3600 * 1000 * 5
            ),
            SenderCacheEntity(
                sender = "ODEME-GUNCELLE",
                riskScore = 0.85,
                riskLevel = "riskli",
                isFinalized = true,
                cachedAt = now - 86400 * 1000 * 3
            ),
            SenderCacheEntity(
                sender = "KREDI-DESTEK",
                riskScore = 0.55,
                riskLevel = "orta_riskli",
                isFinalized = false,
                cachedAt = now - 3600 * 1000 * 3
            ),
            SenderCacheEntity(
                sender = "GARANTI",
                riskScore = 0.02,
                riskLevel = "risksiz",
                isFinalized = true,
                cachedAt = now - 86400 * 1000 * 10
            ),
            SenderCacheEntity(
                sender = "TURKCELL",
                riskScore = 0.03,
                riskLevel = "risksiz",
                isFinalized = true,
                cachedAt = now - 86400 * 1000 * 12
            ),
            SenderCacheEntity(
                sender = "BET-VIP",
                riskScore = 0.98,
                riskLevel = "riskli",
                isFinalized = true,
                cachedAt = now - 3600 * 1000 * 4
            )
        )
        database.senderCacheDao().insertSenders(demoSenders)

        // 2. Örnek Mesajlar (Şekil 3'teki Karantina Listesi ve demo_mesaj_seti_18.csv)
        val demoMessages = listOf(
            // --- KARANTİNA (Şekil 3'teki 3 ana kart) ---
            FilteredMessageEntity(
                sender = "KREDI-DESTEK",
                body = "Sayin musterimiz, onaylanmis 50.000 TL krediniz hazir. Detaylari incelemek ve hemen kullanmak icin: https://kredi-onay.net/basvuru",
                decision = "SUSPICIOUS",
                riskScore = 0.55,
                matchedCategories = "LINK",
                matchedTopics = "BANKA",
                matchedKeywords = "kredi, link",
                reasons = "Mesajda bağlantı var; Risk işareti: link; Gönderen skoru: 0.55",
                timestamp = oneHourAgo
            ),
            FilteredMessageEntity(
                sender = "+90 555 000 00 00",
                body = "b o n u s kazan! 200 Free Spin hesabınıza tanımlandı, hemen oyna: http://slot-bonanza.top",
                decision = "SUSPICIOUS",
                riskScore = 0.75,
                matchedCategories = "LINK",
                matchedTopics = "KUMAR",
                matchedKeywords = "canlı bahis, casino, bonus, link",
                reasons = "2 risk işareti, bağlantı; Yasadışı bahis/kumar tanıtımı tespit edildi",
                timestamp = twoHoursAgo
            ),
            FilteredMessageEntity(
                sender = "BANKA-X",
                body = "Degerli musterimiz, kredi karti yillik aidatiniz iade edilmistir. Islemi tamamlamak ve hesabinizda gormek icin: https://bankax-iadeler.org",
                decision = "SUSPICIOUS",
                riskScore = 0.60,
                matchedCategories = "LINK",
                matchedTopics = "BANKA",
                matchedKeywords = "kredi kartı, kart aidatı, link",
                reasons = "Bağlantı içeriyor; Doğrulanmamış gönderenden banka konusu ve web bağlantısı",
                timestamp = yesterday
            ),
            FilteredMessageEntity(
                sender = "PTT-BILGI",
                body = "Gönderiniz adres yetersizliği nedeniyle şubede bekletilmektedir. Teslimat için adresinizi güncelleyin: http://ptt-kargo-teslim.xyz",
                decision = "SUSPICIOUS",
                riskScore = 0.65,
                matchedCategories = "LINK",
                matchedTopics = "KARGO",
                matchedKeywords = "kargo takip, kurye, link",
                reasons = "Kargo teslimat konusu ile şüpheli web bağlantısı tespit edildi",
                timestamp = yesterday - 3600 * 1000 * 2
            ),
            FilteredMessageEntity(
                sender = "DEFACTO",
                body = "Büyük sezon sonu indirimi başladı! Seçili ürünlerde %50 net indirim mağazalarda ve http://defacto.com adresinde.",
                decision = "SUSPICIOUS",
                riskScore = 0.52,
                matchedCategories = "LINK",
                matchedTopics = "",
                matchedKeywords = "link",
                reasons = "Bağlantı içeriyor; Kullanıcı onayı bekleniyor",
                timestamp = yesterday - 3600 * 1000 * 4
            ),

            // --- GELEN KUTUSU (SAFE / İZİN VERİLEN) ---
            FilteredMessageEntity(
                sender = "GARANTI",
                body = "Değerli müşterimiz, 584920 nolu tek kullanımlık onay şifreniz ile işleminizi tamamlayınız. Güvenliğiniz için kimseyle paylaşmayınız.",
                decision = "SAFE",
                riskScore = 0.05,
                matchedCategories = "SIFRE",
                matchedTopics = "BANKA",
                matchedKeywords = "şifre, kredi kartı",
                reasons = "Doğrulanmış güvenli göndericiden işlem/güvenlik bildirimi",
                timestamp = now - 1800 * 1000
            ),
            FilteredMessageEntity(
                sender = "YURTICI",
                body = "Gönderiniz 40291847596 takip numarası ile dağıtıma çıkarılmıştır. Teslimat kodunuz: 4182.",
                decision = "SAFE",
                riskScore = 0.04,
                matchedCategories = "",
                matchedTopics = "KARGO",
                matchedKeywords = "kargo takip, dağıtıma çıkarıldı",
                reasons = "Şüpheli kelime veya risk tespit edilmedi",
                timestamp = now - 3600 * 1000 * 3
            ),
            FilteredMessageEntity(
                sender = "TURKCELL",
                body = "0532 XXX XX XX nolu hattınıza ait Kasım 2026 fatura tutarınız 215.50 TL'dir. Son ödeme: 18.11.2026.",
                decision = "SAFE",
                riskScore = 0.05,
                matchedCategories = "",
                matchedTopics = "OPERATOR",
                matchedKeywords = "fatura dönemi",
                reasons = "Doğrulanmış güvenli operatör bildirimi",
                timestamp = yesterday - 3600 * 1000 * 6
            ),
            FilteredMessageEntity(
                sender = "MHRS",
                body = "Fırat Üniversitesi Hastanesi Dahiliye polikliniği randevunuz 05.11.2026 saat 11:00'dedir.",
                decision = "SAFE",
                riskScore = 0.02,
                matchedCategories = "",
                matchedTopics = "",
                matchedKeywords = "",
                reasons = "Şüpheli içerik veya bağlantı bulunmuyor",
                timestamp = yesterday - 3600 * 1000 * 8
            ),
            FilteredMessageEntity(
                sender = "TURKIYE-GOV",
                body = "E-Devlet Kapısı üzerinden yapılan başvurunuz sonuçlanmıştır. Detaylar için: https://www.turkiye.gov.tr/",
                decision = "SAFE",
                riskScore = 0.10,
                matchedCategories = "LINK",
                matchedTopics = "",
                matchedKeywords = "link",
                reasons = "Doğrulanmış resmi kamu portalı beyaz liste bağlantısı",
                timestamp = yesterday - 86400 * 1000
            ),
            FilteredMessageEntity(
                sender = "+90 555 000 00 14",
                body = "Selam Taha, kütüphanede misin? Birlikte çalışalım mı?",
                decision = "SAFE",
                riskScore = 0.01,
                matchedCategories = "",
                matchedTopics = "",
                matchedKeywords = "",
                reasons = "Kişisel mesaj, şüpheli kalıp yok",
                timestamp = now - 600 * 1000
            ),

            // --- ENGELLENENLER (SPAM / PHISHING / YÜKSEK RİSK) ---
            FilteredMessageEntity(
                sender = "+90 555 000 00 11",
                body = "Kartınız güvenlik sebebiyle askıya alınmıştır. Kilidi açmak ve kartınızı güncellemek için tıklayınız: http://banka-guvenlik-onay.top",
                decision = "PHISHING",
                riskScore = 0.95,
                matchedCategories = "PIN,LINK",
                matchedTopics = "BANKA",
                matchedKeywords = "kart şifresi, güvenlik kodu, tıklayınız",
                reasons = "Hassas güvenlik bilgisi ve sahte banka web bağlantısı kombinasyonu",
                timestamp = yesterday - 3600 * 1000 * 5
            ),
            FilteredMessageEntity(
                sender = "BET-VIP",
                body = "GÜNCEL BAHİS VE BONUS FIRSATI! 500 TL çevrimsiz kayıp bonusu ve casino slot oyunları hemen tıkla http://betvip99.xyz",
                decision = "SPAM",
                riskScore = 0.95,
                matchedCategories = "LINK",
                matchedTopics = "KUMAR",
                matchedKeywords = "canlı bahis, bonus, casino, slot, link",
                reasons = "Yasadışı bahis/kumar tanıtımı ve web bağlantısı",
                timestamp = yesterday - 3600 * 1000 * 7
            ),
            FilteredMessageEntity(
                sender = "YASAL-TAKIP",
                body = "Hakkınızda açılmış icra takibi başlatılmıştır. Dosya masrafını ödeyerek haczi durdurun: http://e-adli-yargi.online",
                decision = "PHISHING",
                riskScore = 0.90,
                matchedCategories = "ACIL_ODEME,LINK",
                matchedTopics = "",
                matchedKeywords = "haciz işlemi, icra takibi, derhal ödeyiniz, link",
                reasons = "Sahte icra/haciz baskısı ve şüpheli web bağlantısı",
                timestamp = yesterday - 86400 * 1000 * 2
            ),
            FilteredMessageEntity(
                sender = "AKBANK-BILGI",
                body = "Hesabınıza 15.000 TL avans kredi tanımlanmıştır. Onaylamak için kart şifrenizi giriniz: http://ak-avans.site",
                decision = "PHISHING",
                riskScore = 0.95,
                matchedCategories = "SIFRE,LINK",
                matchedTopics = "BANKA",
                matchedKeywords = "şifre, kredi başvurusu, link",
                reasons = "Taklit gönderen adı ile şifre hırsızlığı (Oltalama)",
                timestamp = yesterday - 86400 * 1000 * 3
            )
        )

        database.filteredMessageDao().insertMessages(demoMessages)
    }
}

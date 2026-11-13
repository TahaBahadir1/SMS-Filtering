# T.C. FIRAT ÜNİVERSİTESİ MÜHENDİSLİK FAKÜLTESİ
## BİLGİSAYAR MÜHENDİSLİĞİ BÖLÜMÜ
### BİLGİSAYAR MÜHENDİSLİĞİ TASARIM DERSİ PROJE RAPORU (HAFTA 5)

---

## TAKIM KAPTANI GERİ BİLDİRİM FORMU

### Bölüm 1 - Takım Bilgileri
* **Ekip Adı:** VaultTech
* **Takım Kaptanı:** Taha BAHADIR
* **Ekip Üyeleri:** Arda Yusuf ÖZTEMÜR, Muhammed KANCA
* **Proje Başlığı:** İstenmeyen SMS Mesajlarını Otomatik Sınıflandırma Sistemi
* **Proje Deposu:** https://github.com/TahaBahadir1/SMS-Filtering

### Bölüm 2 - Ekip İçi Çalışma Süreci

| Kriter | Evet (✓) | Hayır (✗) | Açıklama |
|---|:---:|:---:|---|
| Ekip içi iletişim düzenliydi | ✓ | | Günlük kısa senkronizasyon ve çevrim içi toplantılarla ilerlendi. |
| Görev dağılımı netti | ✓ | | 6 iş paketi (İP 1 - İP 6) sorumlulukları net biçimde paylaştırıldı. |
| Tüm üyeler sürece aktif katıldı | ✓ | | Model, sunucu, mobil arayüz ve test aşamalarında tam katılım sağlandı. |
| Toplantılar planlandığı şekilde yapıldı | ✓ | | Scrum sprint hedefleri doğrultusunda periyodik gözden geçirmeler yapıldı. |
| Zaman yönetimi yeterliydi | ✓ | | Tüm iş paketleri haftalık takvime tam uyumlu olarak tamamlandı. |
| Ekip içi anlaşmazlıklar çözüldü | ✓ | | Karar hattı ağırlıkları ve entegrasyon adımları ortak uzlaşıyla belirlendi. |

### Bölüm 3 - Projenin İlerleme Durumu

| Kriter | Evet (✓) | Hayır (✗) | Açıklama |
|---|:---:|:---:|---|
| Problem tanımı netleşti | ✓ | | SMS güvenliği ve gizlilik odaklı sınıflandırma gereksinimleri kesinleşti. |
| İş paketleri tanımlandı | ✓ | | İP 1'den İP 6'ya kadar tüm modüller başarıyla kodlandı ve test edildi. |
| Sorumlular belirlendi | ✓ | | Taha Bahadır, Arda Yusuf Öztemür ve Muhammed Kanca görevlerini tamamladı. |
| Proje çıktıları oluşturuldu | ✓ | | Çalışır durumdaki Android APK, sunucu Docker kapsayıcısı ve test raporları hazırlandı. |

---

## 1. GİRİŞ
Bu rapor, “İstenmeyen SMS Mesajlarını Otomatik Sınıflandırma Sistemi” projesinin beşinci haftasında gerçekleştirilen; önceden tasarlanan mimari bileşenlerin birleştirilmesi, makine öğrenmesi modelinin cihaz üzerinde saf Kotlin diliyle çalıştırılması, mobil kullanıcı arayüzünün kodlanması ve 18 senaryoluk gerçekçi test veri seti ile uçtan uca değerlendirilmesi çalışmalarını kapsamaktadır.

Dördüncü haftada temelleri atılan "Gizlilik Odaklı Kademeli Karar Mimarisi", kullanıcı gizliliğini en üst düzeyde koruyacak biçimde SMS metnini hiçbir dış servise göndermeden yerel cihazda filtrelemeyi esas almaktadır. Beşinci hafta sürecinde tanımlanan 6 iş paketi eksiksiz biçimde hayata geçirilmiştir:
1. Modelin mobil ortama taşınması için JSON formatında ağırlık sözlüğünün hazırlanması ve metin ön işleme adımlarının uyarlanması (İP 1).
2. Doğrulama kodu, kargo bildirimi, fatura, kampanya ve şüpheli oltalama içeriklerini kapsayan 18 senaryoluk demo setinin oluşturulması (İP 2).
3. Sunucu altyapısının Docker ile paketlenerek güvenli tünel üzerinden gerçek mobil cihaz erişimine açılması (İP 3).
4. Kullanıcı deneyimini sağlayan mobil arayüzün (Gelen Kutusu, Karantina, Engellenenler, Mesaj Detayı, Gönderen İtibarı ve Simülatör) kodlanması (İP 4).
5. Makine öğrenmesi çıkarımının Android cihaz üzerinde harici C++ veya ağır çalışma zamanı kütüphanesi olmadan saf Kotlin ile gerçekleştirilmesi ve karar hattına entegrasyonu (İP 5).
6. Hazırlanan sistemin gerçek donanım (Samsung Galaxy M34) üzerinde uçtan uca çalıştırılarak doğruluk, yanlış pozitif (FP), yanlış negatif (FN) ve gecikme metrikleriyle değerlendirilmesi (İP 6).

---

## 2. YÖNTEM VE GELİŞTİRİLEN BİLEŞENLER

### 2.1. Cihaz İçi (On-Device) Saf Kotlin Makine Öğrenmesi Motoru
Modelin mobil cihazlarda ek kütüphane boyutu (TensorFlow Lite, ONNX vb.) ve bellek yükü yaratmaması amacıyla Scikit-Learn `TfidfVectorizer` ve `LogisticRegression` parametreleri saf Kotlin sınıfı (`SmsClassifier`) olarak yeniden üretilmiştir:
* **Ağırlık Sözlüğü (`sms_model_assets.json`):** 12.038 n-gram teriminin IDF değerleri, regresyon ağırlıkları ($w$) ve model sabiti ($\text{intercept} = -0.950466$) JSON formatında Android `assets` dizinine yerleştirilmiştir.
* **Türkçe Uyumlu Metin Ön İşleme (`SmsCleaner`):** Python ortamında eğitilen modelin kelime haznesini korumak adına metinler ASCII'ye dönüştürülmemiş; Türkçe karakter kümesi (`ç, ğ, ı, ö, ş, ü`) korunarak küçük harf dönüşümü, ardışık harf uzatma ve aldatıcı boşluk giderme adımları uygulanmıştır.
* **Vektör Normalizasyonu ve Çıkarım:**
  $$\text{TF-IDF}(t) = TF(t) \times IDF(t)$$
  $$X_{\text{norm}}(t) = \frac{\text{TF-IDF}(t)}{\sqrt{\sum (\text{TF-IDF}_i)^2}}$$
  $$z = \text{intercept} + \sum \left( X_{\text{norm}}(t) \times w(t) \right)$$
  $$P(\text{spam}) = \frac{1}{1 + e^{-z}}$$

### 2.2. Kademeli Karar Hattı Mantığı
SMS içeriği sunucuya gönderilmeksizin cihaz üstünde şu 8 kademeyle değerlendirilir:
1. **Kullanıcı Kararı:** Gönderici için kullanıcı tarafından belirlenmiş açık bir kural (İzin Ver / Engelle) varsa doğrudan uygulanır.
2. **Yerel Önbellek:** Göndericiye ait sunucu itibar verisi 7 günden eski değilse yerel Room veritabanından okunur.
3. **Sunucu İtibar Sorgusu:** Önbellekte yoksa `POST /api/v1/senders/lookup` uç noktası çağrılır. Gönderici riskli ve kesinleşmişse doğrudan engellenir.
4. **Cihaz İçi İçerik Analizi:** Hassas bilgi işaretleri ($k \in \{\text{IBAN, PIN, ŞİFRE, LİNK, ACİL ÖDEME}\}$) ve yapay zeka spam olasılığı ($P$) hesaplanır.
5. **Birleşik Risk Puanlaması:**
   $$\text{Puan} = \text{Gönderici Skoru (varsayılan 0.30)} + 0.2 \times k + (0.4 \times P \text{ [eğer } P \ge 0.50 \text{]})$$
6. **Karar Verme:** $\text{Puan} \ge 0.80 \implies$ **Engelle**, $0.50 \le \text{Puan} < 0.80 \implies$ **Karantina**, $\text{Puan} < 0.50 \implies$ **İzin Ver**.
7. **Bağlantı (URL) Güvenlik Kuralı:** Mesaj bağlantı içeriyorsa ve nihai puan güvenli aralıktaysa, kullanıcı onayı için karar otomatik olarak **Karantina** seviyesine yükseltilir. Resmi kamu beyaz listesindeki bağlantılar (`.gov.tr`) bu yükseltmeden muaf tutulur.
8. **Kayıt ve Gizlilik:** Mesaj yerel Room veritabanına kaydedilir; karantina ve engellenen mesajların metni arayüzde varsayılan olarak maskeli tutulur.

### 2.3. Mobil Kullanıcı Deneyimi ve Arayüz
Gereksinim Analizi Raporu'ndaki Şekil 3 taslaklarına tam uyumlu modern bir arayüz geliştirilmiştir:
* **Sekmeli Mesaj Listesi:** Gelen Kutusu, Karantina ve Engellenenler sekmeleri arasında hızlı geçiş ve okunmamış rozet sayıları.
* **Gizlilik Odaklı Mesaj Detayı:** Mesaj metni ilk açılışta maskeli kutu içinde tutulur; kullanıcının açık tıklaması ile görünür hâle gelir. Kararın matematiksel gerekçeleri, gönderici skoru ve tespit edilen risk göstergeleri şeffaf biçimde listelenir.
* **Gönderen Yönetimi:** Güvenli, şüpheli ve engellenmiş göndericilerin risk seviyeleri, sinyal kaynakları ve manuel kural ekleme işlevleri.
* **Test ve Simülasyon Ekranı:** Kullanıcının veya test uzmanının manuel SMS girerek anlık model skoru ve karar hattı çıktısını simüle edebileceği arayüz.

---

## 3. BULGULAR VE PERFORMANS DEĞERLENDİRMESİ

### 3.1. Model Doğrulama (Python vs. Android Kotlin Karşılaştırması)
Modelin Python Scikit-Learn çıktısı ile Android saf Kotlin çıkarımı arasındaki tutarlılığı doğrulamak için `beklenen_skorlar_50.csv` veri setindeki 50 örnek mesaj test edilmiştir.
* **Maksimum Mutlak Hata:** $\Delta P < 0.00005$
* **Sözlük Kapsama Oranı:** %100 (12.038 / 12.038 özellik)
* Elde edilen sonuçlar, mobil uygulamanın Python modeliyle birebir aynı matematiksel kararları ürettiğini kanıtlamıştır.

### 3.2. Uçtan Uca 18 Senaryo Değerlendirmesi
Gerçek hayatta karşılaşılabilecek tüm meşru ve zararlı varyasyonları içeren 18 senaryoluk etiketli demo mesaj seti sistemden geçirilmiştir:

| Metrik | Elde Edilen Değer | Hedef Değer | Durum |
|---|:---:|:---:|:---:|
| Toplam Senaryo | 18 | 18 | Tamamlandı |
| Doğru Pozitif (TP - Zararlı/Şüpheli Mesajlar) | 10 | 10 | %100 Başarı |
| Doğru Negatif (TN - Meşru Mesajlar) | 8 | 8 | %100 Başarı |
| Yanlış Pozitif (FP - Meşru Mesajın Engellenmesi) | **0** | 0 | Mükemmel |
| Yanlış Negatif (FN - Zararlı Mesajın Sızması) | **0** | 0 | Mükemmel |
| Doğruluk Oranı (Accuracy) | **%100.00** | > %95.0 | Başarılı |
| Ortalama Çıkarım Süresi (Model Inference) | **3.8 ms** | < 10.0 ms | Hedefin Üzerinde |

### 3.3. Donanım ve Çalışma Zamanı Performansı (Samsung Galaxy M34)
Uygulama, Android 14 işletim sistemine sahip fiziksel Samsung Galaxy M34 cihazında çalıştırılmış ve Logcat izleme araçlarıyla incelenmiştir:
* **Model Açılış Yükleme Süresi:** 1.499 ms (Tek seferlik assets ayrıştırması)
* **Tek Bir SMS Çıkarım Süresi:** 2 – 8 ms
* **Bellek (RAM) Kullanımı:** ~45 MB sabit tüketim (Ekstra çalışma zamanı motoru bulunmadığı için son derece hafiftir)
* **Veri Gizliliği Denetimi:** Logcat kayıtları incelenmiş; SMS metni, kişisel veriler veya gönderici bilgisine dair hiçbir sızıntının olmadığı, yalnızca istatistiki sayıların loglandığı doğrulanmıştır.

---

## 4. TARTIŞMA VE KARŞILAŞILAN ZORLUKLAR
1. **Android Regex Motoru Uyumluluğu:** Java standart kütüphanesindeki `Pattern.UNICODE_CHARACTER_CLASS` bayrağının Android libcore çalışma zamanında desteklenmediği tespit edilmiş; token çıkarıcı `[a-zçğıöşü0-9_]{2,}` deseniyle optimize edilerek çökme riski tamamen ortadan kaldırılmıştır.
2. **Birim Test Ortamında JSON Ayrıştırma:** Android SDK'sının birim testlerde sunduğu stub `org.json.JSONObject` sınıfı yerine, projede zaten mevcut olan Gson kütüphanesinin akış tabanlı ayrıştırıcısına geçilerek testlerin bağımsız çalışması sağlanmıştır.
3. **Resmi Beyaz Liste Bağlantıları:** URL içeren meşru mesajların karantinaya yükseltilmesini dengelemek amacıyla resmi kamu alan adları (`.gov.tr`) için beyaz liste kuralı entegre edilmiş ve Yanlış Pozitif (FP) oranı 0'a düşürülmüştür.

---

## 5. SONUÇ VE GELECEK ÇALIŞMALAR
Beşinci hafta itibarıyla projenin tüm yazılımsal hedefleri eksiksiz biçimde gerçekleştirilmiştir. Modelin hafifletilerek saf Kotlin ile cihaza gömülmesi, sunucu bağımlılığını en aza indirmiş ve gizlilik odaklı mimarinin başarısını kanıtlamıştır.

Önümüzdeki süreçte sistemin son kullanıcı geri bildirimleriyle test edilmesi, farklı cihaz modellerinde pil tüketim profilinin çıkarılması ve proje nihai sunum hazırlıklarının tamamlanması planlanmaktadır.

---

## 6. KAYNAKLAR
[1] M. Salman, M. Ikram, and M. A. Kaafar, “Investigating evasive techniques in SMS spam filtering: A comparative analysis of machine learning models,” *IEEE Access*, vol. 12, pp. 24306–24324, 2024.  
[2] Y. Li, R. Zhang, W. Rong, and X. Mi, “SpamDam: Towards privacy-preserving and adversary-resistant SMS spam detection,” *arXiv:2404.09481*, 2024.  
[3] A. R. K. Verma and S. Sadana, “Textual, non-textual, and hybrid feature engineering for SMS spam classification,” *IEEE Access*, vol. 13, pp. 176901–176914, 2025.  
[4] Scikit-learn Developers, “TfidfVectorizer and LogisticRegression Documentation,” *scikit-learn.org*, 2024.  
[5] Android Open Source Project, “Architecture Components: Room Database and WorkManager Guides,” *developer.android.com*, 2026.

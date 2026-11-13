# Gereksinim İzlenebilirlik Matrisi ve Uçtan Uca Değerlendirme Raporu

**Proje:** İstenmeyen SMS Mesajlarını Otomatik Sınıflandırma Sistemi  
**Ekip:** VaultTech (Taha BAHADIR - Yapay Zekâ & Karar Motoru, Arda Yusuf ÖZTEMÜR - Backend & Mimari, Muhammed KANCA - Mobil Arayüz)  
**Doküman Sürümü:** 1.2 (Güncel Sistem Durumu ve Tam Entegrasyon)  
**Tarih:** 13 Kasım 2026  

---

## 1. Giriş ve Amaç

Bu doküman, "İstenmeyen SMS Mesajlarını Otomatik Sınıflandırma Sistemi" projesinin gereksinim analiz raporunda (`docs/Gereksinim_Analizi_ve_Paydaslar_Raporu_VaultTech_Guncel.docx`) tanımlanan tüm fonksiyonel (`FR-01` .. `FR-09`) ve fonksiyonel olmayan (`NFR-01` .. `NFR-07`) gereksinimlerin; mimari bileşenler, kod dosyaları ve birim/entegrasyon/uçtan uca testler ile olan birebir izlenebilirliğini (Traceability) ortaya koymaktadır.

---

## 2. Gereksinim İzlenebilirlik Matrisi (RTM)

### 2.1 Fonksiyonel Gereksinimler (Functional Requirements)

| Gereksinim ID | Gereksinim Tanımı | Mimari Katman / Modül | İlgili Kod Dosyaları | Doğrulama & Test Dosyaları | Durum |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **FR-01** | **Gönderen Durumlarının Yönetimi**<br>Kullanıcının veya sistemin göndericileri İzinli (Whitelist), Engelli (Blacklist) ve Karantina olarak yönetebilmesi. | Android Client / Room DB & Server Domain Rules | `android/.../data/repository/SenderRepository.kt`<br>`android/.../data/local/SenderCacheDao.kt`<br>`server/.../domain/SenderRiskRules.java` | `SenderRepositoryTest.kt`<br>`SenderRiskRulesTest.java`<br>`AddSignalServiceTest.java` | ✅ Tamamlandı |
| **FR-02** | **Gelen SMS'lerin Algılanması**<br>Telefona gelen SMS'lerin anlık olarak arka planda yakalanması ve filtreleme sürecine yönlendirilmesi. | Android Client / OS Receiver Katmanı | `android/.../receiver/SmsReceiver.kt`<br>`android/app/src/main/AndroidManifest.xml` | `SmsReceiverTest.kt` | ✅ Tamamlandı |
| **FR-03** | **Mesajların Risk Sınıflandırması**<br>Kademeli Karar Hattı (İzinli Liste -> Engelli Liste -> Anahtar Kelime Kuralı -> Saf Kotlin ML Motoru) ile Güvenli, Şüpheli ve Tehlikeli olarak puanlanması. | Android Client / Hibrit Karar Motoru & AI Pipeline | `android/.../filtering/SmsFilterEngine.kt`<br>`android/.../ml/SmsClassifier.kt`<br>`android/.../ml/Tokenizer.kt`<br>`ai/export_model_to_kotlin.py` | `DemoMesajSeti18EvaluationTest.kt`<br>`YanlisPozitif40EvaluationTest.kt` | ✅ Tamamlandı |
| **FR-04** | **Mesaj İçeriğinin Cihazda Saklanması**<br>Filtrelenen mesajların içerik, gönderen, risk seviyesi, tespit nedeni ve zaman damgasıyla yerel veritabanında saklanması. | Android Client / Room Veritabanı | `android/.../data/local/FilteredMessageEntity.kt`<br>`android/.../data/local/FilteredMessageDao.kt`<br>`android/.../data/repository/FilteredMessageRepository.kt` | `FilteredMessageRepositoryTest.kt`<br>`AppDatabaseTest.kt` | ✅ Tamamlandı |
| **FR-05** | **Gönderen Bilgilerinin Kaydedilmesi**<br>Sunucudan sorgulanan veya kullanıcı tarafından işaretlenen gönderici risk durumlarının yerel önbelleğe yazılması. | Android Client / Local Cache | `android/.../data/local/SenderCacheEntity.kt`<br>`android/.../data/local/SenderCacheDao.kt`<br>`android/.../data/repository/SenderRepository.kt` | `SenderRepositoryTest.kt` | ✅ Tamamlandı |
| **FR-06** | **Gönderen Bilgilerinin Silinmesi**<br>Kullanıcının tekil bir gönderici kuralını silmesi veya tüm gönderici önbelleğini sıfırlayabilmesi. | Android Client / UI & Repository | `android/.../data/local/SenderCacheDao.kt` (`deleteSender`, `clearAll`)<br>`android/.../data/repository/SenderRepository.kt`<br>`android/.../ui/MessagesViewModel.kt` | `SenderRepositoryTest.kt` | ✅ Tamamlandı |
| **FR-07** | **Karantina Yönetimi**<br>Şüpheli (karantina) sınıfına giren mesajların kullanıcıya maskeli/izole sunulması ve 'Güvenli Olarak İşaretle' veya 'Engelle' opsiyonu sağlanması. | Android Client / Jetpack Compose UI & Karar Hattı | `android/.../filtering/SmsFilterDecision.kt` (`SUSPICIOUS`)<br>`android/.../ui/views/MessagesView.kt`<br>`android/.../ui/MessagesViewModel.kt` | `SmsFilterEngineTest.kt`<br>`DemoMesajSeti18EvaluationTest.kt` | ✅ Tamamlandı |
| **FR-08** | **Bildirim Gönderimi**<br>Gelen SMS şüpheli veya engellenmiş olarak sınıflandırıldığında kullanıcıya Android bildirim kanalı üzerinden güvenlik uyarısı verilmesi. | Android Client / Notification Manager | `android/.../notification/NotificationHelper.kt`<br>`android/.../receiver/SmsReceiver.kt`<br>`android/.../MainActivity.kt` | `NotificationHelperTest.kt`<br>`SmsReceiverTest.kt` | ✅ Tamamlandı |
| **FR-09** | **CSV İçe ve Dışa Aktarma**<br>Kullanıcının gönderen kurallarını CSV formatında dışa aktarabilmesi (`exportSendersToCsv`) ve hazır listeleri içe aktarabilmesi (`importSendersFromCsv`). | Android Client / Veri Taşınabilirliği | `android/.../data/repository/SenderRepository.kt`<br>`android/.../ui/MessagesViewModel.kt`<br>`android/.../ui/views/SendersView.kt` | `SenderRepositoryTest.kt` | ✅ Tamamlandı |

---

### 2.2 Fonksiyonel Olmayan Gereksinimler (Non-Functional Requirements)

| Gereksinim ID | Kriter & Hedef | Gerçekleşen Mimari & Tasarım | Doğrulama & Başarım Ölçümü | Durum |
| :--- | :--- | :--- | :--- | :---: |
| **NFR-01** | **Performans:** SMS başına karar süresinin < 10 ms olması. | Saf Kotlin TF-IDF Naive Bayes/Lojistik Regresyon çıkarım motoru; JNI veya dış bağımlılık olmadan doğrudan bellek içi matris çarpımı. | Ortalama karar süresi: **0.0012 ms** (1.2 mikrosaniye). Hedefin 8000 katı hız. | ✅ Karşılandı |
| **NFR-02** | **Gizlilik ve Güvenlik:** SMS içeriklerinin cihaz dışına asla çıkmaması (On-Device Processing). | Karar motoru tamamen cihaz üstünde çalışır. Sunucuya yalnızca anonim gönderici başlığı (hash/ID) sorulabilir; SMS gövdesi sıfır sızıntı ile yerel Room DB'de kalır. | Mimari denetim, ağ trafiği paket analizi ve sıfır dış API SMS çağrısı ile kanıtlandı. | ✅ Karşılandı |
| **NFR-03** | **Güvenilirlik ve Düşük Yanlış Pozitif (FP):** Meşru bankacılık, kargo ve 2FA mesajlarının yanlışlıkla engellenmemesi. | Kademeli Karar Hattı (Tiered Decision Pipeline) sayesinde B001/B002 izinli başlıkları ve meşru içerikler AI filtresine girmeden güvence altına alınır. | 40 zorlayıcı meşru mesaj testinde **0 Yanlış Pozitif (%0 FP, %100 Meşru Başarı)** elde edildi. | ✅ Karşılandı |
| **NFR-04** | **Kullanılabilirlik:** Kullanıcı dostu ve sezgisel mobil arayüz. | Jetpack Compose ve Material Design 3 tabanlı 3 sekmeli yapı: Mesajlar (filtreleme listesi ve karantina), Gönderenler (kural ve CSV yönetimi), Test (canlı simülasyon). | Kullanılabilirlik kontrolleri ve responsive Compose bileşenleri ile doğrulandı. | ✅ Karşılandı |
| **NFR-05** | **Taşınabilirlik ve Uyumluluk:** Farklı Android sürümlerinde sorunsuz çalışma. | `minSdkVersion: 24` (Android 7.0 Nougat) ve `targetSdkVersion: 34` (Android 14). Modern bildirim kanalları ve çalışma zamanı izinleri (`POST_NOTIFICATIONS`, `RECEIVE_SMS`). | Android 7.0 - 14 uyumluluk testleri ve Manifest yapılandırması tamamlandı. | ✅ Karşılandı |
| **NFR-06** | **Kaynak Tüketimi ve Verimlilik:** Düşük bellek ve pil tüketimi. | Ağır framework'ler (TensorFlow Lite / PyTorch Mobile) yerine saf Kotlin matematiksel sınıflandırıcı (< 1 MB APK ek yükü, sıfır arka plan CPU ısınması). | APK boyut analizi ve bellek profilleme ile doğrulandı. | ✅ Karşılandı |
| **NFR-07** | **Bakım ve Genişletilebilirlik:** Modüler, test edilebilir mimari. | Clean Architecture, MVVM deseni, Repository Pattern, Spring Boot 3.3.4 Hexagonal / Ports & Adapters mikroservis mimarisi. | Kapsamlı birim testleri (JUnit 5, MockK, Spring Boot Test) ile doğrulandı. | ✅ Karşılandı |

---

## 3. Uçtan Uca Değerlendirme ve Test Sonuçları (E2E Test Report)

### 3.1 Test Ortamı ve Altyapısı
- **Mobil Ortam:** Android Studio Hedgehog / Iguana, Android SDK 34, Kotlin 1.9.22, Jetpack Compose Material3.
- **Sunucu Ortamı:** Java 17, Spring Boot 3.3.4, Spring Data JPA, H2 / PostgreSQL, Flyway Migration, Maven.
- **Yapay Zekâ Pipeline:** Python 3.12, Scikit-Learn 1.4, Joblib, Saf Kotlin Kod Üreticisi (`export_model_to_kotlin.py`).
- **Test Kütüphaneleri:** JUnit 5, AssertJ, Mockito, Kotlin Test.

---

### 3.2 Test Senaryoları ve İcra Özeti

#### A. Cihaz İçi Kademeli Karar Hattı ve Yanlış Pozitif Testleri
1. **Demo Mesaj Seti (18 Senaryo) Değerlendirmesi (`DemoMesajSeti18EvaluationTest.kt`):**
   - 18 farklı karmaşık senaryo (bahis, oltalama, sahte kargo, resmi banka dekontu, e-devlet şifresi, B-kodu olan ve olmayan göndericiler) test edildi.
   - **Sonuç:** 18/18 senaryoda beklenen karar (%100 başarı) üretildi.

2. **Zorlayıcı 40 Meşru Mesaj Seti Değerlendirmesi (`YanlisPozitif40EvaluationTest.kt`):**
   - Banka OTP şifreleri, kredi kartı harcama bildirimleri, kargo takip linkleri, hastane randevuları ve fatura hatırlatmaları içeren 40 adet gerçek dünya meşru mesajı test edildi.
   - **Sonuç:** 40 mesajın 40'ı da `SAFE` olarak sınıflandırıldı. Yanlış Pozitif Oranı: **%0.0**.

#### B. Sunucu Uçtan Uca Entegrasyon Testleri
1. **Gönderen Risk Sorgulama (`POST /api/v1/senders/lookup`):**
   - Bilinen spam gönderici başlıkları, nötr başlıklar ve dinamik risk skorları test edildi.
2. **Gönderen Risk Sinyali Kaydı (`POST /api/v1/senders/signals`):**
   - 12 farklı senaryo (`SenderControllerTest.java`, `SenderRiskRulesTest.java`, `AddSignalServiceTest.java`) icra edildi.
   - Puan sınırlandırmaları (0.0 - 1.0 aralığı), geçersiz gönderici doğrulama (validation) ve 400 Bad Request istisna yönetimi test edildi.
   - **Sonuç:** 34/34 birim ve entegrasyon testi eksiksiz geçti.

#### C. Mobil Veri ve CSV Yönetimi Testleri (İP 3, 4, 6)
1. **CSV Dışa Aktarım:** `SenderRepository.exportSendersToCsv` metodu ile önbellekteki kurallar standart `sender;riskScore;riskLevel;isFinalized;cachedAt` formatında başarıyla diske yazıldı.
2. **CSV İçe Aktarım:** `SenderRepository.importSendersFromCsv` metodu ile harici CSV dosyasından kurallar hatasız şekilde ayrıştırılarak Room veritabanına aktarıldı.
3. **Kural Temizleme:** Tekil gönderici silme ve toplu önbellek sıfırlama işlevleri doğrulandı.

---

## 4. Sonuç ve Kabul Durumu

| İş Paketi / Bileşen | Sorumlu | Durum |
| :--- | :--- | :---: |
| **İP 1: AI Servisi & Alternatif Model Karşılaştırması** | Taha BAHADIR | ✅ Tamamlandı (`849f7ff`) |
| **İP 2: Sunucu Sinyal Uç Noktası & Risk Kuralları** | Arda Yusuf ÖZTEMÜR | ✅ Tamamlandı (`80c74a4`) |
| **İP 3: Android SMS Alıcı & Bildirim Yönetimi** | Muhammed KANCA / Takım | ✅ Tamamlandı (`da01153`, temizlendi) |
| **İP 4: Android UI & Karar Hattı Entegrasyonu** | Muhammed KANCA / Takım | ✅ Tamamlandı (`9078c65`) |
| **İP 5: 40 Meşru Mesaj Yanlış Pozitif Test Paketi** | Taha BAHADIR | ✅ Tamamlandı (`c6005b2`) |
| **İP 6: CSV İçe/Dışa Aktarım & Silme Yönetimi & RTM** | Tüm Ekip | ✅ Tamamlandı (`af10afa`, `b401ba4`, düzeltildi) |

Tüm fonksiyonel ve fonksiyonel olmayan gereksinimler eksiksiz karşılanmış, projenin bütünlüğü ve mimari kalitesi korunarak doğrulanmıştır.

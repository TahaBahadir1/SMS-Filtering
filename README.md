# SMS-Filtering — İstenmeyen SMS Mesajlarını Otomatik Sınıflandırma Sistemi

**Ekip:** VaultTech (Fırat Üniversitesi — Bilgisayar Mühendisliği Tasarım Projesi)  
**Mimari:** Gizlilik Odaklı Kademeli Karar Mimarisi (On-Device ML + Kural Motoru + Anonim Gönderen Havuzu)

---

## 1. Proje Özeti ve Gizlilik İlkesi

SMS-Filtering, Android cihazlara gelen SMS mesajlarını **metin içeriğini hiçbir dış sunucuya göndermeden** tamamen cihaz üzerinde analiz eden, kademeli karar hattı ve saf Kotlin makine öğrenmesi motoruyla (TF-IDF + Lojistik Regresyon) **İzin Ver**, **Karantina** veya **Engelle** aksiyonlarını otomatik uygulayan bir SMS güvenlik sistemidir.

- **Sıfır İçerik Sızıntısı:** SMS metni, telefon rehberi veya kişisel veriler sunucuya asla gönderilmez ve uygulama/sunucu loglarına yazılmaz.
- **Cihaz İçi Saf Kotlin Çıkarım:** TensorFlow Lite veya ONNX gibi ağır C++ çalışma zamanı bağımlılıkları olmadan, 12.038 özellikli model (`sms_model_assets.json`) saf Kotlin ile milisaniyeler içinde çalıştırılır.
- **Kademeli Karar Hattı (`docs/karar-hatti.md`):**
  $$\text{Puan} = \text{Gönderen Skoru (kayıt yoksa } 0.30\text{)} + 0.2 \times k + (\text{eğer } P \ge 0.50 \text{ ise } 0.4 \times P\text{, değilse } 0.0)$$
  - `Puan ≥ 0.80` → **Engelle**
  - `0.50 ≤ Puan < 0.80` → **Karantina**
  - `Puan < 0.50` → **İzin Ver** (resmi `.gov.tr` dışındaki bağlantılar güvenlik gereği Karantina'ya yükseltilir)

---

## 2. Depo Yapısı

```text
SMS-Filtering/
├── ai/                 # Python model eğitimi, karşılaştırma, adversarial test ve veri setleri
├── android/            # Kotlin Android uygulaması (Room, Retrofit, WorkManager, saf Kotlin ML, UI)
├── server/             # Java / Spring Boot modüler monolit (Hexagonal mimari, Flyway, PostgreSQL, Rate Limit)
├── docs/               # API sözleşmesi, karar hattı belgesi ve Gereksinim İzlenebilirlik Matrisi (RTM)
└── docker-compose.yml  # PostgreSQL + Spring Boot sunucu konteyner kurulumu
```

---

## 3. Kurulum ve Çalıştırma Rehberi

### 3.1. Sunucu ve PostgreSQL (`server/`)

**Docker Compose ile tek komutla ayağa kaldırma:**
```bash
docker compose up --build -d
```
Sunucu `http://localhost:8080` adresinde başlar ve Flyway migrasyonlarını (`V1`–`V4`) otomatik uygular. Sağlık kontrolü:
```bash
curl http://localhost:8080/api/v1/health
```

**Sunucu birim, güvenlik, yük ve entegrasyon testlerini çalıştırma:**
```bash
cd server
./mvnw test        # Linux / macOS
.\mvnw.cmd test    # Windows PowerShell
```

> **İsteğe Bağlı Ortam Değişkenleri:** `RATE_LIMIT_ENABLED` (varsayılan `true`), `RATE_LIMIT_LOOKUP_PER_MIN` (`60`), `RATE_LIMIT_SIGNAL_PER_MIN` (`20`), `SIGNAL_API_KEY` (dolu ise `/senders/signals` için `X-API-Key` zorunlu kılar).

### 3.2. Android İstemci (`android/`)

1. `android/local.properties.example` dosyasını `android/local.properties` olarak kopyalayın ve sunucu adresini tanımlayın:
   ```properties
   sdk.dir=C\:\\Users\\<KULLANICI>\\AppData\\Local\\Android\\Sdk
   SERVER_BASE_URL=https://<sunucu-veya-tunel-adresi>/api/v1/
   ```
   *(Not: `local.properties` `.gitignore` içerisindedir, Git’e eklenmez.)*

2. Birim testlerini çalıştırın ve Debug / Release APK derleyin:
   ```bash
   cd android
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ./gradlew assembleRelease
   ```

3. Fiziksel cihaza yüklemek için:
   ```bash
   adb install -r android/app/build/outputs/apk/debug/app-debug.apk
   ```

### 3.3. Yapay Zeka ve Değerlendirme Betikleri (`ai/`)

```bash
cd ai
pip install -r requirements.txt
python spam_classifier.py          # Model eğitimi ve JSON ağırlık dışa aktarımı
python model_comparison.py         # Lojistik Regresyon, Naive Bayes, LinearSVC, Random Forest kıyaslaması
python adversarial_evaluation.py   # 40 mesajlık filtre atlatma (adversarial) dayanıklılık testi
```

---

## 4. Sunucu REST API Özeti

Detaylı şema ve hata yanıtları için bkz. [`docs/api-sozlesmesi.md`](docs/api-sozlesmesi.md).

| Metod | Uç Nokta | Açıklama |
|---|---|---|
| `GET` | `/api/v1/keywords?version={v}` *(ve `/api/v2/keywords`)* | Risk kategorisi ve konu kelime listelerini sürüm farkına göre senkronize eder |
| `POST` | `/api/v1/senders/lookup` *(ve `/api/v2/senders/lookup`)* | Gönderenin küresel risk skorunu ve kesinleşme durumunu sorgular |
| `POST` | `/api/v1/senders/signals` *(ve `/api/v2/senders/signals`)* | SMS metni olmadan anonim gönderen risk sinyali kaydeder (`manuel`, `yapay_zeka`, `buyuk_veri`) |
| `GET` | `/api/v1/health` *(ve `/api/v2/health`)* | PII içermeyen çalışma süresi ve anonim işlem sayaçlarını döner |

---

## 5. Test ve Performans Özeti

| Değerlendirme Seti / Test | Sonuç |
|---|---|
| Python vs. Saf Kotlin Model Doğrulaması (50 Referans SMS) | 50 / 50 eşleşme ($\Delta P < 0.00005$) |
| 18 Senaryoluk Uçtan Uca Demo Mesaj Seti | 18 / 18 (%100 Doğruluk, 0 FP, 0 FN) |
| 40 Zorlayıcı Meşru Sınır Mesajı (Banka/Kargo/Operatör/Kamu) | 40 / 40 (%0,0 Yanlış Pozitif) |
| 40 Filtre Atlatma (Adversarial: Leetspeak, Unicode, Noktalama, Hibrit) | 40 / 40 (%100 Yakalama; eski ön işleme %32,5 → +%67,5) |
| Sunucu Eşzamanlı Yük & Rate-Limit Testi (60 istek, 8 thread) | 40× `200 OK`, 20× `429`, 0× `5xx` (p50: 14,05 ms, p95: 69,10 ms) |
| Android Çevrimdışı Dayanıklılık (Sunucu Kapalı, 25 Ardışık SMS) | 25 / 25 kayıpsız yerel kayıt, 0 çökme |

---

## 6. Ekip Üyeleri (VaultTech)

| Öğrenci No | Adı Soyadı | Rol |
|---|---|---|
| 225260061 | Arda Yusuf ÖZTEMÜR | Mimari Tasarım ve Backend |
| 235260047 | Taha BAHADIR | AI Servisi |
| 235260147 | Muhammed KANCA | Mobil Uygulama |

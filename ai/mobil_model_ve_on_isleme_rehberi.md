# Mobil Çıkarım (On-Device Inference) ve Ön İşleme Kılavuzu

Bu belge, Android istemcisinde Python Scikit-Learn ile birebir aynı sonucu verecek yerel çıkarım (on-device inference) ve metin ön işleme adımlarını tanımlar.

---

## 1. Ön İşleme (Preprocessing) Uyuşmazlığı ve Karar

### Kritik Karar:
Model Türkçe karakterlerle (`ş, ı, ğ, ç, ü, ö`) eğitilmiştir. Sözlükteki 12.038 kelimenin içinde Türkçe karakterler korunmuştur.
Bu nedenle metinler **kesinlikle ASCII'ye dönüştürülmemelidir** (`ş` -> `s`, `ı` -> `i` yapılmamalıdır). ASCII'ye dönüştürülürse model kelimeleri eşleştiremez.

### Python'daki `clean_text` Mantığı:
1. **Küçük Harf (Türkçe Locale):** `text.replace('İ', 'i').replace('I', 'ı').lower()`
2. **Harf Arası Boşluk Temizleme (Linkler Hariç):**
   Spam göndericilerin filtreleri atlatmak için uyguladığı `p  a  r  a` -> `para` hilesini temizler.
   Regex: `\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}\b)` (5 iterasyon)
3. **Harf Uzatma Temizleme:**
   `fırsaaaat` -> `fırsat`, `kazaaaan` -> `kazan`
   Regex: `(.)\1{2,}` -> `\1`

### Kotlin Karşılığı (`SmsCleaner.kt`):
```kotlin
package com.smsfiltering.client.util

import java.util.Locale

object SmsCleaner {
    private val TURKISH_LOCALE = Locale("tr", "TR")
    private val SPACE_REGEX = Regex("""\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}\b)""")
    private val REPEAT_REGEX = Regex("""(.)\1{2,}""")

    fun cleanText(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. Türkçe uyumlu küçük harf
        var text = rawText.replace('İ', 'i').replace('I', 'ı').lowercase(TURKISH_LOCALE)

        // 2. Link değilse harf arası boşlukları birleştir
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            repeat(5) {
                text = text.replace(SPACE_REGEX, "$1")
            }
        }

        // 3. Harf uzatmalarını sadeleştir
        text = text.replace(REPEAT_REGEX, "$1")

        return text.trim()
    }
}
```

---

## 2. Mobil Model Dosyası (`sms_model_assets.json` / `.json.gz`)

- Dosya boyutu sıkıştırılmamış: **695 KB**
- GZIP sıkıştırılmış (`.json.gz`): **151 KB**
- Android projesinde `app/src/main/assets/` dizinine yerleştirilir.

### JSON Şeması:
```json
{
  "version": "1.0",
  "algorithm": "LogisticRegression",
  "feature_count": 12038,
  "ngram_range": [1, 2],
  "intercept": -0.950466,
  "features": {
    "kredi": { "i": 4512, "idf": 3.124567, "w": 0.451234 },
    "kredi karti": { "i": 4513, "idf": 4.512345, "w": 0.891234 }
  }
}
```

---

## 3. Kotlin Çıkarım Algoritması (Scikit-Learn Uyumlu)

Scikit-learn `TfidfVectorizer(norm='l2', ngram_range=(1,2))` ve `LogisticRegression` çıktısı tam olarak şu adımlarla hesaplanır:

1. **Tokenize & N-gram Çıkarımı:**
   Temizlenen metin boşluklardan bölünür (`tokens`).
   Unigramlar (`tokens[i]`) ve Bigramlar (`tokens[i] + " " + tokens[i+1]`) toplanır.
   Her terimin metin içi frekansı (Term Frequency - $TF$) sayılır.

2. **TF-IDF Değeri:**
   $$\text{TF-IDF}(t) = TF(t) \times IDF(t)$$

3. **L2 Normalizasyonu (Önemli!):**
   $$\text{norm} = \sqrt{\sum (\text{TF-IDF}_t)^2}$$
   $$X_{\text{norm}}(t) = \frac{\text{TF-IDF}(t)}{\text{norm}}$$

4. **Lojistik Regresyon Skoru (Log-odds $z$):**
   $$z = \text{intercept} + \sum \left( X_{\text{norm}}(t) \times W(t) \right)$$

5. **Spam Olasılığı ($P$):**
   $$P(\text{spam}) = \frac{1}{1 + e^{-z}}$$

---

## 4. Karar Kuralına Entegrasyon (`docs/karar-hatti.md` Uyumu)

- Eğer $P(\text{spam}) \ge 0.50$ ise sistem risk puanına **$+ (0.4 \times P)$** eklenir.
- Eğer $P(\text{spam}) < 0.50$ ise modele bağlı ekleme yapılmaz ($0.0$).

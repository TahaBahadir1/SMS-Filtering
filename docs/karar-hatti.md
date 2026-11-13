# Karar hattı: Gizlilik Odaklı Kademeli Karar Mimarisi

SMS içeriği sunucuya gönderilmez. Adımlar sırayla işletilir, karar verilen adımda hat sona erer.

## Adımlar

1. **Kullanıcı kararı:** Gönderen için kullanıcının verdiği bir durum varsa (sender_status, kaynak: kullanıcı) uygulanır ve hat biter. Kullanıcı kararında URL kuralı uygulanmaz.
2. **Yerel önbellek:** Sunucudan gelmiş ve 7 günden eski olmayan cevap varsa kullanılır, yoksa 3. adıma geçilir.
3. **Sunucu havuzu:** POST /api/v1/senders/lookup çağrılır, cevap önbelleğe yazılır.
   - Tehlikeli gönderen (riskLevel = riskli VE isFinalized = true) doğrudan engellenir, hat biter.
   - Diğer herkes (risksiz, orta riskli, kesinleşmemiş, bilinmeyen) içerik analizine girer. Sebep: alfanümerik gönderen adları taklit edilebilir.
   - Sunucuya ulaşılamazsa gönderen "bilinmiyor" sayılır ve devam edilir.
4. **İçerik analizi (cihazda):**
   - k: Mesajda tetiklenen risk işareti sayısı (IBAN, PIN, şifre, link, acil ödeme). Mesaj ve kelime listesi aynı normalleştirmeden (TurkishTextNormalizer) geçirilir.
   - P: Modelin spam olasılığı (0-1). Model yoksa P tanımsız sayılır.
   - Konu kategorisi (banka, kumar, belediye, kargo, operatör): yalnızca kullanıcıya gösterilir, puana katılmaz.
5. **Puanlama:**
   Puan = gönderen skoru (kayıt yoksa 0.3) + 0.2 × k + (P tanımlı ve P ≥ 0.5 ise 0.4 × P, değilse 0), en çok 1.0
6. **Karar:** Puan ≥ 0.8 engelle; 0.5 ≤ puan < 0.8 karantina; puan < 0.5 izin ver.
7. **URL kuralı:** Mesaj bağlantı içeriyorsa ve karar "izin ver" ise karantinaya yükseltilir. Bağlantılar hiçbir dış servise sorgulanmaz.
8. **Kayıt ve gösterim:** İzin verilen mesaj gelen kutusuna yazılır. Karantina ve engellenen mesajlar karar nedeni, gönderen risk durumu ve konu etiketiyle messages tablosuna kaydedilir; mesaj metni arayüzde varsayılan olarak gizlidir.

## Sabitler

| Ad | Değer |
|---|---|
| Kayıt yoksa gönderen skoru | 0.3 |
| Risk işareti katsayısı | 0.2 |
| Model katkısı | 0.4 × P (yalnızca P ≥ 0.5) |
| Engelle eşiği | 0.8 |
| Karantina eşiği | 0.5 |
| Önbellek süresi | 7 gün |
| Sunucudaki risk düzeyleri | < 0.30 risksiz, 0.30-0.70 orta riskli, ≥ 0.70 riskli |

## Örnekler (beklenen karar)

| Mesaj | Gönderen | k | P | Puan | Karar |
|---|---|---|---|---|---|
| Temiz mesaj | Bilinmeyen | 0 | 0.05 | 0.30 | İzin ver |
| Reklam / kumar | Bilinmeyen | 0 | 0.90 | 0.66 | Karantina |
| IBAN + link + acil ödeme | Bilinmeyen | 3 | 0.99 | 1.00 | Engelle |
| Banka doğrulama kodu | BANKA-X (0.05) | 1 | 0.10 | 0.25 | İzin ver |
| Taklit BANKA-X, link + IBAN | BANKA-X (0.05) | 2 | 0.97 | 0.84 | Engelle |
| Doğrulama kodu ("onay kodu" + "şifre") | Bilinmeyen | 2 | 0.15 | 0.70 | Karantina |
| Kargo bildirimi, link | Bilinmeyen | 1 | 0.20 | 0.50 | Karantina |
| Temiz mesaj | KREDI-DESTEK (0.55) | 0 | 0.10 | 0.55 | Karantina |

## Modelsiz çalışma

Model dosyası yoksa ya da çıkarım başarısızsa P tanımsız sayılır ve model katkısı 0 olur. Diğer adımlar aynen çalışır.


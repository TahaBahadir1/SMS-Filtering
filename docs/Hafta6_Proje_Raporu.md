# T.C. FIRAT ÜNİVERSİTESİ
## MÜHENDİSLİK FAKÜLTESİ - BİLGİSAYAR MÜHENDİSLİĞİ BÖLÜMÜ
### BİLGİSAYAR MÜHENDİSLİĞİ TASARIM DERSİ PROJESİ KAPSAMINDA TAKIM ÇALIŞMASI PROJESİ
## 6. HAFTA PROJE İLERLEME VE UÇTAN UCA DEĞERLENDİRME RAPORU

---

### TAKIM KAPTANI GERİ BİLDİRİM FORMU

#### Bölüm 1 - Takım Bilgileri
| Ekip Adı: | VaultTech |
| :--- | :--- |
| **Takım Kaptanı:** | Muhammed KANCA |
| **Ekip Üyeleri:** | Taha BAHADIR, Arda Yusuf ÖZTEMÜR |
| **Proje Başlığı:** | İstenmeyen SMS Mesajlarını Otomatik Sınıflandırma Sistemi |

#### Bölüm 2 - Ekip İçi Çalışma Süreci
| Kriter | √ | X |
| :--- | :---: | :---: |
| Ekip içi iletişim düzenliydi | √ | |
| Görev dağılımı netti | √ | |
| Tüm üyeler sürece aktif katıldı | √ | |
| Toplantılar planlandığı şekilde yapıldı | √ | |
| Zaman yönetimi yeterliydi | √ | |
| Ekip içi anlaşmazlıklar uygun şekilde çözüldü | √ | |

#### Bölüm 3 - Projenin İlerleme Durumu
| Kriter | √ | X |
| :--- | :---: | :---: |
| Problem tanımı netleşti | √ | |
| İş paketleri tanımlandı | √ | |
| Sorumlular belirlendi | √ | |
| Proje başlangıç çıktıları oluşturuldu | √ | |

---

### 1. GİRİŞ

Bu rapor, “İstenmeyen SMS Mesajlarını Otomatik Sınıflandırma Sistemi” projesinin altıncı haftasında gerçekleştirilen; makine öğrenmesi modellerinin karşılaştırmalı analizi (Lojistik Regresyon, Naive Bayes, Linear SVM, Random Forest), 40 mesajlık zorlayıcı meşru sınır seti üzerinde tek başına model ve kademeli karar hattının yanlış pozitif (FP) başarısının ölçülmesi, sunucu tarafında gizlilik odaklı gönderen risk sinyali uç noktasının (`POST /api/v1/senders/signals`) ve kesinleşme kurallarının geliştirilmesi, Android işletim sisteminde gelen SMS'leri arka planda anlık yakalayan `SmsReceiver` (`BroadcastReceiver`) ve bildirim altyapısının kodlanması, kullanıcı gönderen kurallarının CSV formatında içe/dışa aktarımı ile silme mekanizmalarının geliştirilmesi (FR-06, FR-09) ve projenin resmi Gereksinim Analizi Raporu'ndaki tüm fonksiyonel (`FR-01` .. `FR-09`) ile fonksiyonel olmayan (`NFR-01` .. `NFR-07`) gereksinimlerin Gereksinim İzlenebilirlik Matrisi (RTM) kapsamında canlı cihazda doğrulanması çalışmalarını kapsamaktadır.

Beşinci haftada sistemin kademeli karar mimarisi uçtan uca çalışır prototip hâline getirilmiş ve gerçek cihazda temel testleri yapılmıştı. Altıncı haftada ise sistemin modelleme derinliği, endüstriyel güvenilirliği, işletim sistemi seviyesinde canlı SMS yakalama yeteneği, dinamik sunucu geri bildirim döngüsü ve veri taşınabilirliği eksiksiz biçimde tamamlanmıştır. Yapay zekâ tarafında Scikit-Learn ile 4 farklı algoritma aynı veri kümesi üzerinde 5 katlı çapraz doğrulama ile sınanmış; mobil ortamda sıfır ek kütüphane, 94.7 KB model boyutu ve 0.0012 ms çıkarım süresi sağlayan Lojistik Regresyon modelinin üstünlüğü kanıtlanmıştır. Ayrıca bankacılık, kargo ve resmi bildirimlerden oluşan 40 meşru sınır mesajı üzerinde yapılan testlerde, tek başına istatistiksel modellerin %32.5 yanlış alarm üretmesine karşılık, VaultTech'in kademeli karar hattının %0 yanlış pozitif üreterek meşru iletişimi kusursuz koruduğu belgelenmiştir. Sunucu tarafında kullanıcı bildirimlerini SMS metnini asla dışarı çıkarmadan işleyen sinyal API'si 34/34 birim/entegrasyon testiyle doğrulanmış, Android istemcisinde ise gelen her SMS anında filtrelenerek riskli durumlarda kullanıcıya sistem bildirimi iletilebilir hâle getirilmiştir.

#### Amaç ve Hedefler
Bu haftanın temel amacı, geliştirilen SMS filtreleme sisteminin yapay zekâ modelleme güvenilirliğini alternatif algoritmalarla karşılaştırmak, meşru mesajlarda sıfır yanlış alarm (%0 False Positive) hedefini garantilemek, sunucuda gizlilik odaklı risk sinyali toplama altyapısını kurmak, Android'de canlı arka plan SMS dinleme ve bildirim mekanizmalarını hayata geçirmek, gönderen kurallarının CSV ile taşınabilirliğini sağlamak ve tüm fonksiyonel/fonksiyonel olmayan gereksinimleri Gereksinim İzlenebilirlik Matrisi (RTM) üzerinden doğrulamaktır.
- Lojistik Regresyon modelinin alternatif makine öğrenmesi algoritmalarıyla (Multinomial Naive Bayes, Linear SVM, Random Forest) doğruluk, F1-skoru, model boyutu, çıkarım gecikmesi ve mobil çalışabilirlik açılarından 5-katlı çapraz doğrulama ile karşılaştırılması (İP 1).
- 40 mesajlık zorlayıcı meşru SMS seti (`yanlis_pozitif_analizi_40.csv`) üzerinde tek başına çalışan modeller ile kademeli karar hattının yanlış pozitif (FP) performansının kıyaslanması ve meşru gönderen istisnasıyla %0 FP başarısının kanıtlanması (İP 1, İP 5).
- Sunucu tarafında mevcut `AddSignalUseCase` iş mantığının `POST /api/v1/senders/signals` uç noktasıyla dışarı açılması, gizlilik ilkesine uygun olarak (SMS metni kesinlikle kaydedilmeden) risk sinyallerinin işlenmesi ve kesinleşme kurallarının entegrasyon testleriyle doğrulanması (İP 2).
- Android platformunda `Telephony.SMS_RECEIVED` yayınını anlık yakalayan `SmsReceiver` bileşeninin, gerekli çalışma zamanı izinlerinin (`RECEIVE_SMS`, `POST_NOTIFICATIONS`) ve kullanıcıyı uyaran sistem bildirim kanallarının kodlanması (İP 3: FR-02, FR-08).
- Kullanıcının yerel Room veritabanındaki gönderen listesini CSV formatında dışa aktarabilmesi (`exportSendersToCsv`), uygun formattaki listeleri içe aktarabilmesi (`importSendersFromCsv`) ve kayıtlı kuralları silebilmesi (İP 4: FR-06, FR-09).
- 40 meşru sınır mesajının `YanlisPozitif40EvaluationTest.kt` birim testi ile Android karar hattında otomatik sınanması ve tüm meşru mesajların gelen kutusuna güvenle iletildiğinin belgelenmesi (İP 5).
- Resmi Gereksinim Analizi Raporu'ndaki tüm fonksiyonel (`FR-01` .. `FR-09`) ve fonksiyonel olmayan (`NFR-01` .. `NFR-07`) gereksinimlerin canlı cihazda doğrulanarak Gereksinim İzlenebilirlik Matrisi (RTM) ve Uçtan Uca Değerlendirme Raporu olarak sunulması (İP 6).

---

### 2. YÖNTEM

#### 2.1 Alternatif Makine Öğrenmesi Modellerinin Eğitimi ve Karşılaştırma Mimarisi (İP 1)
Dördüncü ve beşinci haftalarda eğitilen Lojistik Regresyon modelinin başarımını akademik standartlarda doğrulamak amacıyla 4 farklı makine öğrenmesi algoritması (Lojistik Regresyon, Multinomial Naive Bayes, Calibrated Linear SVM, Random Forest) aynı 12.038 terimlik sözlük ve TF-IDF öznitelik çıkarımıyla 5-katlı çapraz doğrulama protokolünde eğitilmiştir (`ai/model_comparison.py`). Modeller doğruluk, kesinlik, duyarlılık, F1 skoru, ROC-AUC, disk/RAM boyutu, tekil SMS çıkarım süresi ve mobil cihazda harici bağımlılık olmadan (C++ çalışma zamanı veya TensorFlow Lite gerektirmeksizin) saf Kotlin ile çalışabilirlik kriterlerine göre kıyaslanmıştır.

#### 2.2 Sunucu Gönderen Risk Sinyali Uç Noktası ve Gizlilik Odaklı Geri Bildirim Döngüsü (İP 2)
Spring Boot 3.3.4 mikroservis mimarisinde `POST /api/v1/senders/signals` uç noktası geliştirilmiştir. Bu uç nokta kullanıcı veya operatör bildirimlerini itibar havuzunda birleştirmektedir. NFR-02 (Gizlilik) gereğince SMS metni sunucuya asla gönderilmemekte, yalnızca gönderici başlığı veya numarası işlenmektedir. Gelen sinyaller `SenderRiskRules` sınıfında ağırlıklı hareketli ortalama ile işlenmekte; sinyal sayısı $\ge 5$ ve skor $\ge 0.70$ olduğunda gönderen kesinleşmiş (`isFinalized = true`) riskli gönderen olarak işaretlenmektedir.

#### 2.3 Android Canlı SMS Algılama (`SmsReceiver`) ve Bildirim Yönetimi (İP 3)
Android `Telephony.SMS_RECEIVED` eylemini dinleyen `SmsReceiver` (`BroadcastReceiver`) bileşeni kodlanmıştır. Uygulama kapalıyken veya arka plandayken gelen SMS'ler anında yakalanıp `SmsFilterEngine` kademeli karar hattına iletilmekte; şüpheli veya engellenen mesajlar için `NotificationHelper` üzerinden kullanıcının bildirim çekmecesine anında uyarı iletilmektedir.

#### 2.4 Gönderen Kurallarının CSV İçe/Dışa Aktarımı ve Silme Yönetimi (İP 4)
FR-06 ve FR-09 gereksinimleri uyarınca `SenderRepository` sınıfına `exportSendersToCsv(file: File)` ve `importSendersFromCsv(file: File)` metotları eklenmiştir. Standart şema `sender;riskScore;riskLevel;isFinalized;cachedAt` olarak tanımlanmış, noktalı virgül ve virgül esnekliği ile UTF-8 BOM desteği sağlanmıştır. `SendersView` arayüzüne dışa/içe aktar butonları eklenmiştir.

#### 2.5 Kademeli Karar Hattı ve Meşru Gönderen İstisnası (İP 5)
`SmsFilterEngine` karar hattında birleşik risk puanı şu formülle hesaplanır:
$$\text{Puan} = \text{Gönderen Skoru (varsayılan 0.30)} + 0.2 \times k + (\text{eğer } P \ge 0.50 \text{ ise } 0.4 \times P, \text{ değilse } 0.0)$$
Bankacılık, kargo ve 2FA bildirimlerinde yer alan "şifre", "kod", "onay" gibi kelimelerin yanlış alarma yol açmasını önlemek için **Meşru Gönderen İstisnası** (`isRisksizSender = true`) uygulanmaktadır. Bu sayede doğrulanmış kurumsal göndericilerden gelen mesajlar doğrudan gelen kutusuna yönlendirilir.

---

### 3. PROJE YÖNETİMİ

#### 3.1 Takım Üyeleri Tanımı
| ÜyeNo | Numara | Adı Soyadı | Görevi |
| :---: | :---: | :--- | :--- |
| 1 | 225260061 | Arda Yusuf ÖZTEMÜR | Mimari Tasarım ve Backend |
| 2 | 235260047 | Taha BAHADIR | AI Servisi ve Karar Motoru |
| 3 | 235260147 | Muhammed KANCA | Mobil Uygulama ve Takım Kaptanı |

#### 3.2 Görev Dağılımı (6. Hafta)
| İP No | İş Paketinin Adı ve Hedefleri | Görevlendirilen Takım Üyesi |
| :---: | :--- | :--- |
| **1** | **Alternatif Modellerin Karşılaştırılması ve Yanlış Pozitif Analizi:** Lojistik Regresyon modelinin alternatif makine öğrenmesi algoritmalarıyla (Naive Bayes, SVM, Random Forest) doğruluk, F1-skoru, model boyutu ve mobil çıkarım uygunluğu açısından karşılaştırılması; 40 mesajlık zorlayıcı meşru SMS seti (`yanlis_pozitif_analizi_40.csv`) üzerinde tek başına model ile kademeli karar hattının yanlış alarm (FP) performansının analiz edilmesi. | Taha BAHADIR |
| **2** | **Sunucu Gönderen Sinyali Uç Noktasının ve Geri Bildirim Akışının Geliştirilmesi:** Sunucuda mevcut `AddSignalUseCase` iş mantığının `POST /api/v1/senders/signals` uç noktasıyla dışarı açılması, gizlilik ilkesine uygun olarak (SMS metni gönderilmeden) gönderen risk sinyallerinin kaydedilmesi, kesinleşme (`isFinalized`) kurallarının entegrasyon testleriyle doğrulanması ve API sözleşmesinin güncellenmesi. | Arda Yusuf ÖZTEMÜR |
| **3** | **Canlı SMS Algılama (BroadcastReceiver) ve Bildirim Sisteminin Kodlanması (FR-02, FR-08):** Android'de gelen SMS'leri gerçek zamanlı yakalayan `SmsReceiver` bileşeninin ve gerekli çalışma zamanı izinlerinin (`RECEIVE_SMS`, `POST_NOTIFICATIONS`) eklenmesi; arka planda karar hattından geçen mesajlar için karantina/gelen kutusu bildirimlerinin gösterilmesi. | Muhammed KANCA |
| **4** | **Gönderen Bilgilerinin CSV İçe/Dışa Aktarımı ve Silme Yönetimi (FR-06, FR-09):** Kullanıcının yerel veritabanındaki gönderen listesini CSV formatında dışa aktarabilmesi (export), uygun formattaki CSV dosyalarını içe aktarabilmesi (import) ve kayıtlı gönderenleri silebilmesi için arayüz ve veri katmanı işlevlerinin geliştirilmesi. | Muhammed KANCA ve Arda Yusuf ÖZTEMÜR |
| **5** | **Kademeli Sistemde 40 Meşru Sınır Mesajının Otomatik Testi:** `yanlis_pozitif_analizi_40.csv` veri setindeki banka, kargo, operatör ve resmi kurum mesajlarının Android birim testlerinde karar hattından geçirilerek meşru gönderen koruması ve birleşik puanlama sayesinde yanlış pozitiflerin önlendiğinin doğrulanması. | Taha BAHADIR ve Arda Yusuf ÖZTEMÜR |
| **6** | **Gereksinim İzlenebilirlik Matrisi ve Uçtan Uca Canlı Cihaz Doğrulaması:** Gereksinim Analizi Raporu'ndaki tüm fonksiyonel (`FR-01` - `FR-09`) ve fonksiyonel olmayan (`NFR-01` - `NFR-07`) gereksinimlerin gerçek telefonda canlı SMS ve CSV senaryolarıyla test edilmesi, karşılama durumlarının izlenebilirlik tablosu olarak raporlanması. | Arda Yusuf ÖZTEMÜR, Taha BAHADIR ve Muhammed KANCA |

---

### 4. TEORİ VE SONUÇLAR

#### 4.1 Alternatif Algoritmaların Karşılaştırma Sonuçları
Scikit-Learn üzerinde yapılan 5-katlı çapraz doğrulama sonuçları Tablo 1'de verilmiştir.

##### Tablo 1: 4 Makine Öğrenmesi Algoritmasının Karşılaştırmalı Performans Analizi
| Algoritma | Doğruluk (%) | F1-Skoru (%) | ROC-AUC (%) | Model Boyutu (KB) | Çıkarım (ms) | Tek Başına FP | Kademeli FP | Mobil Çıkarım Uygunluğu |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Lojistik Regresyon (Seçilen Model)** | **99.2** | **99.35** | **99.93** | **94.7** | **0.0012** | 7 / 40 (%17.5) | **0 / 40 (%0.0)** | **Çok Uygun (Saf Kotlin, 0 ek kütüphane)** |
| **Multinomial Naive Bayes** | 96.8 | 97.46 | 99.92 | 376.8 | 0.0018 | 18 / 40 (%45.0) | 0 / 40 (%0.0) | Uygun (Olasılık kalibrasyonu zayıf) |
| **Doğrusal SVM (Calibrated LinearSVC)** | 99.1 | 99.27 | 99.96 | 283.7 | 0.0055 | 9 / 40 (%22.5) | 0 / 40 (%0.0) | Orta (Platt ölçekleme ek yükü) |
| **Random Forest (100 Ağaç)** | 98.2 | 98.53 | 99.96 | 5219.9 | 0.0639 | 0 / 40 (%0.0) | 0 / 40 (%0.0) | Uygun Değil (5.2 MB boyut, yüksek gecikme) |

Lojistik Regresyon, saf Kotlin ortamında tek bir dot-product ve sigmoid ile çalıştığı, harici runtime (TFLite/ONNX) gerektirmediği ve yalnızca 94.7 KB yer kaplayıp 0.0012 ms'de sonuç ürettiği için üretim modeli olarak seçilmiştir.

#### 4.2 40 Zorlayıcı Meşru Sınır Mesajı Testi ve Yanlış Pozitif (FP) Analizi
Banka OTP, FAST, kredi kartı ekstre, kargo takip ve kamu duyurularından oluşan 40 mesajlık zorlayıcı sınır setinde; tek başına çalışan yapay zekâ modelleri "şifre", "onay", "hesap", "link" kelimelerinden ötürü %17.5 - %45.0 aralığında yüksek yanlış alarm (FP) üretmiştir. Ancak VaultTech kademeli karar hattındaki **Meşru Gönderen Koruması** sayesinde 40 mesajın 40'ı da doğrudan gelen kutusuna iletilmiş ve **Yanlış Pozitif Oranı %0'a indirilmiştir**.

##### Tablo 2: Hafta 6 Genel Sistem Başarım, Test ve Gereksinim Doğrulama Özeti
| Performans Ölçütü | Elde Edilen Değer | Açıklama |
| :--- | :---: | :--- |
| **Alternatif Model Karşılaştırması** | 4 Algoritma | Lojistik Regresyon %99.2 doğruluk, 94.7 KB ve 0.0012 ms ile en uygun model seçildi |
| **Zorlayıcı Meşru Sınır Mesajı Seti** | 40 Mesaj | Banka OTP, FAST, kargo takip ve kamu bildirimlerini içeren sınır veri seti |
| **Tek Başına Model Yanlış Pozitif (FP)** | 13 / 40 (%32.5) | Salt NLP modelleri finans ve kargo kelimeleri sebebiyle yüksek hata üretti |
| **Kademeli Karar Hattı Yanlış Pozitif (FP)** | **0 / 40 (%0.0)** | Meşru gönderen koruma kalkanı sayesinde 0 yanlış pozitif (%100 meşru doğruluk) |
| **Sunucu Gönderen Risk Sinyali API'si** | 34 / 34 Başarılı | `POST /api/v1/senders/signals` uç noktası ve kesinleşme kuralları test edildi |
| **Canlı SMS Dinleyici (BroadcastReceiver)** | Aktif & Doğrulandı | `Telephony.SMS_RECEIVED` ve `NotificationHelper` bildirim kanalları canlı test edildi |
| **Gönderen CSV İçe/Dışa Aktarımı & Silme** | FR-06 & FR-09 Başarılı | `exportSendersToCsv` ve `importSendersFromCsv` işlevleri birim testlerle doğrulandı |
| **Gereksinim İzlenebilirlik Kapsamı** | **%100 Karşılandı** | 9 Fonksiyonel (FR-01..09) ve 7 Fonksiyonel Olmayan (NFR-01..07) gereksinim doğrulandı |

---

### 5. PROJE DOSYALARININ BULUNDUĞU LİNK
[https://github.com/TahaBahadir1/SMS-Filtering](https://github.com/TahaBahadir1/SMS-Filtering)

---

### KAYNAKLAR
[1] M. Salman, M. Ikram, and M. A. Kaafar, “Investigating evasive techniques in SMS spam filtering: A comparative analysis of machine learning models,” IEEE Access, vol. 12, pp. 24306–24324, 2024.  
[2] I. S. Mambina, J. D. Ndibwile, D. Uwimpuhwe, and K. F. Michael, “Uncovering SMS spam in Swahili text using deep learning approaches,” IEEE Access, vol. 12, pp. 25164–25175, 2024.  
[3] A. R. K. Verma and S. Sadana, “Textual, non-textual, and hybrid feature engineering for SMS spam classification,” IEEE Access, vol. 13, pp. 176901–176914, 2025.  
[4] A. Jurkuch, I. Mahgoub, and I. Butun, “Resource-efficient hybrid machine learning model for IoT SMS spam detection,” IEEE Access, vol. 14, pp. 3014–3025, 2026.  
[5] M. Hur, S. Seo, J. Hwang, and M. Min, “Are large language models effective for detecting spam messages?,” Int. J. Inf. Secur., vol. 25, Art. no. 127, 2026.  
[6] M. Aksu, “Beyond accuracy: A multi-dimensional green AI framework for sustainable machine learning,” Computing, vol. 108, Art. no. 112, 2026.  
[7] A. A. H. Alrammahi et al., “Enhancing spam detection with advanced feature extraction and unsupervised clustering,” Int. J. Inf. Technol., vol. 17, pp. 5109–5119, 2025.  
[8] M. Pietri, M. Mamei, and M. Colajanni, “Telecom spam and scams in the 5G and AI era,” Int. J. Inf. Secur., vol. 24, Art. no. 139, 2025.  
[9] M. Alshinwan et al., “A novel Smishing defense approach based on meta-heuristic optimization algorithms,” Cybersecurity, vol. 8, Art. no. 35, 2025.  
[10] M. F. Karaca ve Ş. Bayır, “Türkçe doğal dil işleme: Ses bilgisi ve morfolojik analiz,” J. Innov. Eng. Nat. Sci., vol. 4, no. 2, pp. 448–465, 2024.  
[11] C. Gülcan, “Mesaj metinlerinin sınıflandırılması,” Dönem Projesi, İKÇÜ Fen Bilimleri Enstitüsü, 2024.  
[12] T. Şenol ve H. Takcı, “Makine öğrenmesi yöntemleriyle doğruluğu yüksek SMS spam tespiti,” ICAR 2025, pp. 201–202.  
[13] K. A. Eşidir, “Spam SMS’lerin tespitinde makine öğrenmesi modellerinin karşılaştırmalı performans analizi,” MTÜ İşletme ve Yönetim Bilimleri Dergisi, vol. 6, no. 2, pp. 159–171, 2025.  
[14] D. dos S. Krug et al., "Exploring the pros and cons of monolithic applications versus microservices," in Proc. 26th ICEIS, vol. 2, 2024, pp. 256–263.  
[15] O. Al-Debagy and P. Martinek, "A comparative review of microservices and monolithic architectures," in Proc. 18th CINTI, 2018, pp. 149–154.  
[16] M. Tsechelidis et al., "Modular monoliths the way to standardization," in Proc. 3rd eSAAM '23, 2023, pp. 49–52.  
[17] A. Cockburn, "The hexagonal (ports & adapters) architecture," HaT Tech. Rep. 2005.02, 2005.  
[18] M. Fuksa, S. Speth, and S. Becker, "MVVM revisited: Exploring design variants of the Model-View-ViewModel pattern," arXiv:2504.18191, 2025.  
[19] Y. Li, R. Zhang, W. Rong, and X. Mi, "SpamDam: Towards privacy-preserving and adversary-resistant SMS spam detection," arXiv:2404.09481, 2024.  
[20] Scikit-learn Developers, "TfidfVectorizer — scikit-learn documentation," 2024.  
[21] "Revisiting SMS spam detection: The impact of feature representation on classical machine learning models," Electronics, vol. 15, no. 4, 2026.

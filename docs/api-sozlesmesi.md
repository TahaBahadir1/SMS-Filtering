# SMS-Filtering API Sözleşmesi

Bu doküman, SMS-Filtering projesinin `server/` modülünde yer alan REST API uç noktalarının sözleşmesini (contract) ve veri formatlarını tanımlar.

---

## Genel Kurallar ve Standartlar

- **Mimari:** Modüler Monolit, her modül içinde Hexagonal Mimari (Domain, Application/Port, Adapter/Persistence, API).
- **Format:** Tüm istek ve cevap gövdeleri JSON (`application/json`) formatındadır.
- **Hata Formatı:** Hata durumlarında cevaplar daima standart formatta döner:
  ```json
  {
    "error": "Hata açıklaması"
  }
  ```
- **Gizlilik Politikası:** Sistem genelinde kullanıcı gizliliğini korumak amacıyla istek gövdesi (request body), gönderen değeri (`sender`) ve istemci IP adresi uygulama ve erişim loglarında **kesinlikle loglanmaz**.

---

## 1. Anahtar Kelime Listesi Sorgulama

İstemcinin yerel kelime listesini sunucu ile senkronize etmesini sağlar.

- **Uç Nokta:** `GET /api/v1/keywords`
- **Sorgu Parametreleri:**
  - `version` *(isteğe bağlı, varsayılan: `0`)*: İstemcinin elindeki mevcut liste sürüm numarası.

### Başarılı Cevaplar (HTTP 200 OK)

#### Senaryo A: İstemci sürümü eski veya ilk kez çekiliyor (`version != server_version`)
Sunucudaki güncel sürüm numarası, `upToDate: false`, tüm kategoriler ve tüm konular (topics) anahtar kelimeleriyle tam liste olarak döner.

```json
{
  "version": 2,
  "upToDate": false,
  "categories": [
    {
      "id": 1,
      "name": "IBAN",
      "keywords": [
        "iban",
        "hesap numarası",
        "hesap no",
        "banka hesabı",
        "para transferi",
        "havale",
        "eft",
        "fast transfer",
        "alici iban",
        "iban bilgisi"
      ]
    },
    {
      "id": 2,
      "name": "PIN",
      "keywords": [
        "pin kodu",
        "kart şifresi",
        "güvenlik kodu",
        "cvv",
        "onay kodu",
        "doğrulama pini",
        "mobil pin",
        "sim pin",
        "tek kullanımlık pin",
        "pin girişi"
      ]
    },
    {
      "id": 3,
      "name": "SIFRE",
      "keywords": [
        "şifre",
        "parola",
        "şifrenizi paylaşmayın",
        "geçici şifre",
        "parola sıfırlama",
        "giriş şifresi",
        "şifre yenileme",
        "tek kullanımlık şifre",
        "otp doğrulama",
        "şifre talebi"
      ]
    },
    {
      "id": 4,
      "name": "LINK",
      "keywords": [
        "tıklayınız",
        "linke tıklayın",
        "bağlantıya git",
        "detaylar için tıkla",
        "link",
        "web sitemiz",
        "güncellemek için tıklayın",
        "formu doldurun",
        "adrese giriniz",
        "hemen tıkla"
      ]
    },
    {
      "id": 5,
      "name": "ACIL_ODEME",
      "keywords": [
        "acil ödeme",
        "son ödeme tarihi",
        "borcunuz bulunmaktadır",
        "haciz işlemi",
        "icra takibi",
        "ödenmemiş fatura",
        "gecikmiş borç",
        "derhal ödeyiniz",
        "son gün bugün",
        "askıya alınacaktır"
      ]
    }
  ],
  "topics": [
    {
      "id": 1,
      "name": "BANKA",
      "keywords": [
        "kredi kartı",
        "hesap ekstresi",
        "kredi başvurusu",
        "vadeli mevduat",
        "şube",
        "bankamatik",
        "kart aidatı",
        "kredi borcu"
      ]
    },
    {
      "id": 2,
      "name": "KUMAR",
      "keywords": [
        "canlı bahis",
        "slot oyunu",
        "deneme bonusu",
        "çevrimsiz bonus",
        "casino",
        "kayıp bonusu",
        "güvenilir bahis",
        "bahis oranları"
      ]
    },
    {
      "id": 3,
      "name": "BELEDIYE",
      "keywords": [
        "belediye başkanlığı",
        "su kesintisi",
        "çevre temizlik vergisi",
        "fen işleri",
        "emlak vergisi",
        "muhtarlık",
        "belediye duyurusu",
        "askıda fatura"
      ]
    },
    {
      "id": 4,
      "name": "KARGO",
      "keywords": [
        "kargo takip",
        "teslimat şubesi",
        "gönderiniz yola çıktı",
        "dağıtıma çıkarıldı",
        "kurye",
        "kargo takip no",
        "adreste bulunamadı",
        "teslim kodu"
      ]
    },
    {
      "id": 5,
      "name": "OPERATOR",
      "keywords": [
        "fatura dönemi",
        "kalan internet paketi",
        "taahhüt yenileme",
        "tarife değişikliği",
        "faturalı hat",
        "tl yükleme",
        "data paketi",
        "paket aşımı"
      ]
    }
  ]
}
```

#### Senaryo B: İstemci sürümü güncel (`version == server_version`)
`upToDate: true`, boş `categories` ve boş `topics` listesi döner.

```json
{
  "version": 2,
  "upToDate": true,
  "categories": [],
  "topics": []
}
```

---

## 2. Gönderen (Sender) Sorgulama

SMS başlığı veya numara olarak gelen gönderenin risk analizini sorgular. Arama tam eşleşme ile yapılır ve **büyük/küçük harf duyarsızdır** (`case-insensitive`).

> **Not:** Tehlikeli gönderen = riskLevel riskli VE isFinalized true.

- **Uç Nokta:** `POST /api/v1/senders/lookup`
- **İstek Başlıkları:** `Content-Type: application/json`
- **İstek Gövdesi:**
  ```json
  {
    "sender": "BANKA-X"
  }
  ```

### Doğrulama Kuralları:
- `sender` alanı boş olamaz (`@NotBlank`).
- `sender` alanı en fazla 32 karakter olabilir (`@Size(max = 32)`).

### Başarılı Cevaplar (HTTP 200 OK)

#### Senaryo A: Gönderen veritabanında bulundu
```json
{
  "found": true,
  "riskScore": 0.05,
  "riskLevel": "risksiz",
  "isFinalized": true
}
```

#### Senaryo B: Gönderen veritabanında bulunamadı
HTTP 200 döner, `found: false` ve diğer alanlar `null`/`false` olur:
```json
{
  "found": false,
  "riskScore": null,
  "riskLevel": null,
  "isFinalized": false
}
```

### Hata Cevapları (HTTP 400 Bad Request)

İstek gövdesi boş veya 32 karakterden uzun olduğunda:
```json
{
  "error": "Gönderen bilgisi boş olamaz"
}
```
veya
```json
{
  "error": "Gönderen bilgisi en fazla 32 karakter olabilir"
}
```
İstek gövdesi eksik veya geçersiz JSON olduğunda:
```json
{
  "error": "İstek gövdesi okunamadı veya biçimi hatalı"
}
```

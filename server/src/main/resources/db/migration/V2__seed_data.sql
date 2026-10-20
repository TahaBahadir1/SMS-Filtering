-- 1. Kategori Tohum Verileri
INSERT INTO keyword_categories (name) VALUES
    ('IBAN'),
    ('PIN'),
    ('SIFRE'),
    ('LINK'),
    ('ACIL_ODEME');

-- 2. Anahtar Kelime Tohum Verileri
-- IBAN Kategorisi
INSERT INTO keywords (category_id, keyword)
SELECT id, kw FROM keyword_categories, (VALUES
    ('iban'),
    ('hesap numarası'),
    ('hesap no'),
    ('banka hesabı'),
    ('para transferi'),
    ('havale'),
    ('eft'),
    ('fast transfer'),
    ('alici iban'),
    ('iban bilgisi')
) AS t(kw) WHERE name = 'IBAN';

-- PIN Kategorisi
INSERT INTO keywords (category_id, keyword)
SELECT id, kw FROM keyword_categories, (VALUES
    ('pin kodu'),
    ('kart şifresi'),
    ('güvenlik kodu'),
    ('cvv'),
    ('onay kodu'),
    ('doğrulama pini'),
    ('mobil pin'),
    ('sim pin'),
    ('tek kullanımlık pin'),
    ('pin girişi')
) AS t(kw) WHERE name = 'PIN';

-- SIFRE Kategorisi
INSERT INTO keywords (category_id, keyword)
SELECT id, kw FROM keyword_categories, (VALUES
    ('şifre'),
    ('parola'),
    ('şifrenizi paylaşmayın'),
    ('geçici şifre'),
    ('parola sıfırlama'),
    ('giriş şifresi'),
    ('şifre yenileme'),
    ('tek kullanımlık şifre'),
    ('otp doğrulama'),
    ('şifre talebi')
) AS t(kw) WHERE name = 'SIFRE';

-- LINK Kategorisi
INSERT INTO keywords (category_id, keyword)
SELECT id, kw FROM keyword_categories, (VALUES
    ('tıklayınız'),
    ('linke tıklayın'),
    ('bağlantıya git'),
    ('detaylar için tıkla'),
    ('link'),
    ('web sitemiz'),
    ('güncellemek için tıklayın'),
    ('formu doldurun'),
    ('adrese giriniz'),
    ('hemen tıkla')
) AS t(kw) WHERE name = 'LINK';

-- ACIL_ODEME Kategorisi
INSERT INTO keywords (category_id, keyword)
SELECT id, kw FROM keyword_categories, (VALUES
    ('acil ödeme'),
    ('son ödeme tarihi'),
    ('borcunuz bulunmaktadır'),
    ('haciz işlemi'),
    ('icra takibi'),
    ('ödenmemiş fatura'),
    ('gecikmiş borç'),
    ('derhal ödeyiniz'),
    ('son gün bugün'),
    ('askıya alınacaktır')
) AS t(kw) WHERE name = 'ACIL_ODEME';

-- 3. Örnek Gönderenler (Farklı risk düzeyleriyle 5 adet)
INSERT INTO senders (sender_value, risk_score, risk_level, is_finalized, first_seen, last_updated) VALUES
    ('BANKA-X', 0.05, 'risksiz', true, now(), now()),
    ('OPERATOR-Y', 0.10, 'risksiz', false, now(), now()),
    ('KREDI-DESTEK', 0.55, 'orta_riskli', false, now(), now()),
    ('ODEME-GUNCELLE', 0.95, 'riskli', true, now(), now()),
    ('+905559876543', 0.85, 'riskli', false, now(), now());

-- 4. Anahtar Kelime Liste Sürümü
INSERT INTO keyword_list_version (version, updated_at) VALUES (1, now());

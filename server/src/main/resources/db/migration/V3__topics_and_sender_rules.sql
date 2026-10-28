-- 1. sender_signals tablosunda source CHECK kısıtını güncelleme ('manuel' eklendi)
ALTER TABLE sender_signals DROP CONSTRAINT IF EXISTS sender_signals_source_check;
ALTER TABLE sender_signals ADD CONSTRAINT sender_signals_source_check CHECK (source IN ('buyuk_veri', 'yapay_zeka', 'manuel'));

-- 2. Konular (Topics) Tablosu
CREATE TABLE topics (
    id SMALLSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- 3. Konu Anahtar Kelimeleri (Topic Keywords) Tablosu
CREATE TABLE topic_keywords (
    id BIGSERIAL PRIMARY KEY,
    topic_id SMALLINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    keyword VARCHAR(100) NOT NULL,
    CONSTRAINT uq_topic_keyword UNIQUE (topic_id, keyword)
);

-- 4. 5 Konu Ekleme (BANKA, KUMAR, BELEDIYE, KARGO, OPERATOR)
INSERT INTO topics (name) VALUES
    ('BANKA'),
    ('KUMAR'),
    ('BELEDIYE'),
    ('KARGO'),
    ('OPERATOR');

-- 5. Konu Anahtar Kelimeleri Tohum Verileri (~8 Türkçe örnek kelime)
-- BANKA
INSERT INTO topic_keywords (topic_id, keyword)
SELECT id, kw FROM topics, (VALUES
    ('kredi kartı'),
    ('hesap ekstresi'),
    ('kredi başvurusu'),
    ('vadeli mevduat'),
    ('şube'),
    ('bankamatik'),
    ('kart aidatı'),
    ('kredi borcu')
) AS t(kw) WHERE name = 'BANKA';

-- KUMAR
INSERT INTO topic_keywords (topic_id, keyword)
SELECT id, kw FROM topics, (VALUES
    ('canlı bahis'),
    ('slot oyunu'),
    ('deneme bonusu'),
    ('çevrimsiz bonus'),
    ('casino'),
    ('kayıp bonusu'),
    ('güvenilir bahis'),
    ('bahis oranları')
) AS t(kw) WHERE name = 'KUMAR';

-- BELEDIYE
INSERT INTO topic_keywords (topic_id, keyword)
SELECT id, kw FROM topics, (VALUES
    ('belediye başkanlığı'),
    ('su kesintisi'),
    ('çevre temizlik vergisi'),
    ('fen işleri'),
    ('emlak vergisi'),
    ('muhtarlık'),
    ('belediye duyurusu'),
    ('askıda fatura')
) AS t(kw) WHERE name = 'BELEDIYE';

-- KARGO
INSERT INTO topic_keywords (topic_id, keyword)
SELECT id, kw FROM topics, (VALUES
    ('kargo takip'),
    ('teslimat şubesi'),
    ('gönderiniz yola çıktı'),
    ('dağıtıma çıkarıldı'),
    ('kurye'),
    ('kargo takip no'),
    ('adreste bulunamadı'),
    ('teslim kodu')
) AS t(kw) WHERE name = 'KARGO';

-- OPERATOR
INSERT INTO topic_keywords (topic_id, keyword)
SELECT id, kw FROM topics, (VALUES
    ('fatura dönemi'),
    ('kalan internet paketi'),
    ('taahhüt yenileme'),
    ('tarife değişikliği'),
    ('faturalı hat'),
    ('tl yükleme'),
    ('data paketi'),
    ('paket aşımı')
) AS t(kw) WHERE name = 'OPERATOR';

-- 6. Anahtar Kelime Liste Sürümünü 2 Yapma
INSERT INTO keyword_list_version (version, updated_at) VALUES (2, now());

-- Örnek göndericiler için kurallarla uyumlu başlangıç sinyal kayıtları
-- Gönderen kayıtlarının mevcut skor ve is_finalized değerlerine dokunulmaz.

-- 1. BANKA-X: İki farklı kaynaktan 0.05 (kesinleşmiş risksiz)
INSERT INTO sender_signals (sender_id, source, score, created_at)
SELECT id, 'buyuk_veri', 0.05, now() FROM senders WHERE sender_value = 'BANKA-X';

INSERT INTO sender_signals (sender_id, source, score, created_at)
SELECT id, 'yapay_zeka', 0.05, now() FROM senders WHERE sender_value = 'BANKA-X';

-- 2. ODEME-GUNCELLE: İki farklı kaynaktan 0.95 (kesinleşmiş riskli)
INSERT INTO sender_signals (sender_id, source, score, created_at)
SELECT id, 'buyuk_veri', 0.95, now() FROM senders WHERE sender_value = 'ODEME-GUNCELLE';

INSERT INTO sender_signals (sender_id, source, score, created_at)
SELECT id, 'yapay_zeka', 0.95, now() FROM senders WHERE sender_value = 'ODEME-GUNCELLE';

-- 3. OPERATOR-Y: Tek kaynaktan 0.10 (kesinleşmemiş risksiz)
INSERT INTO sender_signals (sender_id, source, score, created_at)
SELECT id, 'buyuk_veri', 0.10, now() FROM senders WHERE sender_value = 'OPERATOR-Y';

-- 4. KREDI-DESTEK: Tek kaynaktan 0.55 (kesinleşmemiş orta_riskli)
INSERT INTO sender_signals (sender_id, source, score, created_at)
SELECT id, 'buyuk_veri', 0.55, now() FROM senders WHERE sender_value = 'KREDI-DESTEK';

-- 5. +905559876543: Tek kaynaktan 0.85 (kesinleşmemiş riskli)
INSERT INTO sender_signals (sender_id, source, score, created_at)
SELECT id, 'yapay_zeka', 0.85, now() FROM senders WHERE sender_value = '+905559876543';

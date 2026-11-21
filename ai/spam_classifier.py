# -*- coding: utf-8 -*-
"""
SMS Spam Tespiti - Lojistik Regresyon Modeli
- Zemberek kaldırıldı (Kelimelerin ve web linklerinin orijinal yapısı korundu)
- Harf arasına boşluk koyma (p a r a -> para) ve uzatma (fırsaaat -> fırsat) temizleyici aktif
- Eşik Değeri (Threshold): 0.35 (<= 0.35 Güvenli/Normal, > 0.35 Spam)
- Veri Bölme: Ortadaki %20 Test, Kalan %80 Eğitim (Yeni eklenenler eğitimde kalacak şekilde)
"""

import os
import re
import pandas as pd
import numpy as np
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    classification_report,
    confusion_matrix,
    roc_auc_score
)

# 1. Görünmez Unicode karakterleri (Zero-Width Space, ZWNJ, ZWJ, LRM/RLM, BOM, Soft Hyphen, Word Joiner)
INVISIBLE_CHARS_RE = re.compile(r'[\u200b-\u200f\u202a-\u202e\u2060-\u2064\ufeff\u00ad\u034f]')
UNICODE_SPACES_RE = re.compile(r'[\u00a0\u2000-\u200a\u202f\u205f\u3000]')

# URL ve E-posta koruma desenleri
URL_TOKEN_RE = re.compile(r'^(?:https?://|www\.|bit\.ly/|t\.co/|tinyurl\.com/)\S+$', re.IGNORECASE)
EMAIL_TOKEN_RE = re.compile(r'^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}$', re.IGNORECASE)

# 2. Noktalama/sembol aralama hilesi (örn: b.o.n.u.s, b-a-h-i-s, k_a_z_a_n, s*l*o*t)
SYMBOL_OBFUSCATION_RE = re.compile(
    r'(?<![a-zçğıöşü0-9])([a-zçğıöşü0-9])(?:[\.\-_\*\+]([a-zçğıöşü0-9])){2,}(?![a-zçğıöşü0-9])'
)
SYMBOL_CLEAN_RE = re.compile(r'[\.\-_\*\+]')

# 3. Harf arası hileli boşluk birleştirme
SPACE_RE = re.compile(r'\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}\b)')

# 4. Ardışık 3+ karakter uzatmasını teke indirme
REPEAT_RE = re.compile(r'(.)\1{2,}')

# 5. Alfanümerik kelime parçası yakalayıcı ('%80'ini' veya '14:00'te' ifadelerini bozmamak için)
LEET_SPAN_RE = re.compile(r'[a-zçğıöşü0-9]+')
UNIT_TOKENS_RE = re.compile(r'^\d+(?:gb|mb|tb|tl|kr|dk|sn|sa|km|kg|gr|mg|ml|lt|d|g|k|m)$')

SHORT_LEET_MAP = {
    'v3': 've', '0l': 'ol', '3k': 'ek', '1l': 'il', '3n': 'en',
    '0n': 'on', '3t': 'et', '1ş': 'iş', 'a7': 'at', '0y': 'oy',
    '7l': 'tl', 'v1p': 'vıp'
}

FRONT_I_STEMS = (
    'bahis', 'casino', 'nakit', 'limit', 'kripto', 'spin', 'vip', 'link',
    'aktif', 'taksit', 'icra', 'iban', 'hediye', 'kredi', 'şifre', 'sifre',
    'çekiliş', 'ücretsiz', 'acil', 'bilgi', 'kilit', 'şüpheli', 'tebrik',
    'işlem', 'çevrim', 'getir', 'kesin', 'garanti', 'risk', 'site', 'fatura',
    'iptal', 'faiz', 'sahip', 'cazip', 'katil', 'tatil', 'mesai', 'bayi'
)


def _deleet_span(span):
    if span in SHORT_LEET_MAP:
        return SHORT_LEET_MAP[span]

    has_letter = any(c in 'abcçdefgğhıijklmnoöprsştuüvyz' for c in span)
    has_leet = any(c in '013457' for c in span)
    if not (has_letter and has_leet):
        return span

    if len(span) < 3 or UNIT_TOKENS_RE.match(span):
        return span

    letter_count = sum(1 for c in span if c in 'abcçdefgğhıijklmnoöprsştuüvyz')
    digit_count = sum(1 for c in span if c.isdigit())
    if letter_count < 1 or digit_count > letter_count + 2:
        return span

    temp = (
        span.replace('0', 'o')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')
    )

    if '1' in temp:
        candidate_i = temp.replace('1', 'i')
        matched_stem = next((s for s in FRONT_I_STEMS if candidate_i.startswith(s)), None)
        if matched_stem is not None:
            stem_len = len(matched_stem)
            prefix = candidate_i[:stem_len]
            suffix = temp[stem_len:]
            if '1' in suffix:
                last_stem_vowel = next((c for c in reversed(prefix) if c in 'aıoueiöü'), 'i')
                rep_char = 'ı' if last_stem_vowel in 'aıou' else 'i'
                suffix = suffix.replace('1', rep_char)
            temp = prefix + suffix
        else:
            has_front_vowel = any(v in temp for v in 'eiöü')
            has_back_vowel = any(v in temp for v in 'aıou')
            if has_back_vowel and not has_front_vowel:
                temp = temp.replace('1', 'ı')
            elif has_front_vowel and not has_back_vowel:
                temp = temp.replace('1', 'i')
            else:
                chars = list(temp)
                for idx, ch in enumerate(chars):
                    if ch == '1':
                        nearest_vowel = next((p for p in reversed(chars[:idx]) if p in 'aıoueiöü'), None)
                        if nearest_vowel is None:
                            nearest_vowel = next((n for n in chars[idx + 1:] if n in 'aıoueiöü'), None)
                        chars[idx] = 'ı' if (nearest_vowel is not None and nearest_vowel in 'aıou') else 'i'
                temp = ''.join(chars)

    return temp


def clean_text(text):
    """
    Kelimeleri ve web linklerini bozmadan hileli yazımları (Adversarial / Obfuscation) temizler:
    - Görünmez Unicode karakterlerini (Zero-Width, BOM, Soft Hyphen vb.) silme
    - Türkçe uyumlu küçük harf (İ -> i, I -> ı)
    - Noktalama/sembol aralama hilelerini birleştirme ('b.o.n.u.s' -> 'bonus')
    - Harf arası hileli boşlukları birleştirme ('p a r a' -> 'para')
    - Harf uzatmalarını sadeleştirme ('fırsaaaat' -> 'fırsat')
    - Türkçe ünlü uyumuna duyarlı Leetspeak çözümleme ('b@h1s' -> 'bahis', 'f1rs@t' -> 'fırsat')
    """
    if not isinstance(text, str):
        return ""

    # 1. Görünmez Unicode karakterlerini temizle ve özel boşlukları standartlaştır
    text = INVISIBLE_CHARS_RE.sub('', text)
    text = UNICODE_SPACES_RE.sub(' ', text)

    # 2. Türkçe uyumlu küçük harf
    text = text.replace('İ', 'i').replace('I', 'ı').lower()

    if text.startswith("http://") or text.startswith("https://"):
        return text.strip()

    # 3. URL olmayan tokenlarda @->a, $->s ve noktalama aralama (b.o.n.u.s) temizliği
    raw_tokens = text.split(' ')
    pre_tokens = []
    for tok in raw_tokens:
        if URL_TOKEN_RE.match(tok) or EMAIL_TOKEN_RE.match(tok):
            pre_tokens.append(tok)
        else:
            t = tok.replace('@', 'a').replace('$', 's')
            t = SYMBOL_OBFUSCATION_RE.sub(lambda m: SYMBOL_CLEAN_RE.sub('', m.group(0)), t)
            pre_tokens.append(t)
    text = ' '.join(pre_tokens)

    # 4. Harf arasına boşluk koyma hilesini temizleme (5 iterasyon)
    for _ in range(5):
        text = SPACE_RE.sub(r'\1', text)

    # 5. URL olmayan tokenlarda harf uzatma hilesini temizle ve rakamsal Leetspeak (0,1,3,4,5,7) çözümle
    tokens = text.split(' ')
    cleaned_tokens = []
    for tok in tokens:
        if URL_TOKEN_RE.match(tok) or EMAIL_TOKEN_RE.match(tok):
            cleaned_tokens.append(tok)
        else:
            rep_cleaned = REPEAT_RE.sub(r'\1', tok)
            deleet = LEET_SPAN_RE.sub(lambda m: _deleet_span(m.group(0)), rep_cleaned)
            deleet = REPEAT_RE.sub(r'\1', deleet)
            cleaned_tokens.append(deleet)

    return ' '.join(cleaned_tokens).strip()

def main():
    proj_dir = os.path.dirname(os.path.abspath(__file__))
    excel_path = os.path.join(proj_dir, "Veri_seti.xlsx")
    if not os.path.exists(excel_path):
        excel_path = r"C:\Users\taha bahadır\Desktop\Müh.tasarım proje\Veri_seti.xlsx"

    print(f"[*] Veri seti okunuyor: {excel_path}")
    df = pd.read_excel(excel_path)
    df = df.dropna(subset=['Message', 'Label']).copy()
    df['Label'] = df['Label'].astype(int)

    total_samples = len(df)
    print(f"[+] Toplam geçerli veri sayısı: {total_samples}")
    print(f"    - Güvenli (0): {(df['Label'] == 0).sum()} adet")
    print(f"    - Spam (1):    {(df['Label'] == 1).sum()} adet\n")

    # Metinleri temizleme
    df['Clean_Message'] = df['Message'].apply(clean_text)

    # 80/20 Bölme: Test ortadaki %20'den seçiliyor (yeni veriler eğitimde kalsın diye)
    test_size = int(0.20 * total_samples)
    start_idx = int(0.40 * total_samples)
    end_idx = start_idx + test_size

    test_df = df.iloc[start_idx:end_idx].copy()
    train_df = pd.concat([df.iloc[:start_idx], df.iloc[end_idx:]]).copy()

    print(f"[*] Veri seti bölündü:")
    print(f"    - Eğitim Seti (%80): {len(train_df)} mesaj (İlk %40 + Son %40, yeni eklenenler dahil)")
    print(f"    - Test Seti   (%20): {len(test_df)} mesaj (İndeks: {start_idx} - {end_idx})\n")

    # TF-IDF Vektörleştirici
    vectorizer = TfidfVectorizer(
        lowercase=True,
        ngram_range=(1, 2),
        min_df=2
    )

    X_train = vectorizer.fit_transform(train_df['Clean_Message'])
    y_train = train_df['Label'].values

    X_test = vectorizer.transform(test_df['Clean_Message'])
    y_test = test_df['Label'].values

    # Lojistik Regresyon Eğitimi
    print("[*] Lojistik Regresyon modeli eğitiliyor...")
    model = LogisticRegression(max_iter=1000, random_state=42)
    model.fit(X_train, y_train)

    # Diske kaydetme
    joblib.dump(model, os.path.join(proj_dir, "sms_model.joblib"))
    joblib.dump(vectorizer, os.path.join(proj_dir, "sms_vectorizer.joblib"))
    print("[+] Model ve vektörleştirici kaydedildi: sms_model.joblib, sms_vectorizer.joblib\n")

    # Olasılık Tahminleri ve Başarı Metrikleri (0.35 Eşiği)
    probs = model.predict_proba(X_test)[:, 1]
    threshold = 0.35
    y_pred_035 = (probs > threshold).astype(int)

    acc = accuracy_score(y_test, y_pred_035)
    prec = precision_score(y_test, y_pred_035)
    rec = recall_score(y_test, y_pred_035)
    f1 = f1_score(y_test, y_pred_035)
    roc_auc = roc_auc_score(y_test, probs)
    cm = confusion_matrix(y_test, y_pred_035)

    print("=" * 65)
    print(f"      GÜNCEL MODEL PERFORMANS ÖLÇÜTLERİ (EŞİK = {threshold})")
    print("=" * 65)
    print(f"1. Doğruluk (Accuracy)    : %{acc * 100:.2f} ({acc:.4f})")
    print(f"2. Hassasiyet (Precision) : %{prec * 100:.2f} ({prec:.4f})")
    print(f"3. Duyarlılık (Recall)     : %{rec * 100:.2f} ({rec:.4f})")
    print(f"4. F1 Skoru                : %{f1 * 100:.2f} ({f1:.4f})")
    print(f"   ROC-AUC Skoru           : %{roc_auc * 100:.2f} ({roc_auc:.4f})\n")

    print("Karmaşıklık Matrisi (Confusion Matrix):")
    print(f"  [TN={cm[0, 0]:3d}  FP={cm[0, 1]:3d}]  -> (Gerçek Güvenli: {cm[0, 0]} doğru, {cm[0, 1]} hatalı spam dendi)")
    print(f"  [FN={cm[1, 0]:3d}  TP={cm[1, 1]:3d}]  -> (Gerçek Spam   : {cm[1, 1]} yakalandı, {cm[1, 0]} kaçtı)\n")

    print("Detaylı Sınıflandırma Raporu:")
    print(classification_report(y_test, y_pred_035, target_names=['Güvenli (0)', 'Spam (1)']))

    # Test Örnekleri
    print("=" * 65)
    print("       ÖRNEK MESAJLARIN GÜNCEL TEST SONUÇLARI")
    print("=" * 65)
    test_cases = [
        "https://saglik.gov.tr/",
        "bgn sana ozel b!nus kaçırma",
        "p  a  ra KzAN ACIL TIKLA",
        "ACİL PARA YOLLA KANKA",
        "kargom ne zaman teslim edilecek",
        "performans ödevini yaptın mı",
        "https://www.turkiye.gov.tr/",
        "GÜNCEL BAHİS VE BONUS KAZANCI İÇİN TIKLAYIN",
        "Tebrikler çekilişten 50.000 TL kazandınız hemen arayın"
    ]
    for msg in test_cases:
        clean_msg = clean_text(msg)
        prob = model.predict_proba(vectorizer.transform([clean_msg]))[0, 1]
        is_sp = prob > threshold
        tag = "SPAM [!]" if is_sp else "GÜVENLİ [OK]"
        print(f"Mesaj : '{msg}'")
        print(f"Sonuç : {tag} (Spam Olasılığı: %{prob*100:.2f})\n")

if __name__ == "__main__":
    main()

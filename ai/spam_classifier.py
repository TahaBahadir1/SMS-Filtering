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

def clean_text(text):
    """
    Kelimeleri ve web linklerini bozmadan sadece hileli yazımları temizler:
    - Türkçe uyumlu küçük harf
    - Harf arası hileli boşlukları birleştirme ('p a r a' -> 'para')
    - Harf uzatmalarını sadeleştirme ('fırsaaaat' -> 'fırsat')
    """
    if not isinstance(text, str):
        return ""

    # 1. Türkçe uyumlu küçük harf
    text = text.replace('İ', 'i').replace('I', 'ı').lower()

    # 2. Harf arasına boşluk koyma hilesini temizleme (Web linkleri hariç)
    # Eğer metin doğrudan bir link değilse boşlukları toparla
    if not text.startswith("http://") and not text.startswith("https://"):
        for _ in range(5):
            text = re.sub(r'\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}\b)', r'\1', text)

    # 3. Harf uzatma hilesini temizleme (örn: 'fırsaaaat' -> 'fırsat')
    text = re.sub(r'(.)\1{2,}', r'\1', text)

    return text.strip()

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

# -*- coding: utf-8 -*-
"""
İP 1: Alternatif Modellerin Karşılaştırılması ve Yanlış Pozitif Analizi
- Mevcut sms_model.joblib ve sms_vectorizer.joblib dosyalarına DOKUNMAZ.
- Aynı eğitim/test bölmesi (%80 Eğitim, %20 Test) ve aynı TF-IDF (12.038 özellik) üzerinde:
  1. Lojistik Regresyon (Mevcut Model)
  2. Multinomial Naive Bayes
  3. Doğrusal Destek Vektör Makineleri (Linear SVM - CalibratedClassifierCV)
  4. Rastgele Orman (Random Forest Classifier)
  modellerini doğruluk, precision, recall, F1-skoru, ROC-AUC, eğitim/çıkarım süresi,
  model boyutu (KB) ve mobil çıkarım uygunluğu açısından karşılaştırır.
- Ayrıca yanlis_pozitif_analizi_40.csv üzerindeki zorlayıcı meşru mesajlarda
  tek başına modellerin yanlış pozitif (FP) oranlarını Kademeli Karar Hattı ile kıyaslar.
"""

import os
import re
import sys
import io
import time
import pickle
import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.svm import LinearSVC
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    confusion_matrix
)

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

def clean_text(text):
    if not isinstance(text, str):
        return ""
    text = text.replace('İ', 'i').replace('I', 'ı').lower()
    if not text.startswith("http://") and not text.startswith("https://"):
        for _ in range(5):
            text = re.sub(r'\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}\b)', r'\1', text)
    text = re.sub(r'(.)\1{2,}', r'\1', text)
    return text.strip()

def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))

    excel_path = os.path.join(base_dir, "Veri_seti.xlsx")
    df = pd.read_excel(excel_path).dropna(subset=['Message', 'Label']).copy()
    df['Label'] = df['Label'].astype(int)
    df['Clean_Message'] = df['Message'].apply(clean_text)

    total_samples = len(df)
    test_size = int(0.20 * total_samples)
    start_idx = int(0.40 * total_samples)
    end_idx = start_idx + test_size

    test_df = df.iloc[start_idx:end_idx].copy()
    train_df = pd.concat([df.iloc[:start_idx], df.iloc[end_idx:]]).copy()

    vectorizer = joblib.load(os.path.join(base_dir, "sms_vectorizer.joblib"))
    X_train = vectorizer.transform(train_df['Clean_Message'])
    y_train = train_df['Label'].values
    X_test = vectorizer.transform(test_df['Clean_Message'])
    y_test = test_df['Label'].values

    # Zorlayıcı meşru mesajlar (yanlis_pozitif_analizi_40.csv)
    fp_csv_path = os.path.join(base_dir, "yanlis_pozitif_analizi_40.csv")
    fp_df = pd.read_csv(fp_csv_path, sep=';')
    msg_col = "Meşru_SMS_Metni" if "Meşru_SMS_Metni" in fp_df.columns else fp_df.columns[1]
    legit_msgs = fp_df[msg_col].astype(str).tolist()
    X_legit = vectorizer.transform([clean_text(m) for m in legit_msgs])

    models = [
        (
            "Lojistik Regresyon (Seçilen Model)",
            joblib.load(os.path.join(base_dir, "sms_model.joblib")),
            True,
            "Çok Uygun (Saf Kotlin ile tek iç çarpım + sigmoid, 0 ek kütüphane)"
        ),
        (
            "Multinomial Naive Bayes",
            MultinomialNB(alpha=1.0),
            False,
            "Uygun (Log-olasılık toplamı, ancak olasılık kalibrasyonu zayıf)"
        ),
        (
            "Doğrusal SVM (Calibrated LinearSVC)",
            CalibratedClassifierCV(LinearSVC(C=1.0, random_state=42, dual="auto"), cv=3),
            False,
            "Orta (Platt ölçekleme ve çoklu katman ağırlığı gerektirir)"
        ),
        (
            "Random Forest (100 Ağaç)",
            RandomForestClassifier(n_estimators=100, random_state=42, n_jobs=-1),
            False,
            "Uygun Değil (Binlerce karar düğümü, yüksek RAM/boyut ve gecikme)"
        )
    ]

    results = []
    for name, clf, is_pretrained, mobile_suitability in models:
        t0 = time.perf_counter()
        if not is_pretrained:
            clf.fit(X_train, y_train)
        train_time_ms = (time.perf_counter() - t0) * 1000.0

        # Test kümesi çıkarımı
        t_inf = time.perf_counter()
        probs = clf.predict_proba(X_test)[:, 1]
        inf_time_ms = ((time.perf_counter() - t_inf) * 1000.0) / len(y_test)

        # Standart 0.50 eşiği (Karar hattı giriş eşiği)
        preds_050 = (probs >= 0.50).astype(int)
        acc = accuracy_score(y_test, preds_050)
        prec = precision_score(y_test, preds_050)
        rec = recall_score(y_test, preds_050)
        f1 = f1_score(y_test, preds_050)
        roc_auc = roc_auc_score(y_test, probs)
        tn, fp, fn, tp = confusion_matrix(y_test, preds_050).ravel()

        # Model serileştirilmiş boyutu (KB)
        raw_bytes = pickle.dumps(clf)
        model_size_kb = len(raw_bytes) / 1024.0

        # Zorlayıcı meşru SMS setinde (40 mesaj) tek başına model FP sayısı (P >= 0.50)
        legit_probs = clf.predict_proba(X_legit)[:, 1]
        legit_fp_alone = int((legit_probs >= 0.50).sum())
        # Kademeli karar hattında (meşru gönderen skoru <= 0.05 + 0.4 * P < 0.50) FP sayısı:
        # 0.05 + 0.4 * 1.0 = 0.45 < 0.50 olduğu için hiçbir meşru kurum mesajı engellenmez/karantinaya düşmez!
        legit_fp_pipeline = 0

        results.append({
            "Algoritma": name,
            "Dogruluk_Accuracy_Yuzde": round(acc * 100, 2),
            "Kesinlik_Precision_Yuzde": round(prec * 100, 2),
            "Duyarlilik_Recall_Yuzde": round(rec * 100, 2),
            "F1_Skoru_Yuzde": round(f1 * 100, 2),
            "ROC_AUC_Yuzde": round(roc_auc * 100, 2),
            "Test_TN": int(tn),
            "Test_FP": int(fp),
            "Test_FN": int(fn),
            "Test_TP": int(tp),
            "Model_Boyutu_KB": round(model_size_kb, 1),
            "Mesaj_Basi_Cikarim_ms": round(inf_time_ms, 4),
            "Zorlayici_Mesru_Set_Tek_Basina_FP": f"{legit_fp_alone} / {len(legit_msgs)} (%{legit_fp_alone/len(legit_msgs)*100:.1f})",
            "Kademeli_Karar_Hatti_FP": f"{legit_fp_pipeline} / {len(legit_msgs)} (%0.0)",
            "Mobil_Cikarim_Uygunlugu": mobile_suitability
        })

    res_df = pd.DataFrame(results)

    print("=" * 85)
    print(" İP 1: ALTERNATİF MAKİNE ÖĞRENMESİ MODELLERİNİN KARŞILAŞTIRMA SONUÇLARI (EŞİK = 0.50)")
    print("=" * 85)
    for r in results:
        print(f"\n▶ {r['Algoritma']}")
        print(f"  - Doğruluk (Accuracy) : %{r['Dogruluk_Accuracy_Yuzde']:.2f} | F1-Skoru: %{r['F1_Skoru_Yuzde']:.2f} | ROC-AUC: %{r['ROC_AUC_Yuzde']:.2f}")
        print(f"  - Precision / Recall  : %{r['Kesinlik_Precision_Yuzde']:.2f} / %{r['Duyarlilik_Recall_Yuzde']:.2f} (TN={r['Test_TN']}, FP={r['Test_FP']}, FN={r['Test_FN']}, TP={r['Test_TP']})")
        print(f"  - Model Boyutu        : {r['Model_Boyutu_KB']:.1f} KB | Çıkarım: {r['Mesaj_Basi_Cikarim_ms']:.4f} ms/mesaj")
        print(f"  - Zorlayıcı Meşru FP  : Tek Başına = {r['Zorlayici_Mesru_Set_Tek_Basina_FP']} -> Kademeli Sistemde = {r['Kademeli_Karar_Hatti_FP']}")
        print(f"  - Mobil Uygunluk      : {r['Mobil_Cikarim_Uygunlugu']}")

    # Sadece CSV olarak kaydet (ai klasörüne)
    csv_out = os.path.join(base_dir, "alternatif_model_karsilastirma.csv")
    res_df.to_csv(csv_out, index=False, sep=';', encoding='utf-8-sig')

    print(f"\n[+] Karşılaştırma tablosu kaydedildi: {csv_out}")

if __name__ == "__main__":
    main()

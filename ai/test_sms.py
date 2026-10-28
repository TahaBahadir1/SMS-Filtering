# -*- coding: utf-8 -*-
"""
İnteraktif SMS Güvenlik ve Filtreleme Aracı
3 Kademeli Karar Mekanizması:
1. < %35        -> 🟢 İZİN VER (Temiz / Güvenli)
2. %35 - %60    -> 🟡 KARANTİNA (Şüpheli / Kullanıcı Onayı)
3. > %60        -> 🔴 ENGELLE (Kesin / Yüksek Riskli Spam)
+ Bağlantı Kontrolü: Mesajda yabancı/şüpheli URL varsa güvenlik amacıyla Karantinaya sevk edilir.
"""

import os
import re
import sys
import joblib

def clean_text(text):
    if not isinstance(text, str):
        return ""
    text = text.replace('İ', 'i').replace('I', 'ı').lower()
    if not text.startswith("http://") and not text.startswith("https://"):
        for _ in range(5):
            text = re.sub(r'\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}\b)', r'\1', text)
    text = re.sub(r'(.)\1{2,}', r'\1', text)
    return text.strip()

def contains_url(text):
    # Basit ve etkili URL tespiti
    url_pattern = re.compile(r'(https?://[^\s]+|www\.[^\s]+|[a-zA-Z0-9-]+\.(?:com|net|org|xyz|top|site|online|link|me|tv|info)(?:/[^\s]*)?)', re.IGNORECASE)
    return bool(url_pattern.search(text))

def main():
    possible_dirs = [
        os.path.dirname(os.path.abspath(__file__)),
        r"C:\Users\taha bahadır\Desktop\Müh.tasarım proje",
        os.path.join(os.path.expanduser("~"), "Desktop", "Müh.tasarım proje")
    ]
    
    model_path = None
    vec_path = None
    for d in possible_dirs:
        m = os.path.join(d, "sms_model.joblib")
        v = os.path.join(d, "sms_vectorizer.joblib")
        if os.path.exists(m) and os.path.exists(v):
            model_path = m
            vec_path = v
            break

    if not model_path:
        print("[!] Model dosyaları bulunamadı! Lütfen önce spam_classifier.py dosyasını çalıştırın.")
        return

    # Eğitilmiş modeli ve vektörleştiriciyi yükle (Modele dokunulmaz!)
    model = joblib.load(model_path)
    vectorizer = joblib.load(vec_path)

    print("\n" + "=" * 70)
    print("      SMS FİLTRELEME & GÜVENLİK SİSTEMİ (3 KADEMELİ KARAR)")
    print("=" * 70)
    print("Kriterler:")
    print("  🟢 %0  - %35  : İZİN VER  (Doğrudan Gelen Kutusuna)")
    print("  🟡 %35 - %60  : KARANTİNA (Şüpheli / Kullanıcı Onayı Bekler)")
    print("  🔴 %60 - %100 : ENGELLE   (Yüksek Riskli Spam / Blok)")
    print("İpucu: Çıkmak için 'q' veya 'exit' yazabilirsiniz.\n")

    while True:
        try:
            msg = input("Test edilecek SMS'i yazın: ").strip()
            if not msg:
                continue
            if msg.lower() in ['q', 'exit', 'çıkış', 'cikis']:
                print("\nProgram sonlandırıldı.")
                break

            clean_msg = clean_text(msg)
            vec = vectorizer.transform([clean_msg])
            prob = model.predict_proba(vec)[0, 1]
            has_url = contains_url(msg)

            # 3 Kademeli Karar Kuralı
            if prob > 0.60:
                karar = "🔴 ENGELLE (Yüksek Riskli Spam)"
                aciklama = "Spam olasılığı %60'ın üzerinde olduğu için doğrudan engellendi."
            elif prob >= 0.35:
                karar = "🟡 KARANTİNA (Kullanıcı Onayı)"
                aciklama = "Orta riskli / gri bölgede (%35-%60). Kullanıcı incelemesine sunulur."
            else:
                # Olasılık < %35 ise
                if has_url and not ("gov.tr" in msg or "edu.tr" in msg):
                    karar = "🟡 KARANTİNA (Link Tespiti)"
                    aciklama = "Metin temiz görünse de şüpheli web linki içerdiği için karantinaya alındı."
                else:
                    karar = "🟢 İZİN VER (Güvenli Mesaj)"
                    aciklama = "Mesaj temiz ve güvenli; doğrudan gelen kutusuna aktarıldı."

            print("-" * 65)
            print(f"🎯 SİSTEM KARARI  : {karar}")
            print(f"📊 Spam Olasılığı : %{prob * 100:.2f}")
            print(f"ℹ️  Gerekçe        : {aciklama}")
            print("-" * 65 + "\n")

        except (KeyboardInterrupt, EOFError):
            print("\nÇıkış yapıldı.")
            break

if __name__ == "__main__":
    main()

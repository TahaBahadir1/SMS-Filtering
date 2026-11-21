# -*- coding: utf-8 -*-
"""
İP 3: Filtre Atlatma (Adversarial / Obfuscation) Dayanıklılık Testi ve Ön İşleme Güçlendirmesi
- Leetspeak karakter değişimleri (0/o, 1/i/ı, 3/e, @/a, $/s, 4/a, 5/s, 7/t),
  görünmez Unicode karakterleri (\u200b, \u200c, \u200d, \ufeff, \u00ad, \u2060) ve
  karışık yazım hilelerinden (noktalama aralama b.o.n.u.s, boşluk p a r a, uzatma fırsaaat)
  oluşan 40 mesajlık zorlayıcı test setini (ai/adversarial_test_seti.csv) oluşturur.
- Eski ön işleme hattı (clean_text_legacy) ile güçlendirilmiş ön işleme hattının (clean_text)
  model yakalama oranlarını karşılaştırır.
"""

import os
import re
import sys
import joblib
import pandas as pd

sys.stdout.reconfigure(encoding='utf-8')

# 1. Görünmez Unicode karakterleri (Zero-Width Space, ZWNJ, ZWJ, LRM/RLM, BOM, Soft Hyphen, Word Joiner)
INVISIBLE_CHARS_RE = re.compile(r'[\u200b-\u200f\u202a-\u202e\u2060-\u2064\ufeff\u00ad\u034f]')
UNICODE_SPACES_RE = re.compile(r'[\u00a0\u2000-\u200a\u202f\u205f\u3000]')

# URL ve E-posta koruma desenleri
URL_TOKEN_RE = re.compile(r'^(?:https?://|www\.|bit\.ly/|t\.co/|tinyurl\.com/)\S+$', re.IGNORECASE)
EMAIL_TOKEN_RE = re.compile(r'^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}$', re.IGNORECASE)

# 2. Noktalama/sembol aralama hilesi (örn: b.o.n.u.s, b-a-h-i-s, k_a_z_a_n, s*l*o*t, c.a.s.1.n.0)
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

# Meşru ölçü/para birimi sonekleri (2gb, 12tl, 3d vb. korunur)
UNIT_TOKENS_RE = re.compile(r'^\d+(?:gb|mb|tb|tl|kr|dk|sn|sa|km|kg|gr|mg|ml|lt|d|g|k|m)$')

# Kısa 2-3 karakterli özel leetspeak kelimeleri
SHORT_LEET_MAP = {
    'v3': 've',
    '0l': 'ol',
    '3k': 'ek',
    '1l': 'il',
    '3n': 'en',
    '0n': 'on',
    '3t': 'et',
    '1ş': 'iş',
    'a7': 'at',
    '0y': 'oy',
    '7l': 'tl',
    'v1p': 'vıp'
}

# Kalın ünlü (a, o, u) içermesine rağmen '1' rakamının ince 'i' olması gereken kelime kökleri
FRONT_I_STEMS = (
    'bahis', 'casino', 'nakit', 'limit', 'kripto', 'spin', 'vip', 'link',
    'aktif', 'taksit', 'icra', 'iban', 'hediye', 'kredi', 'şifre', 'sifre',
    'çekiliş', 'ücretsiz', 'acil', 'bilgi', 'kilit', 'şüpheli', 'tebrik',
    'işlem', 'çevrim', 'getir', 'kesin', 'garanti', 'risk', 'site', 'fatura',
    'iptal', 'faiz', 'sahip', 'cazip', 'katil', 'tatil', 'mesai', 'bayi'
)


def clean_text_legacy(text):
    """Hafta 5-6'daki eski ön işleme fonksiyonu (karşılaştırma referansı)."""
    if not isinstance(text, str):
        return ""
    text = text.replace('İ', 'i').replace('I', 'ı').lower()
    if not text.startswith("http://") and not text.startswith("https://"):
        for _ in range(5):
            text = re.sub(r'\b([a-zçğıöşü0-9]{1,2})\s+(?=[a-zçğıöşü0-9]{1,2}\b)', r'\1', text)
    text = re.sub(r'(.)\1{2,}', r'\1', text)
    return text.strip()


def _deleet_span(span):
    """Tek bir alfanümerik kelime parçasındaki (span) leetspeak rakamlarını (0,1,3,4,5,7) çözümler."""
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

    # 0->o, 3->e, 4->a, 5->s, 7->t
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
    İP 3 Güçlendirilmiş Ön İşleme Motoru:
    1. Görünmez Unicode karakterlerini (Zero-Width, BOM, Soft Hyphen vb.) temizler.
    2. Türkçe uyumlu küçük harf dönüşümü yapar (İ -> i, I -> ı).
    3. URL ve e-posta dışındaki kelimelerde sembol tabanlı Leetspeak (@ -> a, $ -> s) ve
       noktalama/sembol aralama gizlemelerini (b.o.n.u.s, b-a-h-i-s, s*l*o*t) çözümler.
    4. Harf arası hileli boşlukları (p a r a -> para) birleştirir.
    5. Harf uzatmalarını (fırsaaaat -> fırsat) sadeleştirir.
    6. URL yapısını bozmadan Türkçe ünlü uyumuna duyarlı rakamsal Leetspeak (0/o, 1/i/ı, 3/e, 4/a, 5/s, 7/t) çözümler.
    """
    if not isinstance(text, str):
        return ""

    # 1. Görünmez Unicode karakterlerini sil ve özel boşlukları standartlaştır
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

    # 4. Harf arası boşluk hilesini birleştir (5 iterasyon)
    for _ in range(5):
        text = SPACE_RE.sub(r'\1', text)

    # 5. URL olmayan tokenlarda harf/rakam uzatmalarını sadeleştir ve rakamsal Leetspeak (0,1,3,4,5,7) çözümle
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


ADVERSARIAL_CASES = [
    # =========================================================================
    # KATEGORİ 1: Leetspeak Karakter Değişimleri (0/o, 1/i/ı, 3/e, @/a, $/s, 4/a, 5/s, 7/t) - 10 Mesaj
    # =========================================================================
    (
        1, "Leetspeak_Karakter_Degisimi",
        "GÜNCEL BAHİS VE BONUS FIRSATI! İlk yatırıma %200 çevrimsiz bonus hemen kaydol",
        "GÜNC3L B@H1S V3 B0NUS FIRS@TI! 1lk y@t1r1m@ %200 ç3vr1ms1z b0nus h3m3n k@yd0l"
    ),
    (
        2, "Leetspeak_Karakter_Degisimi",
        "TEBRİKLER! Çekilişten 50.000 TL nakit ödül kazandınız. Hemen arayın",
        "T3BR1KL3R! Ç3k1l1şt3n 50.000 7L n@k1t ödül k@z@nd1n1z. H3m3n @r@y1n"
    ),
    (
        3, "Leetspeak_Karakter_Degisimi",
        "Sayın vatandaş, adınıza açılmış icra takibi bulunmaktadır. Detay ve ödeme için hemen tıklayın",
        "S@y1n v@t@nd@ş, @d1n1z@ @ç1lm1ş 1cr@ t@k1b1 bulunm@kt@d1r. D3t@y v3 öd3m3 1ç1n h3m3n t1kl@y1n"
    ),
    (
        4, "Leetspeak_Karakter_Degisimi",
        "Casino ve slot oyunlarında kayıp bonusu hesabınıza eklendi! Hemen giriş yapın",
        "C@s1n0 v3 sl0t 0yunl@r1nd@ k@y1p b0nusu h3s@b1n1z@ 3kl3nd1! H3m3n g1r1ş y@p1n"
    ),
    (
        5, "Leetspeak_Karakter_Degisimi",
        "Acil nakit kredi limitiniz hazır! Kefilsiz evraksız anında 100.000 TL hesaba havale",
        "@c1l n@k1t kr3d1 l1m1t1n1z h@z1r! K3f1ls1z 3vr@ks1z @n1nd@ 100.000 7L h3s@b@ h@v@l3"
    ),
    (
        6, "Leetspeak_Karakter_Degisimi",
        "Hemen tıkla ve 250 Free Spin kazan! Slot oyunlarında büyük vurgun fırsatı",
        "H3m3n t1kl@ v3 250 Fr33 Sp1n k@z@n! Sl0t 0yunl@r1nd@ büyük vurgun f1rs@t1"
    ),
    (
        7, "Leetspeak_Karakter_Degisimi",
        "Kredi kartınızdan 4.250 TL harcama yapıldı. Siz yapmadıysanız hemen şifrenizi girin",
        "Kr3d1 k@rt1n1zd@n 4.250 7L h@rc@m@ y@p1ld1. S1z y@pm@d1ys@n1z h3m3n ş1fr3n1z1 g1r1n"
    ),
    (
        8, "Leetspeak_Karakter_Degisimi",
        "Deneme bonusu veren siteler! 500 TL çevrimsiz bonus anında hesabında",
        "D3n3m3 b0nusu v3r3n s1t3l3r! 500 7L ç3vr1ms1z b0nus @n1nd@ h3s@b1nd@"
    ),
    (
        9, "Leetspeak_Karakter_Degisimi",
        "Sayın üye, VIP üyeliğiniz aktif edilmiştir. 5000 TL hoşgeldin bonusu sizi bekliyor.",
        "S@y1n üy3, V1P üy3l1ğ1n1z @kt1f 3d1lm1şt1r. 5000 7L h0şg3ld1n b0nusu s1z1 b3kl1y0r."
    ),
    (
        10, "Leetspeak_Karakter_Degisimi",
        "Yatırımsız şartsız 100 TL deneme bonusu tüm slotlarda geçerli!",
        "Y@t1r1ms1z ş4rts1z 100 7L d3n3m3 b0nusu 7üm sl0tl@rd@ g3ç3rl1!"
    ),

    # =========================================================================
    # KATEGORİ 2: Görünmez Unicode Karakter Enjeksiyonu (\u200b, \u200c, \u200d, \ufeff, \u00ad, \u2060) - 10 Mesaj
    # =========================================================================
    (
        11, "Gorunmez_Unicode_Enjeksiyonu",
        "GÜNCEL BAHİS VE BONUS FIRSATI! İlk yatırıma %200 çevrimsiz bonus hemen kaydol",
        "G\u200bÜ\u200cN\u200dCEL B\u200bA\u200cH\u200dİS VE B\u200bO\ufeffN\u00adUS FIR\u200bSA\u200cTI! İ\u200blk ya\u00adtı\u200brıma %200 çe\u2060vrim\u200bsiz bo\u200bnus he\u200cmen kay\u200ddol"
    ),
    (
        12, "Gorunmez_Unicode_Enjeksiyonu",
        "TEBRİKLER! Çekilişten 50.000 TL nakit ödül kazandınız. Hemen arayın",
        "T\u200bE\u200cB\u200dRİKLER! Çe\u200bki\u200cliş\u200dten 50.000 T\u200bL na\u200dkit ö\ufeffdül ka\u00adzan\u200bdı\u200cnız. He\u2060men a\u200bra\u200cyın"
    ),
    (
        13, "Gorunmez_Unicode_Enjeksiyonu",
        "Sayın vatandaş, adınıza açılmış icra takibi bulunmaktadır. Detay ve ödeme için hemen tıklayın",
        "Sa\u200byın va\u200ctan\u200ddaş, a\u200bdı\u200cnıza a\u200dçıl\ufeffmış i\u00adcra ta\u2060ki\u200bbi bu\u200clun\u200dmaktadır. De\u200ctay ve ö\u200bde\u200dme i\u200bçin he\u200cmen tık\u200bla\u200cyın"
    ),
    (
        14, "Gorunmez_Unicode_Enjeksiyonu",
        "Casino ve slot oyunlarında kayıp bonusu hesabınıza eklendi! Hemen giriş yapın",
        "Ca\u200bsi\u200cno ve s\u200blot o\u200cyun\u200dlarında ka\ufeffyıp bo\u00adnu\u200bsu he\u2060sa\u200bbı\u200cnıza ek\u200blen\u200cdi! He\u200bmen gi\u200criş ya\u200dpın"
    ),
    (
        15, "Gorunmez_Unicode_Enjeksiyonu",
        "Acil nakit kredi limitiniz hazır! Kefilsiz evraksız anında 100.000 TL hesaba havale",
        "A\u200bcil na\u200ckit k\u200bre\u200ddi li\ufeffmi\u200bti\u200cniz ha\u00adzır! Ke\u2060fil\u200bsiz ev\u200crak\u200bsız a\u200bnın\u200cda 100.000 T\u200bL he\u200dsa\u200bba ha\u200bva\u200cle"
    ),
    (
        16, "Gorunmez_Unicode_Enjeksiyonu",
        "Hemen tıkla ve 250 Free Spin kazan! Slot oyunlarında büyük vurgun fırsatı",
        "He\u200bmen t\u200bık\u200cla ve 250 F\u200br\u200dee S\u200bp\ufeffin ka\u00adzan! S\u2060l\u200bot o\u200cyun\u200blarında bü\u200cyük vur\u200dgun fır\u200bsa\u200ctı"
    ),
    (
        17, "Gorunmez_Unicode_Enjeksiyonu",
        "Kart limitiniz 150.000 TL'ye yükseltildi. Onaylamak için kart bilgilerinizi giriniz",
        "K\u200bart li\u200bmi\u200ctiniz 150.000 T\u200bL'ye yük\u200bsel\u200dtildi. O\ufeffnay\u200bla\u200cmak i\u200bçin k\u00adart bil\u2060gi\u200ble\u200cri\u200dnizi gi\u200bri\u200cniz"
    ),
    (
        18, "Gorunmez_Unicode_Enjeksiyonu",
        "Son 2 saat! %50 kayıp bonusu ve canlı rulette ekstra kazanç fırsatı kaçırmayın.",
        "S\u200bon 2 sa\u200bat! %50 ka\u200cyıp bo\u200dnu\u200bsu ve can\ufefflı ru\u00adlet\u200bte eks\u2060tra ka\u200bzanç fır\u200csa\u200btı ka\u200bçır\u200cma\u200dyın."
    ),
    (
        19, "Gorunmez_Unicode_Enjeksiyonu",
        "Bankanızdan size özel 0 faizli kredi fırsatı! Hemen başvuru yapın",
        "Ban\u200bka\u200cnızdan si\u200cze ö\u200dzel 0 fa\ufeffiz\u200bli kre\u00addi fır\u2060sa\u200btı! He\u200bmen baş\u200cvu\u200bru ya\u200dpın"
    ),
    (
        20, "Gorunmez_Unicode_Enjeksiyonu",
        "E-Faturanızı görüntülemek ve icra takibini durdurmak için hemen tıklayın",
        "E-Fa\u200btu\u200cra\u200bnızı gö\u200brün\u200ctülemek ve i\u200bc\u200dra ta\u200bki\ufeffbini dur\u00addurmak i\u200bçin he\u2060men tık\u200bla\u200cyın"
    ),

    # =========================================================================
    # KATEGORİ 3: Noktalama / Sembol Aralama ve Boşluk/Uzatma Hileleri (b.o.n.u.s, b-a-h-i-s, k_a_z_a_n) - 10 Mesaj
    # =========================================================================
    (
        21, "Noktalama_Bosluk_Uzatma_Hilesi",
        "GÜNCEL BAHİS VE BONUS FIRSATI! İlk yatırıma %200 çevrimsiz bonus hemen kaydol",
        "G.Ü.N.C.E.L B.A.H.İ.S VE B-O-N-U-S F.I.R.S.A.T.I! İlk y.a.t.ı.r.ı.m.a %200 ç_e_v_r_i_m_s_i_z b*o*n*u*s h.e.m.e.n k-a-y-d-o-l"
    ),
    (
        22, "Noktalama_Bosluk_Uzatma_Hilesi",
        "TEBRİKLER! Çekilişten 50.000 TL nakit ödül kazandınız. Hemen arayın",
        "T.E.B.R.İ.K.L.E.R! Ç-e-k-i-l-i-ş-t-e-n 50.000 TL n_a_k_i_t ö*d*ü*l k.a.z.a.n.d.ı.n.ı.z. H-e-m-e-n a.r.a.y.ı.n"
    ),
    (
        23, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Sayın vatandaş, adınıza açılmış icra takibi bulunmaktadır. Detay ve ödeme için hemen tıklayın",
        "S.a.y.ı.n v.a.t.a.n.d.a.ş, a-d-ı-n-ı-z-a a.ç.ı.l.m.ı.ş i.c.r.a t-a-k-i-b-i b_u_l_u_n_m_a_k_t_a_d_ı_r. D.e.t.a.y ve ö_d_e_m_e için h.e.m.e.n t*ı*k*l*a*y*ı*n"
    ),
    (
        24, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Casino ve slot oyunlarında kayıp bonusu hesabınıza eklendi! Hemen giriş yapın",
        "C.a.s.i.n.o ve s-l-o-t o.y.u.n.l.a.r.ı.n.d.a k_a_y_ı_p b*o*n*u*s*u h.e.s.a.b.ı.n.ı.z.a e-k-l-e-n-d-i! H.e.m.e.n g.i.r.i.ş y_a_p_ı_n"
    ),
    (
        25, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Acil nakit kredi limitiniz hazır! Kefilsiz evraksız anında 100.000 TL hesaba havale",
        "A.c.i.l n-a-k-i-t k_r_e_d_i l*i*m*i*t*i*n*i*z h.a.z.ı.r! K.e.f.i.l.s.i.z e-v-r-a-k-s-ı-z a_n_ı_n_d_a 100.000 TL h.e.s.a.b.a h-a-v-a-l-e"
    ),
    (
        26, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Hemen tıkla ve 250 Free Spin kazan! Slot oyunlarında büyük vurgun fırsatı",
        "H.e.m.e.n t-ı-k-l-a ve 250 F.r.e.e S_p_i_n k*a*z*a*n! S.l.o.t o-y-u-n-l-a-r-ı-n-d-a b_ü_y_ü_k v*u*r*g*u*n f.ı.r.s.a.t.ı"
    ),
    (
        27, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Kredi kartınızdan 4.250 TL harcama yapıldı. Siz yapmadıysanız hemen şifrenizi girin",
        "K.r.e.d.i k-a-r-t-ı-n-ı-z-d-a-n 4.250 TL h_a_r_c_a_m_a y.a.p.ı.l.d.ı. Siz y-a-p-m-a-d-ı-y-s-a-n-ı-z h.e.m.e.n ş*i*f*r*e*n*i*z*i g.i.r.i.n"
    ),
    (
        28, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Deneme bonusu veren siteler! 500 TL çevrimsiz bonus anında hesabında",
        "D.e.n.e.m.e b-o-n-u-s-u v.e.r.e.n s_i_t_e_l_e_r! 500 TL ç*e*v*r*i*m*s*i*z b.o.n.u.s a.n.ı.n.d.a h-e-s-a-b-ı-n-d-a"
    ),
    (
        29, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Sayın üye, VIP üyeliğiniz aktif edilmiştir. 5000 TL hoşgeldin bonusu sizi bekliyor.",
        "S.a.y.ı.n ü.y.e, V.I.P ü-y-e-l-i-ğ-i-n-i-z a_k_t_i_f e*d*i*l*m*i*ş*t*i*r. 5000 TL h.o.ş.g.e.l.d.i.n b-o-n-u-s-u s_i_z_i b*e*k*l*i*y*o*r."
    ),
    (
        30, "Noktalama_Bosluk_Uzatma_Hilesi",
        "Kripto yatırımında garantili kazanç! Günlük %10 kâr garantisiyle hemen katılın.",
        "K.r.i.p.t.o y-a-t-ı-r-ı-m-ı-n-d-a g_a_r_a_n_t_i_l_i k*a*z*a*n*ç! G.ü.n.l.ü.k %10 kâr g-a-r-a-n-t-i-s-i-y-l-e h.e.m.e.n k_a_t_ı_l_ı_n."
    ),

    # =========================================================================
    # KATEGORİ 4: Hibrit / Çok Katmanlı Saldırılar (Leetspeak + Görünmez Unicode + Sembol + Boşluk + Uzatma) - 10 Mesaj
    # =========================================================================
    (
        31, "Hibrit_Cok_Katmanli_Saldiri",
        "GÜNCEL BAHİS VE BONUS FIRSATI! İlk yatırıma %200 çevrimsiz bonus hemen kaydol",
        "G.Ü.N.C.3.L B\u200b@H1S V3 B-0-N-U-S FIRS@@@TI! 1lk y\u200c@t1r1m@ %200 ç_3_v_r_1_m_s_1_z b000nus h.3.m.3.n k@yd0l"
    ),
    (
        32, "Hibrit_Cok_Katmanli_Saldiri",
        "TEBRİKLER! Çekilişten 50.000 TL nakit ödül kazandınız. Hemen arayın",
        "T.3.B.R.1.K.L.3.R! Ç\u200b3k1l1şt3n 50.000 7L n-@-k-1-t ö\ufeffdül k_@_z_@_n_d_1_n_1_z. H.3.m.3.n @r@y111n"
    ),
    (
        33, "Hibrit_Cok_Katmanli_Saldiri",
        "Sayın vatandaş, adınıza açılmış icra takibi bulunmaktadır. Detay ve ödeme için hemen tıklayın",
        "S@y1n v.a.t.a.n.d.a.ş, @d1n1z@ @ç1lm1ş 1.c.r.@ t\u200b@k1b1 bulunm@kt@d1r. D-3-t-@-y v3 ö_d_3_m_3 1ç1n h.3.m.3.n t.1.k.l.@.y.1.n"
    ),
    (
        34, "Hibrit_Cok_Katmanli_Saldiri",
        "Casino ve slot oyunlarında kayıp bonusu hesabınıza eklendi! Hemen giriş yapın",
        "C.@.s.1.n.0 v3 s-l-0-t 0yunl@r1nd@ k\u200b@y1p b000nusu h_3_s_@_b_1_n_1_z_@ 3kl3nd111! H.3.m.3.n g.1.r.1.ş y@p1n"
    ),
    (
        35, "Hibrit_Cok_Katmanli_Saldiri",
        "Acil nakit kredi limitiniz hazır! Kefilsiz evraksız anında 100.000 TL hesaba havale",
        "@.c.1.l n-@-k-1-t k\u200br3d1 l_1_m_1_t_1_n_1_z h@z111r! K3f1ls1z 3vr@ks1z @n1nd@ 100.000 7L h3s@b@ h.a.v.a.l.3"
    ),
    (
        36, "Hibrit_Cok_Katmanli_Saldiri",
        "Hemen tıkla ve 250 Free Spin kazan! Slot oyunlarında büyük vurgun fırsatı",
        "H.3.m.3.n t\u200b1kl@ v3 250 F-r-3-3 S_p_1_n k*a*z*a*n! S.l.0.t 0yunl@r1nd@ büyüüük v.u.r.g.u.n f1rs@t1"
    ),
    (
        37, "Hibrit_Cok_Katmanli_Saldiri",
        "Kart limitiniz 150.000 TL'ye yükseltildi. Onaylamak için kart bilgilerinizi giriniz",
        "K@rt l.1.m.1.t.1.n.1.z 150.000 7L'ye yüks3lt1ld1. 0n@yl@m@k 1ç1n k\u200b@rt b-1-l-g-1-l-3-r-1-n-1-z-1 g.1.r.1.n.1.z"
    ),
    (
        38, "Hibrit_Cok_Katmanli_Saldiri",
        "Son 2 saat! %50 kayıp bonusu ve canlı rulette ekstra kazanç fırsatı kaçırmayın.",
        "S0n 2 s@@t! %50 k.a.y.1.p b\u200b000nusu v3 c-@-n-l-1 rul3tt3 3kstr@ k_@_z_@_n_ç f1rs@t1 k.a.ç.1.r.m.@.y.1.n."
    ),
    (
        39, "Hibrit_Cok_Katmanli_Saldiri",
        "Bankanızdan size özel 0 faizli kredi fırsatı! Hemen başvuru yapın",
        "B@nk@n1zd@n s1z3 öz3l 0 f.a.1.z.l.1 k\u200br3d1 f-1-r-s-@-t-1! H_3_m_3_n b@şvuru y.a.p.1.n"
    ),
    (
        40, "Hibrit_Cok_Katmanli_Saldiri",
        "Yatırımsız şartsız 100 TL deneme bonusu tüm slotlarda geçerli!",
        "Y.@.t.1.r.1.m.s.1.z ş\u200b4rts1z 100 7L d-3-n-3-m-3 b_0_n_u_s_u 7üm s.l.0.t.l.@.r.d.@ g3ç3rl111!"
    ),
]


def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    model = joblib.load(os.path.join(base_dir, "sms_model.joblib"))
    vectorizer = joblib.load(os.path.join(base_dir, "sms_vectorizer.joblib"))

    rows = []
    old_caught_050 = 0
    new_caught_050 = 0
    old_caught_035 = 0
    new_caught_035 = 0

    for sid, cat, ref_msg, adv_msg in ADVERSARIAL_CASES:
        ref_clean = clean_text_legacy(ref_msg)
        old_clean = clean_text_legacy(adv_msg)
        new_clean = clean_text(adv_msg)

        p_ref = float(model.predict_proba(vectorizer.transform([ref_clean]))[0, 1])
        p_old = float(model.predict_proba(vectorizer.transform([old_clean]))[0, 1])
        p_new = float(model.predict_proba(vectorizer.transform([new_clean]))[0, 1])

        if p_old >= 0.50:
            old_caught_050 += 1
        if p_new >= 0.50:
            new_caught_050 += 1
        if p_old > 0.35:
            old_caught_035 += 1
        if p_new > 0.35:
            new_caught_035 += 1

        rows.append({
            "Saldiri_ID": sid,
            "Saldiri_Kategorisi": cat,
            "Temiz_Referans_Mesaj": ref_msg,
            "Saldiri_Gizlenmis_Mesaj": adv_msg,
            "Eski_Cleaner_Ciktisi": old_clean,
            "Guclendirilmis_Cleaner_Ciktisi": new_clean,
            "Referans_Model_P": round(p_ref, 4),
            "Eski_Model_P": round(p_old, 4),
            "Guclendirilmis_Model_P": round(p_new, 4),
            "Eski_Yakalama_Durumu": "YAKALANDI (TP)" if p_old >= 0.50 else "KACTI (FN)",
            "Guclendirilmis_Yakalama_Durumu": "YAKALANDI (TP)" if p_new >= 0.50 else "KACTI (FN)"
        })

    df_out = pd.DataFrame(rows)
    csv_path = os.path.join(base_dir, "adversarial_test_seti.csv")
    xlsx_path = os.path.join(base_dir, "adversarial_test_seti.xlsx")
    df_out.to_csv(csv_path, index=False, sep=';', encoding='utf-8-sig')
    df_out.to_excel(xlsx_path, index=False)

    # Üst klasöre (Müh.tasarım proje) de kaydet
    parent_dir = os.path.dirname(os.path.dirname(base_dir))
    if os.path.exists(parent_dir):
        df_out.to_csv(os.path.join(parent_dir, "adversarial_test_seti.csv"), index=False, sep=';', encoding='utf-8-sig')
        df_out.to_excel(os.path.join(parent_dir, "adversarial_test_seti.xlsx"), index=False)

    print("=" * 100)
    print(" İP 3: FİLTRE ATLATMA (ADVERSARIAL / OBFUSCATION) DAYANIKLILIK TESTİ SONUÇLARI (40 SALDIRI MESAJI)")
    print("=" * 100)
    for r in rows:
        print(
            f"[{r['Saldiri_ID']:02d}] {r['Saldiri_Kategorisi']:<30} | "
            f"Ref P: {r['Referans_Model_P']:.4f} | "
            f"Eski P: {r['Eski_Model_P']:.4f} ({r['Eski_Yakalama_Durumu']:<14}) -> "
            f"Yeni P: {r['Guclendirilmis_Model_P']:.4f} ({r['Guclendirilmis_Yakalama_Durumu']})"
        )

    print("-" * 100)
    print(f"Toplam Saldırı Senaryosu                     : {len(rows)}")
    print(f"Eski SmsCleaner Yakalama Oranı (P >= 0.50)   : {old_caught_050} / {len(rows)} (%{old_caught_050/len(rows)*100:.1f})")
    print(f"Güçlendirilmiş SmsCleaner Yakalama (P >= 0.50): {new_caught_050} / {len(rows)} (%{new_caught_050/len(rows)*100:.1f})")
    print(f"Eski SmsCleaner Yakalama Oranı (P >  0.35)   : {old_caught_035} / {len(rows)} (%{old_caught_035/len(rows)*100:.1f})")
    print(f"Güçlendirilmiş SmsCleaner Yakalama (P >  0.35): {new_caught_035} / {len(rows)} (%{new_caught_035/len(rows)*100:.1f})")
    print(f"[+] CSV kaydedildi: {csv_path}")


if __name__ == "__main__":
    main()

# Fullhdfilmizlesene CS3

CloudStream icin CS3 Cikarici uygulamasi ile otomatik uretilen eklenti projesi.

## GitHub Actions

Bu repo GitHub'a yuklendiginde `main` veya `master` branch'e yapilan push ile otomatik derlenir.
Ayrica Actions > Build CloudStream CS3 > Run workflow ile elle baslatilabilir.

Derleme tamamlaninca workflow sayfasindaki **Artifacts** bolumunden `Fullhdfilmizlesene-CS3` paketini indirebilirsin.
Icinde eklenti icin bir `.cs3` dosyasi bulunur.

## Moduller

- `Fullhdfilmizlesene` (com.fullhdfilmizlesene)

## Analiz Ozeti

```
SİTE: fullhdfilmizlesene.now

✔ Kategori/menü linkleri: 80
✔ Film kartı seçici: div.fut-box (10 adet)
✘ Arama: form bulunamadı (varsayılan ?s= kullanıldı)
✔ Detay sayfası: başlık=h1.blok-baslik açıklama=meta
✘ Dizi bölümleri: yok (film olarak ayarlandı)
✔ Oynatıcı: 3 medya, 1 iframe, 0 altyazı
     iframe: rapidvid.org
     medya : https://s34.imgz.me/m4/DJ5coJSfpl4lZQV2YyqSDv1RGP4kZQtjpP5RIHSZYxthZwL0YHuRGD/eng-3.vtt
     medya : https://s34.imgz.me/m9/DJ5coJSfpl4lZQV2YyqSDv1RGP4kZQtjpP5RIHSZYxthZwL0YHuRGD/tur-2-default.vtt
     medya : https://s34.imgz.me/m5/DJ5coJSfpl4lZQV2YyqSDv1RGP4kZQtjpP5RIHSZYxthZwL0YHuRGD/tur-1.vtt

GİZLEME / ŞİFRELEME
· p.a.c.k.e.r yok
· Base64 katmanı yok
· AES/CryptoJS yok
! Cloudflare doğrulaması var (CloudflareKiller eklendi)

API/ajax uçları: 5
     [POST] https://rapidvid.org/ifr/vod/player-log.php
     https://www.google-analytics.com/g/collect?v=2&tid=G-ZRR1WHXHVX&gtm=45je6a72v9227274004za208zd9227274004xf1&_p…
     https://www.google-analytics.com/g/collect?v=2&tid=G-ZRR1WHXHVX&gtm=45je6a72v9227274004za208zd9227274004xf1&_p…
     https://www.google-analytics.com/g/collect?v=2&tid=G-ZRR1WHXHVX&gtm=45je6a72v9227274004za200zd9227274004xf1&_p…
     https://www.google.com/g/collect?v=2&tid=G-ZRR1WHXHVX&gtm=45je6a72v9227274004za200zd9227274004xf1&_p=179159251…

══════ YAPILACAKLAR ══════
Hiç eksik yok. KOD sekmesindeki dosyayı veya PAKET (.zip) çıktısını kullanabilirsin.

Sonraki adım: 📦 PAKET (.zip) → içindekileri GitHub deposuna yükle → Actions sekmesi Fullhdfilmizlesene-CS3 (.cs3) dosyasını üretir.

```

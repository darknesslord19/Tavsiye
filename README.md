# Iptv Bridge CS3

M3U / M3U8 (IPTV) listelerini **ad + link** olarak kaydet; kaydettiğin her liste CloudStream'de ayrı bir kaynak (repo gibi) olarak görünür.
Bir de GitHub'dan gelen, kullanıcının silemediği **yönetici listesi** var.

## Kurulum
1. Yönetici listesi adresi `IptvStore.kt` içindeki `ADMIN_SOURCE` sabitidir (şu an Firebase `pastes/Cloudstreamtv.json`). İçeriği `Cloudstreamtv.json` örneğindeki gibidir; GitHub raw adresiyle de değiştirilebilir.
2. Repoyu GitHub'a yükle, Actions > *Build CloudStream CS3* ile derle, artifact içindeki `IptvBridge.cs3`'ü yükle.
3. Ayarlar > Eklentiler > Iptv Bridge yanındaki dişliye bas.

## Kullanım
Ekranda altta iki sekme var: **Kullanıcı** ve **Yönetici**. Her listede tek bir **Aktif / Pasif** düğmesi var; pasif liste CloudStream'de kaynak olarak görünmez (silinmez, istediğin an tekrar aktif edilir).
- **Liste ekle:** *Yeni liste* düğmesi, ad + M3U/M3U8 linki > *Listeyi kaydet*. GitHub `blob` linkleri otomatik raw'a çevrilir.
- **Listelerim:** her liste için *Test et* (kanal ve grup sayısı), *Düzenle*, *Sil* (onaylı).
- **Yönetici listeleri:** kilitli; silinemez/düzenlenemez. *Yenile* ile GitHub'dan çekilir, 6 saatte bir kendiliğinden yenilenir.
- Değişiklikten sonra CloudStream'i kapatıp aç (kaynaklar açılışta yüklenir).

## Yönetici listesi biçimi (`ADMIN_SOURCE` adresindeki dosya)
```json
[{"name":"Spor","url":"https://.../spor.m3u"},{"name":"Haber","url":"https://.../haber.m3u8"}]
```
ya da `{"Spor":"https://.../spor.m3u"}`, `{"lists":[...]}`; ya da doğrudan bir `.m3u` dosyası (tek liste olarak eklenir).

## Kategorileri gizleme
Her listede **Kategoriler** düğmesi, listenin gruplarını göz simgesiyle gösterir. Göze basınca kategori gizlenir/gösterilir (gizli kategori ana sayfa satırından, *Tüm kanallar*'dan ve aramadan çıkar). *Hepsini göster / gizle* toplu işlem yapar. Kategoriler listenin ilk indirilmesinden sonra görünür.

## CloudStream'de görünüm
Her liste bir kaynaktır: **Tüm kanallar** + en kalabalık 15 grup ayrı satırlar (60'ar kanal, sayfalı). Arama kanal ve grup adında çalışır.
Gruplar ilk indirmede kaydedilir; yeni eklenen listenin grup satırları bir sonraki açılışta görünür.
`.mp4/.mkv` gibi dosyalar film, diğerleri canlı yayın olarak gösterilir.

## Desteklenen M3U ayrıntıları
`tvg-name`, `tvg-logo`, `group-title`, `#EXTGRP`, `#EXTVLCOPT` (user-agent, referrer, origin), `#EXTHTTP` (JSON), `url|User-Agent=..&Referer=..`.

## Sorun giderme
- **Test et** düğmesi hatanın nedenini yazar: `HTTP 403` (sunucu reddetti), `liste yerine web sayfası geldi` (kısa link/giriş sayfası), `M3U listesi değil` vb.
- İstek sırayla üç kimlikle denenir: oynatıcı (VLC), mobil tarayıcı, kimliksiz. Kısa linkler (tinyurl vb.) yönlendirmeleri takip edilir.
- Adres kanal listesi değil tek bir `.m3u8` akışıysa (`#EXT-X-STREAM-INF` / `#EXT-X-TARGETDURATION`), tek kanallı liste olarak eklenir.

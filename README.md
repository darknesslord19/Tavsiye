# Site Bridge CS3

`MainActivity.java` (Sketchware film uygulaması) içindeki site tarama sistemini CloudStream eklentisine çeviren proje.
Her site CloudStream'de ayrı bir kaynak olarak görünür.

## Derleme

Repoyu GitHub'a yükle. `main`/`master` push'unda ya da Actions > *Build CloudStream CS3* > *Run workflow* ile derlenir.
Artifacts bölümündeki `SiteBridge-CS3` içinde `SiteBridge.cs3` bulunur.

## Kullanım

1. `.cs3` dosyasını CloudStream'e yükle.
2. Ayarlar > Eklentiler > Site Bridge yanındaki dişliye bas.
3. **Site ekle**: ad (isteğe bağlı) + site adresi. Ya da **GitHub / JSON listesi** ekle.
4. CloudStream'i kapatıp aç. Siteler kaynak seçicide görünür.

## Site listesi biçimi (GitHub / Firebase / gist)

Aşağıdakilerden biri olabilir:

```json
{"sites":[{"name":"Site 1","url":"https://site1.com"},{"name":"Site 2","url":"https://site2.com","rules":{}}]}
```
```json
[{"name":"Site 1","url":"https://site1.com"}]
```
```json
{"Site 1":"https://site1.com","Site 2":"https://site2.com"}
```

Girilebilecek liste adresleri: `github.com/kullanici/repo/blob/main/sites.json`, `raw.githubusercontent.com/...`,
gist adresi, Firebase `.json` adresi, ya da sadece `github.com/kullanici/repo` (içinde `sites.json` / `siteler.json` aranır).
Liste 6 saatte bir kendiliğinden yenilenir; ayarlardan elle de yenilenebilir.
Varsayılan liste `SiteStore.kt` içindeki `DEFAULT_SOURCE` sabitidir (MainActivity'deki Firebase adresi). Kendi GitHub adresinle değiştirebilirsin.

## Site kuralları (rules)

Otomatik tanıma çalışmazsa bir sitenin kaydına `rules` eklenir (MainActivity'dekiyle aynı biçim, CSS seçici + `@oznitelik`):

```json
{
  "movies":     {"item":"article.film", "link":"a@href", "image":"img@data-src||img@src", "title":"img@alt"},
  "sections":   {"item":"section.row", "title":"h2@text"},
  "categories": {"item":"nav a", "name":"@text", "link":"@href", "limit":40},
  "next":       "a.next@href",
  "player":     {"video":"video source@src", "videoRegex":"file:\"(.*?)\"", "iframe":"div.player iframe@data-src"},
  "search":     "https://site.com/?s={q}"
}
```

`search` yeni eklendi: CloudStream arama kutusuna yazılan metin için adres şablonu. Verilmezse sırayla
`/?s=`, `/arama/`, `/search?q=`, `/ara?q=` biçimleri denenir.

## MainActivity ile farklar

- Ana sayfa: sayfa başlıklarına göre satırlar + ilk 6 kategori satırı. Hero bandı yok, "İzlemeye devam et" CloudStream'in kendisinde.
- "Site Ara" sekmesindeki adresle gezme yerine CloudStream'in arama kutusu kullanılır.
- Oynatma: sayfadaki `.mp4/.m3u8` → iframe (CloudStream'in hazır çıkarıcıları) → iframe içi tarama → gerçek tarayıcıda ağ dinleme.
  MainActivity'deki reklam temizleyen WebView oynatıcı yoktur; video çıkarılamazsa kaynak link vermez.
- M3U listesi olan siteler kategorili film arşivi olarak çalışır (başlık/afiş `#sb=` parçasında taşınır).
- 403/503 veren sitelerde CloudStream'in Cloudflare aşıcısı denenir.

## Dosyalar

- `Scraper.kt` — MainActivity'deki ayrıştırma motoru (regex + Jsoup tabanlı kural motoru). CloudStream'e bağımlı değil.
- `SiteStore.kt` — kullanıcı siteleri, liste adresleri, GitHub adres çevirme, önbellek.
- `SiteProvider.kt` — her site için CloudStream kaynağı (ana sayfa, arama, detay, bağlantılar).
- `Net.kt` — sayfa indirme + önbellek, paralel çalıştırma yardımcıları.
- `SettingsDialog.kt`, `SettingsHtml.kt` — dişli ayar ekranı.

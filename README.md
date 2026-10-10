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

## Ana sayfa ve kategoriler

- Kaynak seçicide her sitenin altında **Ana Sayfa** + en çok **15 kategori** ayrı satır olarak görünür (sayfalamalı, CloudStream'in kendi satırları).
- İlk açılışta kategoriler ana sayfanın içinde gösterilir ve önbelleğe yazılır; sonraki açılışlarda ayrı satır olurlar.
- Kategorisi bulunamayan sitelerde sonraki sayfalar ("Sayfa 2", "Sayfa 3") ek satır olarak gösterilir.
- Site bir an açılmazsa son başarılı ana sayfa gösterilir. İstekler 2 kez denenir, yönlendirilen son adres (alan adı değişimi) esas alınır.

## Oynatma

Sırayla denenir (bulunan ilk çalışan kaynaklar eklenir):
1. Sayfadaki `.mp4/.m3u8/.mpd` (packed JS ve base64 içi dahil, reklam videoları elenir)
2. Sayfadaki iframe'ler (`src`, `data-src`, `data-embed`, JSON `iframe`, base64) ve `?kaynak=2` gibi alternatif kaynak sayfaları
3. Her iframe için: CloudStream'in hazır çıkarıcıları → iframe sayfasını tarama (iç içe iframe dahil) → gerçek tarayıcıda ağ dinleme
4. Hiçbiri olmazsa sayfanın kendisi tarayıcıda açılıp ağ dinlenir

Tarayıcı adımında sayfadaki "oynat" düğmeleri otomatik tıklanır. Sayfada çıplak yazılmış `/embed/...` adresleri de iframe sayılır.
Bulunan her video adresi oynatıcıya verilmeden önce kısa bir istekle yoklanır; 4xx/5xx veren ya da video yerine HTML dönen adresler atlanır ("Sunucu hatası 2004" önlenir).
Takılan adımlar 20-35 sn'de bırakılır, tüm akış kilitlenmez.

## Site testi

Dişlideki ayar ekranında her sitenin yanında **Test** düğmesi ve **Tüm siteleri test et** var. Test ana sayfayı, kategoriyi, ilk içeriği ve oynatma bağlantısını dener,
raporu alta yazar (sonunda "— oynatma adımları —" bölümü: hangi adımda ne bulundu / nerede takıldı) ve sitenin yanına ✓ / ✗ etiketi koyar. Çalışmayan bir site için raporu gönderirsen neden çalışmadığı hemen görülür.

## MainActivity ile farklar

- Hero bandı yok, "İzlemeye devam et" CloudStream'in kendisinde.
- "Site Ara" sekmesindeki adresle gezme yerine CloudStream'in arama kutusu kullanılır.
- MainActivity'deki reklam temizleyen WebView oynatıcı yoktur; video çıkarılamazsa kaynak link vermez.
- M3U listesi olan siteler kategorili film arşivi olarak çalışır (başlık/afiş `#sb=` parçasında taşınır).
- 403/503 veren sitelerde CloudStream'in Cloudflare aşıcısı denenir (User-Agent'ı aşıcıya bırakır; bir kez geçen site hatırlanır ve sonraki isteklerde baştan aşıcıyla açılır).
- Yerleşik liste (`DEFAULT_SOURCE`) ayarlarda gösterilmez ve silinemez; kullanıcı yalnızca kendi listelerini görür/siler.

## Dosyalar

- `Scraper.kt` — MainActivity'deki ayrıştırma motoru (regex + Jsoup tabanlı kural motoru). CloudStream'e bağımlı değil.
- `SiteStore.kt` — kullanıcı siteleri, liste adresleri, GitHub adres çevirme, önbellek.
- `SiteProvider.kt` — her site için CloudStream kaynağı (ana sayfa, arama, detay, bağlantılar).
- `Net.kt` — sayfa indirme (yeniden deneme, yönlendirme, önbellek), paralel/zaman aşımlı çalıştırma yardımcıları.
- `SettingsDialog.kt`, `SettingsHtml.kt` — dişli ayar ekranı.

# Nuvio Bridge CS3

Nuvio JS provider repolarını CloudStream içinde çalıştıran eklenti projesi.

## GitHub Actions

Bu repo GitHub'a yüklendiğinde `main` veya `master` branch'e yapılan push ile otomatik derlenir.
Ayrıca Actions > Build CloudStream CS3 > Run workflow ile elle başlatılabilir.

Derleme tamamlanınca workflow sayfasındaki **Artifacts** bölümünden `NuvioBridge-CS3` paketini indirebilirsin.
İçinde `NuvioBridge.cs3` dosyası bulunur.

## Kullanım

1. `.cs3` dosyasını CloudStream'e yükle.
2. Ayarlar > Eklentiler > Nuvio Bridge yanındaki dişli simgesine bas.
3. Nuvio reposunun `manifest.json` adresini ekle.

## Notlar

- TMDB API anahtarı `NuvioBridgeProvider.kt` içindeki `TMDB_KEY` alanındadır.
- Ayarlar ekranında repo adı görünür ve her sağlayıcı ayrı ayrı açılıp kapatılabilir.
- Sağlayıcılar paralel çalışır (en fazla 8 aynı anda, toplam bekleme 45 saniye).
- Ana sayfada TMDB trend ve popüler listeleri gösterilir.
- Manifest şeması (`scrapers`/`providers`, `filename`) ve provider fonksiyon imzası varsayımdır; gerçek bir repoyla test edilmemiştir.
- Gerçek provider'lar `cheerio`, `URL` gibi ortam parçaları isteyebilir; `JsRunner.kt` içindeki `PRELUDE` bölümüne eklenmeleri gerekebilir.

## Modüller

- `NuvioBridge` (com.example.nuviobridge)

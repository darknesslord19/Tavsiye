# Darknes Lord IPTV CS3

Kendi M3U/IPTV listelerinizi CloudStream içinde yöneten eklenti projesi.

## GitHub Actions

Bu repo GitHub'a yüklendiğinde `main` veya `master` branch'e yapılan push ile otomatik derlenir.
Ayrıca Actions > Build CloudStream CS3 > Run workflow ile elle başlatılabilir.

Derleme tamamlanınca workflow sayfasındaki **Artifacts** bölümünden `DarknesLordIPTV-CS3` paketini indirebilirsin.
İçinde `DarknesLordIPTV.cs3` dosyası bulunur.

## Kullanım

1. `.cs3` dosyasını CloudStream'e yükle.
2. Ayarlar > Eklentiler > Darknes Lord yanındaki dişli simgesine bas.
3. **Yeni IPTV listesi ekle** ile M3U linkini yapıştır (kısaltılmış link olabilir).

## Özellikler

- Gösterim modu anahtarı: **kapalı** → tüm aktif listeler tek "Darknes Lord" ana sayfasında sıralanır; **açık** → her aktif liste CloudStream kaynak seçicide kendi adıyla ayrı kaynak olur. Değişiklik için CloudStream'i kapatıp yeniden açmak gerekir.
- Başlıkta bilgi (i) düğmesi: uygulama hakkında popup; Telegram butonu (https://t.me/darknes_lord) ve en altta geliştirici satırı.
- Liste kartları: aktif/pasif anahtarı, yenile (test), düzenle, sil.
- Her listenin kategorileri: göz ikonuyla göster/gizle, arama, "tümünü göster/gizle".
- Link kaydedilmeden önce çözülür ve okunur; çalışmayan veya geçersiz link eklenemez.
- Kısaltılmış (bit.ly, tinyurl…) ve yönlendirmeli linkler otomatik çözülür; HTML ara sayfadaki M3U linki bulunur.
  GitHub `blob`, Gist ve Pastebin linkleri ham adrese çevrilir.
- Sonradan çalışmaz hale gelen liste kırmızı görünür ve ana sayfada gösterilmez.
- Oynatmadan önce yayın linki çözülür; kesin ölü (404/410, sunucu yok) kanallar açılmaz.
- "Tüm listeleri test et" düğmesi her listenin durumunu raporlar. Sürüm etiketi `Diagnostics.kt` içindeki `BUILD`.

## Notlar

- Ayarlar ekranı XML/androidx kullanmaz: `SettingsDialog.kt` (Dialog + WebView) ve `SettingsHtml.kt` (gömülü HTML).
- Ağ katmanı (`Net.kt`) yalnızca `java.net` kullanır; yönlendirmeleri elle takip eder.
- Reklam/captcha gerektiren veya JavaScript ile yönlendiren kısaltıcılar çözülemez.
- Kanallar tek tek önceden taranmaz; ölü kanal oynatma anında elenir.
- ClearKey DRM desteği yoktur.
- Link çözümleme/M3U ayrıştırma kodu JVM'de test edildi; CloudStream'e bağlanan kısım gerçek cihazda denenmedi.

## Modüller

- `DarknesLordIPTV` (com.darkneslord.iptv)

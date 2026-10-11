# Darknes Lord – CloudStream IPTV eklentisi

Kendi M3U/IPTV listelerinizi tek yerden yönetin.

* **İki sayfa:** altta *Kullanıcı* ve *Yönetici* ikonları
* **Her liste ayrı kaynak:** kaydettiğiniz her liste CloudStream'de ayrı bir kaynak olarak görünür (ekle/aktif/pasif/sil sonrası CloudStream'i kapatıp açın)
* **Yönetici listeleri:** `ornek/Cloudstreamtv.json` biçiminde, Firebase'den uzaktan gelir; silinemez, yalnızca aktif/pasif yapılır. Adres: `Store.kt` içindeki `AdminSource.URL`
* **Tek Aktif/Pasif düğmesi** (kartın sağ üstü)

* Liste kartları: düzenle, sil, yenile (test et)
* Her listenin kategorileri: göz ikonu ile göster / gizle, arama, "tümünü göster/gizle"
* Kısaltılmış (bit.ly, tinyurl…) ve yönlendirmeli linkler otomatik çözülür; GitHub `blob`, Gist ve Pastebin linkleri ham adrese çevrilir
* Link kaydedilmeden önce okunur: çalışmayan / geçersiz link eklenemez
* Çalışmayan duruma düşen liste kırmızı görünür ve ana sayfada gösterilmez
* Kesin ölü (404/410, sunucu yok) kanallar oynatılmaya çalışılmaz

## Derleme (bilgisayarsız, GitHub ile)
1. GitHub'da yeni bir depo açın ve bu klasörün içeriğini yükleyin.
2. **Actions → Derle (.cs3) → Run workflow**.
3. Bitince:
   * **Artifacts → IPTVProvider-cs3** : `.cs3` dosyası, veya
   * CloudStream → Ayarlar → Eklentiler → **Depo ekle** →  
     `https://raw.githubusercontent.com/KULLANICI/DEPO/builds/repo.json`  
     sonra listeden **Darknes Lord**'u indirin.
4. Eklentiler ekranında Darknes Lord'un yanındaki **dişli** simgesi yönetim panelini açar.

## Yerel derleme
JDK 17 + Android SDK kurulu olmalı: `./gradlew make` → `IPTVProvider/build/IPTVProvider.cs3`

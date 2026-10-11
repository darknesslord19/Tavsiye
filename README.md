# Darknes Lord – CloudStream IPTV eklentisi

M3U / M3U8 (IPTV) linklerini **ad + link** olarak kaydet; her liste CloudStream'de ayrı bir kaynak olarak görünür.

* **İki sekme:** *Kullanıcı* (kendi listelerin) ve *Yönetici* (uzaktan gelen, silinemeyen listeler)
* Liste kartları: **Aktif / Pasif** tek düğme, Test et, Düzenle, Sil (onaylı)
* **Kategoriler:** her listenin gruplarını göz simgesiyle göster / gizle, toplu göster-gizle
* Kısa linkler (tinyurl vb.) takip edilir, ara sayfadaki (meta-refresh / ilk `.m3u`) liste bulunur; GitHub `blob`, Gist ve Pastebin linkleri ham adrese çevrilir
* Tek `.m3u8` akışı da kaydedilebilir (tek kanallı liste olur)
* Test et, hatanın nedenini yazar (HTTP 403, web sayfası geldi, M3U değil …)

## Yönetici listesi
Adres `IPTVProvider/src/main/kotlin/com/darkneslord/iptv/IptvStore.kt` içindeki `ADMIN_SOURCE` sabitidir
(şu an `.../pastes/Cloudstreamtv.json`). Dosya biçimi için `ornek/Cloudstreamtv.json`'a bak:
```json
[{"name":"Haber","url":"https://.../haber.m3u"}]
```
`{"Ad":"link"}`, `{"lists":[...]}` ya da doğrudan bir `.m3u` dosyası da olur. Kullanıcı yönetici listesini silemez/düzenleyemez, yalnızca pasif yapabilir.

## Derleme (bilgisayarsız, GitHub ile)
1. GitHub'da yeni bir depo açın ve bu klasörün içeriğini yükleyin.
2. **Actions → Derle (.cs3) → Run workflow**.
3. Bitince:
   * **Artifacts → IPTVProvider-cs3** : `.cs3` dosyası, veya
   * CloudStream → Ayarlar → Eklentiler → **Depo ekle** →
     `https://raw.githubusercontent.com/KULLANICI/DEPO/builds/repo.json`
     sonra listeden eklentiyi indirin.
4. Eklentiler ekranında eklentinin yanındaki **dişli** simgesi yönetim panelini açar.

## Yerel derleme
JDK 17 + Android SDK kurulu olmalı: `./gradlew make` → `IPTVProvider/build/IPTVProvider.cs3`

## Notlar
* Liste/aktif-pasif/kategori değişiklikleri CloudStream'i kapatıp açınca görünür (kaynaklar açılışta yüklenir).
* Eklenti sürümünü (`IPTVProvider/build.gradle.kts` → `version`) her yeni derlemede artırın.

// Eklenti sürümü: her yeni derlemede artırın, CloudStream güncellemeyi bu sayıdan anlar.
version = 2

android {
    namespace = "com.darkneslord.iptv"
}

cloudstream {
    description = "Darknes Lord - M3U/M3U8 listelerini kaydet, her liste ayrı kaynak olsun. Kullanıcı ve yönetici sekmeleri, aktif/pasif, kategori gizleme."
    authors = listOf("Darknes Lord")

    /**
     * Status int as the following:
     * 0: Down  1: Ok  2: Slow  3: Beta only
     */
    status = 1
    tvTypes = listOf("Live")
    language = "tr"
    requiresResources = false
}

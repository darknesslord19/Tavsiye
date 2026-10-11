// Eklenti sürümü: her yeni derlemede artırın, CloudStream güncellemeyi bu sayıdan anlar.
version = 2

android {
    namespace = "com.darkneslord.iptv"
}

cloudstream {
    description = "Darknes Lord - M3U/IPTV listelerinizi yönetin: her liste ayrı kaynak, aktif/pasif, kategori göz ikonu, yönetici listesi."
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

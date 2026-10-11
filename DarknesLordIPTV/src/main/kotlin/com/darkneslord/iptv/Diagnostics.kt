package com.darkneslord.iptv

/** Ayarlar ekranındaki test düğmesinin çalıştırdığı kontrol. */
object Diagnostics {
    // Yeni bir sürüm yüklediğini bu etiketten anlarsın. Her değişiklikte güncellenir.
    const val BUILD = "v3"

    /** Tüm listeleri (pasif olanlar dahil) yeniden çözer, okur ve durumlarını günceller. Bloklayıcıdır. */
    fun testAll(): String {
        val items = Store.all()
        val sb = StringBuilder()
        sb.append("Sürüm: ").append(BUILD).append("\n")
        if (items.isEmpty()) return sb.append("Kayıtlı liste yok.").toString()
        sb.append("Liste sayısı: ").append(items.size).append("\n\n")

        val results = parallelMap(items) { e -> e to PlaylistLoader.load(e.resolvedUrl ?: e.url) }
        for ((entry, first) in results) {
            // Kayıtlı son adres artık çalışmıyorsa kısaltılmış orijinal link yeniden denenir.
            val r = if (!first.ok && entry.resolvedUrl != null) PlaylistLoader.load(entry.url) else first
            Repo.apply(entry, r)
            sb.append(if (entry.enabled) "● " else "○ ").append(entry.name).append("\n")
            if (r.ok) {
                sb.append("   OK · ").append(r.channels.size).append(" kanal · ")
                    .append(entry.categories.size).append(" kategori\n")
                if (r.finalUrl != entry.url) sb.append("   Son adres: ").append(r.finalUrl).append("\n")
            } else {
                sb.append("   HATA · ").append(r.error ?: "bilinmiyor").append("\n")
            }
        }
        return sb.toString().trimEnd()
    }
}

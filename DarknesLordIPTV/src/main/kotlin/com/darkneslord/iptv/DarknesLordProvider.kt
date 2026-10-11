package com.darkneslord.iptv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/** Kullanıcının M3U listelerini CloudStream'de canlı yayın olarak gösteren kaynak. */
class DarknesLordProvider : MainAPI() {
    override var name = "Darknes Lord"
    override var mainUrl = "https://darknes.lord"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf("all" to "Darknes Lord")

    companion object {
        private const val HELP_URL = "darkneslord://help"
        private const val MAX_PER_ROW = 600
        private const val MAX_SEARCH = 250
    }

    private fun toSearch(ch: Channel): SearchResponse =
        newLiveSearchResponse(ch.name, ch.toJson(), TvType.Live) {
            this.posterUrl = ch.logo
        }

    // ---------- Ana sayfa: aktif listelerin görünür kategorileri ----------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val active = Store.all().filter { it.enabled }

        // Listeler paralel yüklenir; çalışmayanlar (null) atlanır ve kartta kırmızı görünür.
        val loaded = parallelMap(active) { e -> e to runCatching { Repo.get(e) }.getOrNull() }

        val rows = ArrayList<HomePageList>()
        for ((entry, parsed) in loaded) {
            if (parsed == null) continue
            for ((group, channels) in parsed.groups) {
                if (group in entry.hidden) continue // göz ikonuyla gizlenen kategori
                rows.add(
                    HomePageList(
                        entry.name + " · " + group,
                        channels.take(MAX_PER_ROW).map { toSearch(it) },
                        true,
                    )
                )
            }
        }

        if (rows.isEmpty()) {
            val msg = if (active.isEmpty()) "Liste ekle: Eklentiler → Darknes Lord → dişli simgesi"
            else "Aktif listelerde gösterilecek kanal yok (dişli simgesinden kontrol edin)"
            rows.add(HomePageList("Darknes Lord", listOf(newLiveSearchResponse(msg, HELP_URL, TvType.Live)), false))
        }
        return newHomePageResponse(rows, false)
    }

    // ---------- Arama ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val results = ArrayList<SearchResponse>()
        for (entry in Store.all().filter { it.enabled }) {
            val parsed = runCatching { Repo.get(entry) }.getOrNull() ?: continue
            for ((group, channels) in parsed.groups) {
                if (group in entry.hidden) continue
                for (ch in channels) {
                    if (ch.name.contains(q, ignoreCase = true)) {
                        results.add(toSearch(ch))
                        if (results.size >= MAX_SEARCH) return results
                    }
                }
            }
        }
        return results
    }

    // ---------- Kanal sayfası ----------
    override suspend fun load(url: String): LoadResponse? {
        if (url == HELP_URL) {
            return newLiveStreamLoadResponse(name, url, url) {
                plot = "Listelerinizi yönetmek için: Ayarlar → Eklentiler → Darknes Lord → dişli simgesi. " +
                    "Kısaltılmış linkler otomatik çözülür, çalışmayan linkler kaydedilmez."
            }
        }
        val ch = Channel.fromJson(url) ?: return null
        val listName = Store.all().firstOrNull { it.id == ch.playlistId }?.name
        return newLiveStreamLoadResponse(ch.name, url, url) {
            this.posterUrl = ch.logo
            this.plot = "Kategori: " + ch.group + (if (listName != null) "\nListe: " + listName else "")
            this.tags = listOf(ch.group)
        }
    }

    // ---------- Oynatma ----------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (data == HELP_URL) return false
        val ch = Channel.fromJson(data) ?: return false

        val headers = HashMap<String, String>()
        ch.userAgent?.let { headers["User-Agent"] = it }
        ch.referer?.let { headers["Referer"] = it }

        // Kısaltılmış/yönlendirmeli yayın linkini çöz, kesin ölü yayınları ele.
        val probe = Net.probe(ch.url, headers)
        if (probe.dead) return false

        callback(
            newExtractorLink(
                source = name,
                name = ch.name,
                url = probe.url,
                type = if (probe.isHls) ExtractorLinkType.M3U8 else null,
            ) {
                this.referer = ch.referer ?: ""
                this.quality = Qualities.Unknown.value
                this.headers = headers
            }
        )
        return true
    }
}

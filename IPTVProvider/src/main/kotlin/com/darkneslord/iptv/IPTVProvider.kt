package com.darkneslord.iptv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class IPTVProvider : MainAPI() {
    override var mainUrl = "https://darknes.lord"
    override var name = "Darknes Lord"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf("all" to "Darknes Lord")

    private companion object {
        const val HELP_URL = "darkneslord://help"
        const val MAX_PER_ROW = 600
        const val MAX_SEARCH = 250
    }

    private fun toSearch(ch: Channel): SearchResponse =
        newLiveSearchResponse(ch.name, ch.toJson(), TvType.Live) {
            this.posterUrl = ch.logo
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val active = Store.all().filter { it.enabled }

        // Listeler paralel yüklenir; çalışmayanlar (null) atlanır.
        val loaded = coroutineScope {
            active.map { e -> async(Dispatchers.IO) { e to Repo.get(e) } }.awaitAll()
        }

        val rows = ArrayList<HomePageList>()
        for ((entry, parsed) in loaded) {
            if (parsed == null) continue
            for ((group, channels) in parsed.groups) {
                if (group in entry.hidden) continue // göz ikonu ile gizlenen kategori
                rows.add(
                    HomePageList(
                        "${entry.name} · $group",
                        channels.take(MAX_PER_ROW).map { toSearch(it) },
                        true,
                    )
                )
            }
        }

        if (rows.isEmpty()) {
            val msg = if (active.isEmpty()) "Liste ekle: Eklentiler → Darknes Lord → Ayarlar"
            else "Etkin listelerde gösterilecek kanal yok (Ayarlar'dan kontrol edin)"
            rows.add(HomePageList("Darknes Lord", listOf(newLiveSearchResponse(msg, HELP_URL, TvType.Live)), false))
        }
        return newHomePageResponse(rows, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val results = ArrayList<SearchResponse>()
        for (entry in Store.all().filter { it.enabled }) {
            val parsed = withContext(Dispatchers.IO) { Repo.get(entry) } ?: continue
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
            this.plot = buildString {
                append("Kategori: ").append(ch.group)
                if (listName != null) append("\nListe: ").append(listName)
            }
            this.tags = listOf(ch.group)
        }
    }

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

        // Kısaltılmış/yönlendirmeli yayın linklerini çöz, kesin ölü yayınları ele.
        val probe = withContext(Dispatchers.IO) { Net.probe(ch.url, headers) }
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

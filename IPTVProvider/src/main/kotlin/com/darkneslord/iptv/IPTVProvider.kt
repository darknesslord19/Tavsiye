package com.darkneslord.iptv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IPTVProvider(private val entryId: String? = null, displayName: String = "Darknes Lord") : MainAPI() {
    override var mainUrl = "https://darknes.lord"
    override var name = displayName
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf("all" to displayName)

    private companion object {
        const val HELP_URL = "darkneslord://help"
        const val MAX_PER_ROW = 600
        const val MAX_SEARCH = 250
    }

    private fun entry(): PlaylistEntry? = entryId?.let { Store.find(it) }

    private fun toSearch(ch: Channel): SearchResponse =
        newLiveSearchResponse(ch.name, ch.toJson(), TvType.Live) {
            this.posterUrl = ch.logo
        }

    private fun help(msg: String): HomePageResponse =
        newHomePageResponse(
            listOf(HomePageList(name, listOf(newLiveSearchResponse(msg, HELP_URL, TvType.Live)), false)),
            false,
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val e = entry()
        if (e == null) return help("Liste ekle: Eklentiler → Darknes Lord → Ayarlar")
        if (!e.enabled) return help("Bu liste pasif. Ayarlar'dan aktif edin.")

        val parsed = withContext(Dispatchers.IO) { Repo.get(e) }
            ?: return help("Liste açılamadı: ${e.lastError ?: "bilinmeyen hata"}")

        val rows = ArrayList<HomePageList>()
        for ((group, channels) in parsed.groups) {
            if (group in e.hidden) continue // göz ikonu ile gizlenen kategori
            rows.add(HomePageList(group, channels.take(MAX_PER_ROW).map { toSearch(it) }, true))
        }
        if (rows.isEmpty()) return help("Gösterilecek kategori yok (hepsi gizli olabilir)")
        return newHomePageResponse(rows, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        val e = entry()
        if (q.isEmpty() || e == null || !e.enabled) return emptyList()
        val results = ArrayList<SearchResponse>()
        val parsed = withContext(Dispatchers.IO) { Repo.get(e) } ?: return emptyList()
        for ((group, channels) in parsed.groups) {
            if (group in e.hidden) continue
            for (ch in channels) {
                if (ch.name.contains(q, ignoreCase = true)) {
                    results.add(toSearch(ch))
                    if (results.size >= MAX_SEARCH) return results
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
        val listName = Store.find(ch.playlistId)?.name
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

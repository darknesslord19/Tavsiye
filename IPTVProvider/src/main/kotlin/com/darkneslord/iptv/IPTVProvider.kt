package com.darkneslord.iptv

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.text.Normalizer

/**
 * Tek bir M3U listesini CloudStream kaynağı olarak gösterir.
 * Kaynak seçicide: "Tüm kanallar" + listedeki en kalabalık 15 grup, ayrı satırlar olarak (sayfalamalı).
 */
class IPTVProvider(private val list: IptvList) : MainAPI() {
    override var name = list.name
    override var mainUrl = if (list.url.startsWith("http")) list.url else "https://example.com"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live, TvType.Movie)

    companion object {
        private const val PAGE = 60
        const val CAT_MAX = 15
        private const val ALL = "all"
        // Kanal bilgisi adresin içinde taşınır: CloudStream bunu bozmasın diye http ile başlar.
        private const val PREFIX = "https://iptv.invalid/#"
    }

    override val mainPage: List<MainPageData>
        get() {
            val pairs = ArrayList<Pair<String, String>>()
            pairs.add(ALL to "Tüm kanallar")
            if (list.url.isNotBlank()) {
                val hid = IptvStore.hidden(list.url)
                IptvStore.groups(list.url).filter { it !in hid }.take(CAT_MAX).forEach { pairs.add("g:$it" to it) }
            }
            return mainPageOf(*pairs.toTypedArray())
        }

    private fun requireList() {
        if (list.url.isBlank()) throw ErrorLoadingException("Henüz liste yok. Eklentinin dişlisinden M3U linki ekle.")
    }

    // ---------------------------------------------------------------- kanal <-> adres
    private fun pack(ch: Channel): String {
        val o = JSONObject().put("n", ch.name).put("u", ch.url).put("g", ch.group)
        ch.logo?.let { o.put("l", it) }
        if (ch.hls) o.put("s", 1)
        if (ch.headers.isNotEmpty()) o.put("h", JSONObject(ch.headers as Map<*, *>))
        return PREFIX + Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun unpack(data: String): Channel? {
        return try {
            val raw = data.substringAfter('#')
            val o = JSONObject(String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
            val h = HashMap<String, String>()
            o.optJSONObject("h")?.let { for (k in it.keys()) h[k] = it.optString(k) }
            Channel(o.optString("n"), o.optString("u"), o.optString("l").ifBlank { null }, o.optString("g"), h, o.optInt("s", 0) == 1)
        } catch (e: Exception) { null }
    }

    private suspend fun toSearch(ch: Channel): SearchResponse {
        val data = pack(ch)
        return if (ch.isVod) {
            newMovieSearchResponse(ch.name, data, TvType.Movie, fix = false) { posterUrl = ch.logo }
        } else {
            newLiveSearchResponse(ch.name, data, TvType.Live, fix = false) { posterUrl = ch.logo }
        }
    }

    // ---------------------------------------------------------------- ana sayfa
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        requireList()
        val hid = IptvStore.hidden(list.url)
        val all = Net.channels(list.url, title = list.name).filter { it.group !in hid }
        val rows = if (request.data == ALL) all else {
            val g = request.data.removePrefix("g:")
            all.filter { it.group == g }
        }
        val from = (page - 1) * PAGE
        if (from >= rows.size) return newHomePageResponse(request.name, emptyList<SearchResponse>(), false)
        val slice = rows.subList(from, minOf(rows.size, from + PAGE))
        return newHomePageResponse(request.name, slice.map { toSearch(it) }, from + PAGE < rows.size)
    }

    // ---------------------------------------------------------------- arama
    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('ı', 'i')

    override suspend fun search(query: String): List<SearchResponse> {
        requireList()
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val hid = IptvStore.hidden(list.url)
        return Net.channels(list.url, title = list.name).asSequence().filter { it.group !in hid }.filter { fold(it.name).contains(q) || fold(it.group).contains(q) }
            .take(80).toList().map { toSearch(it) }
    }

    // ---------------------------------------------------------------- detay
    override suspend fun load(url: String): LoadResponse {
        val ch = unpack(url) ?: throw ErrorLoadingException("Kanal bilgisi okunamadı")
        return if (ch.isVod) {
            newMovieLoadResponse(ch.name, url, TvType.Movie, url) {
                posterUrl = ch.logo
                plot = ch.group
            }
        } else {
            newLiveStreamLoadResponse(ch.name, url, url) {
                posterUrl = ch.logo
                plot = ch.group
            }
        }
    }

    // ---------------------------------------------------------------- oynatma
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val ch = unpack(data) ?: return false
        val path = ch.url.lowercase().substringBefore('?')
        val type = when {
            ch.hls || path.contains("m3u8") -> ExtractorLinkType.M3U8
            path.endsWith(".mpd") -> ExtractorLinkType.DASH
            else -> ExtractorLinkType.VIDEO
        }
        val hdr = HashMap<String, String>()
        hdr["User-Agent"] = Net.UA
        hdr.putAll(ch.headers)
        callback(
            newExtractorLink(name, ch.name, ch.url, type) {
                this.referer = ch.headers["Referer"] ?: ""
                this.quality = Qualities.Unknown.value
                this.headers = hdr
            }
        )
        return true
    }
}

package com.example.sitebridge

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Tek bir siteyi CloudStream kaynağı olarak gösterir. Mantık MainActivity.java'daki
 * ana sayfa / kategori / arama / detay / oynatıcı akışının karşılığıdır.
 */
class SiteProvider(private val site: SiteInfo) : MainAPI() {
    override var name = site.name
    override var mainUrl = if (site.url.isBlank()) "https://example.com" else Scraper.origin(site.url).trimEnd('/')
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(site.url to "Ana Sayfa")

    companion object {
        private const val CAT_ROWS = 6          // ana sayfada otomatik yüklenen kategori satırı
        private const val MAX_IFRAMES = 4       // oynatıcı sayfasında denenecek iframe sayısı
        private val SEARCH_PATHS = listOf(
            "?s={q}", "arama/{q}", "search?q={q}", "ara?q={q}", "arama?q={q}", "?q={q}",
        )
    }

    // M3U listesiyse içeriği burada tutulur (arama için)
    @Volatile private var m3uGroups: Map<String, List<SiteMovie>>? = null

    // ---------------------------------------------------------------- yardımcılar
    private fun toSearch(m: SiteMovie, referer: String): SearchResponse {
        val ref = mapOf("Referer" to referer)
        if (m.direct) {
            return newMovieSearchResponse(m.title, Scraper.encodeDirect(m), TvType.Movie) {
                posterUrl = m.poster.ifBlank { null }
                posterHeaders = ref
            }
        }
        return if (Scraper.looksLikeSeries(m.url)) {
            newTvSeriesSearchResponse(m.title, m.url, TvType.TvSeries) {
                posterUrl = m.poster
                posterHeaders = ref
            }
        } else {
            newMovieSearchResponse(m.title, m.url, TvType.Movie) {
                posterUrl = m.poster
                posterHeaders = ref
            }
        }
    }

    private fun requireSite() {
        if (site.url.isBlank()) {
            throw ErrorLoadingException("Henüz site yok. Eklentinin yanındaki dişliden site ekle ya da bir GitHub listesi tanımla.")
        }
    }

    // ---------------------------------------------------------------- ana sayfa
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        requireSite()
        if (page > 1) return newHomePageResponse(emptyList<HomePageList>(), false)

        val homeUrl = site.url
        val html = Net.html(homeUrl)
        val lists = ArrayList<HomePageList>()

        if (Scraper.isM3u(html)) {
            val groups = Scraper.parseM3u(html)
            m3uGroups = groups
            for ((g, items) in groups.entries.take(15)) {
                lists.add(HomePageList(g, items.take(60).map { toSearch(it, homeUrl) }))
            }
        } else {
            for (row in Scraper.parseSections(html, homeUrl, site.rules)) {
                lists.add(HomePageList(row.title, row.items.map { toSearch(it, homeUrl) }))
            }
            // Kategori sayfalarından ek satırlar (MainActivity'deki tembel kategori satırları)
            val cats = Scraper.parseCategories(html, homeUrl, site.rules).take(CAT_ROWS)
            val rows = parallel(cats) { c ->
                val h = Net.html(c.url)
                val items = Scraper.parseMovies(h, c.url, site.rules)
                if (items.size >= 2) HomePageList(c.name, items.map { toSearch(it, c.url) }) else null
            }
            for (r in rows) if (r != null && lists.none { it.name == r.name }) lists.add(r)
        }

        if (lists.isEmpty()) {
            throw ErrorLoadingException(
                "Bu sitede içerik bulunamadı. Site JavaScript ile yükleniyor olabilir; ayarlardan site kuralı (rules) eklemeyi dene."
            )
        }
        return newHomePageResponse(lists, false)
    }

    // ---------------------------------------------------------------- arama
    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('ı', 'i')

    override suspend fun search(query: String): List<SearchResponse> {
        requireSite()
        val homeUrl = site.url

        // M3U: bellekte ara
        var groups = m3uGroups
        if (groups == null) {
            val h = Net.htmlOrNull(homeUrl)
            if (h != null && Scraper.isM3u(h)) {
                groups = Scraper.parseM3u(h)
                m3uGroups = groups
            }
        }
        if (groups != null) {
            val needle = fold(query)
            return groups.values.flatten().filter { fold(it.title).contains(needle) }.take(100)
                .map { toSearch(it, homeUrl) }
        }

        // Sitelerin ortak arama adresleri; kurallarda "search": ".../?s={q}" ile ezilebilir.
        val q = URLEncoder.encode(query, "UTF-8")
        val custom = site.rules?.optString("search", "").orEmpty()
        val base = Scraper.origin(homeUrl)
        val candidates = if (custom.isNotBlank()) listOf(custom) else SEARCH_PATHS
        val homeHtml = Net.htmlOrNull(homeUrl)
        val homeUrls = if (homeHtml != null) Scraper.parseMovies(homeHtml, homeUrl, site.rules).map { it.url }.toSet()
        else emptySet()

        for (c in candidates) {
            val url = (if (c.startsWith("http")) c else base + c.trimStart('/')).replace("{q}", q)
            val h = Net.htmlOrNull(url, homeUrl) ?: continue
            val items = Scraper.parseMovies(h, url, site.rules)
            if (items.isEmpty()) continue
            // Arama desteklemeyen site ana sayfayı döndürür; onu sonuç sayma.
            val overlap = items.count { homeUrls.contains(it.url) }
            if (homeUrls.isNotEmpty() && overlap * 5 >= items.size * 4) continue
            return items.map { toSearch(it, url) }
        }
        return emptyList()
    }

    // ---------------------------------------------------------------- detay
    override suspend fun load(url: String): LoadResponse {
        // M3U / doğrudan yayın: detay sayfası yok
        if (Scraper.isDirectUrl(url)) {
            val (_, title, poster) = Scraper.decodeDirect(url)
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
            }
        }

        val seriesUrl = Scraper.seriesUrlFor(url)
        var html = Net.htmlOrNull(seriesUrl)
        var baseUrl = seriesUrl
        var eps = Scraper.parseEpisodes(html, seriesUrl)

        // Kart adresi doğrudan bölüm sayfası olabilir; oradan ya da sayfadaki dizi bağlantısından bölümleri bul.
        if (eps.isEmpty()) {
            val h2 = Net.htmlOrNull(url)
            if (h2 != null) {
                eps = Scraper.parseEpisodes(h2, url)
                if (html == null) {
                    html = h2
                    baseUrl = url
                }
                if (eps.isEmpty()) {
                    for (su in Scraper.seriesLinks(h2, url)) {
                        if (su == seriesUrl) continue
                        val h3 = Net.htmlOrNull(su)
                        val e3 = Scraper.parseEpisodes(h3, su)
                        if (e3.isNotEmpty()) {
                            eps = e3
                            html = h3
                            baseUrl = su
                            break
                        }
                    }
                }
            }
        }
        if (html == null) throw ErrorLoadingException("Sayfa açılamadı: $url")

        val title = Scraper.pageTitle(html) ?: Scraper.hostOf(url)
        val poster = Scraper.pagePoster(html, baseUrl)
        val desc = Scraper.metaDescription(html)

        if (eps.isEmpty()) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                plot = desc
            }
        }
        val episodes = eps.map { e ->
            newEpisode(e.url) {
                this.name = e.title
                this.season = e.season
                this.episode = e.number
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = poster
            plot = desc
        }
    }

    // ---------------------------------------------------------------- oynatma
    private suspend fun emitVideo(
        video: String,
        referer: String,
        label: String,
        extra: Map<String, String>,
        callback: (ExtractorLink) -> Unit,
    ) {
        val clean = video.replace(" ", "%20")
        val path = clean.lowercase().substringBefore("?")
        val type = if (path.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
        val hdr = HashMap<String, String>()
        hdr["User-Agent"] = Net.UA
        hdr.putAll(extra)
        callback(
            newExtractorLink(name, label, clean, type) {
                this.referer = extra["Referer"] ?: referer
                this.quality = Qualities.Unknown.value
                this.headers = hdr
            }
        )
    }

    /** Bir iframe adresinden link çıkarır: önce CloudStream çıkarıcıları, sonra sayfa taraması, en son WebView. */
    private suspend fun fromIframe(
        iframe: String,
        referer: String,
        depth: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var found = false
        val wrap: (ExtractorLink) -> Unit = {
            found = true
            callback(it)
        }

        // 1) CloudStream'in hazır çıkarıcıları (bilinen oynatıcı siteleri)
        try {
            loadExtractor(iframe, referer, subtitleCallback, wrap)
        } catch (ignored: Exception) {
        }
        if (found) return true

        // 2) Iframe sayfasını oku, içinde video / iç içe iframe ara
        val h = Net.htmlOrNull(iframe, referer)
        if (h != null) {
            val v = Scraper.findVideoIn(h)
            if (v != null) {
                emitVideo(v, iframe, name, mapOf("Referer" to iframe), wrap)
                return true
            }
            if (depth < 2) {
                for (inner in Scraper.findPlayer(h, iframe, null).iframes.take(MAX_IFRAMES)) {
                    if (inner == iframe) continue
                    if (fromIframe(inner, iframe, depth + 1, subtitleCallback, callback)) return true
                }
            }
        }

        // 3) Gerçek tarayıcıda aç, ağda geçen ilk .m3u8/.mp4 adresini yakala (MainActivity'deki WebView oynatıcının karşılığı)
        try {
            val r = app.get(iframe, referer = referer, interceptor = WebViewResolver(Regex("""\.(m3u8|mp4)""")))
            val u = r.url
            if (Regex("""\.(m3u8|mp4)""").containsMatchIn(u)) {
                emitVideo(u, iframe, name, mapOf("Referer" to iframe), wrap)
            }
        } catch (ignored: Exception) {
        }
        return found
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // M3U / doğrudan yayın
        if (Scraper.isDirectUrl(data)) {
            val base = data.substringBefore("#sb=")
            val hdr = HashMap<String, String>()
            val link = Scraper.splitStreamHeaders(base, hdr)
            emitVideo(link, hdr["Referer"] ?: "", name, hdr, callback)
            return true
        }

        var found = false
        val wrap: (ExtractorLink) -> Unit = {
            found = true
            callback(it)
        }

        val html = Net.html(data)
        val src = Scraper.findPlayer(html, data, site.rules)

        if (src.video != null) {
            emitVideo(src.video, data, name, mapOf("Referer" to data), wrap)
            return true
        }
        for (f in src.iframes.take(MAX_IFRAMES)) {
            fromIframe(f, data, 0, subtitleCallback, wrap)
        }
        return found
    }
}

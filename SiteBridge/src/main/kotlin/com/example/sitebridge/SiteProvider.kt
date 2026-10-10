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
import java.util.concurrent.ConcurrentHashMap

/**
 * Tek bir siteyi CloudStream kaynağı olarak gösterir. Mantık MainActivity.java'daki
 * ana sayfa / kategori / arama / detay / oynatıcı akışının karşılığıdır.
 *
 * Kaynak seçicide: "Ana Sayfa" + (önbellekteki) en çok 15 kategori ayrı satır olarak görünür.
 * Kategoriler ilk açılışta ana sayfa içinde gösterilir ve önbelleğe yazılır; sonraki açılışlarda
 * CloudStream'in kendi satırları (sayfalamalı) olarak gelir.
 */
class SiteProvider(private val site: SiteInfo) : MainAPI() {
    override var name = site.name
    override var mainUrl = if (site.url.isBlank()) "https://example.com" else Scraper.origin(site.url).trimEnd('/')
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        const val CAT_MAX = 15              // kaynak seçicide gösterilen en çok kategori satırı
        private const val MAX_IFRAMES = 6   // bir sayfada denenecek iframe sayısı
        private const val EXTRACTOR_MS = 20_000L
        private const val WEBVIEW_MS = 35_000L
        private const val WEBVIEW_TRIES = 2     // sayfa başına tarayıcıda dinlenecek en çok iframe
        // Tarayıcıda dinlerken oynat düğmesine otomatik tıklar (birçok oynatıcı tıklanmadan akış başlatmaz)
        private const val AUTOCLICK = "(function(){var s=['.jw-icon-display','.jw-display-icon-container','.vjs-big-play-button'," +
            "'.plyr__control--overlaid','.fp-play','.play-button','.play','#play','[class*=big-play]','[aria-label*=lay]'];" +
            "function go(){try{var v=document.querySelector('video');if(v){v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});}}catch(x){}" +
            "for(var i=0;i<s.length;i++){try{var e=document.querySelector(s[i]);if(e)e.click();}catch(x){}}}" +
            "go();setInterval(go,1500);})();"
        private val SEARCH_PATHS = listOf(
            "?s={q}", "arama/{q}", "search?q={q}", "ara?q={q}", "arama?q={q}", "?q={q}",
        )
    }

    // Kaynak seçicideki satırlar: Ana Sayfa + önbellekteki kategoriler. CloudStream her yüklemede okur.
    @Volatile private var served: Set<String> = emptySet()

    override val mainPage: List<MainPageData>
        get() {
            val cats = if (site.url.isBlank()) emptyList() else SiteStore.cats(site.url).take(CAT_MAX)
            served = cats.map { it.url }.toSet()
            val pairs = ArrayList<Pair<String, String>>()
            pairs.add(site.url to "Ana Sayfa")
            cats.forEach { pairs.add(it.url to it.name) }
            return mainPageOf(*pairs.toTypedArray())
        }

    // Test sırasında doldurulur: oynatma adımlarının neden başarılı/başarısız olduğunu raporlar
    @Volatile private var trace: java.util.concurrent.CopyOnWriteArrayList<String>? = null

    private fun tr(msg: String) {
        trace?.add(msg)
    }

    // M3U listesiyse içeriği burada tutulur (arama için)
    @Volatile private var m3uGroups: Map<String, List<SiteMovie>>? = null

    // Son başarılı ana sayfa (site geçici olarak açılmazsa bu gösterilir)
    @Volatile private var lastHome: List<HomePageList>? = null
    private val nextPages = ConcurrentHashMap<String, String>()

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

    // ---------------------------------------------------------------- ana sayfa / kategoriler
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        requireSite()
        return if (request.data == site.url) homePage(page) else categoryPage(page, request)
    }

    private suspend fun homePage(page: Int): HomePageResponse {
        if (page > 1) return newHomePageResponse(emptyList<HomePageList>(), false)
        try {
            val lists = buildHome()
            lastHome = lists
            return newHomePageResponse(lists, false)
        } catch (e: Exception) {
            // Site bir an açılmadıysa son başarılı içeriği göster
            val old = lastHome
            if (old != null && old.isNotEmpty()) return newHomePageResponse(old, false)
            if (e is ErrorLoadingException) throw e
            throw ErrorLoadingException(site.name + ": " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private suspend fun buildHome(): List<HomePageList> {
        val pg = Net.page(site.url)
        val homeUrl = pg.url
        val html = pg.html
        val lists = ArrayList<HomePageList>()

        if (Scraper.isM3u(html)) {
            val groups = Scraper.parseM3u(html)
            m3uGroups = groups
            for ((g, items) in groups.entries.take(CAT_MAX)) {
                lists.add(HomePageList(g, items.take(60).map { toSearch(it, homeUrl) }))
            }
        } else {
            for (row in Scraper.parseSections(html, homeUrl, site.rules)) {
                lists.add(HomePageList(row.title, row.items.map { toSearch(it, homeUrl) }))
            }
            val cats = Scraper.parseCategories(html, homeUrl, site.rules).take(CAT_MAX)
            if (cats.isNotEmpty()) SiteStore.saveCats(site.url, cats)

            // Kaynak seçicide henüz ayrı satır olmayan kategorileri burada göster (ilk açılış).
            val missing = cats.filter { !served.contains(it.url) }
            val rows = parallel(missing, 5) { c ->
                val h = Net.page(c.url, homeUrl)
                val items = Scraper.parseMovies(h.html, h.url, site.rules)
                if (items.isNotEmpty()) HomePageList(c.name, items.map { toSearch(it, h.url) }) else null
            }
            for (r in rows) if (r != null && lists.none { it.name == r.name }) lists.add(r)

            // Kategorisi bulunamayan sitede sonraki sayfaları ek satır olarak ver
            if (cats.size < 3) {
                var nextUrl = Scraper.parseNext(html, homeUrl, site.rules)
                var n = 2
                while (nextUrl != null && n <= 3) {
                    val np = Net.pageOrNull(nextUrl, homeUrl) ?: break
                    val items = Scraper.parseMovies(np.html, np.url, site.rules)
                    if (items.isNotEmpty()) lists.add(HomePageList("Sayfa $n", items.map { toSearch(it, np.url) }))
                    nextUrl = Scraper.parseNext(np.html, np.url, site.rules)
                    n++
                }
            }
        }

        if (lists.isEmpty()) {
            throw ErrorLoadingException(
                site.name + ": içerik bulunamadı. Site JavaScript ile yükleniyor olabilir; ayarlardan site kuralı (rules) eklemeyi dene."
            )
        }
        return lists
    }

    private suspend fun categoryPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cat = request.data
        val url = if (page <= 1) cat else nextPages["$cat#$page"] ?: (cat.trimEnd('/') + "/page/" + page + "/")
        val pg = Net.page(url, site.url)
        val items = Scraper.parseMovies(pg.html, pg.url, site.rules)
        if (items.isEmpty()) {
            if (page > 1) return newHomePageResponse(request.name, emptyList<SearchResponse>(), false)
            throw ErrorLoadingException(site.name + " · " + request.name + ": içerik bulunamadı")
        }
        val next = Scraper.parseNext(pg.html, pg.url, site.rules)
        if (next != null) {
            if (nextPages.size > 400) nextPages.clear()
            nextPages["$cat#${page + 1}"] = next
        }
        return newHomePageResponse(request.name, items.map { toSearch(it, pg.url) }, next != null)
    }

    // ---------------------------------------------------------------- arama
    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('ı', 'i')

    override suspend fun search(query: String): List<SearchResponse> {
        requireSite()
        val homePg = Net.pageOrNull(site.url)
        val homeUrl = homePg?.url ?: site.url

        // M3U: bellekte ara
        var groups = m3uGroups
        if (groups == null && homePg != null && Scraper.isM3u(homePg.html)) {
            groups = Scraper.parseM3u(homePg.html)
            m3uGroups = groups
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
        val homeUrls = if (homePg != null) Scraper.parseMovies(homePg.html, homeUrl, site.rules).map { it.url }.toSet()
        else emptySet()

        for (c in candidates) {
            val url = (if (c.startsWith("http")) c else base + c.trimStart('/')).replace("{q}", q)
            val h = Net.pageOrNull(url, homeUrl) ?: continue
            val items = Scraper.parseMovies(h.html, h.url, site.rules)
            if (items.isEmpty()) continue
            // Arama desteklemeyen site ana sayfayı döndürür; onu sonuç sayma.
            val overlap = items.count { homeUrls.contains(it.url) }
            if (homeUrls.isNotEmpty() && overlap * 5 >= items.size * 4) continue
            return items.map { toSearch(it, h.url) }
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
        var pg = Net.pageOrNull(seriesUrl)
        var eps = Scraper.parseEpisodes(pg?.html, pg?.url ?: seriesUrl)

        // Kart adresi doğrudan bölüm sayfası olabilir; oradan ya da sayfadaki dizi bağlantısından bölümleri bul.
        if (eps.isEmpty()) {
            val p2 = Net.pageOrNull(url)
            if (p2 != null) {
                eps = Scraper.parseEpisodes(p2.html, p2.url)
                if (pg == null) pg = p2
                if (eps.isEmpty()) {
                    for (su in Scraper.seriesLinks(p2.html, p2.url)) {
                        if (su == seriesUrl) continue
                        val p3 = Net.pageOrNull(su) ?: continue
                        val e3 = Scraper.parseEpisodes(p3.html, p3.url)
                        if (e3.isNotEmpty()) {
                            eps = e3
                            pg = p3
                            break
                        }
                    }
                }
            }
        }
        val page = pg ?: throw ErrorLoadingException("Sayfa açılamadı: $url")

        val title = Scraper.pageTitle(page.html) ?: Scraper.hostOf(url)
        val poster = Scraper.pagePoster(page.html, page.url)
        val desc = Scraper.metaDescription(page.html)

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
    private fun qualityOf(u: String): Int {
        val m = Regex("""(?<![0-9])(2160|1440|1080|720|480|360|240)p""").find(u)
        return m?.groupValues?.get(1)?.toIntOrNull() ?: Qualities.Unknown.value
    }

    private suspend fun emitVideo(
        video: String,
        referer: String,
        label: String,
        extra: Map<String, String>,
        seen: MutableSet<String>,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val clean = video.replace(" ", "%20")
        if (!seen.add(clean)) return false
        val path = clean.lowercase().substringBefore("?")
        val type = when {
            path.contains("m3u8") -> ExtractorLinkType.M3U8
            path.endsWith(".mpd") -> ExtractorLinkType.DASH
            else -> ExtractorLinkType.VIDEO
        }
        val hdr = HashMap<String, String>()
        hdr["User-Agent"] = Net.UA
        hdr.putAll(extra)
        callback(
            newExtractorLink(name, label, clean, type) {
                this.referer = extra["Referer"] ?: referer
                this.quality = qualityOf(clean)
                this.headers = hdr
            }
        )
        return true
    }

    private fun labelFor(u: String): String = name + " • " + Scraper.hostOf(u)

    /**
     * Bir iframe adresinden link çıkarır:
     *  1) CloudStream'in hazır çıkarıcıları (bilinen oynatıcı siteleri)
     *  2) iframe sayfasını oku: packed JS / base64 / JSON içinden video ve iç içe iframe
     *  3) gerçek tarayıcıda aç (oynat düğmesine otomatik tıklanır), ağda geçen .m3u8/.mp4 adresini yakala
     */
    private suspend fun fromIframe(
        iframe: String,
        referer: String,
        depth: Int,
        allowWebView: Boolean,
        seen: MutableSet<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val pad = "  ".repeat(depth + 1)
        tr(pad + "iframe: " + iframe.take(90))
        var found = false
        val wrap: (ExtractorLink) -> Unit = {
            found = true
            callback(it)
        }

        // 1) hazır çıkarıcılar (takılırsa bırakılır)
        timed(EXTRACTOR_MS, { tr(pad + "çıkarıcı hata: $it") }) {
            loadExtractor(iframe, referer, subtitleCallback, wrap)
        }
        tr(pad + "CloudStream çıkarıcıları: " + if (found) "link verdi" else "link yok")
        if (found) return true

        // 2) iframe sayfasını oku
        var pg: Page? = null
        try {
            pg = Net.page(iframe, referer)
        } catch (e: Exception) {
            tr(pad + "sayfa açılamadı: " + (e.message ?: e.javaClass.simpleName).take(80))
        }
        if (pg != null) {
            val ref = mapOf("Referer" to iframe, "Origin" to Scraper.origin(iframe).trimEnd('/'))
            val vids = Scraper.findVideos(pg.html, pg.url)
            val inner = if (depth < 2) Scraper.findPlayer(pg.html, pg.url, null).iframes.filter { it != iframe } else emptyList()
            tr(pad + "sayfa: " + pg.html.length / 1024 + " KB, video " + vids.size + ", iç iframe " + inner.size)
            for (v in vids) {
                if (emitVideo(v, iframe, labelFor(iframe), ref, seen, wrap)) found = true
            }
            if (found) return true
            for (i in inner.take(MAX_IFRAMES)) {
                if (fromIframe(i, iframe, depth + 1, allowWebView, seen, subtitleCallback, callback)) return true
            }
        }

        // 3) gerçek tarayıcıda dinle
        if (!allowWebView) {
            tr(pad + "tarayıcı dinleme atlandı (sınır)")
            return found
        }
        val sniffed = timed(WEBVIEW_MS, { tr(pad + "tarayıcı hata: $it") }) {
            app.get(
                iframe,
                referer = referer,
                interceptor = WebViewResolver(Regex("""\.(m3u8|mp4|mpd)"""), script = AUTOCLICK)
            ).url
        }
        if (sniffed != null && Regex("""\.(m3u8|mp4|mpd)""").containsMatchIn(sniffed) && !sniffed.contains("/ads/")) {
            tr(pad + "tarayıcı yakaladı: " + sniffed.take(90))
            val ref = mapOf("Referer" to iframe, "Origin" to Scraper.origin(iframe).trimEnd('/'))
            if (emitVideo(sniffed, iframe, labelFor(iframe), ref, seen, wrap)) found = true
        } else {
            tr(pad + "tarayıcı: video isteği yakalanamadı")
        }
        return found
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val seen = HashSet<String>()

        // M3U / doğrudan yayın
        if (Scraper.isDirectUrl(data)) {
            val base = data.substringBefore("#sb=")
            val hdr = HashMap<String, String>()
            val link = Scraper.splitStreamHeaders(base, hdr)
            return emitVideo(link, hdr["Referer"] ?: "", name, hdr, seen, callback)
        }

        var found = false
        val wrap: (ExtractorLink) -> Unit = {
            found = true
            callback(it)
        }

        val first = Net.page(data)
        val pages = ArrayList<Page>()
        pages.add(first)
        // Alternatif kaynak sayfaları (?kaynak=2 ...)
        for (alt in Scraper.altSourcePages(first.html, first.url)) {
            Net.pageOrNull(alt, data)?.let { pages.add(it) }
        }

        var tries = 0
        for (pg in pages) {
            val src = Scraper.findPlayer(pg.html, pg.url, site.rules)
            tr("sayfa: " + pg.url.take(80) + " → video " + src.videos.size + ", iframe " + src.iframes.size)
            val ref = mapOf("Referer" to pg.url)
            for (v in src.videos) emitVideo(v, pg.url, name, ref, seen, wrap)
            if (found) return true
            for (f in src.iframes.take(MAX_IFRAMES)) {
                val allow = tries < WEBVIEW_TRIES
                tries++
                fromIframe(f, pg.url, 0, allow, seen, subtitleCallback, wrap)
                if (found) return true
            }
        }

        // Son çare: sayfanın kendisini tarayıcıda aç ve ağdaki videoyu yakala
        val sniffed = timed(WEBVIEW_MS, { tr("sayfa tarayıcı hata: $it") }) {
            app.get(first.url, interceptor = WebViewResolver(Regex("""\.(m3u8|mp4|mpd)"""), script = AUTOCLICK)).url
        }
        if (sniffed != null && Regex("""\.(m3u8|mp4|mpd)""").containsMatchIn(sniffed) && !sniffed.contains("/ads/")) {
            tr("sayfa tarayıcı yakaladı: " + sniffed.take(90))
            emitVideo(sniffed, first.url, name, mapOf("Referer" to first.url), seen, wrap)
        } else {
            tr("sayfa tarayıcı: video isteği yakalanamadı")
        }
        return found
    }

    // ---------------------------------------------------------------- site testi (ayar ekranı)
    /** Siteyi uçtan uca dener: ana sayfa -> kategori -> ilk içerik -> oynatma bağlantısı. Dönüş: (başarılı mı, rapor) */
    suspend fun diagnose(): Pair<Boolean, String> {
        val sb = StringBuilder()
        var ok = true
        if (Scraper.patternErrors.isNotEmpty()) {
            sb.append("⚠ Derlenemeyen regex: ").append(Scraper.patternErrors.joinToString(" | ")).append('\n')
        }
        try {
            val pg = Net.page(site.url)
            sb.append("Ana sayfa: açıldı (").append(pg.html.length / 1024).append(" KB)")
            if (pg.url != site.url) sb.append(" → ").append(Scraper.hostOf(pg.url))
            sb.append('\n')

            if (Scraper.isM3u(pg.html)) {
                val g = Scraper.parseM3u(pg.html)
                sb.append("M3U listesi: ").append(g.size).append(" kategori, ").append(g.values.sumOf { it.size }).append(" içerik\n")
                return Pair(g.isNotEmpty(), sb.toString())
            }

            val rows = Scraper.parseSections(pg.html, pg.url, site.rules)
            val movies = Scraper.parseMovies(pg.html, pg.url, site.rules)
            sb.append("Satır: ").append(rows.size).append(", film: ").append(movies.size).append('\n')
            if (movies.isEmpty()) {
                sb.append("✗ Film kartı bulunamadı (site JavaScript ile yükleniyor olabilir, kural gerekebilir)\n")
                return Pair(false, sb.toString())
            }

            val cats = Scraper.parseCategories(pg.html, pg.url, site.rules).take(CAT_MAX)
            sb.append("Kategori: ").append(cats.size)
            if (cats.isNotEmpty()) sb.append(" (").append(cats.take(5).joinToString { it.name }).append("…)")
            sb.append('\n')
            if (cats.isNotEmpty()) {
                SiteStore.saveCats(site.url, cats)
                val cp = Net.pageOrNull(cats[0].url, pg.url)
                val n = if (cp != null) Scraper.parseMovies(cp.html, cp.url, site.rules).size else -1
                sb.append("İlk kategori sayfası: ").append(if (n < 0) "✗ açılmadı" else "$n film").append('\n')
                if (n <= 0) ok = false
            }

            val target = movies[0]
            sb.append("Deneme içeriği: ").append(target.title).append('\n')
            val lr = load(target.url)
            var data = target.url
            if (lr is TvSeriesLoadResponse) {
                sb.append("Dizi: ").append(lr.episodes.size).append(" bölüm\n")
                data = lr.episodes.firstOrNull()?.data ?: target.url
            } else {
                sb.append("Film sayfası açıldı\n")
            }

            val dp = Net.page(data, pg.url)
            val ps = Scraper.findPlayer(dp.html, dp.url, site.rules)
            sb.append("Oynatıcı sayfası: video ").append(ps.videos.size).append(", iframe ").append(ps.iframes.size)
            if (ps.iframes.isNotEmpty()) sb.append(" (").append(ps.iframes.take(3).joinToString { Scraper.hostOf(it) }).append(")")
            sb.append('\n')

            val links = java.util.concurrent.CopyOnWriteArrayList<String>()
            val log = java.util.concurrent.CopyOnWriteArrayList<String>()
            trace = log
            try {
                timed(120_000L, { log.add("loadLinks hata: $it") }) {
                    loadLinks(data, false, {}, { links.add(it.name + " [" + it.type + "]") })
                }
            } finally {
                trace = null
            }
            sb.append("— oynatma adımları —\n")
            log.forEach { sb.append(it).append('\n') }
            if (links.isEmpty()) {
                sb.append("✗ Oynatma bağlantısı çıkarılamadı\n")
                ok = false
            } else {
                sb.append("✓ ").append(links.size).append(" bağlantı: ").append(links.take(3).joinToString("; ")).append('\n')
            }
        } catch (e: Exception) {
            sb.append("✗ Hata: ").append(e.message ?: e.javaClass.simpleName).append('\n')
            ok = false
        }
        return Pair(ok, sb.toString())
    }
}

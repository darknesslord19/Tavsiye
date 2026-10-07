package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

/**
 * SinemaTV.az (DataLife Engine sitesi).
 *
 * Oynatma zinciri Resolver Lab V81 analizinden (READY) cikarildi:
 *  1) Film sayfasi -> <iframe data-src="https://<player>/t?token_movie=..&token=..">
 *  2) Player sayfasi (Referer: film sayfasi) -> const userParam = { token: '...' }
 *  3) POST https://<player>/bnsi/movies/<id>
 *       token=<token>&av1=..&autoplay=0&audio=&subtitle=
 *       X-Requested-With: XMLHttpRequest, Origin/Referer: player
 *     -> JSON icinde kalite basina master.m3u8 adresleri + .vtt altyazilar
 *  4) m3u8 ve segmentler "Origin: <player>" basligi ister.
 */
class SinemaTv : MainAPI() {
    private val TAG = "SINEMATV"

    override var mainUrl = "https://sinematv.az"
    override var name = "SinemaTV"
    override val hasMainPage = true
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "film" to "Filmlər",
        "serial" to "Seriallar",
        "xarici-filmler" to "Xarici filmlər",
        "hind-filmleri" to "Hind filmləri",
        "sovet-filmleri" to "Sovet filmləri",
        "mult" to "Cizgi filmləri",
        "comedy" to "Komediya",
        "adventures" to "Macəra",
    )

    // ------------------------------------------------------------ katalog

    private val contentRx = Regex("""^https?://[^/]+/[a-z0-9-]+/\d+-[^/?#]+\.html$""", RegexOption.IGNORE_CASE)

    /** "HD Илья Муровец 2026 - Сериал / Новинки" -> ("Илья Муровец", 2026) */
    private fun cleanTitle(raw: String): Pair<String, Int?> {
        var t = raw.trim().replace(Regex("""\s+"""), " ")
        t = t.replace(Regex("""^(HD|FHD|4K|CAM|TS)\s+""", RegexOption.IGNORE_CASE), "")
        t = t.split(" - ").first().trim()
        val year = Regex("""\b((?:19|20)\d{2})\b""").findAll(t).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        if (year != null) t = t.replace(Regex("""\s*\(?\b$year\b\)?\s*$"""), "").trim()
        return Pair(t, year)
    }

    private fun isSeriesUrl(url: String, title: String = ""): Boolean =
        url.contains("/serial/") || title.contains("Сериал", ignoreCase = true)

    private fun Element.toCard(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!contentRx.matches(href)) return null
        val img = selectFirst("img") ?: return null
        val rawTitle = attr("title").ifBlank { img.attr("alt") }.ifBlank { text() }
        if (rawTitle.isBlank()) return null
        val (title, year) = cleanTitle(rawTitle)
        val poster = fixUrlNull(img.attr("data-src").ifBlank { img.attr("src") })
        return if (isSeriesUrl(href, rawTitle)) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    /**
     * Her sayfada ayni duran vitrin/slider bloklarini (ust carousel, populer, yan
     * menu) atlar; yalnizca sayfanin asil liste alanindaki kartlari alir.
     */
    private val sideRx = Regex("""\b(slider|carousel|swiper|owl|popular|top|sidebar|aside|related|footer|header|menu|nav)\b""", RegexOption.IGNORE_CASE)

    private fun Element.inSideBlock(): Boolean {
        var p: Element? = this.parent()
        var depth = 0
        while (p != null && depth < 12) {
            if (p.id() == "dle-content") return false
            val cls = p.className() + " " + p.id()
            if (p.tagName() == "aside" || p.tagName() == "header" || p.tagName() == "footer" || sideRx.containsMatchIn(cls)) return true
            p = p.parent()
            depth++
        }
        return false
    }

    private fun Document.cards(): List<SearchResponse> {
        // DLE sitelerinde asil liste #dle-content icindedir.
        val main = selectFirst("#dle-content")
        val links = if (main != null) main.select("a[href]") else select("a[href]").filterNot { it.inSideBlock() }
        return links.mapNotNull { it.toCard() }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val items = app.get(url, referer = "$mainUrl/").document.cards()
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/?do=search&subaction=search&search_start=0&full_search=0&story=$q"
        return app.get(url, referer = "$mainUrl/").document.cards()
    }

    // ------------------------------------------------------------ detay

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, referer = "$mainUrl/").document
        val rawTitle = doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: return null
        val (title, year) = cleanTitle(rawTitle)
        val poster = fixUrlNull(
            doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("img[src*=poster], .poster img, .fposter img")?.attr("src")
        )
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst("meta[name=description]")?.attr("content")
        val tags = rawTitle.split(" - ").getOrNull(1)
            ?.split("/")?.map { it.trim() }?.filter { it.isNotBlank() && !it.equals("Сериал", true) && !it.equals("Новинки", true) }

        return if (isSeriesUrl(url, rawTitle)) {
            // Bolumler sitenin oynaticisi icinde secilir; sayfa tek giris olarak acilir.
            val episodes = listOf(
                newEpisode(url) {
                    this.name = "Oynatıcı (bölümler oynatıcıda)"
                    this.season = 1
                    this.episode = 1
                }
            )
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        }
    }

    // ------------------------------------------------------------ oynatici

    private fun qualityOf(key: String): Int = when {
        key.contains("2160") || key.contains("4k", true) -> Qualities.P2160.value
        key.contains("1440") -> Qualities.P1440.value
        key.contains("1080") -> Qualities.P1080.value
        key.contains("720") -> Qualities.P720.value
        key.contains("480") -> Qualities.P480.value
        key.contains("360") -> Qualities.P360.value
        key.contains("240") -> Qualities.P240.value
        else -> Qualities.Unknown.value
    }

    private fun subLabel(url: String): String {
        val code = Regex("""sub_([a-z]{2,3})""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase()
        return when (code) {
            "rus", "ru" -> "Русский"
            "eng", "en" -> "English"
            "tur", "tr" -> "Türkçe"
            "aze", "az" -> "Azərbaycan"
            "ukr", "uk" -> "Українська"
            null -> "Altyazı"
            else -> code
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 1) film sayfasindaki player iframe'i
        val page = app.get(data, referer = "$mainUrl/").document
        val frames = page.select("iframe").map { it.attr("data-src").ifBlank { it.attr("src") } }.filter { it.isNotBlank() }
        val frame = frames.firstOrNull { it.contains("token") } ?: frames.firstOrNull { it.startsWith("http") || it.startsWith("//") }
        if (frame == null) {
            Log.w(TAG, "iframe yok: $data (iframes=${frames.size})")
            return false
        }
        val frameUrl = if (frame.startsWith("//")) "https:$frame" else fixUrl(frame)

        // 2) player sayfasi (yonlendirmeden sonraki gercek adres)
        val playerRes = app.get(frameUrl, referer = data)
        val playerUrl = playerRes.url
        val html = playerRes.text
        val uri = URI(playerUrl)
        val origin = "${uri.scheme}://${uri.host}"

        Log.i(TAG, "player=$playerUrl bytes=${html.length}")

        val token = Regex("""token["']?\s*[:=]\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)
            ?: Regex("""[?&]token=([^&#]+)""").find(playerUrl)?.groupValues?.get(1)
            ?: Regex("""[?&]token=([^&#]+)""").find(frameUrl)?.groupValues?.get(1)
        if (token == null) {
            Log.w(TAG, "token yok: $playerUrl")
            return false
        }

        // Icerik numarasi: oynatici sayfasinda /bnsi/<tur>/<id>, id: 123, data-id="123" ...
        val directPath = Regex("""(/bnsi/[A-Za-z_]+/\d+)""").find(html)?.groupValues?.get(1)
        val contentId = Regex("""["']?(?:movie_?id|movieId|content_?id|kp_?id|id)["']?\s*[:=]\s*["']?(\d{5,9})""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""data-(?:id|movie-id|content-id)\s*=\s*["'](\d{5,9})""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        val kind = Regex("""["']?(?:type|content_?type)["']?\s*[:=]\s*["'](movies?|serials?|tv|anime|cartoons?)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.lowercase()
        val paths = LinkedHashSet<String>()
        if (directPath != null) paths.add(directPath)
        if (contentId != null) {
            val k = when {
                kind == null -> null
                kind.startsWith("movie") -> "movies"
                kind.startsWith("serial") || kind == "tv" -> "serials"
                kind.startsWith("cartoon") -> "cartoons"
                else -> kind
            }
            if (k != null) paths.add("/bnsi/$k/$contentId")
            paths.add("/bnsi/movies/$contentId")
            paths.add("/bnsi/serials/$contentId")
        }
        if (paths.isEmpty()) {
            Log.w(TAG, "icerik numarasi yok: $playerUrl html=${html.take(600)}")
            return false
        }
        Log.i(TAG, "token=$token paths=$paths")

        // 3) kaynak listesi (birden fazla aday yol varsa m3u8 donen ilkini kullan)
        var body = ""
        loop@ for (apiPath in paths) for (av1 in listOf("false", "true")) {
            body = app.post(
            "$origin$apiPath",
            data = mapOf(
                "token" to token,
                // Once H.264 (AV1 cozemeyen telefonlar icin), olmazsa AV1.
                "av1" to av1,
                "autoplay" to "0",
                "audio" to "",
                "subtitle" to ""
            ),
            referer = playerUrl,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to origin
            )
            ).text.replace("\\/", "/")
            Log.i(TAG, "POST $apiPath av1=$av1 -> ${body.length} bayt m3u8=${body.contains(".m3u8")}")
            if (body.contains(".m3u8")) break@loop
        }

        val playHeaders = mapOf("Origin" to origin)
        val seen = HashSet<String>()
        var found = false

        // "720":"https://.../master.m3u8" gibi kalite eslesmeleri
        Regex(""""([^"]{1,12})"\s*:\s*"(https?://[^"]+?\.m3u8[^"]*)"""").findAll(body).forEach { m ->
            val key = m.groupValues[1]
            val link = m.groupValues[2]
            if (!seen.add(link)) return@forEach
            found = true
            callback.invoke(
                newExtractorLink(name, "$name ${key.uppercase()}", link, ExtractorLinkType.M3U8) {
                    this.referer = playerUrl
                    this.quality = qualityOf(key)
                    this.headers = playHeaders
                }
            )
        }
        // Anahtarsiz m3u8 adresleri (yedek)
        Regex("""(https?://[^"'\s]+?\.m3u8[^"'\s]*)""").findAll(body).forEach { m ->
            val link = m.groupValues[1]
            if (!seen.add(link)) return@forEach
            found = true
            callback.invoke(
                newExtractorLink(name, name, link, ExtractorLinkType.M3U8) {
                    this.referer = playerUrl
                    this.quality = Qualities.Unknown.value
                    this.headers = playHeaders
                }
            )
        }

        // 4) altyazilar (Origin basligi ister)
        Regex("""(https?://[^"'\s]+?\.vtt[^"'\s]*)""").findAll(body).map { it.groupValues[1] }.distinct().forEach { sub ->
            if (sub.contains("thumb", true) || sub.contains("sprite", true)) return@forEach
            subtitleCallback.invoke(
                newSubtitleFile(subLabel(sub), sub) {
                    this.headers = playHeaders
                }
            )
        }
        Log.i(TAG, "sonuc found=$found")
        return found
    }
}

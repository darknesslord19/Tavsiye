// Otomatik üretildi - fullhdfilmizlesene.now
// Seçiciler sayfadan çıkarıldı; hedef sitede bir kez doğrulayın.
// oynatıcı iframe: rapidvid.org
// medya: https://s34.imgz.me/m4/DJ5coJSfpl4lZQV2YyqSDv1RGP4kZQtjpP5RIHSZYxthZwL0YHuRGD/eng-3.vtt
// medya: https://s34.imgz.me/m9/DJ5coJSfpl4lZQV2YyqSDv1RGP4kZQtjpP5RIHSZYxthZwL0YHuRGD/tur-2-default.vtt
// medya: https://s34.imgz.me/m5/DJ5coJSfpl4lZQV2YyqSDv1RGP4kZQtjpP5RIHSZYxthZwL0YHuRGD/tur-1.vtt
// not: Sunucu: Cloudflare arkasında

package com.fullhdfilmizlesene

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class Fullhdfilmizlesene : MainAPI() {
    override var mainUrl = "https://www.fullhdfilmizlesene.now"
    override var name = "Fullhdfilmizlesene"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val cfKiller = CloudflareKiller()
    private suspend fun getPage(url: String, ref: String? = null) =
        app.get(url, referer = ref ?: "$mainUrl/", interceptor = cfKiller)

    override val mainPage = mainPageOf(
        "https://www.fullhdfilmizlesene.now/en-cok-izlenen-filmler" to "En İyiler",
        "https://www.fullhdfilmizlesene.now/film-listeleri" to "Listeler",
        "https://www.fullhdfilmizlesene.now/seri-filmler" to "Seriler",
        "https://www.fullhdfilmizlesene.now/filmizle/aile-filmleri" to "Aile Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/aksiyon-filmleri" to "Aksiyon Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/animasyon-filmleri" to "Animasyon Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/belgesel-filmleri" to "Belgeseller",
        "https://www.fullhdfilmizlesene.now/filmizle/bilim-kurgu-filmleri" to "Bilim Kurgu Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/bluray-filmler" to "Blu Ray Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/cizgi-filmler" to "Çizgi Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/dram-filmler-izle" to "Dram Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/fantastik-filmler" to "Fantastik Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = request.data
        val url = if (page <= 1) base
            else if (base.contains("?")) "$base&page=$page"
            else "${base.trimEnd('/')}/page/$page/"
        val document = getPage(url).document
        val home = document.select("div.fut-box").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val img = this.selectFirst("img")
        val title = img?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: this.selectFirst("a")?.attr("title")?.takeIf { it.isNotBlank() }
            ?: this.text().trim().takeIf { it.isNotBlank() } ?: return null
        val poster = fixUrlNull(
            img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")
        )
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val document = getPage("https://www.fullhdfilmizlesene.now/?s=$q").document
        return document.select("div.fut-box").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = getPage(url).document
        val title = document.selectFirst("h1.blok-baslik")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content") ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = getPage(data)
        val html = response.text
        val source = html
        var found = emitLinks(source, data, subtitleCallback, callback)

        // iframe oynatıcılar (hazır çıkarıcılar)
        response.document.select("iframe[src], iframe[data-src]").forEach { f ->
            val src = f.attr("src").ifBlank { f.attr("data-src") }
            if (src.isBlank()) return@forEach
            val iframeUrl = fixUrl(src)
            if (loadExtractor(iframeUrl, data, subtitleCallback, callback)) {
                found = true
            } else {
                // hazır çıkarıcı yoksa iframe içeriğini kendimiz tarayalım
                val inner = runCatching { getPage(iframeUrl, data).text }.getOrNull()
                if (inner != null) {
                    if (emitLinks(inner, iframeUrl, subtitleCallback, callback)) found = true
                }
            }
        }
        return found
    }

    private suspend fun emitLinks(
        text: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val clean = text.replace("\\/", "/")
        Regex("""https?://[^\s"'<>\\]+?\.(?:m3u8|mp4|mpd)(?:\?[^\s"'<>\\]*)?""")
            .findAll(clean).map { it.value }.distinct().toList().forEach { link ->
                found = true
                if (link.contains(".m3u8")) {
                    M3u8Helper.generateM3u8(name, link, referer).forEach(callback)
                } else {
                    // Eski CloudStream sürümlerinde: ExtractorLink(name, name, link, referer, Qualities.Unknown.value)
                    callback(newExtractorLink(name, name, link) {
                        this.referer = referer
                        this.quality = Qualities.Unknown.value
                    })
                }
            }
        Regex("""https?://[^\s"'<>\\]+?\.(?:vtt|srt)(?:\?[^\s"'<>\\]*)?""")
            .findAll(clean).map { it.value }.distinct().toList().forEach { sub ->
                subtitleCallback(SubtitleFile("Türkçe", sub))
            }
        return found
    }
}

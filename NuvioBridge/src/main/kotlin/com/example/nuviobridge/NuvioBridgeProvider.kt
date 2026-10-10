package com.example.nuviobridge

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * only == null: tüm etkin sağlayıcılardan link toplayan genel kaynak ("Nuvio Bridge").
 * only != null: yalnızca o sağlayıcıyı çalıştıran, kendi adıyla görünen kaynak.
 */
class NuvioBridgeProvider(private val only: ScriptRef? = null) : MainAPI() {
    override var name = if (only != null) only.provider + " · " + only.repo else "Nuvio Bridge"
    override var mainUrl = "https://www.themoviedb.org"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        // Kendi TMDB API anahtarın (ücretsiz): https://www.themoviedb.org/settings/api
        internal const val TMDB_KEY = "a60812355b356abc3af771f9d8476450"

        // Nuvio repoları koda yazılmaz: eklentinin ayarlar (dişli) ekranından eklenir.

        // Birincil adres açılmazsa (DNS/engel) ikincisi denenir. İkisi de aynı TMDB servisidir.
        internal val TMDB_HOSTS = listOf(
            "https://api.themoviedb.org/3",
            "https://api.tmdb.org/3",
        )
        private const val IMG = "https://image.tmdb.org/t/p/"

        // Aynı anda en fazla kaç provider çalışsın, hepsi için toplam bekleme (ms)
        private const val MAX_PARALLEL = 8
        private const val TOTAL_TIMEOUT_MS = 45_000L
    }

    /**
     * TMDB isteği. Önce birincil, olmazsa yedek adres denenir.
     * İkisi de başarısız olursa nedenini yazan bir hata fırlatır (Cloudstream bunu ekranda gösterir).
     */
    internal suspend fun tmdbGet(path: String, extraParams: String = ""): JSONObject {
        val query = "?api_key=" + TMDB_KEY + "&language=tr-TR" + extraParams
        val problems = mutableListOf<String>()
        for (host in TMDB_HOSTS) {
            try {
                val res = app.get(host + path + query)
                if (res.code in 200..299) return JSONObject(res.text)
                problems += host.substringAfter("//").substringBefore("/") + " -> HTTP " + res.code
            } catch (ex: Exception) {
                problems += host.substringAfter("//").substringBefore("/") + " -> " +
                    (ex.message ?: ex.javaClass.simpleName)
            }
        }
        throw ErrorLoadingException("TMDB'ye ulaşılamadı: " + problems.joinToString("; "))
    }

    // ---------- Ana sayfa: TMDB'den trend / popüler listeler ----------
    override val mainPage = mainPageOf(
        "/trending/all/week" to "Haftanın Trendleri",
        "/movie/popular" to "Popüler Filmler",
        "/tv/popular" to "Popüler Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val json = tmdbGet(request.data, "&page=" + page)
        val forced = when {
            request.data.startsWith("/movie/") -> "movie"
            request.data.startsWith("/tv/") -> "tv"
            else -> null
        }
        return newHomePageResponse(request.name, toSearchList(json.optJSONArray("results"), forced))
    }

    // ---------- Arama ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = tmdbGet("/search/multi", "&query=" + q)
        return toSearchList(json.optJSONArray("results"), null)
    }

    private fun toSearchList(results: JSONArray?, forcedType: String?): List<SearchResponse> {
        if (results == null) return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.getJSONObject(i)
            val type = forcedType ?: o.optString("media_type")
            val title = o.optString("title").ifBlank { o.optString("name") }
            if (title.isBlank()) return@mapNotNull null
            val poster = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                ?.let { IMG + "w342" + it }
            when (type) {
                // Cloudstream http ile başlamayan adresin başına mainUrl ekler; bu yüzden tam adres veriyoruz.
                "movie" -> newMovieSearchResponse(title, mainUrl + "/movie/" + o.getInt("id"), TvType.Movie) {
                    posterUrl = poster
                }
                "tv" -> newTvSeriesSearchResponse(title, mainUrl + "/tv/" + o.getInt("id"), TvType.TvSeries) {
                    posterUrl = poster
                }
                else -> null
            }
        }
    }

    // ---------- Detay sayfası ----------
    override suspend fun load(url: String): LoadResponse {
        // Adres ".../movie/603" veya ".../tv/1399" biçiminde gelir (eski "movie:603" da çalışır).
        val match = Regex("(movie|tv)[/:](\\d+)").find(url)
            ?: throw ErrorLoadingException("Geçersiz adres: " + url)
        val type = match.groupValues[1]
        val id = match.groupValues[2]
        val pageUrl = "$mainUrl/$type/$id"
        return if (type == "movie") {
            val o = tmdbGet("/movie/$id")
            newMovieLoadResponse(o.optString("title"), pageUrl, TvType.Movie, "movie|$id|0|0") {
                plot = o.optString("overview")
                posterUrl = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { IMG + "w500" + it }
            }
        } else {
            val o = tmdbGet("/tv/$id")
            val episodes = mutableListOf<Episode>()
            val seasons = o.optJSONArray("seasons")
            for (i in 0 until (seasons?.length() ?: 0)) {
                val sNo = seasons!!.getJSONObject(i).getInt("season_number")
                if (sNo == 0) continue // özel bölümleri atla
                // Tek bir sezon alınamazsa diğerleri yine de listelensin.
                val s = runCatching { tmdbGet("/tv/$id/season/$sNo") }.getOrNull() ?: continue
                val eps = s.optJSONArray("episodes") ?: continue
                for (j in 0 until eps.length()) {
                    val e = eps.getJSONObject(j)
                    val eNo = e.getInt("episode_number")
                    episodes += newEpisode("tv|$id|$sNo|$eNo") {
                        this.season = sNo
                        this.episode = eNo
                        this.name = e.optString("name")
                    }
                }
            }
            newTvSeriesLoadResponse(o.optString("name"), pageUrl, TvType.TvSeries, episodes) {
                plot = o.optString("overview")
                posterUrl = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { IMG + "w500" + it }
            }
        }
    }

    // ---------- Linkleri al: etkin tüm provider'ları paralel çalıştır ----------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val (type, id, s, e) = data.split("|")
        val season = s.toInt().takeIf { type == "tv" }
        val episode = e.toInt().takeIf { type == "tv" }

        val scripts = RepoStore.enabledScripts().filter { only == null || it.url == only.url }
        if (scripts.isEmpty()) return false

        val pool = Executors.newFixedThreadPool(minOf(MAX_PARALLEL, scripts.size))
        var found = false
        try {
            val ecs = ExecutorCompletionService<Pair<ScriptRef, List<Pair<JsStream, String>>>>(pool)
            scripts.forEach { ref ->
                ecs.submit(Callable {
                    // Her provider kendi thread'inde, kendi JS motoruyla çalışır.
                    val script = blocking { app.get(ref.url).text }
                    val streams = JsRunner.getStreams(script, id, type, season, episode)
                    // Her akışın gerçek türünü belirle; video olmayanları (web sayfası vb.) ele.
                    ref to streams.take(12)
                        .map { it to StreamKind.detect(it) }
                        .filter { it.second != StreamKind.SKIP }
                })
            }

            val deadline = System.currentTimeMillis() + TOTAL_TIMEOUT_MS
            var remaining = scripts.size
            while (remaining > 0) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                val future = ecs.poll(left, TimeUnit.MILLISECONDS) ?: break
                remaining--
                // Hata veren provider diğerlerini durdurmasın.
                val (ref, streams) = runCatching { future.get() }.getOrNull() ?: continue
                for ((st, kind) in streams) {
                    found = true
                    callback(
                        newExtractorLink(
                            source = ref.provider,
                            name = ref.repo + " · " + ref.provider + " - " + st.name,
                            url = st.url,
                            type = when (kind) {
                                StreamKind.M3U8 -> ExtractorLinkType.M3U8
                                StreamKind.DASH -> ExtractorLinkType.DASH
                                else -> ExtractorLinkType.VIDEO
                            },
                        ) {
                            this.quality = st.quality
                            this.headers = st.headers
                        }
                    )
                }
            }
        } finally {
            pool.shutdownNow()
        }
        return found
    }
}

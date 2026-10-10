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

class NuvioBridgeProvider : MainAPI() {
    override var name = "Nuvio Bridge"
    override var mainUrl = "https://www.themoviedb.org"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        // Kendi TMDB API anahtarın (ücretsiz): https://www.themoviedb.org/settings/api
        private const val TMDB_KEY = "a60812355b356abc3af771f9d8476450"

        // Nuvio repoları koda yazılmaz: eklentinin ayarlar (dişli) ekranından eklenir.

        private const val TMDB = "https://api.themoviedb.org/3"
        private const val IMG = "https://image.tmdb.org/t/p/"

        // Aynı anda en fazla kaç provider çalışsın, hepsi için toplam bekleme (ms)
        private const val MAX_PARALLEL = 8
        private const val TOTAL_TIMEOUT_MS = 45_000L
    }

    // ---------- Ana sayfa: TMDB'den trend / popüler listeler ----------
    override val mainPage = mainPageOf(
        "$TMDB/trending/all/week" to "Haftanın Trendleri",
        "$TMDB/movie/popular" to "Popüler Filmler",
        "$TMDB/tv/popular" to "Popüler Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val json = JSONObject(
            app.get("${request.data}?api_key=$TMDB_KEY&language=tr-TR&page=$page").text
        )
        val forced = when {
            request.data.contains("/movie/") -> "movie"
            request.data.contains("/tv/") -> "tv"
            else -> null
        }
        return newHomePageResponse(request.name, toSearchList(json.optJSONArray("results"), forced))
    }

    // ---------- Arama: url alanına "movie:603" / "tv:1399" yazılır ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(
            app.get("$TMDB/search/multi?api_key=$TMDB_KEY&language=tr-TR&query=$q").text
        )
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
            val o = JSONObject(app.get("$TMDB/movie/$id?api_key=$TMDB_KEY&language=tr-TR").text)
            newMovieLoadResponse(o.optString("title"), pageUrl, TvType.Movie, "movie|$id|0|0") {
                plot = o.optString("overview")
                posterUrl = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { IMG + "w500" + it }
            }
        } else {
            val o = JSONObject(app.get("$TMDB/tv/$id?api_key=$TMDB_KEY&language=tr-TR").text)
            val episodes = mutableListOf<Episode>()
            val seasons = o.optJSONArray("seasons")
            for (i in 0 until (seasons?.length() ?: 0)) {
                val sNo = seasons!!.getJSONObject(i).getInt("season_number")
                if (sNo == 0) continue // özel bölümleri atla
                val s = JSONObject(
                    app.get("$TMDB/tv/$id/season/$sNo?api_key=$TMDB_KEY&language=tr-TR").text
                )
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

        val scripts = RepoStore.enabledScripts()
        if (scripts.isEmpty()) return false

        val pool = Executors.newFixedThreadPool(minOf(MAX_PARALLEL, scripts.size))
        var found = false
        try {
            val ecs = ExecutorCompletionService<Pair<ScriptRef, List<JsStream>>>(pool)
            scripts.forEach { ref ->
                ecs.submit(Callable {
                    // Her provider kendi thread'inde, kendi JS motoruyla çalışır.
                    val script = blocking { app.get(ref.url).text }
                    ref to JsRunner.getStreams(script, id, type, season, episode)
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
                for (st in streams) {
                    found = true
                    callback(
                        newExtractorLink(
                            source = ref.provider,
                            name = ref.repo + " · " + ref.provider + " - " + st.name,
                            url = st.url,
                            type = if (st.url.contains(".m3u8")) ExtractorLinkType.M3U8
                            else ExtractorLinkType.VIDEO,
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

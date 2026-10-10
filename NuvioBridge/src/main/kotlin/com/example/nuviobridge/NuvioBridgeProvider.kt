package com.example.nuviobridge

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class NuvioBridgeProvider : MainAPI() {
    override var name = "Nuvio Bridge"
    override var mainUrl = "https://www.themoviedb.org"
    override var lang = "en"
    override val hasMainPage = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        // 1) Kendi TMDB API anahtarını yaz (ücretsiz): https://www.themoviedb.org/settings/api
        private const val TMDB_KEY = "a60812355b356abc3af771f9d8476450"

        // Nuvio repoları artık koda yazılmaz: eklentinin ayarlar (dişli) ekranından eklenir.

        private const val TMDB = "https://api.themoviedb.org/3"
    }

    // ---------- Arama: TMDB'den sonuç al, url alanına "movie:603" / "tv:1399" yaz ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val json = JSONObject(
            app.get("$TMDB/search/multi?api_key=$TMDB_KEY&query=$query").text
        )
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.getJSONObject(i)
            val type = o.optString("media_type")
            val title = o.optString("title").ifBlank { o.optString("name") }
            val poster = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                ?.let { "https://image.tmdb.org/t/p/w342$it" }
            when (type) {
                "movie" -> newMovieSearchResponse(title, "movie:${o.getInt("id")}", TvType.Movie) {
                    posterUrl = poster
                }
                "tv" -> newTvSeriesSearchResponse(title, "tv:${o.getInt("id")}", TvType.TvSeries) {
                    posterUrl = poster
                }
                else -> null
            }
        }
    }

    // ---------- Detay sayfası ----------
    override suspend fun load(url: String): LoadResponse {
        val (type, id) = url.split(":")
        return if (type == "movie") {
            val o = JSONObject(app.get("$TMDB/movie/$id?api_key=$TMDB_KEY").text)
            newMovieLoadResponse(o.optString("title"), url, TvType.Movie, "movie|$id|0|0") {
                plot = o.optString("overview")
                posterUrl = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { "https://image.tmdb.org/t/p/w500$it" }
            }
        } else {
            val o = JSONObject(app.get("$TMDB/tv/$id?api_key=$TMDB_KEY").text)
            val episodes = mutableListOf<Episode>()
            val seasons = o.optJSONArray("seasons")
            for (i in 0 until (seasons?.length() ?: 0)) {
                val sNo = seasons!!.getJSONObject(i).getInt("season_number")
                if (sNo == 0) continue // özel bölümleri atla
                val s = JSONObject(app.get("$TMDB/tv/$id/season/$sNo?api_key=$TMDB_KEY").text)
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
            newTvSeriesLoadResponse(o.optString("name"), url, TvType.TvSeries, episodes) {
                plot = o.optString("overview")
                posterUrl = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { "https://image.tmdb.org/t/p/w500$it" }
            }
        }
    }

    // ---------- Linkleri al: repodaki tüm provider'ları JS motorunda çalıştır ----------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val (type, id, s, e) = data.split("|")
        val season = s.toInt().takeIf { type == "tv" }
        val episode = e.toInt().takeIf { type == "tv" }

        var found = false
        for ((pName, scriptUrl) in RepoStore.enabledScripts()) {
            val script = runCatching { app.get(scriptUrl).text }.getOrNull() ?: continue
            val streams = runCatching {
                JsRunner.getStreams(script, id, type, season, episode)
            }.getOrElse { emptyList() } // bozuk bir provider diğerlerini durdurmasın

            for (st in streams) {
                found = true
                callback(
                    newExtractorLink(
                        source = pName,
                        name = "$pName - ${st.name}",
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
        return found
    }
}

package com.example.nuviobridge

import android.content.Context
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

data class Repo(val url: String, val enabled: Boolean)

/** Kullanıcının eklediği Nuvio repolarını SharedPreferences'ta tutar ve manifest'leri çözer. */
object RepoStore {
    private const val PREFS = "nuvio_bridge"
    private const val KEY = "repos"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<Repo> {
        val raw = prefs()?.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Repo(o.getString("url"), o.optBoolean("enabled", true))
        }
    }

    private fun save(repos: List<Repo>) {
        val arr = JSONArray()
        repos.forEach { arr.put(JSONObject().put("url", it.url).put("enabled", it.enabled)) }
        prefs()?.edit()?.putString(KEY, arr.toString())?.apply()
    }

    fun remove(url: String) = save(list().filterNot { it.url == url })

    fun setEnabled(url: String, enabled: Boolean) =
        save(list().map { if (it.url == url) it.copy(enabled = enabled) else it })

    /** Manifest'i indirip doğrular; geçerliyse kaydeder. Dönüş: provider sayısı. */
    suspend fun add(rawUrl: String): Result<Int> = runCatching {
        val url = rawUrl.trim()
        require(url.startsWith("http")) { "Geçerli bir http(s) adresi gir" }
        require(list().none { it.url == url }) { "Bu repo zaten ekli" }
        val scripts = providerScripts(url)
        require(scripts.isNotEmpty()) { "Manifest okundu ama provider bulunamadı" }
        save(list() + Repo(url, true))
        scripts.size
    }

    /** Etkin tüm repolardaki (ad, script URL'i) çiftleri. */
    suspend fun enabledScripts(): List<Pair<String, String>> =
        list().filter { it.enabled }.flatMap { runCatching { providerScripts(it.url) }.getOrElse { emptyList() } }

    // VARSAYIM: manifest'te "scrapers" (veya "providers") dizisi var; öğelerde "name" ve "filename".
    // Gerçek repo şemasına göre burayı düzelt.
    suspend fun providerScripts(manifestUrl: String): List<Pair<String, String>> {
        val base = manifestUrl.substringBeforeLast("/") + "/"
        val m = JSONObject(app.get(manifestUrl).text)
        val arr = m.optJSONArray("scrapers") ?: m.optJSONArray("providers") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            if (o.has("enabled") && !o.optBoolean("enabled", true)) return@mapNotNull null
            val file = o.optString("filename").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = if (file.startsWith("providers/")) file else "providers/$file"
            o.optString("name", file) to base + path
        }
    }
}

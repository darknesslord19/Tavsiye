package com.example.nuviobridge

import android.content.Context
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

data class ProviderInfo(val name: String, val file: String, val enabled: Boolean)

data class Repo(
    val url: String,
    val enabled: Boolean,
    val name: String,
    val providers: List<ProviderInfo>,
)

/** Çalıştırılacak tek bir provider: hangi repodan, hangi ad, hangi script adresi. */
data class ScriptRef(val repo: String, val provider: String, val url: String)

/** Kullanıcının eklediği Nuvio repolarını SharedPreferences'ta tutar ve manifest'leri çözer. */
object RepoStore {
    private const val PREFS = "nuvio_bridge"
    private const val KEY = "repos"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context? = appContext

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun fallbackName(url: String) =
        runCatching { java.net.URI(url).host }.getOrNull() ?: url

    fun list(): List<Repo> {
        val raw = prefs()?.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val provs = o.optJSONArray("providers")
            val providers = (0 until (provs?.length() ?: 0)).map { j ->
                val p = provs!!.getJSONObject(j)
                ProviderInfo(p.optString("name"), p.optString("file"), p.optBoolean("enabled", true))
            }
            val url = o.getString("url")
            Repo(
                url = url,
                enabled = o.optBoolean("enabled", true),
                name = o.optString("name").ifBlank { fallbackName(url) },
                providers = providers,
            )
        }
    }

    private fun save(repos: List<Repo>) {
        val arr = JSONArray()
        repos.forEach { r ->
            val provs = JSONArray()
            r.providers.forEach {
                provs.put(JSONObject().put("name", it.name).put("file", it.file).put("enabled", it.enabled))
            }
            arr.put(
                JSONObject()
                    .put("url", r.url)
                    .put("enabled", r.enabled)
                    .put("name", r.name)
                    .put("providers", provs)
            )
        }
        prefs()?.edit()?.putString(KEY, arr.toString())?.apply()
    }

    /** Her sağlayıcı Cloudstream'de ayrı kaynak olarak görünsün mü? (Değişiklik uygulama yeniden başlayınca geçerli olur.) */
    fun splitEnabled(): Boolean = prefs()?.getBoolean("split", true) ?: true

    fun setSplit(value: Boolean) {
        prefs()?.edit()?.putBoolean("split", value)?.apply()
    }

    private fun scriptUrl(repo: Repo, p: ProviderInfo): String {
        val base = repo.url.substringBeforeLast("/") + "/"
        val path = if (p.file.contains("/")) p.file else "providers/" + p.file
        return base + path
    }

    /**
     * Test sonucuna göre sağlayıcıları açıp kapatır: link verenler açık, vermeyenler kapalı.
     * Testte yer almayan (zaten kapalı ya da süre aşımına uğrayan) sağlayıcılara dokunmaz.
     * Dönüş: kapatılan sağlayıcı sayısı.
     */
    fun applyTestResults(results: Map<String, Boolean>): Int {
        var disabled = 0
        save(list().map { r ->
            r.copy(providers = r.providers.map { p ->
                val ok = results[scriptUrl(r, p)]
                if (ok == null) p
                else {
                    if (!ok && p.enabled) disabled++
                    p.copy(enabled = ok)
                }
            })
        })
        return disabled
    }

    fun remove(url: String) = save(list().filterNot { it.url == url })

    fun setEnabled(url: String, enabled: Boolean) =
        save(list().map { if (it.url == url) it.copy(enabled = enabled) else it })

    fun setProviderEnabled(url: String, file: String, enabled: Boolean) =
        save(list().map { r ->
            if (r.url != url) r
            else r.copy(providers = r.providers.map {
                if (it.file == file) it.copy(enabled = enabled) else it
            })
        })

    /** Manifest'i indirip doğrular; geçerliyse kaydeder. Dönüş: provider sayısı. */
    suspend fun add(rawUrl: String): Result<Int> = runCatching {
        val url = rawUrl.trim()
        require(url.startsWith("http")) { "Geçerli bir http(s) adresi gir" }
        require(list().none { it.url == url }) { "Bu repo zaten ekli" }
        val (name, providers) = fetchInfo(url)
        require(providers.isNotEmpty()) { "Manifest okundu ama provider bulunamadı" }
        save(list() + Repo(url, true, name, providers))
        providers.size
    }

    /** Eski sürümde eklenmiş (adı/provider listesi kayıtlı olmayan) repoların bilgisini tamamlar. */
    suspend fun refreshMissing() {
        val current = list()
        if (current.none { it.providers.isEmpty() }) return
        val updated = current.map { r ->
            if (r.providers.isNotEmpty()) r
            else runCatching {
                val (name, providers) = fetchInfo(r.url)
                r.copy(name = name, providers = providers)
            }.getOrDefault(r)
        }
        save(updated)
    }

    /** Etkin repolardaki, etkin provider'ların script adresleri (önbellekten, ağ isteği yok). */
    fun enabledScripts(): List<ScriptRef> =
        list().filter { it.enabled }.flatMap { r ->
            val base = r.url.substringBeforeLast("/") + "/"
            r.providers.filter { it.enabled }.map { p ->
                val path = if (p.file.contains("/")) p.file else "providers/" + p.file
                ScriptRef(r.name, p.name, base + path)
            }
        }

    /** Manifest şeması (doğrulandı): { name, scrapers: [ { name, filename, enabled? } ] } */
    private suspend fun fetchInfo(manifestUrl: String): Pair<String, List<ProviderInfo>> {
        val m = JSONObject(app.get(manifestUrl).text)
        val repoName = m.optString("name").ifBlank { fallbackName(manifestUrl) }
        val arr = m.optJSONArray("scrapers") ?: m.optJSONArray("providers") ?: JSONArray()
        val providers = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val file = o.optString("filename").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ProviderInfo(o.optString("name", file), file, o.optBoolean("enabled", true))
        }
        return repoName to providers
    }
}

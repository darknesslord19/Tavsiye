package com.example.sitebridge

import android.content.Context
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

/** Bir site kaydı. source: "user" (elle eklenen) | "remote" (listeden gelen) */
data class SiteInfo(
    val name: String,
    val url: String,
    val rules: JSONObject?,
    val enabled: Boolean,
    val source: String,
)

/**
 * Siteler iki yerden gelir:
 *  1) Kullanıcının ayarlardan elle eklediği siteler
 *  2) Liste adresleri (GitHub raw / gist / Firebase JSON): içlerindeki siteler önbelleğe alınır
 *
 * Liste biçimleri (MainActivity ile aynı):
 *  {"sites":[{"name":"..","url":"..","rules":{..}}]}  |  [{"name":"..","url":".."}]
 *  {"Ad":"https://..", ...}  |  Firebase'in sayısal anahtarlı nesneleri
 */
object SiteStore {
    // Ayarlardan hiç liste eklenmediyse kullanılan varsayılan liste. İstersen kendi GitHub raw adresinle değiştir.
    const val DEFAULT_SOURCE = "https://darknes-lord-paste-default-rtdb.firebaseio.com/pastes/Film.json"

    private const val PREFS = "site_bridge"
    private const val K_USER = "user_sites"
    private const val K_SOURCES = "sources"
    private const val K_CACHE = "remote_cache"
    private const val K_DISABLED = "disabled"
    private const val K_STAMP = "remote_stamp"
    private const val K_CATS = "cats"
    private const val K_STATUS = "status"
    private const val REFRESH_EVERY_MS = 6L * 3600 * 1000

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readArr(key: String): JSONArray =
        try {
            JSONArray(prefs()?.getString(key, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

    private fun normKey(u: String) = u.trim().lowercase().trimEnd('/')

    // ---------- Kayıt okuma ----------
    private fun disabledSet(): Set<String> {
        val a = readArr(K_DISABLED)
        return (0 until a.length()).map { normKey(a.optString(it)) }.toSet()
    }

    private fun toInfos(arr: JSONArray, source: String, disabled: Set<String>): List<SiteInfo> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url")
            if (url.isBlank()) return@mapNotNull null
            SiteInfo(
                name = o.optString("name").ifBlank { Scraper.hostOf(url) },
                url = url,
                rules = o.optJSONObject("rules"),
                enabled = !disabled.contains(normKey(url)),
                source = source,
            )
        }

    fun userSites(): List<SiteInfo> = toInfos(readArr(K_USER), "user", disabledSet())

    fun remoteSites(): List<SiteInfo> = toInfos(readArr(K_CACHE), "remote", disabledSet())

    /** Kullanıcının siteleri önce; aynı adres iki yerde varsa kullanıcınınki geçerli. */
    fun allSites(): List<SiteInfo> {
        val seen = HashSet<String>()
        val out = ArrayList<SiteInfo>()
        for (s in userSites() + remoteSites()) if (seen.add(normKey(s.url))) out.add(s)
        return out
    }

    fun enabledSites(): List<SiteInfo> = allSites().filter { it.enabled }

    /** Kullanıcının eklediği listeler (varsayılan liste burada yoktur, ayarlarda da gösterilmez). */
    fun userSources(): List<String> {
        val raw = prefs()?.getString(K_SOURCES, null) ?: return emptyList()
        val a = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until a.length()).map { a.optString(it) }
            .filter { it.isNotBlank() && normKey(it) != normKey(DEFAULT_SOURCE) }
    }

    /** Taranan tüm listeler: varsayılan liste her zaman başta, silinemez. */
    fun sources(): List<String> = listOf(DEFAULT_SOURCE) + userSources()

    // ---------- Kayıt yazma ----------
    private fun fixUrl(raw: String): String? {
        var u = raw.trim()
        if (u.isEmpty()) return null
        if (!u.startsWith("http")) {
            if (u.length > 3 && u.contains(".")) u = "https://$u" else return null
        }
        return u
    }

    /** Dönüş: hata metni ya da null (başarılı). */
    fun addSite(name: String, rawUrl: String, rulesJson: String): String? {
        val url = fixUrl(rawUrl) ?: return "Geçerli bir site adresi gir"
        var rules: JSONObject? = null
        if (rulesJson.isNotBlank()) {
            rules = try {
                JSONObject(rulesJson)
            } catch (e: Exception) {
                return "Kural JSON'u geçersiz: " + (e.message ?: "")
            }
        }
        val arr = readArr(K_USER)
        for (i in 0 until arr.length()) {
            if (normKey(arr.optJSONObject(i)?.optString("url") ?: "") == normKey(url)) return "Bu site zaten ekli"
        }
        val o = JSONObject().put("name", name.trim().ifBlank { Scraper.hostOf(url) }).put("url", url)
        if (rules != null) o.put("rules", rules)
        arr.put(o)
        prefs()?.edit()?.putString(K_USER, arr.toString())?.apply()
        setEnabled(url, true)
        return null
    }

    fun removeSite(url: String) {
        val arr = readArr(K_USER)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (normKey(o.optString("url")) != normKey(url)) out.put(o)
        }
        prefs()?.edit()?.putString(K_USER, out.toString())?.apply()
    }

    fun setEnabled(url: String, enabled: Boolean) {
        val set = disabledSet().toMutableSet()
        if (enabled) set.remove(normKey(url)) else set.add(normKey(url))
        prefs()?.edit()?.putString(K_DISABLED, JSONArray(set.toList()).toString())?.apply()
    }

    fun addSource(raw: String): String? {
        val u = fixUrl(raw) ?: return "Geçerli bir liste adresi gir"
        val list = userSources().toMutableList()
        if (normKey(u) == normKey(DEFAULT_SOURCE) || list.any { normKey(it) == normKey(u) }) return "Bu liste zaten ekli"
        list.add(u)
        prefs()?.edit()?.putString(K_SOURCES, JSONArray(list).toString())?.apply()
        return null
    }

    fun removeSource(url: String) {
        if (normKey(url) == normKey(DEFAULT_SOURCE)) return
        val list = userSources().filterNot { normKey(it) == normKey(url) }
        prefs()?.edit()?.putString(K_SOURCES, JSONArray(list).toString())?.apply()
    }

    // ---------- Kategori önbelleği (kaynak seçicide kategori satırları olarak görünür) ----------
    fun cats(siteUrl: String): List<SiteCat> {
        return try {
            val o = JSONObject(prefs()?.getString(K_CATS, "{}") ?: "{}")
            val a = o.optJSONArray(normKey(siteUrl)) ?: return emptyList()
            (0 until a.length()).mapNotNull { i ->
                val c = a.optJSONObject(i) ?: return@mapNotNull null
                val n = c.optString("n")
                val u = c.optString("u")
                if (n.isBlank() || u.isBlank()) null else SiteCat(n, u)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCats(siteUrl: String, cats: List<SiteCat>) {
        try {
            val o = JSONObject(prefs()?.getString(K_CATS, "{}") ?: "{}")
            val a = JSONArray()
            cats.forEach { a.put(JSONObject().put("n", it.name).put("u", it.url)) }
            o.put(normKey(siteUrl), a)
            prefs()?.edit()?.putString(K_CATS, o.toString())?.apply()
        } catch (ignored: Exception) {
        }
    }

    // ---------- Site durumu (ayar ekranındaki test sonucu) ----------
    class Status(val ok: Boolean, val text: String, val time: Long)

    fun status(siteUrl: String): Status? {
        return try {
            val o = JSONObject(prefs()?.getString(K_STATUS, "{}") ?: "{}").optJSONObject(normKey(siteUrl))
                ?: return null
            Status(o.optBoolean("ok", false), o.optString("t"), o.optInt("ts", 0).toLong())
        } catch (e: Exception) {
            null
        }
    }

    fun setStatus(siteUrl: String, ok: Boolean, text: String) {
        try {
            val all = JSONObject(prefs()?.getString(K_STATUS, "{}") ?: "{}")
            all.put(
                normKey(siteUrl),
                JSONObject().put("ok", ok).put("t", text.take(200)).put("ts", (System.currentTimeMillis() / 1000).toInt())
            )
            prefs()?.edit()?.putString(K_STATUS, all.toString())?.apply()
        } catch (ignored: Exception) {
        }
    }

    // ---------- Liste çözümleme ----------
    fun parseSites(json: String): List<SiteInfo> {
        val out = ArrayList<SiteInfo>()
        try {
            val root0 = org.json.JSONTokener(json).nextValue()
            var list: Any? = root0
            if (root0 is JSONObject && root0.has("sites")) list = root0.get("sites")
            if (list is JSONArray) {
                for (i in 0 until list.length()) addParsed(out, list.get(i), null)
            } else if (list is JSONObject) {
                val it = list.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    if (k == "version" || k == "updated") continue
                    addParsed(out, list.get(k), k)
                }
            }
        } catch (ignored: Exception) {
        }
        return out
    }

    private fun addParsed(out: MutableList<SiteInfo>, el: Any?, key: String?) {
        var name: String? = null
        var url: String? = null
        var rules: JSONObject? = null
        if (el is JSONObject) {
            if (!el.optBoolean("enabled", true)) return
            rules = el.optJSONObject("rules")
            url = el.optString("url", el.optString("link", el.optString("site", "")))
            name = el.optString("name", el.optString("ad", el.optString("title", key ?: "")))
        } else if (el is String) {
            url = el
            name = key
        }
        val fixed = fixUrl(url ?: return) ?: return
        val n = if (name.isNullOrBlank()) Scraper.hostOf(fixed) else name.trim()
        out.add(SiteInfo(n, fixed, rules, true, "remote"))
    }

    /**
     * Liste adresini indirilecek adres(ler)e çevirir:
     *  github.com/k/r/blob/dal/yol  -> raw.githubusercontent.com/k/r/dal/yol
     *  github.com/k/r               -> raw.../k/r/HEAD|main|master/sites.json (ve siteler.json)
     *  gist.github.com/k/id         -> gist.github.com/k/id/raw
     */
    fun candidates(url: String): List<String> {
        val u = url.trim()
        val m = Regex("^https?://(?:www\\.)?github\\.com/([^/]+)/([^/#?]+)(?:/blob/([^/]+)/(.+?))?/?(?:[#?].*)?$")
            .find(u)
        if (m != null) {
            val (owner, repo0) = m.destructured
            val repo = repo0.removeSuffix(".git")
            val branch = m.groupValues[3]
            val path = m.groupValues[4]
            if (branch.isNotEmpty() && path.isNotEmpty()) {
                return listOf("https://raw.githubusercontent.com/$owner/$repo/$branch/$path")
            }
            val out = ArrayList<String>()
            for (b in listOf("HEAD", "main", "master")) {
                for (f in listOf("sites.json", "siteler.json")) {
                    out.add("https://raw.githubusercontent.com/$owner/$repo/$b/$f")
                }
            }
            return out
        }
        if (Regex("^https?://gist\\.github\\.com/[^/]+/[0-9a-f]+/?$").matches(u)) {
            return listOf(u.trimEnd('/') + "/raw")
        }
        return listOf(u)
    }

    /** Tüm liste adreslerini indirip önbelleğe yazar. Dönüş: bulunan site sayısı ya da hata metni. */
    suspend fun refreshRemote(): Result<Int> = runCatching {
        val merged = ArrayList<SiteInfo>()
        val seen = HashSet<String>()
        val errors = ArrayList<String>()
        for (src in sources()) {
            var got: List<SiteInfo> = emptyList()
            var lastErr = ""
            for (cand in candidates(src)) {
                try {
                    val res = app.get(cand)
                    if (res.code !in 200..299) {
                        lastErr = "HTTP " + res.code
                        continue
                    }
                    got = parseSites(res.text)
                    if (got.isNotEmpty()) break
                    lastErr = "içinde site bulunamadı"
                } catch (e: Exception) {
                    lastErr = e.message ?: e.javaClass.simpleName
                }
            }
            if (got.isEmpty()) errors.add(src.substringAfter("//").take(40) + ": " + lastErr)
            for (s in got) if (seen.add(normKey(s.url))) merged.add(s)
        }
        if (merged.isEmpty() && errors.isNotEmpty()) {
            throw RuntimeException("Liste okunamadı — " + errors.joinToString("; "))
        }
        val arr = JSONArray()
        for (s in merged) {
            val o = JSONObject().put("name", s.name).put("url", s.url)
            if (s.rules != null) o.put("rules", s.rules)
            arr.put(o)
        }
        prefs()?.edit()?.putString(K_CACHE, arr.toString())
            ?.putLong(K_STAMP, System.currentTimeMillis())?.apply()
        merged.size
    }

    fun cacheEmpty(): Boolean = readArr(K_CACHE).length() == 0

    fun refreshDue(): Boolean {
        val last = prefs()?.getLong(K_STAMP, 0L) ?: 0L
        return System.currentTimeMillis() - last > REFRESH_EVERY_MS
    }
}

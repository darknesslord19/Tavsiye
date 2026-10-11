package com.darkneslord.iptv

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

/** Bir liste kaydı. admin=true: GitHub'dan gelen, silinemez/düzenlenemez liste. */
data class IptvList(val id: String, val name: String, val url: String, val admin: Boolean, val enabled: Boolean = true)

data class Status(val ok: Boolean, val text: String, val time: Long)

object IptvStore {
    /**
     * Yönetici listesinin adresi (GitHub raw / Firebase .json). Kullanıcı bunu silemez.
     * İçerik şunlardan biri olabilir:
     *   [{"name":"Spor","url":"https://.../spor.m3u"}]   ya da   {"Spor":"https://.../spor.m3u"}   ya da   {"lists":[...]}
     *   ya da doğrudan bir .m3u dosyası (tek liste olarak eklenir)
     */
    const val ADMIN_SOURCE = "https://darknes-lord-paste-default-rtdb.firebaseio.com/pastes/Cloudstreamtv.json"
    private const val ADMIN_PLACEHOLDER = "KULLANICI/REPO"

    private const val PREFS = "iptv_bridge"
    private const val K_USER = "user"
    private const val K_ADMIN = "admin_cache"
    private const val K_STAMP = "admin_stamp"
    private const val K_GROUPS = "groups"
    private const val K_STATUS = "status"
    private const val K_OFF = "disabled"
    private const val K_HIDDEN = "hidden_groups"
    private const val REFRESH_MS = 6 * 3600_000L

    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun adminConfigured() = !ADMIN_SOURCE.contains(ADMIN_PLACEHOLDER)

    // ---------------------------------------------------------------- kullanıcı listeleri
    private fun readList(key: String, admin: Boolean): List<IptvList> {
        val a = try { JSONArray(prefs?.getString(key, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val off = disabledIds()
        val out = ArrayList<IptvList>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            val id = o.optString("id").ifBlank { newId(url) }
            out.add(IptvList(id, o.optString("name").ifBlank { hostOf(url) }, url, admin, id !in off))
        }
        return out
    }

    private fun writeList(key: String, items: List<IptvList>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        prefs?.edit()?.putString(key, a.toString())?.apply()
    }

    fun userLists(): List<IptvList> = readList(K_USER, false)
    fun adminLists(): List<IptvList> = if (adminConfigured()) readList(K_ADMIN, true) else emptyList()
    fun all(): List<IptvList> = adminLists() + userLists()
    /** CloudStream'e kaynak olarak kaydedilecek (aktif) listeler. */
    fun enabledLists(): List<IptvList> = all().filter { it.enabled }

    // ---------------------------------------------------------------- aktif / pasif
    private fun disabledIds(): Set<String> {
        val a = try { JSONArray(prefs?.getString(K_OFF, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).map { a.optString(it) }.toSet()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val s = disabledIds().toMutableSet()
        if (enabled) s.remove(id) else s.add(id)
        prefs?.edit()?.putString(K_OFF, JSONArray(s.toList()).toString())?.apply()
    }

    private fun newId(seed: String) = Integer.toHexString((seed + System.nanoTime()).hashCode())
    private fun key(u: String) = u.trim().lowercase().removeSuffix("/")

    fun hostOf(u: String): String = u.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.")

    /** GitHub blob adresini raw'a çevirir, boşlukları temizler. */
    fun fixUrl(raw: String): String? {
        var u = raw.trim()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return null
        val m = Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/(.+)$""", RegexOption.IGNORE_CASE).find(u)
        if (m != null) u = "https://raw.githubusercontent.com/" + m.groupValues[1] + "/" + m.groupValues[2] + "/" + m.groupValues[3]
        Regex("""^https?://gist\.github\.com/([^/]+)/([0-9a-f]+)/?$""", RegexOption.IGNORE_CASE).find(u)?.let {
            u = "https://gist.githubusercontent.com/" + it.groupValues[1] + "/" + it.groupValues[2] + "/raw"
        }
        Regex("""^https?://(?:www\.)?pastebin\.com/(?!raw/)([A-Za-z0-9]+)/?$""", RegexOption.IGNORE_CASE).find(u)?.let {
            u = "https://pastebin.com/raw/" + it.groupValues[1]
        }
        return u
    }

    private fun check(name: String, rawUrl: String, exceptId: String?): Pair<String?, String?> {
        val n = name.trim()
        if (n.isEmpty()) return null to "Bir ad yaz"
        val u = fixUrl(rawUrl) ?: return null to "Link http:// ya da https:// ile başlamalı"
        val clash = all().any { it.id != exceptId && it.name.equals(n, true) }
        if (clash) return null to "Bu adda bir liste zaten var"
        val dup = all().any { it.id != exceptId && key(it.url) == key(u) }
        if (dup) return null to "Bu link zaten ekli"
        return (n + "\n" + u) to null
    }

    /** Hata metni, başarılıysa null. */
    fun add(name: String, url: String): String? {
        val (ok, err) = check(name, url, null)
        if (ok == null) return err
        val (n, u) = ok.split("\n", limit = 2)
        writeList(K_USER, userLists() + IptvList(newId(u), n, u, false))
        return null
    }

    fun update(id: String, name: String, url: String): String? {
        val cur = userLists()
        if (cur.none { it.id == id }) return "Liste bulunamadı"
        val (ok, err) = check(name, url, id)
        if (ok == null) return err
        val (n, u) = ok.split("\n", limit = 2)
        writeList(K_USER, cur.map { if (it.id == id) it.copy(name = n, url = u) else it })
        return null
    }

    fun remove(id: String) {
        writeList(K_USER, userLists().filterNot { it.id == id })
        setStatus(id, null)
        setEnabled(id, true)
    }

    // ---------------------------------------------------------------- yönetici listesi
    fun adminCacheEmpty() = adminLists().isEmpty()
    fun adminRefreshDue(): Boolean {
        if (!adminConfigured()) return false
        val t = prefs?.getLong(K_STAMP, 0L) ?: 0L
        return System.currentTimeMillis() - t > REFRESH_MS
    }

    /** Dönüş: bulunan liste sayısı. Hata olursa eski önbellek korunur. */
    suspend fun refreshAdmin(): Result<Int> {
        if (!adminConfigured()) return Result.failure(Exception("Yönetici adresi ayarlanmamış (IptvStore.ADMIN_SOURCE)"))
        return try {
            val res = app.get(fixUrl(ADMIN_SOURCE) ?: ADMIN_SOURCE, headers = mapOf("User-Agent" to Net.UA), timeout = 30L)
            if (res.code !in 200..299) return Result.failure(Exception("HTTP " + res.code))
            val text = res.text
            val items = if (M3u.looksLikeM3u(text)) {
                listOf(IptvList("a" + Integer.toHexString(ADMIN_SOURCE.lowercase().hashCode()), "Yönetici listesi", ADMIN_SOURCE, true))
            } else parseAdmin(text)
            if (items.isEmpty()) return Result.failure(Exception("Listede bağlantı bulunamadı"))
            writeList(K_ADMIN, items)
            prefs?.edit()?.putLong(K_STAMP, System.currentTimeMillis())?.apply()
            Result.success(items.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAdmin(text: String): List<IptvList> {
        val out = ArrayList<IptvList>()
        fun add(name: String?, url: String?) {
            val u = url?.let { fixUrl(it) } ?: return
            out.add(IptvList("a" + Integer.toHexString(u.lowercase().hashCode()), (name ?: "").ifBlank { hostOf(u) }, u, true))
        }
        fun fromArray(a: JSONArray) {
            for (i in 0 until a.length()) {
                val v = a.opt(i)
                if (v is JSONObject) add(v.optString("name").ifBlank { v.optString("title") }, v.optString("url").ifBlank { v.optString("link") })
                else if (v is String) add(null, v)
            }
        }
        try {
            val t = text.trim()
            if (t.startsWith("[")) fromArray(JSONArray(t)) else {
                val o = JSONObject(t)
                val arr = o.optJSONArray("lists") ?: o.optJSONArray("playlists") ?: o.optJSONArray("items")
                if (arr != null) fromArray(arr) else for (k in o.keys()) {
                    val v = o.opt(k)
                    if (v is JSONObject) add(v.optString("name").ifBlank { k }, v.optString("url").ifBlank { v.optString("link") })
                    else if (v is String) add(k, v)
                }
            }
        } catch (e: Exception) { /* bozuk JSON: boş döner */ }
        return out.distinctBy { key(it.url) }
    }

    // ---------------------------------------------------------------- gruplar (kategoriler)
    /** Listedeki tüm gruplar ve kanal sayıları, kalabalıktan aza. */
    fun groupCounts(url: String): List<Pair<String, Int>> {
        return try {
            val o = JSONObject(prefs?.getString(K_GROUPS, "{}") ?: "{}")
            val a = o.optJSONArray(key(url)) ?: return emptyList()
            val out = ArrayList<Pair<String, Int>>()
            for (i in 0 until a.length()) {
                val v = a.opt(i)
                if (v is JSONObject) out.add(v.optString("n") to v.optInt("c"))
                else if (v is String) out.add(v to 0)   // eski kayıt biçimi
            }
            out.filter { it.first.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }

    fun groups(url: String): List<String> = groupCounts(url).map { it.first }

    fun saveGroups(url: String, groups: List<Pair<String, Int>>) {
        try {
            val o = JSONObject(prefs?.getString(K_GROUPS, "{}") ?: "{}")
            val a = JSONArray()
            groups.forEach { a.put(JSONObject().put("n", it.first).put("c", it.second)) }
            o.put(key(url), a)
            prefs?.edit()?.putString(K_GROUPS, o.toString())?.apply()
        } catch (e: Exception) { /* önemsiz */ }
    }

    // ---------------------------------------------------------------- gizlenen kategoriler
    fun hidden(url: String): Set<String> {
        return try {
            val a = JSONObject(prefs?.getString(K_HIDDEN, "{}") ?: "{}").optJSONArray(key(url)) ?: return emptySet()
            (0 until a.length()).map { a.optString(it) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    private fun saveHidden(url: String, names: Set<String>) {
        try {
            val o = JSONObject(prefs?.getString(K_HIDDEN, "{}") ?: "{}")
            o.put(key(url), JSONArray(names.toList()))
            prefs?.edit()?.putString(K_HIDDEN, o.toString())?.apply()
        } catch (e: Exception) { /* önemsiz */ }
    }

    fun setGroupHidden(url: String, name: String, hide: Boolean) {
        val s = hidden(url).toMutableSet()
        if (hide) s.add(name) else s.remove(name)
        saveHidden(url, s)
    }

    fun setAllGroupsHidden(url: String, hide: Boolean) =
        saveHidden(url, if (hide) groups(url).toSet() else emptySet())

    // ---------------------------------------------------------------- test sonucu
    fun status(id: String): Status? {
        return try {
            val o = JSONObject(prefs?.getString(K_STATUS, "{}") ?: "{}").optJSONObject(id) ?: return null
            Status(o.optBoolean("ok"), o.optString("text"), o.optLong("ts"))
        } catch (e: Exception) { null }
    }

    fun setStatus(id: String, st: Status?) {
        try {
            val o = JSONObject(prefs?.getString(K_STATUS, "{}") ?: "{}")
            if (st == null) o.remove(id) else o.put(id, JSONObject().put("ok", st.ok).put("text", st.text).put("ts", st.time))
            prefs?.edit()?.putString(K_STATUS, o.toString())?.apply()
        } catch (e: Exception) { /* önemsiz */ }
    }
}

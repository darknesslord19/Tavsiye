package com.darkneslord.iptv

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Kullanıcının listeleri (SharedPreferences içinde JSON olarak saklanır). */
object Store {
    private const val PREF = "darknes_lord_iptv"
    private const val KEY = "playlists_v1"

    private var prefs: SharedPreferences? = null
    private val items = CopyOnWriteArrayList<PlaylistEntry>()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        items.clear()
        try {
            val arr = JSONArray(prefs?.getString(KEY, "[]") ?: "[]")
            for (i in 0 until arr.length()) items.add(PlaylistEntry.fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            // bozuk kayıt: boş başla
        }
    }

    fun all(): List<PlaylistEntry> = items.toList()
    fun users(): List<PlaylistEntry> = items.filter { !it.admin }
    fun admins(): List<PlaylistEntry> = items.filter { it.admin }
    fun find(id: String): PlaylistEntry? = items.firstOrNull { it.id == id }

    @Synchronized
    fun add(entry: PlaylistEntry) {
        items.add(entry)
        save()
    }

    @Synchronized
    fun remove(entry: PlaylistEntry) {
        if (entry.admin) return // yönetici listeleri silinemez
        items.remove(entry)
        Repo.invalidate(entry.id)
        save()
    }

    /**
     * Yönetici listelerini uzaktan (Firebase JSON) eşitler. Başarısızsa eldekiler korunur.
     * Biçim: [{"name":"..","url":".."}, ...]  veya  {"name":"url", ...}. Bloklayıcıdır.
     */
    fun syncAdmin(): Boolean {
        val remote = AdminSource.fetch() ?: return false
        synchronized(this) {
            val keep = HashSet<String>()
            for ((name, url) in remote) {
                val id = "admin:" + Integer.toHexString(url.lowercase().hashCode())
                keep.add(id)
                val old = items.firstOrNull { it.id == id }
                if (old != null) old.name = name
                else items.add(PlaylistEntry(id, name, url, enabled = true, admin = true))
            }
            items.removeAll { it.admin && it.id !in keep }
            save()
        }
        return true
    }

    @Synchronized
    fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString(KEY, arr.toString())?.apply()
    }
}

/** Ayrıştırılmış liste önbelleği + durum güncelleme. Bloklayıcıdır; IO dispatcher'da çağırın. */
object Repo {
    class Parsed(val groups: LinkedHashMap<String, List<Channel>>, val loadedAt: Long)

    private val cache = ConcurrentHashMap<String, Parsed>()
    private const val TTL_MS = 10 * 60 * 1000L
    private const val STALE_OK_MS = 6 * 60 * 60 * 1000L

    fun invalidate(id: String) {
        cache.remove(id)
    }

    /** Liste çalışıyorsa kanalları döndürür; çalışmıyorsa null (kart kırmızı olur, ana sayfada gösterilmez). */
    fun get(entry: PlaylistEntry, force: Boolean = false): Parsed? {
        val now = System.currentTimeMillis()
        val cached = cache[entry.id]
        if (!force && cached != null && now - cached.loadedAt < TTL_MS) return cached
        return refresh(entry)
    }

    fun refresh(entry: PlaylistEntry): Parsed? {
        // Önce daha önce çözülen son adres, olmazsa kısaltılmış orijinal link yeniden çözülür.
        var r = PlaylistLoader.load(entry.resolvedUrl ?: entry.url)
        if (!r.ok && entry.resolvedUrl != null && entry.resolvedUrl != entry.url) r = PlaylistLoader.load(entry.url)
        val parsed = apply(entry, r)
        if (parsed != null) return parsed
        // Geçici bağlantı sorununda ekranın boşalmaması için en fazla 6 saatlik eski veriyi kullan
        val old = cache[entry.id]
        return if (old != null && System.currentTimeMillis() - old.loadedAt < STALE_OK_MS) old else null
    }

    /** Yükleme sonucunu karta işler (durum, sayılar, kategoriler) ve önbelleğe yazar. */
    fun apply(entry: PlaylistEntry, r: PlaylistLoader.Result): Parsed? {
        entry.lastChecked = System.currentTimeMillis()
        if (!r.ok) {
            entry.status = STATUS_ERROR
            entry.lastError = r.error
            Store.save()
            return null
        }
        val tagged = r.channels.map { it.copy(playlistId = entry.id) }
        val groups = LinkedHashMap<String, MutableList<Channel>>()
        tagged.forEach { groups.getOrPut(it.group) { ArrayList() }.add(it) }
        entry.status = STATUS_OK
        entry.lastError = null
        entry.resolvedUrl = r.finalUrl
        entry.channelCount = tagged.size
        entry.categories = groups.map { CategoryInfo(it.key, it.value.size) }
        Store.save()
        val parsed = Parsed(LinkedHashMap<String, List<Channel>>(groups), System.currentTimeMillis())
        cache[entry.id] = parsed
        return parsed
    }
}

/** Yönetici listesi kaynağı (Firebase). Adres değişirse yalnızca burası güncellenir. */
object AdminSource {
    const val URL = "https://darknes-lord-paste-default-rtdb.firebaseio.com/pastes/Cloudstreamtv.json"

    /** (ad, link) çiftleri; erişilemezse null, geçerli ama boşsa boş liste. */
    fun fetch(): List<Pair<String, String>>? {
        val f = try {
            Net.fetchText(URL, timeoutMs = 15000)
        } catch (e: Exception) {
            return null
        }
        val text = f.text ?: return null
        if (f.code !in 200..299) return null
        val out = ArrayList<Pair<String, String>>()
        try {
            var node: Any = org.json.JSONTokener(text.trim()).nextValue()
            // Firebase'e metin olarak yapıştırılmış JSON ("[...]" tırnaklı) olabilir
            if (node is String) {
                val t = node.trim()
                node = if (t.startsWith("[") || t.startsWith("{")) org.json.JSONTokener(t).nextValue() else return out
            }
            fun add(name: String?, url: String?, idx: Int) {
                val u = url?.trim().orEmpty()
                if (!u.startsWith("http", true)) return
                out.add((name?.trim().takeUnless { it.isNullOrEmpty() } ?: "Liste ${idx + 1}") to u)
            }
            when (node) {
                is org.json.JSONArray -> for (i in 0 until node.length()) {
                    val o = node.optJSONObject(i)
                    if (o != null) add(o.optString("name"), o.optString("url"), i)
                    else add(null, node.optString(i), i)
                }
                is org.json.JSONObject -> {
                    var i = 0
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = node.opt(k)
                        if (v is org.json.JSONObject) add(v.optString("name", k), v.optString("url"), i)
                        else add(k, v?.toString(), i)
                        i++
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        return out
    }
}

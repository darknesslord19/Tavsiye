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

    @Synchronized
    fun add(entry: PlaylistEntry) {
        items.add(entry)
        save()
    }

    @Synchronized
    fun remove(entry: PlaylistEntry) {
        items.remove(entry)
        Repo.invalidate(entry.id)
        save()
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

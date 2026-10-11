package com.darkneslord.iptv

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Coroutine kütüphanesine bağımlı olmadan suspend bloğu çağıran thread'i bekleterek çalıştırır. Ana thread'den çağırma. */
fun <T> blocking(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { outcome = result; latch.countDown() }
    })
    latch.await()
    return outcome!!.getOrThrow()
}

object Net {
    /** IPTV sunucularının çoğu oynatıcı gibi görünen istemcileri kabul eder. */
    const val UA = "VLC/3.0.20 LibVLC/3.0.20"

    private const val TTL_MS = 15 * 60_000L
    private const val MAX_LISTS = 6
    private class Entry(val time: Long, val list: List<Channel>)
    private val cache = ConcurrentHashMap<String, Entry>()

    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    // Bazı sunucular yalnızca oynatıcı UA'sını, bazıları yalnızca tarayıcı UA'sını kabul eder: sırayla denenir.
    private val AGENTS = listOf(UA, BROWSER_UA, "")

    private val META_REFRESH = Regex("""(?i)<meta[^>]+http-equiv=["']?refresh["']?[^>]*content=["'][^"']*url=([^"'>\s]+)""")
    private val M3U_LINK = Regex("""https?://[^\s"'<>\\]+?\.(?:m3u8?)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)

    private fun describe(t: String): String {
        val head = t.trimStart().take(400)
        if (head.contains("<html", true) || head.contains("<!doctype", true)) {
            val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(t)?.groupValues?.get(1)?.trim()?.take(50)
            return "liste yerine web sayfası geldi" + (if (title.isNullOrBlank()) "" else ": «$title»")
        }
        return "M3U listesi değil: " + head.replace(Regex("\\s+"), " ").take(50)
    }

    /**
     * Listeyi indirir ve ayrıştırır (15 dk bellek önbelleği). Gruplar ana sayfa satırları için kaydedilir.
     * Adres tek bir .m3u8 akışıysa (kanal listesi değil) tek kanallı liste olarak döner.
     */
    suspend fun channels(url: String, force: Boolean = false, title: String = "", depth: Int = 0): List<Channel> {
        val hit = cache[url]
        if (!force && hit != null && System.currentTimeMillis() - hit.time < TTL_MS) return hit.list

        var text: String? = null
        var page: String? = null     // liste yerine gelen HTML (kısaltıcı ara sayfası olabilir)
        var last = "bilinmeyen hata"
        for (ua in AGENTS) {
            try {
                val h = HashMap<String, String>()
                h["Accept"] = "*/*"
                if (ua.isNotEmpty()) h["User-Agent"] = ua
                val res = app.get(url, headers = h, timeout = 60L)
                if (res.code !in 200..299) { last = "HTTP " + res.code; continue }
                val t = res.text
                if (M3u.looksLikeM3u(t)) { text = t; break }
                last = describe(t)
                if (page == null && (t.contains("<html", true) || t.contains("<!doctype", true))) page = t
            } catch (e: Exception) {
                last = (e.message ?: e.javaClass.simpleName).take(70)
            }
        }
        if (text == null && page != null && depth < 2) {
            // Ara sayfa: meta-refresh yönlendirmesi ya da sayfadaki ilk .m3u/.m3u8 bağlantısı
            val next = META_REFRESH.find(page)?.groupValues?.get(1) ?: M3U_LINK.find(page)?.value
            val abs = next?.let { try { java.net.URL(java.net.URL(url), it.trim()).toString() } catch (e: Exception) { null } }
            if (abs != null && abs != url) {
                val l = channels(abs, force, title, depth + 1)
                if (cache.size >= MAX_LISTS) cache.keys.firstOrNull()?.let { cache.remove(it) }
                cache[url] = Entry(System.currentTimeMillis(), l)
                IptvStore.saveGroups(url, M3u.groupCounts(l))
                return l
            }
        }
        if (text == null) throw ErrorLoadingException(IptvStore.hostOf(url) + " açılamadı (" + last + ")")

        val list = if (M3u.isHls(text)) {
            listOf(Channel(title.ifBlank { IptvStore.hostOf(url) }, url, null, "Canlı", emptyMap(), hls = true))
        } else M3u.parse(text)
        if (list.isEmpty()) throw ErrorLoadingException("Listede kanal bulunamadı (#EXTINF satırı yok)")

        if (cache.size >= MAX_LISTS) cache.keys.firstOrNull()?.let { cache.remove(it) }
        cache[url] = Entry(System.currentTimeMillis(), list)
        IptvStore.saveGroups(url, M3u.groupCounts(list))
        return list
    }
}

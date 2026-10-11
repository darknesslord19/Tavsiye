package com.example.iptvbridge

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

    /** Listeyi indirir ve ayrıştırır (15 dk bellek önbelleği). Gruplar ana sayfa satırları için kaydedilir. */
    suspend fun channels(url: String, force: Boolean = false): List<Channel> {
        val hit = cache[url]
        if (!force && hit != null && System.currentTimeMillis() - hit.time < TTL_MS) return hit.list

        val res = try {
            app.get(url, headers = mapOf("User-Agent" to UA, "Accept" to "*/*"), timeout = 60L)
        } catch (e: Exception) {
            throw ErrorLoadingException(IptvStore.hostOf(url) + " açılamadı (" + (e.message ?: e.javaClass.simpleName).take(70) + ")")
        }
        if (res.code !in 200..299) throw ErrorLoadingException(IptvStore.hostOf(url) + " açılamadı (HTTP " + res.code + ")")
        val text = res.text
        if (!M3u.looksLikeM3u(text)) throw ErrorLoadingException("Bu adres bir M3U / M3U8 listesi değil")
        val list = M3u.parse(text)
        if (list.isEmpty()) throw ErrorLoadingException("Listede kanal bulunamadı")

        if (cache.size >= MAX_LISTS) cache.keys.firstOrNull()?.let { cache.remove(it) }
        cache[url] = Entry(System.currentTimeMillis(), list)
        IptvStore.saveGroups(url, M3u.topGroups(list))
        return list
    }
}

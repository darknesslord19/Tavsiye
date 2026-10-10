package com.example.sitebridge

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * kotlinx.coroutines'e bağımlı olmadan, suspend bir bloğu çağıran thread'i bekleterek çalıştırır.
 * Ana thread'den çağırma.
 */
fun <T> blocking(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
            latch.countDown()
        }
    })
    latch.await()
    return outcome!!.getOrThrow()
}

/** Öğeleri paralel işler (en fazla [max] iş aynı anda). Hata veren öğe için null döner. */
fun <T, R> parallel(items: List<T>, max: Int = 6, f: suspend (T) -> R?): List<R?> {
    if (items.isEmpty()) return emptyList()
    val pool = Executors.newFixedThreadPool(minOf(max, items.size))
    try {
        val futures = items.map { item ->
            pool.submit(Callable<R?> { runCatching { blocking { f(item) } }.getOrNull() })
        }
        return futures.map { it.get() }
    } finally {
        pool.shutdownNow()
    }
}

/** Sayfa indirme: 10 dk önbellek, 403/503'te Cloudflare aşıcıyla yeniden dener. */
object Net {
    const val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val CACHE_MS = 600_000L
    private const val CACHE_MAX = 80

    private class Entry(val time: Long, val html: String)

    private val cache = LinkedHashMap<String, Entry>()
    private val killer by lazy { CloudflareKiller() }

    private fun headers(): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    )

    suspend fun html(url: String, referer: String? = null): String {
        synchronized(cache) {
            val e = cache[url]
            if (e != null && System.currentTimeMillis() - e.time < CACHE_MS) return e.html
        }
        var res = app.get(url, headers = headers(), referer = referer ?: Scraper.origin(url))
        if (res.code == 403 || res.code == 503) {
            res = app.get(url, headers = headers(), referer = referer ?: Scraper.origin(url), interceptor = killer)
        }
        if (res.code >= 400) throw ErrorLoadingException("HTTP " + res.code + " — " + url)
        val text = res.text
        if (text.length >= 200) {
            synchronized(cache) {
                cache[url] = Entry(System.currentTimeMillis(), text)
                while (cache.size > CACHE_MAX) cache.remove(cache.keys.first())
            }
        }
        return text
    }

    suspend fun htmlOrNull(url: String, referer: String? = null): String? =
        try {
            html(url, referer)
        } catch (e: Exception) {
            null
        }
}

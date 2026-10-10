package com.example.sitebridge

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

/**
 * [ms] içinde bitmeyen işi bırakır ve null döner (takılan WebView / çıkarıcılar tüm akışı kilitlemesin).
 * Süre dolunca iş arka planda kendi zaman aşımına kadar sürebilir.
 */
fun <T> timed(ms: Long, onError: ((String) -> Unit)? = null, f: suspend () -> T?): T? {
    val pool = Executors.newSingleThreadExecutor()
    return try {
        val fut = pool.submit(Callable<T?> {
            try {
                blocking { f() }
            } catch (e: Throwable) {
                onError?.invoke(e.javaClass.simpleName + ": " + (e.message ?: "-").take(80))
                null
            }
        })
        try {
            fut.get(ms, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            fut.cancel(true)
            onError?.invoke("zaman aşımı (" + ms / 1000 + " sn)")
            null
        }
    } finally {
        pool.shutdown()
    }
}

/** İndirilen sayfa: [url] yönlendirmeden sonraki son adrestir; göreli bağlantılar buna göre çözülür. */
class Page(val html: String, val url: String)

/** Sayfa indirme: 10 dk önbellek, 2 deneme, 403/503'te Cloudflare aşıcısı, yönlendirilen son adres. */
object Net {
    const val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val CACHE_MS = 600_000L
    private const val CACHE_MAX = 120

    private class Entry(val time: Long, val page: Page)

    private val cache = LinkedHashMap<String, Entry>()
    private val killer by lazy { CloudflareKiller() }

    /** Cloudflare'in geçmesi gereken siteler: bu sitelerde baştan aşıcı kullanılır. */
    private val cfHosts = HashSet<String>()

    /**
     * cf_clearance çerezi, onu çözen WebView'ın User-Agent'ına bağlıdır; aşıcıyla yapılan isteklerde
     * User-Agent'ı kendimiz vermeyiz (aşıcı kendisininkini koyar).
     */
    private fun headers(withUa: Boolean = true): Map<String, String> = (if (withUa) mapOf("User-Agent" to UA) else emptyMap()) + mapOf(
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    )

    suspend fun page(url: String, referer: String? = null): Page {
        synchronized(cache) {
            val e = cache[url]
            if (e != null && System.currentTimeMillis() - e.time < CACHE_MS) return e.page
        }
        val ref = referer ?: Scraper.origin(url)
        var lastError = "bilinmeyen hata"
        for (attempt in 0..1) {
            try {
                val host = Scraper.hostOf(url)
                val useCf = synchronized(cfHosts) { host in cfHosts }
                var res = if (useCf) {
                    app.get(url, headers = headers(false), referer = ref, interceptor = killer)
                } else {
                    app.get(url, headers = headers(), referer = ref)
                }
                if (!useCf && (res.code == 403 || res.code == 503)) {
                    res = app.get(url, headers = headers(false), referer = ref, interceptor = killer)
                    // Aşıcı ilk istekte çerezi alıp ikinci istekte geçebilir: başarısız olsa da bir sonraki denemede aşıcıyla devam et
                    synchronized(cfHosts) { cfHosts.add(host) }
                }
                if (res.code in 200..399) {
                    val text = res.text
                    if (text.length < 50) {
                        lastError = "boş yanıt"
                    } else {
                        val finalUrl = res.url.takeIf { it.startsWith("http") } ?: url
                        val p = Page(text, finalUrl)
                        synchronized(cache) {
                            cache[url] = Entry(System.currentTimeMillis(), p)
                            while (cache.size > CACHE_MAX) cache.remove(cache.keys.first())
                        }
                        return p
                    }
                } else {
                    lastError = "HTTP " + res.code
                    // 404/410 tekrar denemeye değmez
                    if (res.code == 404 || res.code == 410) break
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
            if (attempt == 0) Thread.sleep(700)
        }
        throw ErrorLoadingException(Scraper.hostOf(url) + " açılamadı (" + lastError + ")")
    }

    suspend fun html(url: String, referer: String? = null): String = page(url, referer).html

    suspend fun pageOrNull(url: String, referer: String? = null): Page? =
        try {
            page(url, referer)
        } catch (e: Exception) {
            null
        }

    suspend fun htmlOrNull(url: String, referer: String? = null): String? = pageOrNull(url, referer)?.html
}

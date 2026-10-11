package com.darkneslord.iptv

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Sadece java.net kullanan küçük ağ katmanı.
 * Yönlendirmeleri (kısaltılmış linkler dahil) elle takip eder; http<->https geçişleri de çalışır.
 */
object Net {
    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val MAX_HOPS = 10

    class Resolved(val url: String, val code: Int, val contentType: String?)
    class Fetched(val url: String, val code: Int, val contentType: String?, val text: String?)
    class Probe(val url: String, val dead: Boolean, val isHls: Boolean)

    private fun open(url: String, headers: Map<String, String>, timeoutMs: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", DEFAULT_UA)
        c.setRequestProperty("Accept", "*/*")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c
    }

    /** Yönlendirmeleri takip eder, son cevabı almış bağlantıyı ve son adresi döndürür. Çağıran disconnect() eder. */
    private fun connectFollow(start: String, headers: Map<String, String>, timeoutMs: Int): Pair<HttpURLConnection, String> {
        var cur = start
        for (hop in 0 until MAX_HOPS) {
            val c = open(cur, headers, timeoutMs)
            val code = try {
                c.responseCode
            } catch (e: IOException) {
                c.disconnect()
                throw e
            }
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val loc = c.getHeaderField("Location")?.trim()
                c.disconnect()
                if (loc.isNullOrEmpty()) throw IOException("Yönlendirme adresi boş")
                cur = URL(URL(cur), loc).toString()
                continue
            }
            return c to cur
        }
        throw IOException("Çok fazla yönlendirme")
    }

    /** Sadece başlıkları okur (gövdeyi indirmez). Canlı yayınlar için güvenlidir. */
    fun resolve(start: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 12000): Resolved {
        val (c, finalUrl) = connectFollow(start, headers, timeoutMs)
        try {
            return Resolved(finalUrl, c.responseCode, c.contentType)
        } finally {
            c.disconnect()
        }
    }

    /** Metin içeriğini indirir. Video/ses gibi yayın içerikleri indirilmez (text = null). */
    fun fetchText(start: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 30000): Fetched {
        val (c, finalUrl) = connectFollow(start, headers, timeoutMs)
        try {
            val code = c.responseCode
            val ct = c.contentType
            if (code !in 200..299) return Fetched(finalUrl, code, ct, null)
            val lower = ct?.lowercase().orEmpty()
            // audio/x-mpegurl, audio/mpegurl vb. aslında M3U listesidir; gerçek yayınlar indirilmez.
            val isPlaylistType = lower.contains("mpegurl")
            if (!isPlaylistType && (lower.startsWith("video/") || lower.startsWith("audio/") || lower.contains("mp2t"))) {
                return Fetched(finalUrl, code, ct, null)
            }
            val out = ByteArrayOutputStream()
            c.inputStream.use { ins ->
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw IOException("Liste çok büyük (32 MB üstü)")
                    out.write(buf, 0, n)
                }
            }
            return Fetched(finalUrl, code, ct, out.toString("UTF-8"))
        } finally {
            c.disconnect()
        }
    }

    /**
     * Oynatmadan önce yayını yoklar: kısaltılmış/yönlendirmeli yayın linkini çözer.
     * Yalnızca kesin ölü olanlar (404/410, sunucu yok, bağlantı reddedildi) elenir;
     * 401/403 gibi cevaplar oynatıcının kendi başlıklarıyla çalışabileceği için geçirilir.
     */
    fun probe(url: String, headers: Map<String, String>): Probe = try {
        val r = resolve(url, headers, 8000)
        val ct = r.contentType?.lowercase().orEmpty()
        val hls = ct.contains("mpegurl") || r.url.substringBefore('?').endsWith(".m3u8", true)
        Probe(r.url, r.code == 404 || r.code == 410, hls)
    } catch (e: UnknownHostException) {
        Probe(url, true, false)
    } catch (e: ConnectException) {
        Probe(url, true, false)
    } catch (e: SocketTimeoutException) {
        Probe(url, false, false)
    } catch (e: Exception) {
        Probe(url, false, false) // rtmp/rtsp vb. şemalar burada oynatıcıya bırakılır
    }
}

/** Kullanıcının yapıştırdığı linki oynatılabilir doğrudan adrese çevirir. */
object LinkTools {
    private val GITHUB_BLOB = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/]+)/blob/(.+)$""", RegexOption.IGNORE_CASE)
    private val GIST = Regex("""^https?://gist\.github\.com/([^/]+)/([0-9a-f]+)/?$""", RegexOption.IGNORE_CASE)
    private val PASTEBIN = Regex("""^https?://(?:www\.)?pastebin\.com/(?!raw/)([A-Za-z0-9]+)/?$""", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String? {
        var u = raw.trim().replace(" ", "%20")
        if (u.isEmpty()) return null
        if (!u.contains("://")) u = "https://$u"
        try {
            val parsed = URL(u)
            if (parsed.protocol != "http" && parsed.protocol != "https") return null
            if (parsed.host.isNullOrEmpty()) return null
        } catch (e: Exception) {
            return null
        }
        GITHUB_BLOB.find(u)?.let { return "https://raw.githubusercontent.com/${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}" }
        GIST.find(u)?.let { return "https://gist.githubusercontent.com/${it.groupValues[1]}/${it.groupValues[2]}/raw" }
        PASTEBIN.find(u)?.let { return "https://pastebin.com/raw/${it.groupValues[1]}" }
        return u
    }

    fun hostOf(url: String): String = try {
        URL(url).host.removePrefix("www.")
    } catch (e: Exception) {
        "Liste"
    }
}

/** Link -> (yönlendirmeleri çöz) -> indir -> M3U ayrıştır. Bloklayıcıdır; IO dispatcher'da çağırın. */
object PlaylistLoader {
    class Result(
        val ok: Boolean,
        val finalUrl: String,
        val channels: List<Channel>,
        val error: String? = null,
    )

    private val META_REFRESH = Regex("""(?i)<meta[^>]+http-equiv=["']?refresh["']?[^>]*content=["'][^"']*url=([^"'>\s]+)""")
    private val M3U_LINK = Regex("""https?://[^\s"'<>\\]+?\.(?:m3u8?|txt)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)

    private fun fail(url: String, msg: String) = Result(false, url, emptyList(), msg)

    /** Bazı sunucular yalnızca tarayıcı, bazıları yalnızca oynatıcı kimliğini kabul eder: sırayla denenir. */
    private val AGENTS = listOf(Net.DEFAULT_UA, "VLC/3.0.20 LibVLC/3.0.20")

    fun load(input: String, depth: Int = 0): Result {
        var last: Result? = null
        for (ua in AGENTS) {
            val r = loadOnce(input, ua, depth)
            if (r.ok) return r
            last = r
            val e = r.error.orEmpty()
            // Ağ düzeyi hatalarda (alan adı yok, bağlanılamadı, zaman aşımı) kimlik değiştirmek işe yaramaz
            if (e.startsWith("Sunucu bulunamadı") || e.startsWith("Sunucuya bağlanılamadı") || e.startsWith("Sunucu zaman aşımına") || e.startsWith("Geçersiz link")) break
        }
        return last!!
    }

    private fun loadOnce(input: String, ua: String, depth: Int): Result {
        val start = LinkTools.normalize(input) ?: return fail(input, "Geçersiz link. http:// veya https:// ile başlayan bir adres girin.")

        val f = try {
            Net.fetchText(start, mapOf("User-Agent" to ua))
        } catch (e: UnknownHostException) {
            return fail(start, "Sunucu bulunamadı (alan adı çözülemedi).")
        } catch (e: SocketTimeoutException) {
            return fail(start, "Sunucu zaman aşımına uğradı.")
        } catch (e: ConnectException) {
            return fail(start, "Sunucuya bağlanılamadı.")
        } catch (e: IOException) {
            return fail(start, "Bağlantı hatası: ${e.message ?: "bilinmiyor"}")
        } catch (e: Exception) {
            return fail(start, "Beklenmeyen hata: ${e.message ?: e.javaClass.simpleName}")
        }

        if (f.code !in 200..299) return fail(f.url, "Sunucu HTTP ${f.code} döndürdü.")

        // Link bir liste değil de doğrudan canlı yayınsa tek kanal olarak ekle
        if (f.text == null) {
            val name = LinkTools.hostOf(f.url)
            return Result(true, f.url, listOf(Channel(name, f.url, null, DEFAULT_GROUP, null, null)))
        }

        val text = f.text.removePrefix("﻿")
        val isMaster = text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-TARGETDURATION")
        if (isMaster) {
            val name = f.url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { LinkTools.hostOf(f.url) }
            return Result(true, f.url, listOf(Channel(name, f.url, null, DEFAULT_GROUP, null, null)))
        }

        if (text.contains("#EXTM3U") || text.contains("#EXTINF")) {
            val channels = M3uParser.parse(text)
            return if (channels.isEmpty()) fail(f.url, "Listede oynatılabilir kanal bulunamadı.")
            else Result(true, f.url, channels)
        }

        // HTML ara sayfa (bazı kısaltıcılar): meta-refresh veya sayfadaki ilk .m3u/.m3u8 linki
        if (depth < 2) {
            val next = META_REFRESH.find(text)?.groupValues?.get(1)?.let { resolveAgainst(f.url, it) }
                ?: M3U_LINK.find(text)?.value
            if (next != null && next != f.url) return load(next, depth + 1)
        }
        return fail(f.url, "Bu link geçerli bir M3U/IPTV listesi değil.")
    }

    private fun resolveAgainst(base: String, link: String): String? = try {
        URL(URL(base), link.trim()).toString()
    } catch (e: Exception) {
        null
    }
}

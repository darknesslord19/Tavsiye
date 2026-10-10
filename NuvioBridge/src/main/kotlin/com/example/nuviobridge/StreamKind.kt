package com.example.nuviobridge

import java.net.HttpURLConnection
import java.net.URL

/**
 * Bir akış adresinin gerçekte ne olduğunu belirler: HLS, DASH, doğrudan video dosyası
 * ya da video olmayan bir sayfa. Oynatıcıya yanlış tür verilince "3003" hatası çıkar.
 */
object StreamKind {
    const val M3U8 = "m3u8"
    const val DASH = "dash"
    const val VIDEO = "video"
    const val SKIP = "skip" // video değil (HTML sayfası, bulunamayan adres vb.)

    fun detect(st: JsStream): String {
        // 1) Provider türü kendisi söylediyse ona güven.
        val hint = st.kind.lowercase()
        if (hint.contains("hls") || hint.contains("m3u8")) return M3U8
        if (hint.contains("dash") || hint.contains("mpd")) return DASH

        // 2) Adresin kendisinden anlaşılıyorsa ağ isteği yapma.
        val path = st.url.lowercase().substringBefore("?")
        if (path.contains("m3u8")) return M3U8
        if (path.endsWith(".mpd")) return DASH
        if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".mov")) {
            return VIDEO
        }

        // 3) Anlaşılmadı: adresin ilk birkaç yüz baytına bakarak karar ver.
        return sniff(st.url, st.headers)
    }

    private fun sniff(url: String, headers: Map<String, String>): String {
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.instanceFollowRedirects = true
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (c.getRequestProperty("User-Agent") == null) {
                c.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                )
            }
            c.setRequestProperty("Range", "bytes=0-2047")

            val code = c.responseCode
            if (code == 404 || code == 410) return SKIP

            val contentType = (c.contentType ?: "").lowercase()
            val buf = ByteArray(2048)
            val n = c.inputStream.use { it.read(buf) }
            val head = if (n > 0) String(buf, 0, n, Charsets.ISO_8859_1) else ""
            val trimmed = head.trimStart()
            val lower = trimmed.lowercase()

            when {
                trimmed.startsWith("#EXTM3U") -> M3U8
                trimmed.contains("<MPD") -> DASH
                contentType.contains("mpegurl") -> M3U8
                contentType.contains("dash+xml") -> DASH
                contentType.contains("text/html") || lower.startsWith("<!doctype html") || lower.startsWith("<html") -> SKIP
                else -> VIDEO
            }
        } catch (e: Exception) {
            VIDEO // ölçülemedi; kararı oynatıcıya bırak
        } finally {
            conn?.disconnect() // kalan indirmeyi keser
        }
    }
}

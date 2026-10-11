package com.darkneslord.iptv

import org.json.JSONObject

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val headers: Map<String, String>,
    /** Liste değil, tek bir HLS akışı (.m3u8 medya/ana oynatma listesi). */
    val hls: Boolean = false,
) {
    /** .mp4/.mkv gibi dosyalar canlı yayın değil, film olarak gösterilir. */
    val isVod: Boolean
        get() = Regex("""\.(mp4|mkv|avi|mov|wmv|flv)$""").containsMatchIn(url.substringBefore('?').lowercase())
}

/** M3U / M3U8 (IPTV) oynatma listesi ayrıştırıcı. */
object M3u {
    private val ATTR = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    const val DEFAULT_GROUP = "Diğer"

    /** .m3u8 akış dosyası mı (kanal listesi değil)? Parçalar (.ts) kanal sanılmasın diye ayrı ele alınır. */
    fun isHls(text: String): Boolean = text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-TARGETDURATION")

    fun looksLikeM3u(text: String): Boolean {
        val t = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return t.startsWith("#EXTM3U") || t.contains("#EXTINF")
    }

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var logo: String? = null
        var group: String? = null
        var hdr = HashMap<String, String>()

        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('﻿')
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val rest = ATTR.replace(line, "")
                    val comma = rest.indexOf(',')
                    val shown = if (comma >= 0) rest.substring(comma + 1).trim() else ""
                    name = shown.ifBlank { attrs["tvg-name"] ?: "" }
                    logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") }
                    group = attrs["group-title"]?.trim()?.ifBlank { null }
                }
                line.startsWith("#EXTGRP:") -> if (group == null) group = line.substringAfter(':').trim().ifBlank { null }
                line.startsWith("#EXTVLCOPT:") -> {
                    val kv = line.substringAfter(':')
                    val v = kv.substringAfter('=', "")
                    when (kv.substringBefore('=').lowercase()) {
                        "http-user-agent" -> hdr["User-Agent"] = v
                        "http-referrer", "http-referer" -> hdr["Referer"] = v
                        "http-origin" -> hdr["Origin"] = v
                    }
                }
                line.startsWith("#EXTHTTP:") -> {
                    try {
                        val o = JSONObject(line.substringAfter(':'))
                        for (k in o.keys()) hdr[k] = o.optString(k)
                    } catch (e: Exception) { /* geçersiz başlık satırı */ }
                }
                line.startsWith("#") -> { /* diğer yorumlar */ }
                line.contains("://") -> {
                    // url|User-Agent=X&Referer=Y biçimi
                    var link = line
                    val h = HashMap(hdr)
                    if (line.contains('|') && line.substringAfter('|').contains('=')) {
                        link = line.substringBefore('|')
                        for (p in line.substringAfter('|').split('&')) {
                            val k = p.substringBefore('=').trim()
                            val v = p.substringAfter('=', "").trim()
                            if (k.isNotEmpty() && v.isNotEmpty()) h[normalizeHeader(k)] = v
                        }
                    }
                    val title = name.ifBlank { link.substringAfter("://").substringBefore('/').substringBefore(':') }
                    out.add(Channel(title, link, logo, group ?: DEFAULT_GROUP, h))
                    name = ""; logo = null; group = null; hdr = HashMap()
                }
            }
        }
        return out
    }

    private fun normalizeHeader(k: String): String = when (k.lowercase().replace("-", "").replace("_", "")) {
        "useragent" -> "User-Agent"
        "referer", "referrer" -> "Referer"
        "origin" -> "Origin"
        "cookie" -> "Cookie"
        else -> k
    }

    /** Tüm gruplar ve kanal sayıları (kalabalıktan aza). */
    fun groupCounts(list: List<Channel>, max: Int = 500): List<Pair<String, Int>> =
        list.groupingBy { it.group }.eachCount().entries.sortedByDescending { it.value }.take(max).map { it.key to it.value }

    /** Ana sayfa satırları için: en kalabalık gruplar (en çok [max] tane). */
    fun topGroups(list: List<Channel>, max: Int = 15): List<String> =
        list.groupingBy { it.group }.eachCount().entries.sortedByDescending { it.value }.take(max).map { it.key }
}

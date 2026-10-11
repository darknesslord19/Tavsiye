package com.darkneslord.iptv

/** Standart M3U / M3U8 IPTV listesi ayrıştırıcı. */
object M3uParser {
    private val ATTR = Regex("""([\w\-.]+)\s*=\s*"([^"]*)"""")
    private val STREAM = Regex("""^(https?|rtmp|rtmps|rtsp|udp|rtp|mms)://""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        val seen = HashSet<String>()

        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var extGroup: String? = null
        var ua: String? = null
        var ref: String? = null

        fun reset() {
            name = null; logo = null; group = null; extGroup = null; ua = null; ref = null
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    // #EXTGRP / #EXTVLCOPT satırları EXTINF'ten önce de sonra da gelebilir: sıfırlama yapma.
                    val comma = firstCommaOutsideQuotes(line)
                    val head = if (comma >= 0) line.substring(0, comma) else line
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    val attrs = ATTR.findAll(head).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.trim()?.ifBlank { null }
                    group = attrs["group-title"]?.trim()?.ifBlank { null }
                    name = title.ifBlank { attrs["tvg-name"]?.trim() }?.ifBlank { null }
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    extGroup = line.substringAfter(':').trim().ifBlank { null }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim().ifBlank { null }
                    when (key) {
                        "http-user-agent" -> ua = value
                        "http-referrer", "http-referer" -> ref = value
                    }
                }

                line.startsWith("#") -> Unit // diğer etiketler (KODIPROP vb.) yok sayılır

                STREAM.containsMatchIn(line) -> {
                    var streamUrl = line
                    var streamUa = ua
                    var streamRef = ref
                    // Kodi tarzı:  http://...m3u8|User-Agent=xxx&Referer=yyy
                    val pipe = line.indexOf('|')
                    if (pipe > 0) {
                        streamUrl = line.substring(0, pipe)
                        line.substring(pipe + 1).split('&').forEach { kv ->
                            val k = kv.substringBefore('=').trim().lowercase()
                            val v = kv.substringAfter('=', "").trim().ifBlank { null }
                            when (k) {
                                "user-agent" -> streamUa = v
                                "referer", "referrer" -> streamRef = v
                            }
                        }
                    }
                    val finalName = name ?: streamUrl.substringAfterLast('/').substringBefore('?').ifBlank { streamUrl }
                    val finalGroup = (group ?: extGroup)?.ifBlank { null } ?: DEFAULT_GROUP
                    if (seen.add("$finalName|$streamUrl")) {
                        out.add(Channel(finalName, streamUrl, logo, finalGroup, streamUa, streamRef))
                    }
                    reset()
                }
            }
        }
        return out
    }

    private fun firstCommaOutsideQuotes(s: String): Int {
        var inQuote = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> inQuote = !inQuote
                ',' -> if (!inQuote) return i
            }
        }
        return -1
    }
}

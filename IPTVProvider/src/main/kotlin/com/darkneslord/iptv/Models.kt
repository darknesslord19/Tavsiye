package com.darkneslord.iptv

import org.json.JSONArray
import org.json.JSONObject

/** Bir M3U kanalı. SearchResponse.url / loadLinks.data olarak JSON halinde taşınır. */
data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val userAgent: String?,
    val referer: String?,
    val playlistId: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("n", name)
        put("u", url)
        put("l", logo ?: "")
        put("g", group)
        put("ua", userAgent ?: "")
        put("r", referer ?: "")
        put("p", playlistId)
    }.toString()

    companion object {
        fun fromJson(s: String): Channel? = try {
            val o = JSONObject(s)
            Channel(
                name = o.getString("n"),
                url = o.getString("u"),
                logo = o.optString("l").ifBlank { null },
                group = o.optString("g", DEFAULT_GROUP),
                userAgent = o.optString("ua").ifBlank { null },
                referer = o.optString("r").ifBlank { null },
                playlistId = o.optString("p"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

const val DEFAULT_GROUP = "Diğer"

data class CategoryInfo(val name: String, val count: Int)

const val STATUS_UNKNOWN = 0
const val STATUS_OK = 1
const val STATUS_ERROR = 2

/** Kullanıcının eklediği bir IPTV listesi (kartın verisi). */
class PlaylistEntry(
    val id: String,
    var name: String,
    var url: String,
    var resolvedUrl: String? = null,
    var enabled: Boolean = true,
    val hidden: MutableSet<String> = linkedSetOf(),
    var status: Int = STATUS_UNKNOWN,
    var channelCount: Int = 0,
    var categories: List<CategoryInfo> = emptyList(),
    var lastChecked: Long = 0L,
    var lastError: String? = null,
    /** Yönetici listesi: uzaktan gelir, silinemez/düzenlenemez; yalnızca açılıp kapatılır. */
    val admin: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("admin", admin)
        put("name", name)
        put("url", url)
        put("resolved", resolvedUrl ?: "")
        put("enabled", enabled)
        put("hidden", JSONArray(hidden.toList()))
        put("status", status)
        put("count", channelCount)
        put("checked", lastChecked)
        put("error", lastError ?: "")
        put("cats", JSONArray().also { arr ->
            categories.forEach { arr.put(JSONObject().put("n", it.name).put("c", it.count)) }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): PlaylistEntry {
            val hidden = linkedSetOf<String>()
            o.optJSONArray("hidden")?.let { a -> for (i in 0 until a.length()) hidden.add(a.getString(i)) }
            val cats = ArrayList<CategoryInfo>()
            o.optJSONArray("cats")?.let { a ->
                for (i in 0 until a.length()) {
                    val c = a.getJSONObject(i)
                    cats.add(CategoryInfo(c.getString("n"), c.optInt("c")))
                }
            }
            return PlaylistEntry(
                id = o.getString("id"),
                name = o.optString("name"),
                url = o.getString("url"),
                resolvedUrl = o.optString("resolved").ifBlank { null },
                enabled = o.optBoolean("enabled", true),
                hidden = hidden,
                status = o.optInt("status", STATUS_UNKNOWN),
                channelCount = o.optInt("count"),
                categories = cats,
                lastChecked = o.optLong("checked"),
                lastError = o.optString("error").ifBlank { null },
                admin = o.optBoolean("admin", false),
            )
        }
    }
}

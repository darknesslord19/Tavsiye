package com.example.nuviobridge

import com.lagradost.cloudstream3.app
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

data class JsStream(
    val name: String,
    val url: String,
    val quality: Int,
    val headers: Map<String, String>,
)

/** JS tarafının çağırdığı köprü: senkron HTTP isteği yapar, sonucu JSON string olarak döner. */
class HttpBridge {
    fun request(url: String, method: String, headersJson: String, body: String): String {
        val headers = JSONObject(headersJson).let { j ->
            j.keys().asSequence().associateWith { j.getString(it) }
        }
        val res = blocking {
            app.custom(
                method = method.uppercase(),
                url = url,
                headers = headers,
                requestBody = if (body.isEmpty()) null
                else body.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()),
            )
        }
        val outHeaders = JSONObject()
        res.headers.names().forEach { outHeaders.put(it.lowercase(), res.headers[it]) }
        return JSONObject()
            .put("status", res.code)
            .put("body", res.text)
            .put("headers", outHeaders)
            .toString()
    }
}

object JsRunner {
    // Nuvio provider'larının beklediği tarayıcı/Node benzeri ortamın minimum taklidi.
    // Gerçek provider'larda eksik çıkan her global'i (URL, URLSearchParams, cheerio, atob...) buraya ekle.
    private const val PRELUDE = """
        var global = this;
        var module = { exports: {} };
        var exports = module.exports;
        var console = { log: function(){}, warn: function(){}, error: function(){}, info: function(){} };
        function setTimeout(fn){ fn(); return 0; }
        function require(name){ throw new Error('require() shim yok: ' + name); }
        function fetch(url, opts) {
            opts = opts || {};
            var raw = __bridge.request(
                String(url),
                String(opts.method || 'GET'),
                JSON.stringify(opts.headers || {}),
                opts.body ? String(opts.body) : ''
            );
            var r = JSON.parse(raw);
            return Promise.resolve({
                ok: r.status >= 200 && r.status < 300,
                status: r.status,
                headers: { get: function(k){ var v = r.headers[String(k).toLowerCase()]; return v == null ? null : v; } },
                text: function(){ return Promise.resolve(r.body); },
                json: function(){ return Promise.resolve(JSON.parse(r.body)); }
            });
        }
    """

    fun getStreams(
        providerScript: String,
        tmdbId: String,
        mediaType: String, // "movie" | "tv"
        season: Int?,
        episode: Int?,
    ): List<JsStream> {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1 // Android'de dex üretme, interpreted çalış
            cx.languageVersion = Context.VERSION_ES6 // Promise için gerekli
            val scope: Scriptable = cx.initStandardObjects()
            ScriptableObject.putProperty(scope, "__bridge", Context.javaToJS(HttpBridge(), scope))

            cx.evaluateString(scope, PRELUDE, "prelude", 1, null)
            cx.evaluateString(scope, providerScript, "provider", 1, null)

            val call = """
                var __out = null, __err = null;
                var __fn = (module.exports && module.exports.getStreams) || global.getStreams;
                if (!__fn) { __err = 'getStreams bulunamadi'; }
                else {
                    Promise.resolve(__fn(${jsStr(tmdbId)}, ${jsStr(mediaType)}, ${season ?: "null"}, ${episode ?: "null"}))
                        .then(function(r){ __out = JSON.stringify(r || []); },
                              function(e){ __err = String(e); });
                }
            """
            cx.evaluateString(scope, call, "call", 1, null)
            cx.processMicrotasks()

            val err = ScriptableObject.getProperty(scope, "__err")
            if (err != null && err != Scriptable.NOT_FOUND && err.toString() != "null") {
                throw RuntimeException("Provider hatası: $err")
            }
            val out = ScriptableObject.getProperty(scope, "__out").toString()
            return parse(out)
        } finally {
            Context.exit()
        }
    }

    private fun jsStr(s: String) = JSONObject.quote(s)

    // VARSAYIM: her stream {name|title, url, quality, headers} alanlarına sahip.
    private fun parse(json: String): List<JsStream> {
        if (json == "null") return emptyList()
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val h = o.optJSONObject("headers")
            JsStream(
                name = o.optString("name").ifBlank { o.optString("title", "Nuvio") },
                url = url,
                quality = Regex("\\d{3,4}").find(o.optString("quality"))?.value?.toIntOrNull() ?: 0,
                headers = h?.keys()?.asSequence()?.associateWith { h.getString(it) } ?: emptyMap(),
            )
        }
    }
}

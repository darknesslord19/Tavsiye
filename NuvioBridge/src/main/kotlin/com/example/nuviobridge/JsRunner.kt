package com.example.nuviobridge

import com.lagradost.cloudstream3.app
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.WrappedException

data class JsStream(
    val name: String,
    val url: String,
    val quality: Int,
    val headers: Map<String, String>,
    val kind: String = "", // provider'ın verdiği tür ipucu (varsa): "hls", "dash", "mp4"...
)

/** JS tarafının çağırdığı köprü: senkron HTTP isteği yapar, sonucu JSON string olarak döner. */
class HttpBridge {
    fun request(url: String, method: String, headersJson: String, body: String): String {
        val headers = JSONObject(headersJson).let { j ->
            j.keys().asSequence().associateWith { j.getString(it) }
        }
        // Gövdenin türü provider'ın verdiği Content-Type'a uysun (JSON, form vb.).
        val contentType = headers.entries
            .firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
            ?: "application/x-www-form-urlencoded"
        val res = blocking {
            app.custom(
                method = method.uppercase(),
                url = url,
                headers = headers,
                requestBody = if (body.isEmpty()) null
                else body.toRequestBody(contentType.toMediaTypeOrNull()),
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
    // Nuvio provider'larının beklediği tarayıcı/Node benzeri ortamın taklidi (Rhino bunları kendiliğinden vermez).
    // Test ekranı bir provider'ın hangi global'in eksikliğinden düştüğünü gösterir; eksikleri buraya ekle.
    private const val PRELUDE = """
        var global = this;
        var module = { exports: {} };
        var exports = module.exports;

        // --- console: çıktıyı biriktir (test ekranında gösterilir) ---
        var __logText = '';
        function __log(level, args) {
            if (__logText.length > 3000) return;
            var parts = [];
            for (var i = 0; i < args.length; i++) {
                var a = args[i];
                try { parts.push(typeof a === 'string' ? a : (a instanceof Error ? String(a) : JSON.stringify(a))); }
                catch (e) { parts.push(String(a)); }
            }
            __logText += level + ': ' + parts.join(' ') + '\n';
        }
        var console = {
            log: function(){ __log('log', arguments); },
            info: function(){ __log('info', arguments); },
            debug: function(){ __log('debug', arguments); },
            warn: function(){ __log('warn', arguments); },
            error: function(){ __log('error', arguments); }
        };

        // --- zamanlayıcılar: beklemeden hemen çalıştır ---
        function setTimeout(fn){ fn(); return 0; }
        function clearTimeout(){}
        function setInterval(){ return 0; }
        function clearInterval(){}
        function require(name){ throw new Error('require() shim yok: ' + name); }

        // --- Promise.allSettled ---
        if (typeof Promise !== 'undefined' && !Promise.allSettled) {
            Promise.allSettled = function(list) {
                return Promise.all(Array.prototype.map.call(list, function(p) {
                    return Promise.resolve(p).then(
                        function(v){ return { status: 'fulfilled', value: v }; },
                        function(e){ return { status: 'rejected', reason: e }; });
                }));
            };
        }

        // --- AbortController (sadece arayüz, istek iptal edilmez) ---
        if (typeof AbortController === 'undefined') {
            var AbortController = function() {
                this.signal = { aborted: false, addEventListener: function(){}, removeEventListener: function(){} };
            };
            AbortController.prototype.abort = function() { this.signal.aborted = true; };
        }

        // --- atob / btoa ---
        var __B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        function __b64idx(ch) { return ch === '' ? -1 : __B64.indexOf(ch); }
        if (typeof btoa === 'undefined') {
            var btoa = function(input) {
                var s = String(input), out = '', i = 0, c1, c2, c3, e1, e2, e3, e4;
                while (i < s.length) {
                    c1 = s.charCodeAt(i++); c2 = s.charCodeAt(i++); c3 = s.charCodeAt(i++);
                    e1 = c1 >> 2;
                    e2 = ((c1 & 3) << 4) | (c2 >> 4);
                    e3 = ((c2 & 15) << 2) | (c3 >> 6);
                    e4 = c3 & 63;
                    if (isNaN(c2)) { e3 = 64; e4 = 64; } else if (isNaN(c3)) { e4 = 64; }
                    out += __B64.charAt(e1) + __B64.charAt(e2) +
                        (e3 === 64 ? '=' : __B64.charAt(e3)) + (e4 === 64 ? '=' : __B64.charAt(e4));
                }
                return out;
            };
        }
        if (typeof atob === 'undefined') {
            var atob = function(input) {
                var s = String(input).replace(/[^A-Za-z0-9+\/]/g, ''), out = '', i = 0, e1, e2, e3, e4;
                while (i < s.length) {
                    e1 = __b64idx(s.charAt(i++)); e2 = __b64idx(s.charAt(i++));
                    e3 = __b64idx(s.charAt(i++)); e4 = __b64idx(s.charAt(i++));
                    out += String.fromCharCode((e1 << 2) | (e2 >> 4));
                    if (e3 !== -1) out += String.fromCharCode(((e2 & 15) << 4) | (e3 >> 2));
                    if (e4 !== -1) out += String.fromCharCode(((e3 & 3) << 6) | e4);
                }
                return out;
            };
        }

        // --- URLSearchParams ---
        if (typeof URLSearchParams === 'undefined') {
            var URLSearchParams = function(init) {
                this._p = [];
                var self = this;
                if (typeof init === 'string') {
                    init = init.replace(/^\?/, '');
                    if (init) {
                        init.split('&').forEach(function(kv) {
                            var i = kv.indexOf('=');
                            var k = i < 0 ? kv : kv.slice(0, i);
                            var v = i < 0 ? '' : kv.slice(i + 1);
                            self._p.push([decodeURIComponent(k.replace(/\+/g, ' ')), decodeURIComponent(v.replace(/\+/g, ' '))]);
                        });
                    }
                } else if (init && typeof init === 'object') {
                    Object.keys(init).forEach(function(k) { self._p.push([k, String(init[k])]); });
                }
            };
            URLSearchParams.prototype.append = function(k, v) { this._p.push([String(k), String(v)]); };
            URLSearchParams.prototype.get = function(k) {
                for (var i = 0; i < this._p.length; i++) if (this._p[i][0] === k) return this._p[i][1];
                return null;
            };
            URLSearchParams.prototype.getAll = function(k) {
                return this._p.filter(function(x) { return x[0] === k; }).map(function(x) { return x[1]; });
            };
            URLSearchParams.prototype.has = function(k) { return this.get(k) !== null; };
            URLSearchParams.prototype.set = function(k, v) {
                var done = false, out = [];
                for (var i = 0; i < this._p.length; i++) {
                    if (this._p[i][0] === k) { if (!done) { out.push([k, String(v)]); done = true; } }
                    else out.push(this._p[i]);
                }
                if (!done) out.push([String(k), String(v)]);
                this._p = out;
            };
            URLSearchParams.prototype['delete'] = function(k) {
                this._p = this._p.filter(function(x) { return x[0] !== k; });
            };
            URLSearchParams.prototype.forEach = function(fn) {
                for (var i = 0; i < this._p.length; i++) fn(this._p[i][1], this._p[i][0], this);
            };
            URLSearchParams.prototype.toString = function() {
                return this._p.map(function(x) {
                    return encodeURIComponent(x[0]) + '=' + encodeURIComponent(x[1]);
                }).join('&');
            };
        }

        // --- URL (temel) ---
        if (typeof URL === 'undefined') {
            var URL = function(href, base) {
                href = String(href);
                if (!/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(href) && base) {
                    var b = new URL(base);
                    if (href.indexOf('//') === 0) href = b.protocol + href;
                    else if (href.charAt(0) === '/') href = b.origin + href;
                    else href = b.origin + b.pathname.slice(0, b.pathname.lastIndexOf('/') + 1) + href;
                }
                var m = /^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^\/?#]*)([^?#]*)(\?[^#]*)?(#.*)?${'$'}/.exec(href);
                if (!m) throw new TypeError('Invalid URL: ' + href);
                this.protocol = m[1];
                this.host = m[2];
                var hp = m[2].replace(/^[^@]*@/, '').split(':');
                this.hostname = hp[0];
                this.port = hp[1] || '';
                this.pathname = m[3] || '/';
                this.search = m[4] || '';
                this.hash = m[5] || '';
                this.origin = this.protocol + '//' + m[2];
                this.searchParams = new URLSearchParams(this.search);
                this.href = this.origin + this.pathname + this.search + this.hash;
            };
            URL.prototype.toString = function() { return this.href; };
        }

        // --- fetch: senkron köprü, Promise döndürür ---
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
                url: String(url),
                headers: { get: function(k){ var v = r.headers[String(k).toLowerCase()]; return v == null ? null : v; } },
                text: function(){ return Promise.resolve(r.body); },
                json: function(){ return Promise.resolve(JSON.parse(r.body)); }
            });
        }
    """

    /**
     * Önce hafif olan Rhino ile dener. Sağlayıcı Rhino'nun bilmediği modern JS (async/await, ?. vb.)
     * kullanıyorsa ya da ortam eksikliğinden düşerse WebView motoruna geçer.
     *
     * @param logSink verilirse provider'ın console çıktısı (log/warn/error) buraya eklenir;
     *                hata durumunda da doldurulur.
     */
    fun getStreams(
        providerScript: String,
        tmdbId: String,
        mediaType: String, // "movie" | "tv"
        season: Int?,
        episode: Int?,
        logSink: StringBuilder? = null,
    ): List<JsStream> {
        try {
            return getStreamsRhino(providerScript, tmdbId, mediaType, season, episode, logSink)
        } catch (e: Throwable) {
            if (!needsBrowserEngine(e)) throw e
            logSink?.append("[Rhino yetmedi, WebView'a geçildi: " + (e.message ?: "").take(60) + "]\n")
            return WebJsRunner.getStreams(providerScript, tmdbId, mediaType, season, episode, logSink)
        }
    }

    // Ağ hatası (WrappedException) değil; söz dizimi / eksik ortam hatası ise tarayıcı motoru dene.
    private fun needsBrowserEngine(e: Throwable): Boolean {
        if (e is WrappedException) return false
        if (e is EvaluatorException || e is EcmaError) return true
        val m = e.message ?: return false
        return m.contains("ReferenceError") || m.contains("TypeError") || m.contains("SyntaxError") ||
            m.contains("is not defined") || m.contains("is not a function")
    }

    private fun getStreamsRhino(
        providerScript: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        logSink: StringBuilder?,
    ): List<JsStream> {
        val cx = Context.enter()
        var scopeRef: Scriptable? = null
        try {
            cx.optimizationLevel = -1 // Android'de dex üretme, interpreted çalış
            cx.languageVersion = Context.VERSION_ES6 // Promise için gerekli
            val scope: Scriptable = cx.initStandardObjects()
            scopeRef = scope
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
            if (logSink != null && scopeRef != null) {
                val log = ScriptableObject.getProperty(scopeRef, "__logText")
                if (log != null && log != Scriptable.NOT_FOUND) logSink.append(log.toString())
            }
            Context.exit()
        }
    }

    private fun jsStr(s: String) = JSONObject.quote(s)

    // VARSAYIM: her stream {name|title, url, quality, headers} alanlarına sahip.
    internal fun parse(json: String): List<JsStream> {
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
                kind = o.optString("type").ifBlank { o.optString("format") },
            )
        }
    }
}

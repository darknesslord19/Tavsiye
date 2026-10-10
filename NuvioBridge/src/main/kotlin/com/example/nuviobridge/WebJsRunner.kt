package com.example.nuviobridge

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Rhino'nun okuyamadığı modern JavaScript (async/await, ?., ?? vb.) için yedek motor:
 * sağlayıcıyı ekrana bağlanmamış gizli bir WebView'da çalıştırır. Tarayıcı motoru olduğu için
 * URL, atob, TextEncoder, crypto gibi şeyler hazır gelir. Ağ istekleri CORS'a takılmasın diye
 * fetch, Kotlin köprüsüne (NB.request) yönlendirilir.
 */
object WebJsRunner {
    private val main = Handler(Looper.getMainLooper())

    // Aynı anda en fazla kaç WebView açık olsun (bellek için sınırlı tutulur).
    private val gate = Semaphore(6)
    private const val TIMEOUT_SECONDS = 40L

    private const val PRELUDE = """
        var global = window;
        var module = { exports: {} };
        var exports = module.exports;

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
        ['log', 'info', 'debug', 'warn', 'error'].forEach(function(l) {
            console[l] = function() { __log(l, arguments); };
        });
        window.onerror = function(msg, src, line, col) { __log('onerror', [msg + ' @' + line + ':' + col]); };
        window.addEventListener('unhandledrejection', function(ev) { __log('unhandledrejection', [String(ev.reason)]); });

        // Zamanlayıcılar: gizli WebView'da yavaşlatılmasın diye beklemeden çalıştır.
        window.setTimeout = function(fn) { Promise.resolve().then(function() { if (typeof fn === 'function') fn(); }); return 0; };
        window.setInterval = function() { return 0; };
        window.require = function(name) { throw new Error('require() shim yok: ' + name); };

        function __hdrs(h) {
            var out = {};
            if (!h) return out;
            if (Array.isArray(h)) { h.forEach(function(p) { out[p[0]] = p[1]; }); }
            else if (typeof h.forEach === 'function' && typeof h.get === 'function') { h.forEach(function(v, k) { out[k] = v; }); }
            else { Object.keys(h).forEach(function(k) { out[k] = h[k]; }); }
            return out;
        }

        // fetch: CORS'a takılmamak için Kotlin tarafında yapılır (senkron köprü, Promise döndürür).
        window.fetch = function(url, opts) {
            opts = opts || {};
            return new Promise(function(resolve, reject) {
                try {
                    var body = (opts.body === undefined || opts.body === null) ? '' : String(opts.body);
                    var raw = NB.request(String(url), String(opts.method || 'GET'), JSON.stringify(__hdrs(opts.headers)), body);
                    var r = JSON.parse(raw);
                    if (r.error) throw new Error(r.error);
                    var res = {
                        ok: r.status >= 200 && r.status < 300,
                        status: r.status,
                        statusText: '',
                        url: String(url),
                        headers: {
                            get: function(k) { var v = r.headers[String(k).toLowerCase()]; return v == null ? null : v; },
                            has: function(k) { return r.headers[String(k).toLowerCase()] != null; }
                        },
                        text: function() { return Promise.resolve(r.body); },
                        json: function() { return Promise.resolve(JSON.parse(r.body)); },
                        clone: function() { return res; }
                    };
                    resolve(res);
                } catch (e) { reject(e); }
            });
        };
    """

    /** JS'in çağırdığı köprü. */
    internal class Bridge(
        private val latch: CountDownLatch,
        val json: AtomicReference<String>,
        val err: AtomicReference<String>,
        val log: AtomicReference<String>,
    ) {
        @JavascriptInterface
        fun request(url: String, method: String, headersJson: String, body: String): String {
            return try {
                HttpBridge().request(url, method, headersJson, body)
            } catch (t: Throwable) {
                JSONObject().put("error", t.message ?: t.javaClass.simpleName).toString()
            }
        }

        @JavascriptInterface
        fun done(resultJson: String, error: String, logText: String) {
            json.set(resultJson)
            err.set(error)
            log.set(logText)
            latch.countDown()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getStreams(
        providerScript: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        logSink: StringBuilder?,
    ): List<JsStream> {
        val ctx = RepoStore.context() ?: throw RuntimeException("Uygulama bağlamı yok (WebView açılamadı)")

        val call = "(function(){ try { var fn = (module.exports && module.exports.getStreams) || window.getStreams; " +
            "if (!fn) { NB.done('', 'getStreams bulunamadi', __logText); return; } " +
            "Promise.resolve(fn(" + JSONObject.quote(tmdbId) + "," + JSONObject.quote(mediaType) + "," +
            (season ?: "null") + "," + (episode ?: "null") + ")).then(" +
            "function(r){ NB.done(JSON.stringify(r || []), '', __logText); }, " +
            "function(e){ NB.done('', String((e && e.message) ? e.message : e), __logText); }); " +
            "} catch (e) { NB.done('', 'Hata: ' + String(e), __logText); } })();"

        // Sağlayıcı kodundaki "</script" ve "<!--" HTML ayrıştırıcıyı bozmasın.
        val safeProvider = providerScript
            .replace("</script", "<\\/script", ignoreCase = true)
            .replace("<!--", "<\\!--")

        val html = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body>" +
            "<script>" + PRELUDE + "</script>" +
            "<script>" + safeProvider + "\n</script>" +
            "<script>" + call + "</script>" +
            "</body></html>"

        gate.acquire()
        try {
            val latch = CountDownLatch(1)
            val json = AtomicReference("")
            val err = AtomicReference("")
            val log = AtomicReference("")
            val holder = AtomicReference<WebView?>(null)

            main.post {
                try {
                    val wv = WebView(ctx)
                    holder.set(wv)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.addJavascriptInterface(Bridge(latch, json, err, log), "NB")
                    // https tabanlı adres: tarayıcı API'leri (crypto.subtle vb.) güvenli bağlamda çalışır.
                    wv.loadDataWithBaseURL("https://nuvio.local/", html, "text/html", "utf-8", null)
                } catch (t: Throwable) {
                    err.set("WebView açılamadı: " + (t.message ?: t.javaClass.simpleName))
                    latch.countDown()
                }
            }

            val finished = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            main.post {
                holder.get()?.let {
                    it.removeJavascriptInterface("NB")
                    it.destroy()
                }
            }
            logSink?.append(log.get())

            if (!finished) throw RuntimeException("WebView zaman aşımı (" + TIMEOUT_SECONDS + " sn)")
            if (err.get().isNotEmpty()) throw RuntimeException("Provider hatası: " + err.get())
            return JsRunner.parse(json.get().ifEmpty { "[]" })
        } finally {
            gate.release()
        }
    }
}

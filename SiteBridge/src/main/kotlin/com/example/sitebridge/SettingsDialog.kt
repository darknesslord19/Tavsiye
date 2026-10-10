package com.example.sitebridge

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/** Düz android.app.Dialog içinde WebView ile açılan ayar ekranı (XML/androidx gerekmez). */
object SettingsDialog {

    @SuppressLint("SetJavaScriptEnabled")
    fun show(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val webView = WebView(context).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(Bridge(this), "Android")
            loadDataWithBaseURL(null, SettingsHtml.PAGE, "text/html", "utf-8", null)
        }
        dialog.setContentView(webView)
        dialog.setOnDismissListener {
            webView.removeJavascriptInterface("Android")
            webView.destroy()
        }
        dialog.show()
    }

    /** HTML'in çağırdığı fonksiyonlar (arka plan thread'inde çalışır). */
    private class Bridge(private val view: WebView) {

        @JavascriptInterface
        fun getState(): String {
            val sites = JSONArray()
            SiteStore.allSites().forEach {
                sites.put(
                    JSONObject().put("name", it.name).put("url", it.url).put("enabled", it.enabled)
                        .put("source", it.source).put("rules", it.rules != null)
                )
            }
            val src = JSONArray()
            SiteStore.sources().forEach { src.put(it) }
            return JSONObject().put("sites", sites).put("sources", src).toString()
        }

        /** Hata metni, başarılıysa boş string. */
        @JavascriptInterface
        fun addSite(name: String, url: String, rules: String): String = SiteStore.addSite(name, url, rules) ?: ""

        @JavascriptInterface
        fun removeSite(url: String) = SiteStore.removeSite(url)

        @JavascriptInterface
        fun setEnabled(url: String, enabled: Boolean) = SiteStore.setEnabled(url, enabled)

        @JavascriptInterface
        fun addSource(url: String): String = SiteStore.addSource(url) ?: ""

        @JavascriptInterface
        fun removeSource(url: String) = SiteStore.removeSource(url)

        /** Listeleri indirir; bitince sayfadaki onRefresh(ok, mesaj) çağrılır. */
        @JavascriptInterface
        fun refresh() {
            Thread {
                val r = runCatching { blocking { SiteStore.refreshRemote() } }.getOrElse { Result.failure(it) }
                val ok = r.isSuccess
                val text = r.fold(
                    onSuccess = { "$it site bulundu. Kaynak listesinin güncellenmesi için CloudStream'i kapatıp aç." },
                    onFailure = { it.message ?: "Liste okunamadı" },
                )
                view.post {
                    view.evaluateJavascript("onRefresh(" + ok + "," + JSONObject.quote(text) + ")", null)
                }
            }.start()
        }
    }
}

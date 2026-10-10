package com.example.nuviobridge

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * XML ve androidx kullanmadan, düz android.app.Dialog içinde WebView ile açılan ayarlar ekranı.
 * Context olarak Cloudstream'in verdiği Activity kullanılır.
 */
object SettingsDialog {

    @SuppressLint("SetJavaScriptEnabled")
    fun show(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val webView = WebView(context).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            settings.javaScriptEnabled = true
            // Sayfa sadece gömülü HTML'i gösterir; dış içerik yüklenmez.
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

    /** HTML'in çağırdığı fonksiyonlar. JavascriptInterface metotları arka plan thread'inde çalışır. */
    private class Bridge(private val view: WebView) {

        @JavascriptInterface
        fun getRepos(): String {
            val arr = JSONArray()
            RepoStore.list().forEach {
                arr.put(JSONObject().put("url", it.url).put("enabled", it.enabled))
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun addRepo(url: String) {
            Thread {
                val result = blocking { RepoStore.add(url) }
                val ok = result.isSuccess
                val text = result.fold(
                    onSuccess = { "Eklendi: $it provider bulundu" },
                    onFailure = { it.message ?: "Eklenemedi" },
                )
                view.post {
                    view.evaluateJavascript(
                        "onAddResult(" + ok + "," + JSONObject.quote(text) + ")", null
                    )
                }
            }.start()
        }

        @JavascriptInterface
        fun removeRepo(url: String) = RepoStore.remove(url)

        @JavascriptInterface
        fun setEnabled(url: String, enabled: Boolean) = RepoStore.setEnabled(url, enabled)
    }
}

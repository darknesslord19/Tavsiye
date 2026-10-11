package com.example.iptvbridge

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Düz Dialog + WebView ile açılan ayar ekranı (XML/androidx gerekmez). */
object SettingsDialog {

    @SuppressLint("SetJavaScriptEnabled")
    fun show(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val webView = WebView(context).apply {
            setBackgroundColor(Color.parseColor("#0D0F12"))
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

    private class Bridge(private val view: WebView) {

        private fun item(l: IptvList): JSONObject {
            val o = JSONObject().put("id", l.id).put("name", l.name).put("url", l.url).put("enabled", l.enabled)
            IptvStore.status(l.id)?.let { o.put("status", JSONObject().put("ok", it.ok).put("text", it.text)) }
            return o
        }

        @JavascriptInterface
        fun getState(): String {
            val user = JSONArray(); IptvStore.userLists().forEach { user.put(item(it)) }
            val admin = JSONArray(); IptvStore.adminLists().forEach { admin.put(item(it)) }
            return JSONObject().put("user", user).put("admin", admin).put("adminSet", IptvStore.adminConfigured()).toString()
        }

        /** Hata metni, başarılıysa boş string. Kayıttan sonra gruplar arka planda hazırlanır. */
        @JavascriptInterface
        fun addList(name: String, url: String): String {
            val err = IptvStore.add(name, url)
            if (err == null) warm(IptvStore.fixUrl(url) ?: url)
            return err ?: ""
        }

        @JavascriptInterface
        fun updateList(id: String, name: String, url: String): String {
            val err = IptvStore.update(id, name, url)
            if (err == null) warm(IptvStore.fixUrl(url) ?: url)
            return err ?: ""
        }

        @JavascriptInterface
        fun setEnabled(id: String, enabled: Boolean) = IptvStore.setEnabled(id, enabled)

        @JavascriptInterface
        fun removeList(id: String) = IptvStore.remove(id)

        private fun warm(url: String) {
            Thread { runCatching { blocking { Net.channels(url, true) } } }.start()
        }

        /** Listeyi indirip kanal/grup sayısını bulur; bitince onTest(id, ok, metin) çağrılır. */
        @JavascriptInterface
        fun testList(id: String) {
            Thread {
                val l = IptvStore.all().firstOrNull { it.id == id }
                var ok = false
                val text = if (l == null) "Liste bulunamadı" else try {
                    val ch = blocking { Net.channels(l.url, true) }
                    ok = true
                    val nf = String.format(Locale("tr"), "%,d", ch.size)
                    nf + " kanal · " + ch.map { it.group }.toSet().size + " grup"
                } catch (e: Throwable) {
                    (e.message ?: e.javaClass.simpleName).take(90)
                }
                if (l != null) IptvStore.setStatus(id, Status(ok, text, System.currentTimeMillis()))
                view.post { view.evaluateJavascript("onTest(" + JSONObject.quote(id) + ")", null) }
            }.start()
        }

        /** Yönetici listesini GitHub'dan yeniler; bitince onRefresh(ok, metin) çağrılır. */
        @JavascriptInterface
        fun refreshAdmin() {
            Thread {
                val r = runCatching { blocking { IptvStore.refreshAdmin() } }.getOrElse { Result.failure(it) }
                val ok = r.isSuccess
                val text = r.fold(
                    onSuccess = { "$it yönetici listesi güncellendi" },
                    onFailure = { it.message ?: "Liste okunamadı" },
                )
                view.post { view.evaluateJavascript("onRefresh(" + ok + "," + JSONObject.quote(text) + ")", null) }
            }.start()
        }
    }
}

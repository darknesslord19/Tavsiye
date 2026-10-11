package com.darkneslord.iptv

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * XML ve androidx kullanmadan, düz android.app.Dialog içinde WebView ile açılan ayarlar ekranı.
 * Context olarak Cloudstream'in verdiği Activity kullanılır.
 */
object SettingsDialog {

    @SuppressLint("SetJavaScriptEnabled")
    fun show(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        dialog.window?.apply {
            statusBarColor = Color.parseColor("#0A0A0F")
            navigationBarColor = Color.parseColor("#0A0A0F")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val webView = WebView(context).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            settings.javaScriptEnabled = true
            // Sayfa sadece gömülü HTML'i gösterir; dış içerik yüklenmez.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            addJavascriptInterface(Bridge(this, dialog, context, clipboard), "Android")
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
    private class Bridge(
        private val view: WebView,
        private val dialog: Dialog,
        private val context: Context,
        private val clipboard: ClipboardManager?,
    ) {
        private fun find(id: String): PlaylistEntry? = Store.all().firstOrNull { it.id == id }

        private fun callJs(code: String) {
            view.post { view.evaluateJavascript(code, null) }
        }

        @JavascriptInterface
        fun getState(): String {
            val arr = JSONArray()
            Store.all().forEach { arr.put(it.toJson()) }
            return JSONObject()
                .put("version", Diagnostics.BUILD)
                .put("split", Store.splitEnabled())
                .put("playlists", arr)
                .toString()
        }

        @JavascriptInterface
        fun setSplit(value: Boolean) = Store.setSplit(value)

        @JavascriptInterface
        fun setEnabled(id: String, enabled: Boolean) {
            find(id)?.let { it.enabled = enabled; Store.save() }
        }

        @JavascriptInterface
        fun setCategoryHidden(id: String, category: String, hidden: Boolean) {
            find(id)?.let {
                if (hidden) it.hidden.add(category) else it.hidden.remove(category)
                Store.save()
            }
        }

        @JavascriptInterface
        fun setAllHidden(id: String, hidden: Boolean) {
            find(id)?.let {
                if (hidden) it.hidden.addAll(it.categories.map { c -> c.name }) else it.hidden.clear()
                Store.save()
            }
        }

        @JavascriptInterface
        fun removePlaylist(id: String) {
            find(id)?.let { Store.remove(it) }
        }

        /** Yeni liste (id boş) ya da düzenleme: link çözülür, okunur; çalışmıyorsa kaydedilmez. */
        @JavascriptInterface
        fun savePlaylist(id: String, name: String, url: String) {
            Thread {
                val link = url.trim()
                val existing = if (id.isBlank()) null else find(id)
                val duplicate = Store.all().any { it.url == link && it.id != existing?.id }
                var ok = false
                val text: String
                if (duplicate) {
                    text = "Bu link zaten ekli."
                } else {
                    val r = runCatching { PlaylistLoader.load(link) }
                        .getOrElse { PlaylistLoader.Result(false, link, emptyList(), it.message ?: "Beklenmeyen hata") }
                    if (!r.ok) {
                        text = r.error ?: "Liste okunamadı."
                    } else {
                        val finalName = name.trim().ifEmpty { LinkTools.hostOf(r.finalUrl) }
                        val entry = existing ?: PlaylistEntry(UUID.randomUUID().toString(), finalName, link)
                        entry.name = finalName
                        entry.url = link
                        Repo.apply(entry, r)
                        if (existing == null) Store.add(entry) else Store.save()
                        ok = true
                        text = r.channels.size.toString() + " kanal bulundu"
                    }
                }
                callJs("onSaveResult(" + ok + "," + JSONObject.quote(text) + ")")
            }.start()
        }

        /** Tek listeyi yeniden çözüp okur, durumunu günceller. */
        @JavascriptInterface
        fun testPlaylist(id: String) {
            Thread {
                val entry = find(id)
                var ok = false
                val text: String
                if (entry == null) {
                    text = "Liste bulunamadı."
                } else {
                    val r = runCatching { PlaylistLoader.load(entry.url) }
                        .getOrElse { PlaylistLoader.Result(false, entry.url, emptyList(), it.message ?: "Beklenmeyen hata") }
                    Repo.apply(entry, r)
                    ok = r.ok
                    text = if (r.ok) r.channels.size.toString() + " kanal okundu" else (r.error ?: "Liste okunamadı.")
                }
                callJs("onTestResult(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(text) + ")")
            }.start()
        }

        @JavascriptInterface
        fun testAll() {
            Thread {
                val text = runCatching { Diagnostics.testAll() }
                    .getOrElse { "Test çalışmadı: " + (it.message ?: it.javaClass.simpleName) }
                callJs("onDiag(" + JSONObject.quote(text) + ")")
            }.start()
        }

        @JavascriptInterface
        fun paste(): String {
            val clip = clipboard?.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            return clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
        }

        /** Durum çubuğu yüksekliği (CSS px). Başlığın çubuğun altında kalmaması için. */
        @JavascriptInterface
        fun topInset(): Float = inset("status_bar_height")

        /** Gezinme çubuğu yüksekliği (CSS px). */
        @JavascriptInterface
        fun bottomInset(): Float = inset("navigation_bar_height")

        private fun inset(name: String): Float {
            val res = context.resources
            val id = res.getIdentifier(name, "dimen", "android")
            val px = if (id > 0) res.getDimensionPixelSize(id) else 0
            return px / res.displayMetrics.density
        }

        /** Yalnızca geliştiricinin Telegram adresini açar. */
        @JavascriptInterface
        fun openUrl(url: String) {
            if (url != "https://t.me/darknes_lord") return
            view.post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Telegram/tarayıcı yoksa sessizce geç
                }
            }
        }

        @JavascriptInterface
        fun close() {
            view.post { dialog.dismiss() }
        }
    }
}

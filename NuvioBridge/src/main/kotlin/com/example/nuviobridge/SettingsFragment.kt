package com.example.nuviobridge

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.fragment.app.DialogFragment
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** XML layout kullanmadan, tamamen kodla kurulan ayarlar ekranı (WebView içinde HTML). */
class SettingsFragment : DialogFragment() {

    private var webView: WebView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return dialog
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val wv = WebView(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            settings.javaScriptEnabled = true
            // Sayfa sadece gömülü HTML'i gösterir; dış içerik yüklenmez.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(Bridge(this), "Android")
            loadDataWithBaseURL(null, SettingsHtml.PAGE, "text/html", "utf-8", null)
        }
        webView = wv
        return wv
    }

    override fun onDestroyView() {
        webView?.apply { removeJavascriptInterface("Android"); destroy() }
        webView = null
        super.onDestroyView()
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
                val result = runBlocking { RepoStore.add(url) }
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

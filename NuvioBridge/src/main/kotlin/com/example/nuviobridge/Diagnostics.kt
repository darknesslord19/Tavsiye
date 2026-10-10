package com.example.nuviobridge

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Ayarlar ekranındaki test düğmelerinin çalıştırdığı kontroller. Anahtarı ekrana yazmaz. */
object Diagnostics {
    // Yeni bir sürüm yüklediğini bu etiketten anlarsın. Her değişiklikte güncellenir.
    const val BUILD = "v11"

    // Son Sağlayıcı testinin sonucu: script adresi -> link döndürdü mü. "Sadece çalışanları aç" bunu kullanır.
    private val lastResults = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    fun results(): Map<String, Boolean> = HashMap(lastResults)

    suspend fun run(): String {
        val sb = StringBuilder()
        sb.append("Sürüm: ").append(BUILD).append("\n")
        sb.append("Kayıtlı repo: ").append(RepoStore.list().size)
            .append(", etkin sağlayıcı: ").append(RepoStore.enabledScripts().size).append("\n\n")

        // 1) Her TMDB adresini, Cloudstream'in kendi HTTP istemcisiyle (eklentinin kullandığıyla) dene.
        for (host in NuvioBridgeProvider.TMDB_HOSTS) {
            sb.append(host.substringAfter("//").substringBefore("/")).append(": ")
            try {
                val res = app.get(
                    host + "/movie/popular?api_key=" + NuvioBridgeProvider.TMDB_KEY + "&language=tr-TR"
                )
                val count = runCatching { JSONObject(res.text).optJSONArray("results")?.length() }.getOrNull()
                sb.append("HTTP ").append(res.code)
                if (count != null) {
                    sb.append(", ").append(count).append(" sonuç")
                } else {
                    sb.append(", yanıt JSON değil: ").append(res.text.take(80).replace("\n", " "))
                }
            } catch (e: Exception) {
                sb.append("HATA ").append(e.message ?: e.javaClass.simpleName)
            }
            sb.append("\n")
        }

        // 2) Ana sayfanın kullandığı gerçek fonksiyonu çalıştır.
        sb.append("\nEklentinin kendi isteği: ")
        try {
            val json = NuvioBridgeProvider().tmdbGet("/movie/popular")
            sb.append(json.optJSONArray("results")?.length() ?: 0).append(" film alındı")
        } catch (e: Exception) {
            sb.append("HATA ").append(e.message ?: e.javaClass.simpleName)
        }
        return sb.toString()
    }

    /**
     * Etkin her sağlayıcıyı bilinen bir filmle (The Matrix, TMDB 603) çalıştırır ve
     * kaç link döndürdüğünü ya da hangi hatayı verdiğini listeler.
     */
    suspend fun testProviders(): String {
        val scripts = RepoStore.enabledScripts()
        if (scripts.isEmpty()) return "Etkin sağlayıcı yok. Önce yukarıdan bir repo ekle."

        val lines = mutableListOf<String>()
        var working = 0
        lastResults.clear()
        val futures = java.util.concurrent.ConcurrentHashMap<java.util.concurrent.Future<String>, ScriptRef>()
        val pool = Executors.newFixedThreadPool(minOf(8, scripts.size))
        try {
            val ecs = ExecutorCompletionService<String>(pool)
            scripts.forEach { ref ->
                ecs.submit(Callable {
                    val label = ref.repo + " · " + ref.provider
                    // Provider'ın console çıktısı: link gelmeyen ya da hata veren sağlayıcının sebebini gösterir.
                    val log = StringBuilder()
                    fun logNote(): String {
                        val t = log.toString().trim().replace("\n", " | ")
                        // Sonda hatanın sebebi olur; baştaki tekrarlayan adımları değil sonunu göster.
                        return if (t.isEmpty()) "" else
                            "\n    günlük: " + (if (t.length > 300) "…" + t.takeLast(300) else t)
                    }
                    try {
                        val script = blocking { app.get(ref.url).text }
                        val streams = JsRunner.getStreams(script, "603", "movie", null, null, log)
                        val first = streams.firstOrNull()
                        val extra = if (first == null) "" else
                            " (ilk: " + StreamKind.detect(first) + ", " +
                                (runCatching { java.net.URI(first.url).host }.getOrNull() ?: "?") + ")"
                        label + ": " + streams.size + " link" + extra +
                            (if (streams.isEmpty() || log.contains("WebView")) logNote() else "")
                    } catch (e: Throwable) {
                        label + ": HATA " + (e.message ?: e.javaClass.simpleName).take(120) + logNote()
                    }
                }).also { futures[it] = ref }
            }

            val deadline = System.currentTimeMillis() + 240_000L
            var remaining = scripts.size
            while (remaining > 0) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                val done = ecs.poll(left, TimeUnit.MILLISECONDS) ?: break
                remaining--
                val line = runCatching { done.get() }.getOrDefault("(okunamadı)")
                val head = line.substringBefore("\n") // ilk satır; günlük notu sonraki satırda
                val isWorking = !head.contains(": HATA") && !head.endsWith(": 0 link") && head != "(okunamadı)"
                if (isWorking) working++
                futures[done]?.let { lastResults[it.url] = isWorking }
                lines += line
            }
            if (remaining > 0) lines += remaining.toString() + " sağlayıcı 240 sn içinde yanıt vermedi (bunlar sonuçlara dahil edilmedi)"
        } finally {
            pool.shutdownNow()
        }

        return "Link döndüren sağlayıcı: " + working + " / " + scripts.size +
            "\nTest filmi: The Matrix (TMDB 603)\n\n" + lines.joinToString("\n")
    }
}

package com.example.nuviobridge

import com.lagradost.cloudstream3.app
import org.json.JSONObject

/** Ayarlar ekranındaki "Bağlantı testi" düğmesinin çalıştırdığı kontrol. Anahtarı ekrana yazmaz. */
object Diagnostics {
    // Yeni bir sürüm yüklediğini bu etiketten anlarsın. Her değişiklikte güncellenir.
    const val BUILD = "v5-tani"

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
}

package com.darkneslord.iptv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DarknesLordPlugin : Plugin() {
    override fun load(context: Context) {
        Store.init(context)

        val active = Store.all().filter { it.enabled }
        if (Store.splitEnabled() && active.isNotEmpty()) {
            // Ayrı kaynak modu: her aktif liste kaynak seçicide kendi adıyla görünür.
            // Liste ekleme/silme/ad değişikliği CloudStream yeniden başlatılınca burada yansır.
            val used = HashSet<String>()
            for (entry in active) {
                var label = entry.name.ifBlank { "Liste" }
                var n = 2
                while (!used.add(label)) label = entry.name + " (" + n++ + ")"
                registerMainAPI(DarknesLordProvider(entry.id, label))
            }
        } else {
            // Tek kaynak modu: tüm aktif listeler "Darknes Lord" ana sayfasında sıralanır.
            registerMainAPI(DarknesLordProvider())
        }

        // Eklentinin yanındaki dişli simgesine basınca açılır.
        openSettings = { ctx -> SettingsDialog.show(ctx) }
    }
}

package com.darkneslord.iptv

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IPTVPlugin : Plugin() {
    override fun load(context: Context) {
        Store.init(context)

        // Yönetici listesi: ilk kurulumda kısa süre beklenir, sonra arka planda tazelenir.
        val first = Store.admins().isEmpty()
        val t = Thread { runCatching { Store.syncAdmin() } }
        t.start()
        if (first) runCatching { t.join(7000) }

        // Her etkin liste CloudStream'de ayrı bir kaynak olarak görünür.
        val active = Store.all().filter { it.enabled }
        if (active.isEmpty()) {
            registerMainAPI(IPTVProvider(null, "Darknes Lord"))
        } else {
            val used = HashSet<String>()
            for (e in active) {
                var n = e.name.ifBlank { LinkTools.hostOf(e.url) }
                if (!used.add(n.lowercase())) n = "$n (${LinkTools.hostOf(e.url)})"
                used.add(n.lowercase())
                runCatching { registerMainAPI(IPTVProvider(e.id, n)) }
            }
        }

        // Eklentiler ekranındaki dişli simgesi -> yönetim paneli
        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null) {
                DarknesSettingsFragment().show(activity.supportFragmentManager, "darknes_lord_settings")
            }
        }
    }
}

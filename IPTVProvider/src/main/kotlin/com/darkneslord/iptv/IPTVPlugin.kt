package com.darkneslord.iptv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IPTVPlugin : Plugin() {
    override fun load(context: Context) {
        IptvStore.init(context)

        // Yönetici listesi: ilk kurulumda kısa süre bekleyerek indir, sonra arka planda tazele.
        if (IptvStore.adminConfigured()) {
            if (IptvStore.adminCacheEmpty()) {
                val t = Thread { runCatching { blocking { IptvStore.refreshAdmin() } } }
                t.start()
                t.join(7000)
            } else if (IptvStore.adminRefreshDue()) {
                Thread { runCatching { blocking { IptvStore.refreshAdmin() } } }.start()
            }
        }

        val lists = IptvStore.enabledLists()
        if (lists.isEmpty()) {
            registerMainAPI(IPTVProvider(IptvList("", "IPTV Yöneticisi (dişliden liste ekle / etkinleştir)", "", false)))
        } else {
            val used = HashSet<String>()
            for (l in lists) {
                var n = l.name
                if (!used.add(n.lowercase())) n = n + " (" + IptvStore.hostOf(l.url) + ")"
                used.add(n.lowercase())
                runCatching { registerMainAPI(IPTVProvider(l.copy(name = n))) }
            }
            // Ana sayfa grup satırları için henüz grubu bilinmeyen listeleri arka planda hazırla
            Thread {
                for (l in lists) if (IptvStore.groups(l.url).isEmpty()) runCatching { blocking { Net.channels(l.url, title = l.name) } }
            }.start()
        }

        openSettings = { ctx -> SettingsDialog.show(ctx) }
    }
}

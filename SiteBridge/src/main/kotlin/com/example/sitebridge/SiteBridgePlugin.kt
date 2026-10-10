package com.example.sitebridge

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SiteBridgePlugin : Plugin() {
    override fun load(context: Context) {
        SiteStore.init(context)

        // Liste önbelleği boşsa (ilk kurulum) kısa süre bekleyerek indir; aksi halde arka planda tazele.
        if (SiteStore.cacheEmpty()) {
            val t = Thread { runCatching { blocking { SiteStore.refreshRemote() } } }
            t.start()
            t.join(7000)
        } else if (SiteStore.refreshDue()) {
            Thread { runCatching { blocking { SiteStore.refreshRemote() } } }.start()
        }

        val sites = SiteStore.enabledSites()
        if (sites.isEmpty()) {
            // Site yokken kaynak seçicide yönerge gösteren boş bir kaynak
            registerMainAPI(SiteProvider(SiteInfo("Site Köprüsü (dişliden site ekle)", "", null, true, "user")))
        } else {
            // Aynı adı taşıyan siteler karışmasın: tekrar edenin yanına alan adı eklenir.
            val used = HashSet<String>()
            for (s in sites) {
                var n = s.name.ifBlank { Scraper.hostOf(s.url) }
                if (!used.add(n.lowercase())) n = n + " (" + Scraper.hostOf(s.url) + ")"
                used.add(n.lowercase())
                runCatching { registerMainAPI(SiteProvider(s.copy(name = n))) }
            }
        }

        // Eklentinin yanındaki dişli simgesine basınca açılır.
        openSettings = { ctx -> SettingsDialog.show(ctx) }
    }
}

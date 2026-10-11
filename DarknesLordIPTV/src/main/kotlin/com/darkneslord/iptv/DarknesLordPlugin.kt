package com.darkneslord.iptv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DarknesLordPlugin : Plugin() {
    override fun load(context: Context) {
        Store.init(context)

        // Tek kaynak: aktif listelerin, gizlenmemiş kategorilerindeki tüm kanallar.
        registerMainAPI(DarknesLordProvider())

        // Eklentinin yanındaki dişli simgesine basınca açılır.
        openSettings = { ctx -> SettingsDialog.show(ctx) }
    }
}

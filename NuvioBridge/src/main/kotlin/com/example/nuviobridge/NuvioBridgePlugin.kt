package com.example.nuviobridge

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NuvioBridgePlugin : Plugin() {
    override fun load(context: Context) {
        RepoStore.init(context)
        registerMainAPI(NuvioBridgeProvider())

        // Eklentinin yanındaki dişli simgesine basınca açılır.
        openSettings = { ctx -> SettingsDialog.show(ctx) }
    }
}

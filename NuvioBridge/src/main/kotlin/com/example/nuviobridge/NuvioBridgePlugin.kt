package com.example.nuviobridge

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NuvioBridgePlugin : Plugin() {
    override fun load(context: Context) {
        RepoStore.init(context)

        // Genel kaynak: tüm etkin sağlayıcılardan link toplar.
        registerMainAPI(NuvioBridgeProvider())

        // İsteğe bağlı: her sağlayıcı kaynak seçicide kendi adıyla ayrı bir kaynak olarak görünür.
        // Repo/sağlayıcı değişiklikleri Cloudstream yeniden başlatılınca burada yansır.
        if (RepoStore.splitEnabled()) {
            RepoStore.enabledScripts().forEach { registerMainAPI(NuvioBridgeProvider(it)) }
        }

        // Eklentinin yanındaki dişli simgesine basınca açılır.
        openSettings = { ctx -> SettingsDialog.show(ctx) }
    }
}

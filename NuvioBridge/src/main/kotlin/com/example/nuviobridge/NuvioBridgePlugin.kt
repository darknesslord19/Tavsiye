package com.example.nuviobridge

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NuvioBridgePlugin : Plugin() {
    override fun load(context: Context) {
        RepoStore.init(context)
        registerMainAPI(NuvioBridgeProvider())

        // Eklentinin yanındaki dişli simgesine basınca açılır.
        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            SettingsFragment().show(activity.supportFragmentManager, "NuvioBridgeSettings")
        }
    }
}

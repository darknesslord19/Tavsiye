package com.darkneslord.iptv

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IPTVPlugin : Plugin() {
    override fun load(context: Context) {
        Store.init(context)
        registerMainAPI(IPTVProvider())

        // Eklentiler ekranındaki dişli simgesi -> yönetim paneli
        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null) {
                DarknesSettingsFragment().show(activity.supportFragmentManager, "darknes_lord_settings")
            }
        }
    }
}

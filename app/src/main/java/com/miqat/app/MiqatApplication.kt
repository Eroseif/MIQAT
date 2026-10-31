package com.miqat.app

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class MiqatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val cfg = Configuration.getInstance()
        cfg.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        cfg.userAgentValue = packageName
        // Keep map tiles in the app cache so no storage permission is needed.
        val base = File(cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
    }
}

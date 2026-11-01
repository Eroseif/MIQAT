package com.miqat.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.miqat.app.zakat.ZakatWorker
import java.util.concurrent.TimeUnit
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

        // Refresh gold prices about every 6 hours while online (no personal data is sent).
        val request = PeriodicWorkRequestBuilder<ZakatWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(ZakatWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

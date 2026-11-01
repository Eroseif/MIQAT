package com.miqat.app.zakat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miqat.app.MainActivity
import com.miqat.app.R
import com.miqat.app.core.Prefs
import com.miqat.app.data.MiqatDb
import com.miqat.app.hijri.HijriCalendar
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Runs about every 6 hours when the phone is online: refreshes gold prices into the cache and, only if the
 * user switched the reminder on, notifies once when their zakat year (hawl) is complete.
 * It sends no personal data anywhere; it only downloads public prices.
 */
class ZakatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        try {
            val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
            val cacheDb = MiqatDb.build(app)
            try {
                PriceRepository(cacheDb.cache(), http).load(force = true)
            } finally {
                cacheDb.close()
            }
            checkReminder(app)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
        return Result.success()
    }

    private suspend fun checkReminder(app: Context) {
        val zdb = ZakatDb.build(app)
        try {
            val p = zdb.dao().profile() ?: return
            val start = p.hawlStartEpochDay ?: return
            if (!p.reminder) return
            val prefs = Prefs(app)
            val cal = HijriCalendar(prefs.calendarMethod, prefs.dayAdjustment)
            val due = HawlMath.dueDate(LocalDate.ofEpochDay(start), cal)
            if (HawlMath.isComplete(due, LocalDate.now()) && prefs.zakatNotifiedDue != due.toEpochDay()) {
                if (notifyDue(app)) prefs.zakatNotifiedDue = due.toEpochDay()
            }
        } finally {
            zdb.close()
        }
    }

    private fun notifyDue(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Zakat reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo_fg)
            .setContentTitle("Zakat year complete")
            .setContentText("Your lunar year (hawl) is complete. Open MIQAT to review your zakat.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
        return true
    }

    companion object {
        const val CHANNEL = "zakat_reminder"
        const val UNIQUE_NAME = "miqat-zakat-refresh"
    }
}

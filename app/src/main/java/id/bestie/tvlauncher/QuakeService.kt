package id.bestie.tvlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

/**
 * Foreground service that polls the BMKG open TEWS feed every 60 seconds and
 * raises popup / full-screen alerts for new earthquakes above the threshold.
 */
class QuakeService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureMonitorChannel()
        QuakeAlerter.ensureAlertChannel(this)
        val notif = Notification.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_monitor_running))
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            // If foreground start is refused (e.g. started at a restricted moment),
            // keep the polling thread alive as a plain background service.
        }
        running = true
        thread = HandlerThread("quake-poll").apply { start() }
        handler = Handler(thread!!.looper)
        handler?.post(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            pollOnce()
            handler?.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun pollOnce() {
        try {
            val json = Net.get(AUTOGEMPA_URL) ?: return
            val quake = QuakeParser.parseLatest(json) ?: return
            if (quake.dateTime.isBlank()) return
            val last = Prefs.lastQuakeDateTime(this)
            if (last.isEmpty()) {
                // First ever poll: take the current quake as the baseline, never alert for it.
                Prefs.setLastQuakeDateTime(this, quake.dateTime)
                return
            }
            if (quake.dateTime == last) return
            Prefs.setLastQuakeDateTime(this, quake.dateTime)
            if (quake.magnitude >= Prefs.threshold(this) || quake.isTsunami) {
                QuakeAlerter.alert(this, quake)
            }
        } catch (e: Exception) {
            // Network hiccups are ignored; the next poll retries.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        super.onDestroy()
    }

    private fun ensureMonitorChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_MONITOR) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_MONITOR,
                        getString(R.string.notif_channel_quake),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    companion object {
        const val AUTOGEMPA_URL = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json"
        private const val CHANNEL_MONITOR = "quake_monitor"
        private const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, QuakeService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
            }
        }
    }
}

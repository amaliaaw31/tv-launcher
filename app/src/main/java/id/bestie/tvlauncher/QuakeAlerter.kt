package id.bestie.tvlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Decides how a quake is surfaced: overlay popup, big red overlay, or AlertActivity fallback. */
object QuakeAlerter {
    const val CHANNEL_ALERT = "quake_alert"
    private const val NOTIF_ID_BIG = 2001

    fun alert(context: Context, quake: Quake) {
        val appCtx = context.applicationContext
        if (!quake.isBig) {
            OverlayPopup.showQuake(appCtx, quake)
            return
        }
        if (OverlayPopup.canShow(appCtx)) {
            OverlayPopup.showBig(appCtx, quake)
        } else {
            // Fallback when the overlay permission was not granted:
            // full-screen-intent notification that opens the red AlertActivity.
            launchAlertActivity(appCtx, quake)
            postFullScreenNotification(appCtx, quake)
        }
    }

    fun launchAlertActivity(context: Context, quake: Quake) {
        try {
            context.startActivity(alertIntent(context, quake))
        } catch (e: Exception) {
            // Background activity starts may be blocked on Android 14; the
            // full-screen notification remains as the delivery path.
        }
    }

    fun alertIntent(context: Context, quake: Quake): Intent =
        Intent(context, AlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Quake.EXTRA_MAG, OverlayPopup.formatMag(quake.magnitude))
            putExtra(Quake.EXTRA_WILAYAH, quake.wilayah)
            putExtra(Quake.EXTRA_KEDALAMAN, quake.kedalaman)
            putExtra(Quake.EXTRA_JAM, quake.jam)
            putExtra(Quake.EXTRA_POTENSI, quake.potensi)
        }

    fun ensureAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ALERT) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ALERT,
                    context.getString(R.string.notif_channel_quake),
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun postFullScreenNotification(context: Context, quake: Quake) {
        try {
            ensureAlertChannel(context)
            val ctx = LocaleHelper.wrap(context)
            val pending = PendingIntent.getActivity(
                context, 0, alertIntent(context, quake),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = Notification.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(ctx.getString(R.string.quake_big_title))
                .setContentText("M ${OverlayPopup.formatMag(quake.magnitude)} • ${quake.wilayah}")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pending, true)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID_BIG, notif)
        } catch (e: Exception) {
        }
    }
}
